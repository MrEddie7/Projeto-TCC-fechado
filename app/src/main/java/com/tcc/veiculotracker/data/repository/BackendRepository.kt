package com.tcc.veiculotracker.data.repository

import android.content.Context
import android.util.Log
import com.tcc.veiculotracker.data.model.VehicleModel
import com.tcc.veiculotracker.data.remote.ApiClient
import com.tcc.veiculotracker.util.Constants

/**
 * Repositório que gerencia a comunicação com o backend Node.js.
 * Room foi removido — não há mais DAO. Os veículos retornados pelo backend
 * são passados diretamente ao Firestore via SyncManager (VehicleRepository).
 */
class BackendRepository(
    private val context: Context
) {
    val apiClient: ApiClient = ApiClient(context)
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "BackendRepository"
    }

    // ── Configuração ──────────────────────────────────────────────────────────

    var baseUrl: String
        get() = apiClient.baseUrl
        set(value) { apiClient.baseUrl = value }

    val isConfigured: Boolean
        get() = apiClient.isConfigured

    fun saveAuthToken(token: String) { apiClient.authToken = token }
    fun clearAuthToken()             { apiClient.authToken = null  }

    // ── Auth ──────────────────────────────────────────────────────────────────

    suspend fun register(name: String, email: String, password: String, phone: String): Result<ApiClient.UserDto> {
        val result = apiClient.register(name, email, password, phone)
        result.onSuccess { auth ->
            apiClient.authToken = auth.token
            prefs.edit()
                .putLong(Constants.KEY_BACKEND_USER_ID, auth.user.id)
                .putString(Constants.KEY_USER_NAME, auth.user.name)
                .putString(Constants.KEY_USER_EMAIL, auth.user.email)
                .apply()
        }
        return result.map { it.user }
    }

    suspend fun login(email: String, password: String): Result<ApiClient.UserDto> {
        val result = apiClient.login(email, password)
        result.onSuccess { auth ->
            apiClient.authToken = auth.token
            prefs.edit()
                .putLong(Constants.KEY_BACKEND_USER_ID, auth.user.id)
                .putString(Constants.KEY_USER_NAME, auth.user.name)
                .putString(Constants.KEY_USER_EMAIL, auth.user.email)
                .apply()
        }
        return result.map { it.user }
    }

    suspend fun logout(): Result<Boolean> {
        val result = apiClient.logout()
        result.onSuccess {
            clearAuthToken()
            prefs.edit()
                .remove(Constants.KEY_BACKEND_USER_ID)
                .remove(Constants.KEY_USER_NAME)
                .remove(Constants.KEY_USER_EMAIL)
                .apply()
        }
        return result
    }

    // ── Veículos ──────────────────────────────────────────────────────────────

    /**
     * Retorna os veículos do backend como lista de VehicleModel.
     * O caller (VehicleRepository) decide o que fazer com eles (ex: mandar ao Firestore).
     */
    suspend fun syncVehiclesFromBackend(): Result<Int> {
        return apiClient.getVehicles().map { remoteVehicles ->
            Log.d(TAG, "Veículos obtidos do backend: ${remoteVehicles.size}")
            remoteVehicles.size
        }
    }

    suspend fun getVehiclesFromBackend(): Result<List<VehicleModel>> {
        return apiClient.getVehicles().map { list -> list.map { it.toModel() } }
    }

    suspend fun createVehicle(plate: String, model: String, brand: String, year: Int, color: String): Result<VehicleModel> {
        return apiClient.createVehicle(plate, model, brand, year, color).map { it.toModel() }
    }

    suspend fun pushLocalVehiclesToBackend(userId: Long): Result<Int> {
        // Sem Room não há "veículos apenas locais" — o Firestore é a fonte.
        // Esta operação é no-op; fica para compatibilidade com SyncManager.
        return Result.success(0)
    }

    suspend fun updateVehicle(vehicle: VehicleModel): Result<VehicleModel> {
        val updates = mutableMapOf<String, Any>(
            "plate" to vehicle.plate,
            "model" to vehicle.model,
            "brand" to vehicle.brand,
            "year"  to vehicle.year,
            "color" to vehicle.color
        )
        return updateVehicle(vehicle.id, updates)
    }

    suspend fun updateVehicle(vehicleId: Long, updates: Map<String, Any>): Result<VehicleModel> {
        return apiClient.updateVehicle(vehicleId, updates).map { it.toModel() }
    }

    suspend fun deleteVehicle(vehicleId: Long): Result<Boolean> {
        return apiClient.deleteVehicle(vehicleId)
    }

    // ── Simulação ─────────────────────────────────────────────────────────────

    suspend fun getSimulationDecision(vehicleId: Long): Result<ApiClient.SimulationDecisionDto> =
        apiClient.getSimulationDecision(vehicleId)

    suspend fun setVehicleSimulation(vehicleId: Long, simulate: Boolean): Result<ApiClient.SimulationDecisionDto> =
        apiClient.setVehicleSimulation(vehicleId, simulate)

    // ── Comandos ──────────────────────────────────────────────────────────────

    suspend fun sendCommand(vehicleId: Long, command: String, note: String? = null): Result<ApiClient.CommandDto> =
        apiClient.sendCommand(vehicleId, command, note)

    suspend fun getCommands(vehicleId: Long, status: String? = null): Result<List<ApiClient.CommandDto>> =
        apiClient.getCommands(vehicleId, status)

    // ── Telemetria ────────────────────────────────────────────────────────────

    suspend fun getLatestTelemetry(vehicleId: Long): Result<ApiClient.TelemetryDto?> =
        apiClient.getLatestTelemetry(vehicleId)

    suspend fun getLatestMobileLocation(): Result<ApiClient.MobileLocationDto?> =
        apiClient.getLatestMobileLocation()

    suspend fun getTelemetryHistory(vehicleId: Long, limit: Int = 100): Result<List<ApiClient.TelemetryDto>> =
        apiClient.getTelemetryHistory(vehicleId, limit)

    // ── Rotas ─────────────────────────────────────────────────────────────────

    suspend fun startRoute(vehicleId: Long): Result<ApiClient.RouteDto> = apiClient.startRoute(vehicleId)
    suspend fun stopRoute(vehicleId: Long): Result<ApiClient.RouteDto>  = apiClient.stopRoute(vehicleId)
    suspend fun getRoutes(vehicleId: Long): Result<List<ApiClient.RouteDto>> = apiClient.getRoutes(vehicleId)
    suspend fun getRoute(routeId: Long): Result<Pair<ApiClient.RouteDto, List<ApiClient.TelemetryDto>>> = apiClient.getRoute(routeId)

    // ── Mapeamento ─────────────────────────────────────────────────────────────

    private fun ApiClient.VehicleDto.toModel() = VehicleModel(
        id        = id,
        userId    = userId,
        plate     = plate,
        model     = model,
        brand     = brand,
        year      = year,
        color     = color,
        isBlocked = isBlocked,
        latitude  = latitude,
        longitude = longitude,
        speed     = speed,
        lastUpdate = lastUpdate,
        createdAt  = createdAt
    )
}
