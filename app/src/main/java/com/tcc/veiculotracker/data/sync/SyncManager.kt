package com.tcc.veiculotracker.data.sync

import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.local.entity.Route
import com.tcc.veiculotracker.data.local.entity.User
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.data.remote.FirebaseDataSource
import kotlinx.coroutines.flow.Flow

/**
 * Responsável por encapsular o FirebaseDataSource + DAOs e orquestrar a
 * sincronização bidirecional entre Room (fonte imediata) e a nuvem.
 *
 * Estratégia híbrida: as escritas são feitas primeiro no banco local (Room),
 * e então propagadas para o Firebase via write-through. Telemetria e comandos
 * (baixa latência) são tratados pelo Firebase Realtime Database.
 */
class SyncManager(
    val firebaseDataSource: FirebaseDataSource,
    private val db: AppDatabase
) {

    // ── Usuários ─────────────────────────────────────────────────────────

    /**
     * Propagação para a nuvem é best-effort: o Room é a fonte imediata da
     * verdade, portanto falhas de rede/console não podem bloquear a operação local.
     */
    suspend fun syncUser(user: User) {
        runCatching { firebaseDataSource.syncUser(user) }
    }

    // ── Veículos ─────────────────────────────────────────────────────────

    suspend fun syncVehicle(vehicle: Vehicle) {
        runCatching { firebaseDataSource.syncVehicle(vehicle) }
    }

    suspend fun deleteVehicle(vehicleId: Long) {
        runCatching { firebaseDataSource.deleteVehicle(vehicleId.toString()) }
    }

    /** Importa os veículos da nuvem (best-effort); vazio se indisponível. */
    suspend fun pullVehicles(userId: Long): List<Vehicle> {
        return runCatching { firebaseDataSource.getVehiclesOnce(userId) }
            .getOrDefault(emptyList())
    }

    fun getVehiclesByUser(userId: Long): Flow<List<Vehicle>> {
        return firebaseDataSource.getVehiclesByUser(userId.toString())
    }

    // ── Rotas ────────────────────────────────────────────────────────────

    suspend fun syncRoute(route: Route) {
        runCatching { firebaseDataSource.syncRoute(route) }
    }

    suspend fun deleteRoute(routeId: Long) {
        runCatching { firebaseDataSource.deleteRoute(routeId.toString()) }
    }

    fun getRoutesByUser(userId: Long): Flow<List<Route>> {
        return firebaseDataSource.getRoutesByUser(userId.toString())
    }

    // ── Telemetria (Realtime Database) ───────────────────────────────────

    fun listenTelemetry(vehicleId: Long): Flow<FirebaseDataSource.TelemetryData> {
        return firebaseDataSource.listenTelemetry(vehicleId.toString())
    }

    suspend fun sendTelemetry(vehicleId: Long, data: FirebaseDataSource.TelemetryData) {
        runCatching { firebaseDataSource.sendTelemetry(vehicleId.toString(), data) }
    }

    // ── Comandos (Realtime Database) ─────────────────────────────────────

    suspend fun sendCommand(vehicleId: Long, command: String) {
        runCatching { firebaseDataSource.sendCommand(vehicleId.toString(), command) }
    }

    fun listenCommandResponse(vehicleId: Long): Flow<FirebaseDataSource.RemoteCommand> {
        return firebaseDataSource.listenCommandResponse(vehicleId.toString())
    }

    // ── Dados brutos (para acesso direto quando necessário) ──────────────

    val vehicleDao get() = db.vehicleDao()
    val routeDao get() = db.routeDao()
    val userDao get() = db.userDao()
}
