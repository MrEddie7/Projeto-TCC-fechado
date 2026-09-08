package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.RouteDao
import com.tcc.veiculotracker.data.local.entity.Route
import com.tcc.veiculotracker.data.local.entity.RoutePoint
import com.tcc.veiculotracker.data.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class RouteRepository(
    private val routeDao: RouteDao,
    private val syncManager: SyncManager
) {

    fun getRoutesByUser(userId: Long): Flow<List<Route>> {
        return routeDao.getRoutesByUser(userId)
    }

    fun getRoutesByVehicle(vehicleId: Long): Flow<List<Route>> {
        return routeDao.getRoutesByVehicle(vehicleId)
    }

    fun getRouteById(routeId: Long): Flow<Route?> {
        return routeDao.getRouteById(routeId)
    }

    fun getRoutePoints(routeId: Long): Flow<List<RoutePoint>> {
        return routeDao.getRoutePoints(routeId)
    }

    suspend fun startRoute(route: Route): Long {
        val id = routeDao.insertRoute(route)
        syncManager.syncRoute(route.copy(id = id))
        return id
    }

    suspend fun endRoute(route: Route) {
        routeDao.updateRoute(route)
        syncManager.syncRoute(route)
    }

    suspend fun addRoutePoint(point: RoutePoint): Long {
        return routeDao.insertRoutePoint(point)
    }

    suspend fun deleteRoute(route: Route) {
        routeDao.deleteRoutePoints(route.id)
        routeDao.deleteRoute(route)
        syncManager.deleteRoute(route.id)
    }
}
