package com.tcc.veiculotracker.ui.screens.vehicle

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.local.entity.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VehicleDetailViewModel(
    application: Application,
    private val vehicleId: Long
) : AndroidViewModel(application) {

    private val vehicleRepository = (application as App).vehicleRepository

    private val _vehicle = MutableStateFlow<Vehicle?>(null)
    val vehicle: StateFlow<Vehicle?> = _vehicle.asStateFlow()

    init {
        loadVehicle()
    }

    private fun loadVehicle() {
        viewModelScope.launch {
            vehicleRepository.getVehicleById(vehicleId).collect { v ->
                _vehicle.value = v
            }
        }
    }
}

class VehicleDetailViewModelFactory(
    private val vehicleId: Long
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VehicleDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VehicleDetailViewModel(
                vehicleId = vehicleId,
                application = getApplication()
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    private fun getApplication(): Application {
        return App()
    }
}
