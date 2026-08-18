package com.tcc.veiculotracker.data.local.dao

import androidx.room.*
import com.tcc.veiculotracker.data.local.entity.Vehicle
import kotlinx.coroutines.flow.Flow

@Dao
interface VehicleDao {

    @Query("SELECT * FROM vehicles WHERE userId = :userId ORDER BY createdAt DESC")
    fun getVehiclesByUser(userId: Long): Flow<List<Vehicle>>

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId LIMIT 1")
    fun getVehicleById(vehicleId: Long): Flow<Vehicle?>

    @Query("SELECT * FROM vehicles WHERE userId = :userId")
    suspend fun getVehiclesListByUser(userId: Long): List<Vehicle>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vehicle: Vehicle): Long

    @Update
    suspend fun update(vehicle: Vehicle)

    @Query("UPDATE vehicles SET isBlocked = :blocked WHERE id = :vehicleId")
    suspend fun setBlocked(vehicleId: Long, blocked: Boolean)

    @Query("UPDATE vehicles SET latitude = :lat, longitude = :lng, speed = :speed, lastUpdate = :timestamp WHERE id = :vehicleId")
    suspend fun updateLocation(vehicleId: Long, lat: Double, lng: Double, speed: Double, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun delete(vehicle: Vehicle)

    @Query("DELETE FROM vehicles WHERE id = :vehicleId")
    suspend fun deleteById(vehicleId: Long)
}
