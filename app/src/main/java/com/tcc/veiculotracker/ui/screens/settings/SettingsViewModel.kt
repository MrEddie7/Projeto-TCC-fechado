package com.tcc.veiculotracker.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.model.UserModel
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsState(
    val user: UserModel? = null,
    val isDarkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val trackingInterval: Int = 5,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val authRepository = (application as App).authRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.value = SettingsState(
            user                 = authRepository.currentUser(),
            isDarkMode           = prefs.getBoolean(Constants.KEY_DARK_MODE, false),
            notificationsEnabled = prefs.getBoolean(Constants.KEY_NOTIFICATIONS, true),
            trackingInterval     = prefs.getInt(Constants.KEY_TRACKING_INTERVAL, 5)
        )
    }

    fun updateDarkMode(enabled: Boolean) {
        _state.value = _state.value.copy(isDarkMode = enabled)
    }

    fun updateNotifications(enabled: Boolean) {
        _state.value = _state.value.copy(notificationsEnabled = enabled)
    }

    fun updateTrackingInterval(interval: Int) {
        _state.value = _state.value.copy(trackingInterval = interval)
    }

    fun saveSettings(name: String, email: String, phone: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true)
            try {
                prefs.edit().apply {
                    putBoolean(Constants.KEY_DARK_MODE, _state.value.isDarkMode)
                    putBoolean(Constants.KEY_NOTIFICATIONS, _state.value.notificationsEnabled)
                    putInt(Constants.KEY_TRACKING_INTERVAL, _state.value.trackingInterval)
                    putString(Constants.KEY_USER_NAME, name)
                    putString(Constants.KEY_USER_EMAIL, email)
                    apply()
                }

                val user = _state.value.user
                if (user != null) {
                    authRepository.updateUser(user.copy(name = name, email = email, phone = phone))
                }

                _state.value = _state.value.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.message ?: "Erro ao salvar configurações"
                )
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.signOut()
        }
        // Remove só a SESSÃO. `clear()` apagava também a URL da API
        // (Configurações > Vínculo de API), o tema e as preferências — quem
        // apontava o app para um servidor próprio perdia a configuração ao
        // sair e o app voltava a enviar dados para a URL padrão.
        prefs.edit()
            .remove(Constants.KEY_USER_ID)
            .remove(Constants.KEY_FIREBASE_UID)
            .remove(Constants.KEY_LOGGED_IN)
            .remove(Constants.KEY_USER_NAME)
            .remove(Constants.KEY_USER_EMAIL)
            .remove(Constants.KEY_BACKEND_USER_ID)
            .apply()
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, saved = false)
    }
}
