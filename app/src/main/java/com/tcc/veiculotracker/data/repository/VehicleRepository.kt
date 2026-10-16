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
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { backend.createVehicle(vehicle.plate, vehicle.model, vehicle.brand, vehicle.year, vehicle.color) }
                .onSuccess { result ->
                    result.onSuccess { dto ->
                        vehicleDao.delete(vehicle.copy(id = id))
                        val v = Vehicle(
                            id = dto.id,
                            userId = dto.userId,
                            plate = dto.plate,
                            model = dto.model,
                            brand = dto.brand,
                            year = dto.year,
                            color = dto.color,
                            isBlocked = dto.isBlocked,
                            latitude = dto.latitude,
                            longitude = dto.longitude,
                            speed = dto.speed,
                            lastUpdate = dto.lastUpdate,
                            createdAt = dto.createdAt
                        )
                        vehicleDao.insert(v)
                    }
                }
        } else {
            runCatching { syncManager.syncVehicle(vehicle.copy(id = id)) }
        }
        return id
    }

    /**
     * Importa os veículos do Firestore e mescla com o Room.
     * Veículos já existentes localmente (mesmo id) são preservados, evitando
     * sobrescrever dados locais; os demais são inseridos.
     */
    suspend fun pullFromCloud(userId: Long) {
        if (syncManager.backendRepository?.isConfigured == true) {
            runCatching { syncManager.pushLocalVehiclesToBackend(userId) }
            runCatching { syncManager.syncVehiclesFromBackend() }
            return
        }
        val remote = syncManager.pullVehicles(userId)
        if (remote.isEmpty()) return
        val localIds = vehicleDao.getVehiclesListByUser(userId).map { it.id }.toSet()
        remote.filter { it.id !in localIds }.forEach { vehicleDao.insert(it) }
    }

    suspend fun updateVehicle(vehicle: Vehicle) {
        vehicleDao.update(vehicle)
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching {
                backend.updateVehicle(vehicle)
            }
        } else {
            runCatching { syncManager.syncVehicle(vehicle) }
        }
    }

    suspend fun setBlocked(vehicleId: Long, blocked: Boolean) {
        vehicleDao.setBlocked(vehicleId, blocked)
        val backend = syncManager.backendRepository
        getVehicleById(vehicleId).first()?.let { v ->
            if (backend != null && backend.isConfigured) {
                runCatching {
                    val updates = mutableMapOf<String, Any>()
                    updates["plate"] = v.plate
                    updates["model"] = v.model
                    updates["brand"] = v.brand
                    updates["year"] = v.year
                    updates["color"] = v.color
                    updates["isBlocked"] = v.isBlocked
                    backend.updateVehicle(v.id, updates)
                }
            } else {
                runCatching { syncManager.syncVehicle(v) }
            }
        }
    }

    suspend fun updateLocation(vehicleId: Long, lat: Double, lng: Double, speed: Double) {
        vehicleDao.updateLocation(vehicleId, lat, lng, speed)
        getVehicleById(vehicleId).first()?.let { syncManager.syncVehicle(it) }
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { backend.deleteVehicle(vehicle.id) }
        } else {
            runCatching { syncManager.deleteVehicle(vehicle.id) }
        }
        vehicleDao.delete(vehicle)
    }
}
