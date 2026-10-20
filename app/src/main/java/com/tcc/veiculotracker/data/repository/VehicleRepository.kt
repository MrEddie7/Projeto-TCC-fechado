package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * Repositório de veículos sem Room. Fonte da verdade = Firestore.
 * Cache offline em JSON atualizado sempre que novos dados chegam do Firestore.
 */
class VehicleRepository(
    private val syncManager: SyncManager
) {

    /** Flow em tempo real dos veículos do usuário (Firestore). */
    fun getVehiclesByUser(userId: Long): Flow<List<VehicleModel>> =
        syncManager.getVehiclesByUser(userId)
            .onEach { syncManager.refreshOfflineCache(it) }

    /** Veículos do cache offline (JSON). Usado quando sem rede. */
    fun getOfflineVehicles(): List<VehicleModel> = syncManager.loadOfflineVehicles()

    /** Tempo desde a última atualização do cache, em ms. */
    fun offlineCacheAgeMs(): Long = syncManager.offlineCacheAgeMs()

    suspend fun registerVehicle(vehicle: VehicleModel): VehicleModel {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            val result = runCatching {
                backend.createVehicle(
                    vehicle.plate, vehicle.model, vehicle.brand, vehicle.year, vehicle.color
                )
            }.getOrNull()
            val created = result?.getOrNull()
            if (created != null) {
                runCatching { syncManager.syncVehicle(created) }
                return created
            }
        }
        // Sem backend: grava direto no Firestore.
        runCatching { syncManager.syncVehicle(vehicle) }
        return vehicle
    }

    suspend fun pullFromCloud(userId: Long) {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { syncManager.pushLocalVehiclesToBackend(userId) }
            runCatching { syncManager.syncVehiclesFromBackend() }
            return
        }
        val remote = syncManager.pullVehicles(userId)
        syncManager.refreshOfflineCache(remote)
    }

    suspend fun updateVehicle(vehicle: VehicleModel) {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { backend.updateVehicle(vehicle) }
        }
        runCatching { syncManager.syncVehicle(vehicle) }
    }

    suspend fun setBlocked(vehicleId: Long, blocked: Boolean, vehicle: VehicleModel) {
        val updated = vehicle.copy(isBlocked = blocked)
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching {
                backend.updateVehicle(vehicleId, mapOf(
                    "plate"     to vehicle.plate,
                    "model"     to vehicle.model,
                    "brand"     to vehicle.brand,
                    "year"      to vehicle.year,
                    "color"     to vehicle.color,
                    "isBlocked" to blocked
                ))
            }
        }
        runCatching { syncManager.syncVehicle(updated) }
    }

    suspend fun updateLocation(vehicleId: Long, lat: Double, lng: Double, speed: Double, vehicle: VehicleModel) {
        val updated = vehicle.copy(
            latitude   = lat,
            longitude  = lng,
            speed      = speed,
            lastUpdate = System.currentTimeMillis()
        )
        runCatching { syncManager.syncVehicle(updated) }
    }

    suspend fun deleteVehicle(vehicle: VehicleModel) {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { backend.deleteVehicle(vehicle.id) }
        }
        runCatching { syncManager.deleteVehicle(vehicle.id) }
    }
}
