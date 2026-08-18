package com.tcc.veiculotracker.ui.screens.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.local.entity.User
import com.tcc.veiculotracker.data.repository.AuthRepository
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsState(
    val user: User? = null,
    val isDarkMode: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val trackingInterval: Int = 5,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val authRepository = AuthRepository(db.userDao())
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state

    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _state.value = SettingsState(
            isDarkMode = prefs.getBoolean(Constants.KEY_DARK_MODE, false),
            notificationsEnabled = prefs.getBoolean(Constants.KEY_NOTIFICATIONS, true),
            trackingInterval = prefs.getInt(Constants.KEY_TRACKING_INTERVAL, 5)
        )

        viewModelScope.launch {
            authRepository.getUserById(userId).collect { user ->
                _state.value = _state.value.copy(user = user)
            }
        }
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
        prefs.edit().clear().apply()
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, saved = false)
    }
}
