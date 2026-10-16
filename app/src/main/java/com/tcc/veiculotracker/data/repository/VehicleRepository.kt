package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.VehicleDao
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

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
        var id = vehicleDao.insert(vehicle)
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { backend.createVehicle(vehicle.plate, vehicle.model, vehicle.brand, vehicle.year, vehicle.color) }
                .onSuccess { result ->
                    result.onSuccess { dto ->
                        // Bug 4 fix: substitui o registro local pelo do backend de forma
                        // atômica — insere primeiro, só depois apaga o temporário.
                        // Assim nunca há janela em que o veículo desaparece do banco.
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
                        val newId = vehicleDao.insert(v)   // insert primeiro
                        vehicleDao.deleteById(id)           // apaga temporário depois
                        id = newId
                    }
                }
        }
        // Sempre replica para o Firestore (vinculado à conta logada), inclusive
        // quando o backend está configurado: é o que o painel exibe.
        vehicleDao.getVehicleByIdOnce(id)?.let {
            runCatching { syncManager.syncVehicle(it) }
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
            // Mesmo com o backend configurado, a frota local é enviada ao
            // Firestore vinculada à conta logada, para o painel do backend.
            runCatching { syncManager.syncVehiclesToCloud(userId) }
            return
        }
        // Envia os veículos locais à nuvem antes de importar os remotos.
        runCatching { syncManager.syncVehiclesToCloud(userId) }
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
        }
        // Sempre replica ao Firestore (conta logada + veículos) para o painel.
        runCatching { syncManager.syncVehicle(vehicle) }
    }

    suspend fun setBlocked(vehicleId: Long, blocked: Boolean) {
        vehicleDao.setBlocked(vehicleId, blocked)
        val backend = syncManager.backendRepository
        // Bug 3 fix: usa getVehicleByIdOnce (suspend) em vez de .first() num Flow,
        // garantindo que a leitura acontece após o setBlocked ter commitado no Room.
        vehicleDao.getVehicleByIdOnce(vehicleId)?.let { v ->
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
            }
            runCatching { syncManager.syncVehicle(v) }
        }
    }

    suspend fun updateLocation(vehicleId: Long, lat: Double, lng: Double, speed: Double) {
        vehicleDao.updateLocation(vehicleId, lat, lng, speed)
        // Bug 3 fix: consistente com setBlocked — leitura pós-write via suspend.
        vehicleDao.getVehicleByIdOnce(vehicleId)?.let { syncManager.syncVehicle(it) }
    }

    suspend fun deleteVehicle(vehicle: Vehicle) {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { backend.deleteVehicle(vehicle.id) }
        }
        // Remove também no Firestore, para o painel não exibir o veículo excluído.
        runCatching { syncManager.deleteVehicle(vehicle.id) }
        vehicleDao.delete(vehicle)
    }
}
