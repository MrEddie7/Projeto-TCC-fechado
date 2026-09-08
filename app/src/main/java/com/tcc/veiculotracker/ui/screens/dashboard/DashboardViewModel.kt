package com.tcc.veiculotracker.ui.screens.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardState(
    val userName: String = "",
    val totalVehicles: Int = 0,
    val activeVehicles: Int = 0,
    val blockedVehicles: Int = 0,
    val vehicles: List<Vehicle> = emptyList(),
    val isLoading: Boolean = true
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val vehicleRepository = (application as App).vehicleRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        viewModelScope.launch {
            vehicleRepository.pullFromCloud(userId)
        }
        loadDashboard()
    }

    private fun loadDashboard() {
        val userName = prefs.getString(Constants.KEY_USER_NAME, "Usuário") ?: "Usuário"
        _state.value = _state.value.copy(userName = userName)

        viewModelScope.launch {
            vehicleRepository.getVehiclesByUser(userId).collect { vehicles ->
                _state.value = _state.value.copy(
                    vehicles = vehicles,
                    totalVehicles = vehicles.size,
                    activeVehicles = vehicles.count { !it.isBlocked },
                    blockedVehicles = vehicles.count { it.isBlocked },
                    isLoading = false
                )
            }
        }
    }
}
