package com.tcc.veiculotracker.data.repository

import com.tcc.veiculotracker.data.local.dao.UserDao
import com.tcc.veiculotracker.data.local.entity.User
import kotlinx.coroutines.flow.Flow

class AuthRepository(private val userDao: UserDao) {

    suspend fun login(email: String, password: String): User? {
        return userDao.login(email, password)
    }

    suspend fun register(user: User): Long {
        val existing = userDao.getUserByEmail(user.email)
        if (existing != null) {
            throw IllegalArgumentException("Email já cadastrado")
        }
        return userDao.insert(user)
    }

    fun getUserById(userId: Long): Flow<User?> {
        return userDao.getUserById(userId)
    }

    suspend fun updateUser(user: User) {
        userDao.update(user)
    }
}
