package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.VehicleDao
import com.tcc.veiculotracker.data.local.entity.Vehicle
import kotlinx.coroutines.flow.Flow

class VehicleRepository(private val vehicleDao: VehicleDao) {

    fun getVehiclesByUser(userId: Long): Flow<List<Vehicle>> {
        return vehicleDao.getVehiclesByUser(userId)
    }

    fun getVehicleById(vehicleId: Long): Flow<Vehicle?> {
        return vehicleDao.getVehicleById(vehicleId)
    }

    suspend fun getVehiclesListByUser(userId: Long): List<Vehicle> {
        return vehicleDao.getVehiclesListByUser(userId)
    }

    suspend fun registerVehicle(vehicle: Vehicle): Long {
        return vehicleDao.insert(vehicle)
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        vehicleDao.update(vehicle)
    }

    suspend fun setBlocked(vehicleId: Long, blocked: Boolean) {
        vehicleDao.setBlocked(vehicleId, blocked)
    }

    suspend fun updateLocation(vehicleId: Long, lat: Double, lng: Double, speed: Double) {
        vehicleDao.updateLocation(vehicleId, lat, lng, speed)
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.delete(vehicle)
    }
}
