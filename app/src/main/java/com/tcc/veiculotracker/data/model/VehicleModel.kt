package com.tcc.veiculotracker.data.model

data class VehicleModel(
    val id: Long = 0,
    val userId: Long = 0,
    /** UID do Firebase Auth do dono (identidade usada pela API e pelas regras). */
    val ownerUid: String? = null,
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
