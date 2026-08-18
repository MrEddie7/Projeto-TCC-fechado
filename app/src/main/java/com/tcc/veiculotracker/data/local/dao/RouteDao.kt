package com.tcc.veiculotracker.data.local.dao

import androidx.room.*
import com.tcc.veiculotracker.data.local.entity.Route
import com.tcc.veiculotracker.data.local.entity.RoutePoint
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {

    @Query("SELECT * FROM routes WHERE userId = :userId ORDER BY startTime DESC")
    fun getRoutesByUser(userId: Long): Flow<List<Route>>

    @Query("SELECT * FROM routes WHERE vehicleId = :vehicleId ORDER BY startTime DESC")
    fun getRoutesByVehicle(vehicleId: Long): Flow<List<Route>>

    @Query("SELECT * FROM routes WHERE id = :routeId LIMIT 1")
    fun getRouteById(routeId: Long): Flow<Route?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: Route): Long

    @Update
    suspend fun updateRoute(route: Route)

    @Delete
    suspend fun deleteRoute(route: Route)

    @Query("SELECT * FROM route_points WHERE routeId = :routeId ORDER BY timestamp ASC")
    fun getRoutePoints(routeId: Long): Flow<List<RoutePoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutePoint(point: RoutePoint): Long

    @Query("DELETE FROM route_points WHERE routeId = :routeId")
    suspend fun deleteRoutePoints(routeId: Long)

    @Query("DELETE FROM routes WHERE id = :routeId")
    suspend fun deleteRouteById(routeId: Long)
}
