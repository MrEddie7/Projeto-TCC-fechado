package com.tcc.veiculotracker.ui.screens.history

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.data.model.RouteModel
import com.tcc.veiculotracker.data.model.RoutePointModel
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryState(
    val routes: List<RouteModel> = emptyList(),
    val selectedRoute: RouteModel? = null,
    val routePoints: List<RoutePointModel> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

class RouteHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val routeRepository = (application as App).routeRepository
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state

    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            routeRepository.getRoutesByUser(userId)
                .catch { e ->
                    // Ex.: PERMISSION_DENIED nas regras do Firestore.
                    _state.value = _state.value.copy(
                        routes    = emptyList(),
                        isLoading = false,
                        error     = e.message ?: "Erro ao carregar rotas"
                    )
                }
                .collect { routes ->
                    _state.value = _state.value.copy(routes = routes, isLoading = false)
                }
        }
    }

    fun selectRoute(route: RouteModel) {
        _state.value = _state.value.copy(selectedRoute = route)
        viewModelScope.launch {
            val points = routeRepository.getRoutePoints(route.id)
            _state.value = _state.value.copy(routePoints = points)
        }
    }

    fun deleteRoute(route: RouteModel) {
        viewModelScope.launch {
            routeRepository.deleteRoute(route)
            _state.value = _state.value.copy(selectedRoute = null, routePoints = emptyList())
        }
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedRoute = null, routePoints = emptyList())
    }
}
