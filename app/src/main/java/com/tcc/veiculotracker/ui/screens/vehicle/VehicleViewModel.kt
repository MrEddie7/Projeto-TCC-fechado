package com.tcc.veiculotracker.ui.screens.vehicle

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SimulationPrompt(
    val vehicleId: Long,
    val recommended: Boolean,
    val reason: String
)

data class VehicleRegisterState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val error: String? = null,
    val pendingSimulation: SimulationPrompt? = null
)

data class VehicleListState(
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true
)

class VehicleViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).vehicleRepository
    private val backend = (application as App).backendRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    private val _registerState = MutableStateFlow(VehicleRegisterState())
    val registerState: StateFlow<VehicleRegisterState> = _registerState

    private val _listState = MutableStateFlow(VehicleListState())
    val listState: StateFlow<VehicleListState> = _listState

    val vehicles: StateFlow<List<Vehicle>> = repository.getVehiclesByUser(userId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        viewModelScope.launch {
            if (backend.isConfigured && userId > 0) {
                runCatching { backend.pushLocalVehiclesToBackend(userId) }
                runCatching { backend.syncVehiclesFromBackend() }
            }
        }
    }

    fun registerVehicle(plate: String, model: String, brand: String, year: String, color: String) {
        if (plate.isBlank() || model.isBlank() || brand.isBlank()) {
            _registerState.value = _registerState.value.copy(error = "Preencha os campos obrigatórios (placa, modelo, marca)")
            return
        }

        viewModelScope.launch {
            _registerState.value = _registerState.value.copy(isLoading = true, error = null)
            val plateStr = plate.trim().uppercase()
            val modelStr = model.trim()
            val brandStr = brand.trim()
            val colorStr = color.trim()
            val yearInt = year.toIntOrNull() ?: 0

            // Com o backend (API Node) configurado, cadastra o veículo na API —
            // que é a fonte da verdade — para que ele apareça também no painel
            // do administrador. O BackendRepository salva o veículo retornado
            // (com o id do servidor) no Room. Sem backend, mantém o cadastro
            // apenas local (modo offline).
            val created: Result<Vehicle> = if (backend.isConfigured) {
                backend.createVehicle(plateStr, modelStr, brandStr, yearInt, colorStr)
            } else {
                runCatching {
                    val id = repository.registerVehicle(
                        Vehicle(
                            userId = userId,
                            plate = plateStr,
                            model = modelStr,
                            brand = brandStr,
                            year = yearInt,
                            color = colorStr
                        )
                    )
                    Vehicle(
                        id = id,
                        userId = userId,
                        plate = plateStr,
                        model = modelStr,
                        brand = brandStr,
                        year = yearInt,
                        color = colorStr
                    )
                }
            }

            created.fold(
                onSuccess = { vehicle ->
                    // Pergunta ao backend se este veículo deve entrar na
                    // simulação (frota simulada). A resposta vira um prompt
                    // Sim/Não para o usuário confirmar e enviar de volta.
                    if (backend.isConfigured) {
                        val decision = runCatching {
                            backend.getSimulationDecision(vehicle.id).getOrNull()
                        }.getOrNull()
                        if (decision != null &&
                            decision.simulationAvailable &&
                            !decision.simulated &&
                            decision.recommended
                        ) {
                            _registerState.value = VehicleRegisterState(
                                pendingSimulation = SimulationPrompt(
                                    vehicleId = vehicle.id,
                                    recommended = decision.recommended,
                                    reason = decision.reason
                                )
                            )
                            return@fold
                        }
                    }
                    _registerState.value = VehicleRegisterState(isRegistered = true)
                },
                onFailure = { e ->
                    _registerState.value = VehicleRegisterState(error = e.message ?: "Erro ao cadastrar veículo")
                }
            )
        }
    }

    /**
     * Envia ao backend a escolha do usuário sobre adicionar o veículo à
     * simulação e conclui o cadastro.
     */
    fun answerSimulation(simulate: Boolean) {
        val prompt = _registerState.value.pendingSimulation ?: return
        viewModelScope.launch {
            _registerState.value = _registerState.value.copy(isLoading = true, pendingSimulation = null)
            runCatching { backend.setVehicleSimulation(prompt.vehicleId, simulate).getOrNull() }
            _registerState.value = VehicleRegisterState(isRegistered = true)
        }
    }

    fun deleteVehicle(vehicle: Vehicle) {
        viewModelScope.launch {
            repository.deleteVehicle(vehicle)
        }
    }

    fun clearRegisterError() {
        _registerState.value = _registerState.value.copy(error = null)
    }

    fun resetRegisterState() {
        _registerState.value = VehicleRegisterState()
    }
}
