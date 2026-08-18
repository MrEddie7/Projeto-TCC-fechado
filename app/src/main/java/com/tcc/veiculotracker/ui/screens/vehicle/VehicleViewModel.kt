package com.tcc.veiculotracker.ui.screens.vehicle

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.data.repository.VehicleRepository
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

    private val db = AppDatabase.getInstance(application)
    private val repository = VehicleRepository(db.vehicleDao())

    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    private val _registerState = MutableStateFlow(VehicleRegisterState())
    val registerState: StateFlow<VehicleRegisterState> = _registerState

    private val _listState = MutableStateFlow(VehicleListState())
    val listState: StateFlow<VehicleListState> = _listState

    val vehicles: StateFlow<List<Vehicle>> = repository.getVehiclesByUser(userId)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun registerVehicle(plate: String, model: String, brand: String, year: String, color: String) {
        if (plate.isBlank() || model.isBlank() || brand.isBlank()) {
            _registerState.value = _registerState.value.copy(error = "Preencha os campos obrigatórios (placa, modelo, marca)")
            return
        }

        viewModelScope.launch {
            _registerState.value = _registerState.value.copy(isLoading = true, error = null)
            try {
                val vehicle = Vehicle(
                    userId = userId,
                    plate = plate.trim().uppercase(),
                    model = model.trim(),
                    brand = brand.trim(),
                    year = year.toIntOrNull() ?: 0,
                    color = color.trim()
                )
                repository.registerVehicle(vehicle)
                _registerState.value = VehicleRegisterState(isRegistered = true)
            } catch (e: Exception) {
                _registerState.value = VehicleRegisterState(error = e.message ?: "Erro ao cadastrar veículo")
            }
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
