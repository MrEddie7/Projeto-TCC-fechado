package com.tcc.veiculotracker.data.model

data class UserModel(
    val id: Long = 0,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val firebaseUid: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
