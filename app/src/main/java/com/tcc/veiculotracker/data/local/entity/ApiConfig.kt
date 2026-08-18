package com.tcc.veiculotracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "api_configs")
data class ApiConfig(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val apiUrl: String,
    val apiKey: String = "",
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
