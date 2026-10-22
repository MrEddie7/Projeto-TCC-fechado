package com.tcc.veiculotracker.ui.screens.vehicle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.data.remote.ApiClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Estado da tela de detalhe: o veículo (Firestore) e o rastreador vinculado
 * (API). São duas fontes: o Firestore dá o cadastro, a API diz se existe
 * GPS real, se o aparelho está conectado e qual a última posição enviada.
 */
data class VehicleDetailState(
    val vehicle: VehicleModel? = null,
    val device: ApiClient.DeviceDto? = null,
    val telemetry: ApiClient.TelemetryDto? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null
)

class VehicleDetailViewModel(
    application: Application,
    private val vehicleId: Long
) : AndroidViewModel(application) {

    private val app = application as App
    private val vehicleRepository = app.vehicleRepository
    private val syncManager = app.syncManager

    private val _state = MutableStateFlow(VehicleDetailState())
    val state: StateFlow<VehicleDetailState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        loadVehicle()
        startTrackerRefresh()
    }

    private fun loadVehicle() {
        viewModelScope.launch {
            vehicleRepository.getVehiclesByUser(-1)
                .catch { _state.value = _state.value.copy(isLoading = false) }
                .collect { list ->
                    _state.value = _state.value.copy(
                        vehicle = list.firstOrNull { it.id == vehicleId },
                        isLoading = false
                    )
                }
        }
    }

    /**
     * Consulta o rastreador vinculado e a última telemetria.
     * Sem API configurada o app continua funcionando com os dados do Firestore
     * (mas sem saber o estado do GPS).
     */
    fun startTrackerRefresh() {
        val backend = syncManager.backendRepository ?: return
        if (!backend.isConfigured) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshTracker(backend)
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refreshNow() {
        val backend = syncManager.backendRepository ?: return
        if (!backend.isConfigured) return
        viewModelScope.launch { refreshTracker(backend) }
    }

    private suspend fun refreshTracker(backend: com.tcc.veiculotracker.data.repository.BackendRepository) {
        _state.value = _state.value.copy(isRefreshing = true, error = null)
        backend.getVehicleDetail(vehicleId)
            .onSuccess { detail ->
                val fromApi = with(backend) { detail.vehicle.toModel() }
                _state.value = _state.value.copy(
                    device = detail.device,
                    // O Firestore manda na tela, mas a API é a fonte do
                    // rastreador: se o cache local estiver vazio, usa a API.
                    vehicle = _state.value.vehicle ?: fromApi
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(error = e.message ?: "Erro ao consultar o rastreador")
            }
        backend.getLatestTelemetry(vehicleId)
            .onSuccess { t -> _state.value = _state.value.copy(telemetry = t) }
            .onFailure { /* telemetria ausente não é erro fatal */ }
        _state.value = _state.value.copy(isRefreshing = false)
    }

    override fun onCleared() {
        refreshJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val REFRESH_INTERVAL_MS = 15_000L
    }
}

class VehicleDetailViewModelFactory(
    private val application: Application,
    private val vehicleId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VehicleDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VehicleDetailViewModel(
                vehicleId = vehicleId,
                application = application
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
