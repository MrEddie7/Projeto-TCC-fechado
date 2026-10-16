package com.tcc.veiculotracker.ui.screens.remote

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.local.entity.Vehicle
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

    private val app = application as App
    private val vehicleRepository = app.vehicleRepository
    private val syncManager = app.syncManager
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(RemoteControlState())
    val state: StateFlow<RemoteControlState> = _state

    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        viewModelScope.launch {
            val backend = syncManager.backendRepository
            if (backend != null && backend.isConfigured && userId > 0) {
                runCatching { syncManager.pushLocalVehiclesToBackend(userId) }
                runCatching { syncManager.syncVehiclesFromBackend() }
            }
        }
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
                // Atualiza o Room (otimista) para refletir na hora.
                vehicleRepository.setBlocked(vehicle.id, true)
                dispatchCommand(vehicle.id, "block")
                _state.value = _state.value.copy(
                    isExecuting = false,
                    lastAction = "Comando de bloqueio enviado ao rastreador"
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
                dispatchCommand(vehicle.id, "unblock")
                _state.value = _state.value.copy(
                    isExecuting = false,
                    lastAction = "Comando de desbloqueio enviado ao rastreador"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isExecuting = false,
                    error = "Erro ao desbloquear veículo"
                )
            }
        }
    }

    /**
     * Envia o comando de controle remoto pelo canal correto:
     *  - com backend configurado, usa a fila do backend
     *    (`POST /v1/vehicles/:id/commands`), consumida e confirmada pelo
     *    rastreador — inclusive os veículos simulados pelo próprio backend;
     *  - sem backend, cai para o Realtime Database (modo offline do app).
     *
     * Sem isso o comando ia só para o RTDB, que ninguém consome, e o
     * dispositivo simulado nunca era efetivamente bloqueado.
     */
    private suspend fun dispatchCommand(vehicleId: Long, command: String) {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            syncManager.sendCommandToBackend(vehicleId, command).getOrThrow()
        } else {
            syncManager.sendCommand(vehicleId, command)
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, lastAction = null)
    }
}
