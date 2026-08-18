package com.tcc.veiculotracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val plate: String,
    val model: String,
    val brand: String,
    val year: Int,
    val color: String = "",
    val isBlocked: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0,
    val lastUpdate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
