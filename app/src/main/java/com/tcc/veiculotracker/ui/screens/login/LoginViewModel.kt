package com.tcc.veiculotracker.ui.screens.login

import android.app.Application
import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.R
import com.tcc.veiculotracker.data.local.entity.User
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
    val user: User? = null
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).authRepository
    // Usa o default_web_client_id (gerado automaticamente pelo plugin google-services
    // a partir do google-services.json), com fallback para o recurso manual.
    private val webClientId = runCatching {
        application.getString(R.string.default_web_client_id)
    }.getOrNull().takeUnless { it.isNullOrBlank() }
        ?: application.getString(
            application.resources.getIdentifier("google_web_client_id", "string", application.packageName)
        )

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

    fun updateEmail(value: String) {
        _state.value = _state.value.copy(email = value, error = null)
    }

    fun updatePassword(value: String) {
        _state.value = _state.value.copy(password = value, error = null)
    }

    /**
     * Autentica por email/senha via Firebase Auth (provedor Email/Password).
     */
    fun loginWithEmail() {
        val email = _state.value.email.trim().lowercase()
        val password = _state.value.password
        if (email.isBlank() || password.isBlank()) {
            _state.value = _state.value.copy(error = "Preencha email e senha")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val user = try {
                repository.signInWithEmail(email, password)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Falha ao entrar"
                )
                return@launch
            }
            if (user == null) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = "Email ou senha incorretos"
                )
            } else {
                persistSession(user)
            }
        }
    }

    /**
     * Abre o picker do Google (Credential Manager) e autentica via Firebase Auth.
     * Deve ser chamado com o contexto da Activity atual.
     */
    fun signInWithGoogle(context: Context) {
        if (webClientId.isNullOrBlank()) {
            _state.value = _state.value.copy(
                error = "Configuração do Google Sign-In pendente. Verifique o 'Web client ID' no Console Firebase."
            )
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val credentialManager = CredentialManager.create(context)
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(
                        GetGoogleIdOption.Builder()
                            .setServerClientId(webClientId)
                            .setFilterByAuthorizedAccounts(false)
                            .setAutoSelectEnabled(false)
                            .build()
                    )
                    .build()

                val result: GetCredentialResponse = credentialManager.getCredential(context, request)
                val credential = result.credential
                val idToken = if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                ) {
                    GoogleIdTokenCredential.createFrom(credential.data).idToken
                } else {
                    throw IllegalStateException(
                        "Credencial do Google inesperada: ${credential.type}"
                    )
                }

                val user = repository.signInWithGoogle(idToken)
                persistSession(user)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Falha ao entrar com Google"
                )
            }
        }
    }

    private fun persistSession(user: User) {
        prefs.edit().apply {
            putBoolean(Constants.KEY_LOGGED_IN, true)
            putLong(Constants.KEY_USER_ID, user.id)
            putString(Constants.KEY_USER_NAME, user.name)
            putString(Constants.KEY_USER_EMAIL, user.email)
            apply()
        }
        _state.value = LoginState(isLoggedIn = true, user = user)
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
