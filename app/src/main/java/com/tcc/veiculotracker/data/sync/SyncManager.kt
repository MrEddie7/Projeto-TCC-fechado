package com.tcc.veiculotracker.data.sync

import android.content.Context
import com.tcc.veiculotracker.data.local.OfflineCacheManager
import com.tcc.veiculotracker.data.model.RouteModel
import com.tcc.veiculotracker.data.model.UserModel
import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.data.remote.ApiClient
import com.tcc.veiculotracker.data.remote.FirebaseDataSource
import com.tcc.veiculotracker.data.repository.BackendRepository
import kotlinx.coroutines.flow.Flow

/**
 * Orquestra a sincronização entre o Firestore (fonte da verdade) e o cache
 * offline em JSON. O Room foi removido; os dados vivem no Firestore enquanto
 * há rede, e no arquivo JSON do dispositivo quando offline.
 */
class SyncManager(
    val firebaseDataSource: FirebaseDataSource,
    private val context: Context,
    val backendRepository: BackendRepository? = null
) {

    // ── Usuários ─────────────────────────────────────────────────────────────

    suspend fun syncUser(user: UserModel) {
        runCatching { firebaseDataSource.syncUser(user) }
    }

    suspend fun syncAccountToCloud(user: UserModel) {
        runCatching { firebaseDataSource.syncUser(user) }
        syncVehiclesToCloud(user.id)
    }

    suspend fun syncAccountToCloud(userId: Long) {
        // No-op quando não há um UserModel disponível; VehicleRepository chama
        // a versão com UserModel após o login.
    }

    // ── Veículos ─────────────────────────────────────────────────────────────

    /**
     * Publica um veículo no Firestore.
     *
     * ATENÇÃO: o Firestore é a fonte da verdade e o documento do veículo é
     * atualizado pela API (telemetria, bloqueio, simulação). Fazer `set`
     * completo com um VehicleModel obtido de um Flow/observer sobrescreveria
     * esses campos com valores defasados — por isso só publicamos o que o app
     * realmente cria/edita (cadastro, edição cadastral e exclusão).
     *
     * `isNew = true` marca cadastro (o id é gerado). Edição preserva o id
     * existente — inclusive o id 0, que é um veículo real com rastreador.
     */
    suspend fun syncVehicle(vehicle: VehicleModel, isNew: Boolean = false) {
        // Erros sobem para quem chamou (a edição/exclusão avisa o usuário);
        // antes o runCatching aqui escondia falhas de permissão do Firestore.
        firebaseDataSource.syncVehicle(vehicle, isNew)
    }

    /**
     * Envia ao Firestore todos os veículos obtidos do backend (ou cache local).
     * Retorna o número enviado.
     */
    suspend fun syncVehiclesToCloud(userId: Long): Int {
        // Firestore já é a fonte de verdade; regravar os mesmos documentos
        // não sincroniza nada e pode sobrescrever alterações mais recentes.
        return 0
    }

    suspend fun deleteVehicle(vehicleId: Long) {
        firebaseDataSource.deleteVehicle(vehicleId.toString())
    }

    suspend fun pullVehicles(userId: Long): List<VehicleModel> {
        return runCatching { firebaseDataSource.getVehiclesOnce(userId) }
            .getOrDefault(emptyList())
    }

    fun getVehiclesByUser(userId: Long): Flow<List<VehicleModel>> =
        firebaseDataSource.getVehiclesByUser(userId)

    // ── Rotas ─────────────────────────────────────────────────────────────────

    suspend fun syncRoute(route: RouteModel) {
        runCatching { firebaseDataSource.syncRoute(route) }
    }

    suspend fun deleteRoute(routeId: Long) {
        runCatching { firebaseDataSource.deleteRoute(routeId.toString()) }
    }

    fun getRoutesByUser(userId: Long): Flow<List<RouteModel>> =
        firebaseDataSource.getRoutesByUser(userId)

    // ── Cache offline ─────────────────────────────────────────────────────────

    /**
     * Atualiza o cache JSON com os veículos do Firestore.
     * Chamado periodicamente (ex: a cada 15 minutos) ou ao receber novos dados.
     */
    fun refreshOfflineCache(vehicles: List<VehicleModel>) {
        OfflineCacheManager.saveVehicles(context, vehicles)
    }

    fun loadOfflineVehicles(): List<VehicleModel> =
        OfflineCacheManager.loadVehicles(context)

    fun offlineCacheAgeMs(): Long =
        OfflineCacheManager.vehiclesCacheAge(context)

    // ── Telemetria (Realtime Database) ────────────────────────────────────────

    /**
     * Le a posição publicada pela API/hardware. O app não escreve neste canal
     * (é o canal do rastreador).
     */
    fun listenTelemetry(vehicleId: Long): Flow<FirebaseDataSource.TelemetryData> =
        firebaseDataSource.listenTelemetry(vehicleId.toString())

    // ── Comandos (Realtime Database) ──────────────────────────────────────────

    suspend fun sendCommand(vehicleId: Long, command: String) {
        runCatching { firebaseDataSource.sendCommand(vehicleId.toString(), command) }
    }

    fun listenCommandResponse(vehicleId: Long): Flow<FirebaseDataSource.RemoteCommand> =
        firebaseDataSource.listenCommandResponse(vehicleId.toString())

    // ── Backend (Node.js API) ─────────────────────────────────────────────────

    suspend fun syncVehiclesFromBackend(): Result<Int> =
        backendRepository?.syncVehiclesFromBackend()
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun pushLocalVehiclesToBackend(userId: Long): Result<Int> =
        backendRepository?.pushLocalVehiclesToBackend(userId)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun sendCommandToBackend(vehicleId: Long, command: String, note: String? = null): Result<ApiClient.CommandDto> =
        backendRepository?.sendCommand(vehicleId, command, note)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun getLatestTelemetryFromBackend(vehicleId: Long): Result<ApiClient.TelemetryDto?> =
        backendRepository?.getLatestTelemetry(vehicleId)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun getLatestMobileLocationFromBackend(): Result<ApiClient.MobileLocationDto?> =
        backendRepository?.getLatestMobileLocation()
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun getTelemetryHistoryFromBackend(vehicleId: Long, limit: Int = 100): Result<List<ApiClient.TelemetryDto>> =
        backendRepository?.getTelemetryHistory(vehicleId, limit)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun startRouteInBackend(vehicleId: Long): Result<ApiClient.RouteDto> =
        backendRepository?.startRoute(vehicleId)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun stopRouteInBackend(vehicleId: Long): Result<ApiClient.RouteDto> =
        backendRepository?.stopRoute(vehicleId)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun getRoutesFromBackend(vehicleId: Long): Result<List<ApiClient.RouteDto>> =
        backendRepository?.getRoutes(vehicleId)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))

    suspend fun getRouteFromBackend(routeId: Long): Result<Pair<ApiClient.RouteDto, List<ApiClient.TelemetryDto>>> =
        backendRepository?.getRoute(routeId)
            ?: Result.failure(IllegalStateException("BackendRepository não configurado"))
}
