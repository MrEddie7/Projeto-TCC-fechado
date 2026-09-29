package com.tcc.veiculotracker.data.remote

import android.content.Context
import android.util.Log
import com.tcc.veiculotracker.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP para comunicação com o backend Node.js (VeiculoTracker API).
 * 
 * O backend é a ponte entre o app e o hardware rastreador (GPS IoT).
 * Todas as chamadas passam por ele, que gerencia a fila de comandos,
 * telemetria e sincronização com o Firebase.
 */
class ApiClient(private val context: Context) {

    private val gson = Gson()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "ApiClient"
        private const val DEFAULT_BASE_URL = "http://192.168.0.10:3000"
    }

    // ── Configuração ──────────────────────────────────────────────────────

    var baseUrl: String
        get() = prefs.getString(Constants.KEY_API_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        set(value) = prefs.edit().putString(Constants.KEY_API_URL, value).apply()

    var authToken: String?
        get() = prefs.getString(Constants.KEY_API_TOKEN, null)
        set(value) = prefs.edit().putString(Constants.KEY_API_TOKEN, value).apply()

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && authToken != null

    // ── Auth ──────────────────────────────────────────────────────────────

    data class AuthResponse(
        val user: UserDto,
        val token: String,
        val expiresAt: Long
    )

    data class UserDto(
        val id: Long,
        val name: String,
        val email: String,
        val phone: String,
        val createdAt: Long
    )

    suspend fun register(name: String, email: String, password: String, phone: String): Result<AuthResponse> {
        val body = mapOf("name" to name, "email" to email, "password" to password, "phone" to phone)
        return post("/v1/auth/register", body, auth = false).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            AuthResponse(
                user = gson.fromJson(obj.getAsJsonObject("user"), UserDto::class.java),
                token = obj.get("token").asString,
                expiresAt = obj.get("expiresAt").asLong
            )
        }
    }

    suspend fun login(email: String, password: String): Result<AuthResponse> {
        val body = mapOf("email" to email, "password" to password)
        return post("/v1/auth/login", body, auth = false).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            AuthResponse(
                user = gson.fromJson(obj.getAsJsonObject("user"), UserDto::class.java),
                token = obj.get("token").asString,
                expiresAt = obj.get("expiresAt").asLong
            )
        }
    }

    suspend fun logout(): Result<Boolean> {
        return post("/v1/auth/logout", emptyMap()).map { true }
    }

    // ── Veículos ──────────────────────────────────────────────────────────

    data class VehicleDto(
        val id: Long,
        val userId: Long,
        val plate: String,
        val model: String,
        val brand: String,
        val year: Int,
        val color: String,
        val isBlocked: Boolean,
        val latitude: Double,
        val longitude: Double,
        val speed: Double,
        val lastUpdate: Long,
        val createdAt: Long
    )

    suspend fun getVehicles(): Result<List<VehicleDto>> {
        return get("/v1/vehicles").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            val arr = obj.getAsJsonArray("vehicles")
            arr.map { gson.fromJson(it, VehicleDto::class.java) }
        }
    }

    suspend fun getVehicle(id: Long): Result<VehicleDto> {
        return get("/v1/vehicles/$id").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("vehicle"), VehicleDto::class.java)
        }
    }

    suspend fun createVehicle(plate: String, model: String, brand: String, year: Int, color: String): Result<VehicleDto> {
        val body = mapOf("plate" to plate, "model" to model, "brand" to brand, "year" to year, "color" to color)
        return post("/v1/vehicles", body).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("vehicle"), VehicleDto::class.java)
        }
    }

    suspend fun updateVehicle(id: Long, updates: Map<String, Any>): Result<VehicleDto> {
        return put("/v1/vehicles/$id", updates).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("vehicle"), VehicleDto::class.java)
        }
    }

    suspend fun deleteVehicle(id: Long): Result<Boolean> {
        return delete("/v1/vehicles/$id").map { true }
    }

    // ── Comandos (bloqueio/desbloqueio) ────────────────────────────────────

    data class CommandDto(
        val id: Long,
        val vehicleId: Long,
        val command: String,
        val status: String,
        val note: String?,
        val createdAt: Long,
        val acknowledgedAt: Long?
    )

    suspend fun sendCommand(vehicleId: Long, command: String, note: String? = null): Result<CommandDto> {
        val body = mutableMapOf("command" to command)
        if (note != null) body["note"] = note
        return post("/v1/vehicles/$vehicleId/commands", body).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("command"), CommandDto::class.java)
        }
    }

    suspend fun getCommands(vehicleId: Long, status: String? = null): Result<List<CommandDto>> {
        val path = if (status != null) "/v1/vehicles/$vehicleId/commands?status=$status" else "/v1/vehicles/$vehicleId/commands"
        return get(path).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            val arr = obj.getAsJsonArray("commands")
            arr.map { gson.fromJson(it, CommandDto::class.java) }
        }
    }

    // ── Telemetria ────────────────────────────────────────────────────────

    data class TelemetryDto(
        val id: Long,
        val latitude: Double,
        val longitude: Double,
        val speed: Double,
        val heading: Double,
        val battery: Double,
        val timestamp: Long
    )

    suspend fun getLatestTelemetry(vehicleId: Long): Result<TelemetryDto?> {
        return get("/v1/vehicles/$vehicleId/telemetry/latest").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            val latest = obj.get("latest")
            if (latest.isJsonNull) null else gson.fromJson(latest, TelemetryDto::class.java)
        }
    }

    suspend fun getTelemetryHistory(vehicleId: Long, limit: Int = 100): Result<List<TelemetryDto>> {
        return get("/v1/vehicles/$vehicleId/telemetry/history?limit=$limit").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            val arr = obj.getAsJsonArray("points")
            arr.map { gson.fromJson(it, TelemetryDto::class.java) }
        }
    }

    // ── Rotas ─────────────────────────────────────────────────────────────

    data class RouteDto(
        val id: Long,
        val vehicleId: Long,
        val userId: Long,
        val startLatitude: Double,
        val startLongitude: Double,
        val endLatitude: Double,
        val endLongitude: Double,
        val startTime: Long,
        val endTime: Long,
        val distance: Double,
        val maxSpeed: Double,
        val status: String
    )

    suspend fun startRoute(vehicleId: Long): Result<RouteDto> {
        return post("/v1/vehicles/$vehicleId/routes/start", emptyMap()).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("route"), RouteDto::class.java)
        }
    }

    suspend fun stopRoute(vehicleId: Long): Result<RouteDto> {
        return post("/v1/vehicles/$vehicleId/routes/stop", emptyMap()).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("route"), RouteDto::class.java)
        }
    }

    suspend fun getRoutes(vehicleId: Long): Result<List<RouteDto>> {
        return get("/v1/vehicles/$vehicleId/routes").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            val arr = obj.getAsJsonArray("routes")
            arr.map { gson.fromJson(it, RouteDto::class.java) }
        }
    }

    suspend fun getRoute(routeId: Long): Result<Pair<RouteDto, List<TelemetryDto>>> {
        return get("/v1/routes/$routeId").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            val route = gson.fromJson(obj.getAsJsonObject("route"), RouteDto::class.java)
            val pointsArr = obj.getAsJsonArray("points")
            val points = pointsArr.map { gson.fromJson(it, TelemetryDto::class.java) }
            Pair(route, points)
        }
    }

    // ── HTTP Helpers ──────────────────────────────────────────────────────

    private suspend fun get(path: String): Result<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .get()
            .build()
        execute(request)
    }

    private suspend fun post(path: String, body: Map<String, Any?>, auth: Boolean = true): Result<String> = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl$path")
            .apply {
                if (auth) authToken?.let { header("Authorization", "Bearer $it") }
            }
            .post(requestBody)
            .build()
        execute(request)
    }

    private suspend fun put(path: String, body: Map<String, Any?>): Result<String> = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$baseUrl$path")
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .put(requestBody)
            .build()
        execute(request)
    }

    private suspend fun delete(path: String): Result<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .apply { authToken?.let { header("Authorization", "Bearer $it") } }
            .delete()
            .build()
        execute(request)
    }

    private fun execute(request: Request): Result<String> {
        return try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                Result.success(body)
            } else {
                val errorMsg = try {
                    val obj = JsonParser.parseString(body).asJsonObject
                    obj.get("error")?.asString ?: "Erro ${response.code}"
                } catch (e: Exception) {
                    "Erro ${response.code}"
                }
                Log.e(TAG, "HTTP ${response.code}: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Request failed: ${e.message}")
            Result.failure(e)
        }
    }
}
