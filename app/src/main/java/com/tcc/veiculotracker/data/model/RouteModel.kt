package com.tcc.veiculotracker.data.model

data class RouteModel(
    val id: Long = 0,
    val vehicleId: Long = 0,
    val userId: Long = 0,
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = 0L,
    val distance: Double = 0.0,
    val status: String = "em_andamento"
)

data class RoutePointModel(
    val id: Long = 0,
    val routeId: Long = 0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val speed: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)
