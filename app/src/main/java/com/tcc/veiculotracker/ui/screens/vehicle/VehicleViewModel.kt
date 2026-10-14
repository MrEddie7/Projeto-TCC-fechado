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

data class VehicleRegisterState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val error: String? = null
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
        // Ao abrir a lista de veículos, garante que veículos cadastrados apenas
        // localmente também sejam enviados ao backend e apareçam no painel do
        // administrador (best-effort; sem backend configurado, não faz nada).
        if (backend.isConfigured && userId > 0) {
            viewModelScope.launch { backend.pushLocalVehiclesToBackend(userId) }
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
            val result: Result<Unit> = if (backend.isConfigured) {
                backend.createVehicle(plateStr, modelStr, brandStr, yearInt, colorStr).map { }
            } else {
                runCatching {
                    repository.registerVehicle(
                        Vehicle(
                            userId = userId,
                            plate = plateStr,
                            model = modelStr,
                            brand = brandStr,
                            year = yearInt,
                            color = colorStr
                        )
                    )
                }.map { }
            }

            result.fold(
                onSuccess = {
                    _registerState.value = VehicleRegisterState(isRegistered = true)
                },
                onFailure = { e ->
                    _registerState.value = VehicleRegisterState(error = e.message ?: "Erro ao cadastrar veículo")
                }
            )
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
