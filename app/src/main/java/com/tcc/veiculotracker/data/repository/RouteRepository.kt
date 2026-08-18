package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.RouteDao
import com.tcc.veiculotracker.data.local.entity.Route
import com.tcc.veiculotracker.data.local.entity.RoutePoint
import kotlinx.coroutines.flow.Flow

class RouteRepository(private val routeDao: RouteDao) {

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
        return routeDao.insertRoute(route)
    }

    suspend fun endRoute(route: Route) {
        routeDao.updateRoute(route)
    }

    suspend fun addRoutePoint(point: RoutePoint): Long {
        return routeDao.insertRoutePoint(point)
    }

    suspend fun deleteRoute(route: Route) {
        routeDao.deleteRoutePoints(route.id)
        routeDao.deleteRoute(route)
    }
}
