package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.ApiConfigDao
import com.tcc.veiculotracker.data.local.entity.ApiConfig
import kotlinx.coroutines.flow.Flow

class ApiConfigRepository(private val apiConfigDao: ApiConfigDao) {

    fun getConfigsByUser(userId: Long): Flow<List<ApiConfig>> {
        return apiConfigDao.getConfigsByUser(userId)
    }

    fun getActiveConfig(userId: Long): Flow<ApiConfig?> {
        return apiConfigDao.getActiveConfig(userId)
    }

    suspend fun saveConfig(config: ApiConfig): Long {
        return apiConfigDao.insert(config)
    }

    suspend fun setActiveConfig(userId: Long, configId: Long) {
        apiConfigDao.deactivateAll(userId)
        val config = apiConfigDao.getConfigsByUser(userId)
        apiConfigDao.update(config.first().copy(id = configId, isActive = true))
    }

    suspend fun deleteConfig(config: ApiConfig) {
        apiConfigDao.delete(config)
    }
}
