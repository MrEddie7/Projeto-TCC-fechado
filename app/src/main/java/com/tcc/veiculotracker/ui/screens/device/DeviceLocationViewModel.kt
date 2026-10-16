package com.tcc.veiculotracker.ui.screens.device

import android.app.Application
import android.location.Location
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.remote.LocationService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeviceLocationState(
    val currentLocation: Location? = null,
    val isTracking: Boolean = false,
    val isSendingToBackend: Boolean = false,
    val isAutoSend: Boolean = false,
    val lastSentTime: Long = 0L,
    val sendStatus: String? = null,
    val error: String? = null,
    val hasPermission: Boolean = false
)

class DeviceLocationViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as App
    private val locationService = app.locationService
    private val backendRepository = app.backendRepository

    private val _state = MutableStateFlow(DeviceLocationState())
    val state: StateFlow<DeviceLocationState> = _state.asStateFlow()

    private var locationJob: Job? = null
    private var sendJob: Job? = null

    companion object {
        private const val TAG = "DeviceLocationVM"
        private const val SEND_INTERVAL_MS = 15000L // Envia a cada 15 segundos
    }

    init {
        checkPermission()
    }

    fun checkPermission() {
        _state.value = _state.value.copy(
            hasPermission = locationService.hasLocationPermission()
        )
    }

    fun startTracking() {
        if (!locationService.hasLocationPermission()) {
            _state.value = _state.value.copy(error = "Permissão de localização necessária")
            return
        }

        _state.value = _state.value.copy(isTracking = true, error = null)
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationService.requestLocationUpdates().collect { location ->
                _state.value = _state.value.copy(currentLocation = location)
                Log.d(TAG, "Localização atualizada: ${location.latitude}, ${location.longitude}")
            }
        }

        // Ao iniciar o rastreamento, já liga o envio automático para a API:
        // assim o celular aparece como "conectado/ativo" no painel sem exigir
        // que o usuário ligue o switch manualmente.
        if (backendRepository.isConfigured) {
            setAutoSend(true)
        }
    }

    fun stopTracking() {
        _state.value = _state.value.copy(isTracking = false)
        locationJob?.cancel()
        locationJob = null
        if (_state.value.isAutoSend) setAutoSend(false)
    }

    fun sendLocationToBackend() {
        val location = _state.value.currentLocation ?: run {
            _state.value = _state.value.copy(error = "Nenhuma localização disponível")
            return
        }

        if (!backendRepository.isConfigured) {
            _state.value = _state.value.copy(error = "Backend não configurado. Configure a URL da API.")
            return
        }

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            _state.value = _state.value.copy(isSendingToBackend = true, sendStatus = null)
            try {
                val result = locationService.sendLocationToBackend(
                    backendRepository.apiClient,
                    location
                )
                result.onSuccess {
                    _state.value = _state.value.copy(
                        isSendingToBackend = false,
                        lastSentTime = System.currentTimeMillis(),
                        sendStatus = "Localização enviada com sucesso",
                        error = null
                    )
                }.onFailure { e ->
                    _state.value = _state.value.copy(
                        isSendingToBackend = false,
                        sendStatus = "Falha ao enviar",
                        error = e.message
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSendingToBackend = false,
                    sendStatus = "Erro",
                    error = e.message
                )
            }
        }
    }

    fun startAutoSend() {
        if (!backendRepository.isConfigured) {
            _state.value = _state.value.copy(error = "Backend não configurado")
            return
        }

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            while (true) {
                val location = _state.value.currentLocation
                if (location != null) {
                    val result = locationService.sendLocationToBackend(
                        backendRepository.apiClient,
                        location
                    )
                    result.onSuccess {
                        _state.value = _state.value.copy(
                            lastSentTime = System.currentTimeMillis(),
                            sendStatus = "Enviado automaticamente"
                        )
                    }
                    // Depois do primeiro envio, mantém o intervalo normal.
                    kotlinx.coroutines.delay(SEND_INTERVAL_MS)
                } else {
                    // Ainda sem fix de GPS: tenta de novo logo para o celular
                    // aparecer conectado o quanto antes.
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }

    fun stopAutoSend() {
        sendJob?.cancel()
        sendJob = null
    }

    /**
     * Liga/desliga o envio automático da posição do celular para a API.
     * Com isso a API sempre conhece a posição atual do aparelho, permitindo
     * exibi-lo "junto do veículo" em qualquer cliente (app ou painel web).
     */
    fun setAutoSend(enabled: Boolean) {
        if (enabled) {
            if (!backendRepository.isConfigured) {
                _state.value = _state.value.copy(error = "Backend não configurado para envio automático")
                return
            }
            startAutoSend()
        } else {
            stopAutoSend()
        }
        _state.value = _state.value.copy(isAutoSend = enabled)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    override fun onCleared() {
        stopTracking()
        stopAutoSend()
        super.onCleared()
    }
}
