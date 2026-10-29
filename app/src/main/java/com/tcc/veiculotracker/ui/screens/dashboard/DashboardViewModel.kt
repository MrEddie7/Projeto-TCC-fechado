package com.tcc.veiculotracker.ui.screens.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardState(
    val userName: String = "",
    val totalVehicles: Int = 0,
    val activeVehicles: Int = 0,
    val blockedVehicles: Int = 0,
    val vehicles: List<VehicleModel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    // Edição/exclusão disparadas pelo cartão do veículo.
    val isSaving: Boolean = false,
    val feedback: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val vehicleRepository = (application as App).vehicleRepository
    private val syncManager = (application as App).syncManager
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        viewModelScope.launch {
            if (userId > 0) runCatching { syncManager.syncAccountToCloud(userId) }
            val backend = (application as App).backendRepository
            if (backend.isConfigured && userId > 0) {
                runCatching { syncManager.pushLocalVehiclesToBackend(userId) }
                runCatching { syncManager.syncVehiclesFromBackend() }
            } else {
                vehicleRepository.pullFromCloud(userId)
            }
        }
        loadDashboard()
    }

    /**
     * Edição cadastral do veículo, feita direto no cartão da aba principal.
     * Só os campos do cadastro vão para a API: posição e bloqueio continuam
     * sendo do rastreador (o PUT da API ignora esses campos de propósito).
     */
    fun updateVehicle(
        vehicle: VehicleModel,
        plate: String,
        brand: String,
        model: String,
        year: String,
        color: String
    ) {
        if (plate.isBlank() || brand.isBlank() || model.isBlank()) {
            _state.value = _state.value.copy(feedback = "Preencha placa, marca e modelo")
            return
        }
        val edited = vehicle.copy(
            plate = plate.trim().uppercase(),
            brand = brand.trim(),
            model = model.trim(),
            year  = year.trim().toIntOrNull() ?: 0,
            color = color.trim()
        )
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, feedback = null)
            vehicleRepository.updateVehicle(edited).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isSaving = false, feedback = "Veículo atualizado")
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        feedback = e.message ?: "Não foi possível salvar as alterações"
                    )
                }
            )
        }
    }

    fun deleteVehicle(vehicle: VehicleModel) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, feedback = null)
            vehicleRepository.deleteVehicle(vehicle).fold(
                onSuccess = {
                    _state.value = _state.value.copy(isSaving = false, feedback = "Veículo excluído")
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(
                        isSaving = false,
                        feedback = e.message ?: "Não foi possível excluir o veículo"
                    )
                }
            )
        }
    }

    fun clearFeedback() { _state.value = _state.value.copy(feedback = null) }

    private fun loadDashboard() {
        val userName = prefs.getString(Constants.KEY_USER_NAME, "Usuário") ?: "Usuário"
        _state.value = _state.value.copy(userName = userName)

        viewModelScope.launch {
            vehicleRepository.getVehiclesByUser(userId)
                .catch { e ->
                    // Ex.: PERMISSION_DENIED nas regras do Firestore. Não deve
                    // derrubar o app: encerra o loading e mostra a frota vazia.
                    _state.value = _state.value.copy(
                        vehicles  = emptyList(),
                        isLoading = false,
                        error     = e.message ?: "Erro ao carregar veículos"
                    )
                }
                .collect { vehicles ->
                    _state.value = _state.value.copy(
                        vehicles        = vehicles,
                        totalVehicles   = vehicles.size,
                        activeVehicles  = vehicles.count { !it.isBlocked },
                        blockedVehicles = vehicles.count { it.isBlocked },
                        isLoading       = false,
                        error           = null
                    )
                }
        }
    }
}
