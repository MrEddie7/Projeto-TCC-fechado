package com.tcc.veiculotracker.data.local.dao

import androidx.room.*
import com.tcc.veiculotracker.data.local.entity.ApiConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiConfigDao {

    @Query("SELECT * FROM api_configs WHERE userId = :userId ORDER BY createdAt DESC")
    fun getConfigsByUser(userId: Long): Flow<List<ApiConfig>>

    @Query("SELECT * FROM api_configs WHERE userId = :userId AND isActive = 1 LIMIT 1")
    fun getActiveConfig(userId: Long): Flow<ApiConfig?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ApiConfig): Long

    @Update
    suspend fun update(config: ApiConfig)

    @Query("UPDATE api_configs SET isActive = 0 WHERE userId = :userId")
    suspend fun deactivateAll(userId: Long)

    @Delete
    suspend fun delete(config: ApiConfig)
}
