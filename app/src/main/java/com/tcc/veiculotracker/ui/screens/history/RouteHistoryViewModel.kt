package com.tcc.veiculotracker.ui.screens.history

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tcc.veiculotracker.data.local.AppDatabase
import com.tcc.veiculotracker.data.local.entity.Route
import com.tcc.veiculotracker.data.local.entity.RoutePoint
import com.tcc.veiculotracker.data.repository.RouteRepository
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class HistoryState(
    val routes: List<Route> = emptyList(),
    val selectedRoute: Route? = null,
    val routePoints: List<RoutePoint> = emptyList(),
    val isLoading: Boolean = true
)

class RouteHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val routeRepository = RouteRepository(db.routeDao())
    private val prefs = application.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(HistoryState())
    val state: StateFlow<HistoryState> = _state

    private val userId: Long get() = prefs.getLong(Constants.KEY_USER_ID, -1)

    init {
        loadRoutes()
    }

    private fun loadRoutes() {
        viewModelScope.launch {
            routeRepository.getRoutesByUser(userId).collect { routes ->
                _state.value = _state.value.copy(routes = routes, isLoading = false)
            }
        }
    }

    fun selectRoute(route: Route) {
        _state.value = _state.value.copy(selectedRoute = route)
        viewModelScope.launch {
            routeRepository.getRoutePoints(route.id).collect { points ->
                _state.value = _state.value.copy(routePoints = points)
            }
        }
    }

    fun deleteRoute(route: Route) {
        viewModelScope.launch {
            routeRepository.deleteRoute(route)
            _state.value = _state.value.copy(selectedRoute = null, routePoints = emptyList())
        }
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedRoute = null, routePoints = emptyList())
    }
}
