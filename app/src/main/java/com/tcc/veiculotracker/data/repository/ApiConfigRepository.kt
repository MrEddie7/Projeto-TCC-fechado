package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.ApiConfigDao
import com.tcc.veiculotracker.data.local.entity.ApiConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

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
        val configs = apiConfigDao.getConfigsByUser(userId).first()
        configs.find { it.id == configId }?.let {
            apiConfigDao.update(it.copy(isActive = true))
        }
    }

    suspend fun deleteConfig(config: ApiConfig) {
        apiConfigDao.delete(config)
    }
}
