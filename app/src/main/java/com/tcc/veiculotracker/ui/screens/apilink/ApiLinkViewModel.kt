package com.tcc.veiculotracker.ui.screens.apilink

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.local.entity.ApiConfig
import com.tcc.veiculotracker.data.repository.ApiConfigRepository
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ApiLinkState(
    val configs: List<ApiConfig> = emptyList(),
    val activeConfig: ApiConfig? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = true
)

class ApiLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val repository = ApiConfigRepository(db.apiConfigDao())
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ApiLinkState())
    val state: StateFlow<ApiLinkState> = _state

    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        loadConfigs()
    }

    private fun loadConfigs() {
        viewModelScope.launch {
            repository.getConfigsByUser(userId).collect { configs ->
                _state.value = _state.value.copy(configs = configs, isLoading = false)
            }
        }
        viewModelScope.launch {
            repository.getActiveConfig(userId).collect { config ->
                _state.value = _state.value.copy(activeConfig = config)
            }
        }
    }

    fun saveConfig(apiUrl: String, apiKey: String) {
        if (apiUrl.isBlank()) {
            _state.value = _state.value.copy(error = "A URL da API é obrigatória")
            return
        }

        viewModelScope.launch {
            _state.value = _state.value.copy(isSaving = true, error = null)
            try {
                val config = ApiConfig(
                    userId = userId,
                    apiUrl = apiUrl.trim(),
                    apiKey = apiKey.trim(),
                    isActive = _state.value.configs.isEmpty()
                )
                repository.saveConfig(config)
                _state.value = _state.value.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isSaving = false,
                    error = e.message ?: "Erro ao salvar configuração"
                )
            }
        }
    }

    fun setActiveConfig(config: ApiConfig) {
        viewModelScope.launch {
            repository.setActiveConfig(userId, config.id)
        }
    }

    fun deleteConfig(config: ApiConfig) {
        viewModelScope.launch {
            repository.deleteConfig(config)
        }
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, saved = false)
    }
}
