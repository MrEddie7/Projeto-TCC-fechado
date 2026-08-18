package com.tcc.veiculotracker.ui.screens.login

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.local.entity.User
import com.tcc.veiculotracker.data.repository.AuthRepository
import com.tcc.veiculotracker.util.Constants
import com.tcc.veiculotracker.util.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val user: User? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = AuthRepository(db.userDao())

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state

    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    init {
        val loggedIn = prefs.getBoolean(Constants.KEY_LOGGED_IN, false)
        if (loggedIn) {
            val userId = prefs.getLong(Constants.KEY_USER_ID, -1)
            if (userId != -1L) {
                _state.value = LoginState(isLoggedIn = true)
            }
        }
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Preencha todos os campos")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val user = repository.login(email, password)
                if (user != null) {
                    prefs.edit().apply {
                        putBoolean(Constants.KEY_LOGGED_IN, true)
                        putLong(Constants.KEY_USER_ID, user.id)
                        putString(Constants.KEY_USER_NAME, user.name)
                        putString(Constants.KEY_USER_EMAIL, user.email)
                        apply()
                    }
                    _state.value = LoginState(isLoggedIn = true, user = user)
                } else {
                    _state.value = LoginState(error = "Email ou senha incorretos")
                }
            } catch (e: Exception) {
                _state.value = LoginState(error = e.message ?: "Erro ao fazer login")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
