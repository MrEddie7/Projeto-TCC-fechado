package com.tcc.veiculotracker.ui.screens.tracking

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.data.repository.VehicleRepository
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TrackingState(
    val vehicles: List<Vehicle> = emptyList(),
    val selectedVehicle: Vehicle? = null,
    val currentLatitude: Double = 0.0,
    val currentLongitude: Double = 0.0,
    val isTracking: Boolean = false,
    val isLoading: Boolean = true
)

class TrackingViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val vehicleRepository = VehicleRepository(db.vehicleDao())
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state

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
        _state.value = _state.value.copy(
            selectedVehicle = vehicle,
            currentLatitude = vehicle.latitude,
            currentLongitude = vehicle.longitude
        )
    }

    fun startTracking() {
        _state.value = _state.value.copy(isTracking = true)
        viewModelScope.launch {
            while (_state.value.isTracking) {
                val vehicle = _state.value.selectedVehicle ?: break
                // Simula atualização de localização - substituir por GPS real
                val lat = vehicle.latitude + (-0.001..0.001).random()
                val lng = vehicle.longitude + (-0.001..0.001).random()
                vehicleRepository.updateLocation(vehicle.id, lat, lng, (0.0..120.0).random())
                delay(5000L)
            }
        }
    }

    fun stopTracking() {
        _state.value = _state.value.copy(isTracking = false)
    }
}
