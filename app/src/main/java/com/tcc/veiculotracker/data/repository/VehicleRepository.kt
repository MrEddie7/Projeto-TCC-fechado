package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.VehicleDao
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class VehicleRepository(
    private val vehicleDao: VehicleDao,
    private val syncManager: SyncManager
) {

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
        val id = vehicleDao.insert(vehicle)
        syncManager.syncVehicle(vehicle.copy(id = id))
        return id
    }

    /**
     * Importa os veículos do Firestore e mescla com o Room.
     * Veículos já existentes localmente (mesmo id) são preservados, evitando
     * sobrescrever dados locais; os demais são inseridos.
     */
    suspend fun pullFromCloud(userId: Long) {
        val remote = syncManager.pullVehicles(userId)
        if (remote.isEmpty()) return
        val localIds = vehicleDao.getVehiclesListByUser(userId).map { it.id }.toSet()
        remote.filter { it.id !in localIds }.forEach { vehicleDao.insert(it) }
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        vehicleDao.update(vehicle)
        syncManager.syncVehicle(vehicle)
    }

    suspend fun setBlocked(vehicleId: Long, blocked: Boolean) {
        vehicleDao.setBlocked(vehicleId, blocked)
        getVehicleById(vehicleId).first()?.let { syncManager.syncVehicle(it) }
    }

    suspend fun updateLocation(vehicleId: Long, lat: Double, lng: Double, speed: Double) {
        vehicleDao.updateLocation(vehicleId, lat, lng, speed)
        getVehicleById(vehicleId).first()?.let { syncManager.syncVehicle(it) }
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        vehicleDao.delete(vehicle)
        syncManager.deleteVehicle(vehicle.id)
    }
}
