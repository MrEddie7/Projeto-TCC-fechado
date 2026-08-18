package com.tcc.veiculotracker.ui.screens.remote

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

data class RemoteControlState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val isExecuting: Boolean = false,
    val lastAction: String? = null,
    val error: String? = null,
    val isLoading: Boolean = true
)

class RemoteControlViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val vehicleRepository = VehicleRepository(db.vehicleDao())
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(RemoteControlState())
    val state: StateFlow<RemoteControlState> = _state

    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

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
        _state.value = _state.value.copy(selectedVehicle = vehicle)
    }

    fun blockVehicle() {
        val vehicle = _state.value.selectedVehicle ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, error = null)
            try {
                vehicleRepository.setBlocked(vehicle.id, true)
                _state.value = _state.value.copy(
                    isExecuting = false,
                    lastAction = "Veículo bloqueado com sucesso"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExecuting = false,
                    error = "Erro ao bloquear veículo"
                )
            }
        }
    }

    fun unblockVehicle() {
        val vehicle = _state.value.selectedVehicle ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isExecuting = true, error = null)
            try {
                vehicleRepository.setBlocked(vehicle.id, false)
                _state.value = _state.value.copy(
                    isExecuting = false,
                    lastAction = "Veículo desbloqueado com sucesso"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExecuting = false,
                    error = "Erro ao desbloquear veículo"
                )
            }
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, lastAction = null)
    }
}
