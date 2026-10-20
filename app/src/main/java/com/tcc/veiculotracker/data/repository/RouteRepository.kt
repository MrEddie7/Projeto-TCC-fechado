package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.model.RouteModel
import com.tcc.veiculotracker.data.model.RoutePointModel
import com.tcc.veiculotracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

/**
 * Repositório de rotas sem Room. Fonte da verdade = Firestore.
 * RoutePoints (pontos GPS de uma rota) são armazenados no Firestore sob
 * routes/{id}/points como sub-coleção — ou gerenciados pelo backend.
 */
class RouteRepository(
    private val syncManager: SyncManager
) {

    fun getRoutesByUser(userId: Long): Flow<List<RouteModel>> =
        syncManager.getRoutesByUser(userId)

    suspend fun startRoute(route: RouteModel): RouteModel {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            val result = runCatching { backend.startRoute(route.vehicleId).getOrNull() }.getOrNull()
            if (result != null) return result.toModel()
        }
        runCatching { syncManager.syncRoute(route) }
        return route
    }

    suspend fun endRoute(route: RouteModel) {
        val backend = syncManager.backendRepository
        if (backend != null && backend.isConfigured) {
            runCatching { backend.stopRoute(route.vehicleId) }
            return
        }
        runCatching { syncManager.syncRoute(route) }
    }

    suspend fun deleteRoute(route: RouteModel) {
        runCatching { syncManager.deleteRoute(route.id) }
    }

    // RoutePoints são mantidos pelo backend via telemetria; o app não precisa
    // gravá-los localmente. Retorna lista vazia quando sem backend.
    suspend fun getRoutePoints(routeId: Long): List<RoutePointModel> = emptyList()

    // ── Mapeamento DTO → Model ─────────────────────────────────────────────

    private fun com.tcc.veiculotracker.data.remote.ApiClient.RouteDto.toModel() = RouteModel(
        id             = id,
        vehicleId      = vehicleId,
        userId         = userId,
        startLatitude  = startLatitude,
        startLongitude = startLongitude,
        endLatitude    = endLatitude,
        endLongitude   = endLongitude,
        startTime      = startTime,
        endTime        = endTime,
        distance       = distance,
        status         = status
    )
}
