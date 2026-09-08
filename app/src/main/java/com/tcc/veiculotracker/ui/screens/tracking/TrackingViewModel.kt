package com.tcc.veiculotracker.ui.screens.tracking

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TrackingState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val isTracking: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null
)

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val vehicleRepository = app.vehicleRepository
    private val syncManager = app.syncManager
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state

    private var telemetryJob: Job? = null

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
    }

    private fun startListening(vehicle: Vehicle) {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
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
    }

    fun stopTracking() {
        _state.value = _state.value.copy(isTracking = false)
        stopListening()
    }

    private fun stopListening() {
        telemetryJob?.cancel()
        telemetryJob = null
    }

    override fun onCleared() {
        stopListening()
        super.onCleared()
    }
}
