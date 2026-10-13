package com.tcc.veiculotracker.ui.screens.tracking

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TrackingState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val isTracking: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    // ── Localização do celular (rastreada JUNTO com o veículo) ──
    val phoneLatitude: Double = 0.0,
    val phoneLongitude: Double = 0.0,
    val isPhoneVisible: Boolean = false,
    val phonePermissionGranted: Boolean = false,
    val isPhoneSending: Boolean = false,
    val phoneLastSentAt: Long = 0L
)

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val vehicleRepository = app.vehicleRepository
    private val syncManager = app.syncManager
    private val locationService = app.locationService
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state

    private var telemetryJob: Job? = null
    private var phoneJob: Job? = null

    companion object {
        private const val POLLING_INTERVAL_MS = 5000L // 5 segundos
        private const val PHONE_SEND_INTERVAL_MS = 15000L // 15 segundos
    }

    init {
        loadVehicles()
    }

    private fun loadVehicles() {
        viewModelScope.launch {
            vehicleRepository.getVehiclesByUser(userId).collect { vehicles ->
                _state.value = _state.value.copy(vehicles = vehicles, isLoading = false)
            }
        }
    }

    fun selectVehicle(vehicle: Vehicle) {
        stopListening()
        stopPhoneTracking()
        _state.value = _state.value.copy(
            selectedVehicle = vehicle,
            currentLatitude = vehicle.latitude,
            currentLongitude = vehicle.longitude
        )
    }

    fun startTracking() {
        val vehicle = _state.value.selectedVehicle ?: return
        _state.value = _state.value.copy(isTracking = true)
        startListening(vehicle)
        startPhoneTracking()
    }

    private fun startListening(vehicle: Vehicle) {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            // Tenta usar o backend primeiro (mais confiável)
            if (syncManager.backendRepository?.isConfigured == true) {
                startBackendPolling(vehicle)
            } else {
                // Fallback para o Firebase Realtime Database
                startFirebaseListening(vehicle)
            }
        }
    }

    /**
     * Obtém a telemetria do backend via polling.
     * O backend é a fonte da verdade para veículos, telemetria e comandos.
     */
    private suspend fun startBackendPolling(vehicle: Vehicle) {
        while (true) {
            try {
                val result = syncManager.getLatestTelemetryFromBackend(vehicle.id)
                result.onSuccess { telemetry ->
                    if (telemetry != null) {
                        _state.value = _state.value.copy(
                            currentLatitude = telemetry.latitude,
                            currentLongitude = telemetry.longitude,
                            error = null
                        )
                        // Persiste a última posição recebida no Room
                        vehicleRepository.updateLocation(
                            vehicle.id,
                            telemetry.latitude,
                            telemetry.longitude,
                            telemetry.speed
                        )
                    }
                }.onFailure { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Erro ao obter telemetria do backend"
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    error = e.message ?: "Erro desconhecido na telemetria"
                )
            }
            delay(POLLING_INTERVAL_MS)
        }
    }

    /**
     * Fallback: escuta a telemetria do Firebase Realtime Database.
     * Usado quando o backend não está configurado.
     */
    private suspend fun startFirebaseListening(vehicle: Vehicle) {
        syncManager.listenTelemetry(vehicle.id)
            .catch { e ->
                _state.value = _state.value.copy(
                    error = e.message ?: "Erro desconhecido na telemetria",
                    isTracking = false
                )
            }
            .collect { telemetry ->
                _state.value = _state.value.copy(
                    currentLatitude = telemetry.latitude,
                    currentLongitude = telemetry.longitude,
                    error = null
                )
                // Persiste a última posição recebida no Room (e propaga para o Firestore)
                vehicleRepository.updateLocation(
                    vehicle.id,
                    telemetry.latitude,
                    telemetry.longitude,
                    telemetry.speed
                )
            }
    }

    /**
     * Rastreia o CELULAR junto do veículo:
     *  1. carrega a última posição já enviada à API (para fixar o marcador já);
     *  2. coleta o GPS do aparelho em tempo real;
     *  3. envia a posição para a API a cada 15s (a API passa a conhecer os dois).
     */
    private fun startPhoneTracking() {
        if (!hasLocationPermission()) {
            _state.value = _state.value.copy(
                phonePermissionGranted = false,
                error = "Permissão de localização necessária para rastrear o celular junto do veículo"
            )
            return
        }
        _state.value = _state.value.copy(phonePermissionGranted = true)

        // 1. Última posição conhecida no backend (marcador aparece mesmo antes do GPS fixar).
        viewModelScope.launch {
            if (syncManager.backendRepository?.isConfigured == true) {
                syncManager.getLatestMobileLocationFromBackend()
                    .onSuccess { location ->
                        if (location != null && !_state.value.isPhoneVisible) {
                            _state.value = _state.value.copy(
                                phoneLatitude = location.latitude,
                                phoneLongitude = location.longitude,
                                isPhoneVisible = true
                            )
                        }
                    }
            }
        }

        // 2 + 3. GPS em tempo real + envio periódico à API.
        phoneJob?.cancel()
        phoneJob = viewModelScope.launch {
            var lastSent = 0L
            locationService.requestLocationUpdates()
                .onEach { location ->
                    _state.value = _state.value.copy(
                        phoneLatitude = location.latitude,
                        phoneLongitude = location.longitude,
                        isPhoneVisible = true,
                        error = null
                    )

                    val now = System.currentTimeMillis()
                    val canSend = now - lastSent >= PHONE_SEND_INTERVAL_MS
                    val backend = syncManager.backendRepository
                    if (canSend && backend != null && backend.isConfigured) {
                        lastSent = now
                        _state.value = _state.value.copy(isPhoneSending = true)
                        locationService.sendLocationToBackend(backend.apiClient, location)
                            .onSuccess {
                                _state.value = _state.value.copy(
                                    isPhoneSending = false,
                                    phoneLastSentAt = now
                                )
                            }
                            .onFailure {
                                _state.value = _state.value.copy(isPhoneSending = false)
                            }
                    }
                }
                .catch { e ->
                    _state.value = _state.value.copy(
                        error = e.message ?: "Erro ao obter localização do celular"
                    )
                }
                .collect()
        }
    }

    private fun stopPhoneTracking() {
        phoneJob?.cancel()
        phoneJob = null
    }

    fun stopTracking() {
        _state.value = _state.value.copy(isTracking = false)
        stopListening()
        stopPhoneTracking()
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            getApplication(),
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun stopListening() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    override fun onCleared() {
        stopListening()
        stopPhoneTracking()
        super.onCleared()
    }
}