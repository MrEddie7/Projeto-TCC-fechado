
package com.tcc.veiculotracker.ui.screens.register

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.tcc.veiculotracker.App
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class RegisterState(
    val isLoading: Boolean = false,
    val isRegistered: Boolean = false,
    val error: String? = null
)

class RegisterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).authRepository

    private val _state = MutableStateFlow(RegisterState())
    val state: StateFlow<RegisterState> = _state

    fun register(name: String, email: String, password: String, confirmPassword: String, phone: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Preencha todos os campos obrigatórios")
            return
        }

        if (password != confirmPassword) {
            _state.value = _state.value.copy(error = "As senhas não coincidem")
            return
        }

        if (password.length < 6) {
            _state.value = _state.value.copy(error = "A senha deve ter no mínimo 6 caracteres")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                repository.registerWithEmail(
                    name = name.trim(),
                    email = email.trim().lowercase(),
                    password = password,
                    phone = phone.trim()
                )
                _state.value = RegisterState(isRegistered = true)
            } catch (e: FirebaseAuthUserCollisionException) {
                _state.value = RegisterState(error = "Este email já está cadastrado. Faça login.")
            } catch (e: Exception) {
                _state.value = RegisterState(error = e.message ?: "Erro ao registrar")
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
