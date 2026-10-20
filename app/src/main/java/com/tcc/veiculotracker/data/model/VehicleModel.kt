package com.tcc.veiculotracker.data.model

data class VehicleModel(
    val id: Long = 0,
    val userId: Long = 0,
    val plate: String = "",
    val model: String = "",
    val brand: String = "",
    val year: Int = 0,
    val color: String = "",
    val isBlocked: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0,
    val lastUpdate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
