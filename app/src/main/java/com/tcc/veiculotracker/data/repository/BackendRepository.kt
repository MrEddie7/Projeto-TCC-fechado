package com.tcc.veiculotracker.data.repository

import android.content.Context
import android.util.Log
import com.tcc.veiculotracker.data.local.dao.VehicleDao
import com.tcc.veiculotracker.data.local.entity.Vehicle
import com.tcc.veiculotracker.data.remote.ApiClient
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Repositório que gerencia a comunicação com o backend Node.js.
 * 
 * O backend é a ponte entre o app e o hardware rastreador (GPS IoT).
 * Este repositório sincroniza os dados locais (Room) com o backend,
 * garantindo que os comandos cheguem ao hardware e a telemetria chegue ao app.
 */
class BackendRepository(
    private val context: Context,
    private val vehicleDao: VehicleDao
) {
    val apiClient: ApiClient = ApiClient(context)
    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "BackendRepository"
    }

    // ── Configuração ──────────────────────────────────────────────────────

    var baseUrl: String
        get() = apiClient.baseUrl
        set(value) { apiClient.baseUrl = value }

    val isConfigured: Boolean
        get() = apiClient.isConfigured

    fun saveAuthToken(token: String) {
        apiClient.authToken = token
    }

    fun clearAuthToken() {
        apiClient.authToken = null
    }

    // ── Auth ──────────────────────────────────────────────────────────────

    suspend fun register(name: String, email: String, password: String, phone: String): Result<ApiClient.UserDto> {
        val result = apiClient.register(name, email, password, phone)
        result.onSuccess { auth ->
            apiClient.authToken = auth.token
            prefs.edit().putLong(Constants.KEY_USER_ID, auth.user.id).apply()
            prefs.edit().putString(Constants.KEY_USER_NAME, auth.user.name).apply()
            prefs.edit().putString(Constants.KEY_USER_EMAIL, auth.user.email).apply()
        }
        return result.map { it.user }
    }

    suspend fun login(email: String, password: String): Result<ApiClient.UserDto> {
        val result = apiClient.login(email, password)
        result.onSuccess { auth ->
            apiClient.authToken = auth.token
            prefs.edit().putLong(Constants.KEY_USER_ID, auth.user.id).apply()
            prefs.edit().putString(Constants.KEY_USER_NAME, auth.user.name).apply()
            prefs.edit().putString(Constants.KEY_USER_EMAIL, auth.user.email).apply()
        }
        return result.map { it.user }
    }

    suspend fun logout(): Result<Boolean> {
        val result = apiClient.logout()
        result.onSuccess {
            clearAuthToken()
            prefs.edit().remove(Constants.KEY_USER_ID).apply()
            prefs.edit().remove(Constants.KEY_USER_NAME).apply()
            prefs.edit().remove(Constants.KEY_USER_EMAIL).apply()
        }
        return result
    }

    // ── Veículos ──────────────────────────────────────────────────────────

    /**
     * Sincroniza os veículos do backend com o banco local (Room).
     * Veículos já existentes localmente são atualizados; novos são inseridos.
     */
    suspend fun syncVehiclesFromBackend(): Result<Int> {
        return apiClient.getVehicles().map { remoteVehicles ->
            var synced = 0
            for (remote in remoteVehicles) {
                val local = vehicleDao.getVehicleById(remote.id).first()
                if (local == null) {
                    vehicleDao.insert(remote.toEntity())
                    synced++
                } else {
                    vehicleDao.update(remote.toEntity())
                    synced++
                }
            }
            Log.d(TAG, "Sincronizados $synced veículos do backend")
            synced
        }
    }

    /**
     * Cria um veículo no backend e localmente.
     */
    suspend fun createVehicle(plate: String, model: String, brand: String, year: Int, color: String): Result<Vehicle> {
        return apiClient.createVehicle(plate, model, brand, year, color).map { dto ->
            val vehicle = dto.toEntity()
            vehicleDao.insert(vehicle)
            vehicle
        }
    }

    /**
     * Atualiza um veículo no backend e localmente.
     */
    suspend fun updateVehicle(vehicle: Vehicle): Result<Vehicle> {
        val updates = mutableMapOf<String, Any>()
        updates["plate"] = vehicle.plate
        updates["model"] = vehicle.model
        updates["brand"] = vehicle.brand
        updates["year"] = vehicle.year
        updates["color"] = vehicle.color
        return apiClient.updateVehicle(vehicle.id, updates).map { dto ->
            val updated = dto.toEntity()
            vehicleDao.update(updated)
            updated
        }
    }

    /**
     * Remove um veículo do backend e localmente.
     */
    suspend fun deleteVehicle(vehicleId: Long): Result<Boolean> {
        return apiClient.deleteVehicle(vehicleId).map { ok ->
            vehicleDao.getVehicleById(vehicleId).first()?.let { vehicleDao.delete(it) }
            ok
        }
    }

    // ── Comandos (bloqueio/desbloqueio) ────────────────────────────────────

    /**
     * Envia comando de bloqueio/desbloqueio para o veículo via backend.
     * O backend coloca o comando na fila e o hardware (rastreador) busca via polling.
     */
    suspend fun sendCommand(vehicleId: Long, command: String, note: String? = null): Result<ApiClient.CommandDto> {
        return apiClient.sendCommand(vehicleId, command, note)
    }

    /**
     * Busca comandos do veículo no backend.
     */
    suspend fun getCommands(vehicleId: Long, status: String? = null): Result<List<ApiClient.CommandDto>> {
        return apiClient.getCommands(vehicleId, status)
    }

    // ── Telemetria ────────────────────────────────────────────────────────

    /**
     * Busca a última telemetria do veículo no backend.
     */
    suspend fun getLatestTelemetry(vehicleId: Long): Result<ApiClient.TelemetryDto?> {
        return apiClient.getLatestTelemetry(vehicleId)
    }

    /**
     * Busca o histórico de telemetria do veículo no backend.
     */
    suspend fun getTelemetryHistory(vehicleId: Long, limit: Int = 100): Result<List<ApiClient.TelemetryDto>> {
        return apiClient.getTelemetryHistory(vehicleId, limit)
    }

    // ── Rotas ─────────────────────────────────────────────────────────────

    /**
     * Inicia uma rota no backend.
     */
    suspend fun startRoute(vehicleId: Long): Result<ApiClient.RouteDto> {
        return apiClient.startRoute(vehicleId)
    }

    /**
     * Finaliza uma rota no backend.
     */
    suspend fun stopRoute(vehicleId: Long): Result<ApiClient.RouteDto> {
        return apiClient.stopRoute(vehicleId)
    }

    /**
     * Busca rotas do veículo no backend.
     */
    suspend fun getRoutes(vehicleId: Long): Result<List<ApiClient.RouteDto>> {
        return apiClient.getRoutes(vehicleId)
    }

    /**
     * Busca uma rota com seus pontos geográficos no backend.
     */
    suspend fun getRoute(routeId: Long): Result<Pair<ApiClient.RouteDto, List<ApiClient.TelemetryDto>>> {
        return apiClient.getRoute(routeId)
    }

    // ── Mapeamento DTO -> Entity ───────────────────────────────────────────

    private fun ApiClient.VehicleDto.toEntity(): Vehicle {
        return Vehicle(
            id = id,
            userId = userId,
            plate = plate,
            model = model,
            brand = brand,
            year = year,
            color = color,
            isBlocked = isBlocked,
            latitude = latitude,
            longitude = longitude,
            speed = speed,
            lastUpdate = lastUpdate,
            createdAt = createdAt
        )
    }
}
