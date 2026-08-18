package com.tcc.veiculotracker.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class Route(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val userId: Long,
    val startLatitude: Double,
    val startLongitude: Double,
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0L,
    val distance: Double = 0.0,
    val status: String = "em_andamento"
)
