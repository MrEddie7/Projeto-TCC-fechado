package com.tcc.veiculotracker.ui.screens.apilink

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ApiLinkState(
    val apiUrl: String = "",
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

class ApiLinkViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as App).apiConfigRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(ApiLinkState())
    val state: StateFlow<ApiLinkState> = _state

    init {
        _state.value = _state.value.copy(apiUrl = repository.apiUrl)
    }

    fun saveConfig(apiUrl: String, apiKey: String) {
        if (apiUrl.isBlank()) {
            _state.value = _state.value.copy(error = "A URL da API é obrigatória")
            return
        }
        _state.value = _state.value.copy(isSaving = true, error = null)
        repository.apiUrl   = apiUrl.trim()
        repository.apiToken = apiKey.trim()
        _state.value = _state.value.copy(isSaving = false, saved = true, apiUrl = apiUrl.trim())
        // Com a API conhecida, o aparelho pode registrar o token de push.
        // Falha é silenciosa: ocorre de novo no próximo boot/login.
        viewModelScope.launch {
            com.tcc.veiculotracker.data.remote.PushTokenManager
                .registerCurrentToken(getApplication())
        }
    }

    fun clearConfig() {
        repository.clear()
        _state.value = ApiLinkState()
    }

    fun clearMessages() {
        _state.value = _state.value.copy(error = null, saved = false)
    }
}
