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

    /**
     * Cria o veículo no Firestore. No modelo novo a criação passa pelo backend
     * (que grava ownerUid + telemetria); sem backend, grava direto na nuvem.
     * Regravar um veículo que só mudou de posição do simulador sobrescreveria
     * os dados da API, então a criação é separada da atualização.
     */
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
                // O backend já gravou no Firestore; nada a publicar de novo.
                return created
            }
        }
        // Sem backend: grava direto no Firestore (id novo — é um cadastro).
        runCatching { syncManager.syncVehicle(vehicle, isNew = true) }
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

    /**
     * Edição cadastral. Com backend configurado, só a API altera o documento
     * (evita `set` completo com dados defasados do Firestore).
     *
     * Devolve o erro em vez de engoli-lo: a tela de edição precisa dizer ao
     * usuário quando o servidor recusou a alteração (ex.: placa duplicada).
     */
    suspend fun updateVehicle(vehicle: VehicleModel): Result<Unit> {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            return backend.updateVehicle(vehicle).map { }
        }
        return runCatching { syncManager.syncVehicle(vehicle) }
    }

    /**
     * Bloqueio/desbloqueio. A mudança de estado é feita pelo hardware (ACK do
     * comando); aqui só refletimos no Firestore quando não há backend, para o
     * app não gravar estado que o servidor acabaria sobrescrevendo.
     */
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
            return
        }
        runCatching { syncManager.syncVehicle(updated) }
    }

    /**
     * Atualização de posição vinda de um observer. NÃO grava no Firestore: a
     * API/telemetria é a dona desses campos e regravar aqui apagaria a posição
     * real do servidor. Mantido para compatibilidade (no-op).
     */
    suspend fun updateLocation(vehicleId: Long, lat: Double, lng: Double, speed: Double, vehicle: VehicleModel) {
        // Intencionalmente vazio — ver comentário acima.
    }

    /**
     * Exclusão do veículo. Com backend configurado, a API apaga o documento e
     * é ela quem manda: se a exclusão falhar, NÃO tentamos apagar direto no
     * Firestore (o veículo voltaria no próximo sincronismo, com estado parcial).
     */
    suspend fun deleteVehicle(vehicle: VehicleModel): Result<Unit> {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            return backend.deleteVehicle(vehicle.id).map { }
        }
        return runCatching { syncManager.deleteVehicle(vehicle.id) }
    }
}
