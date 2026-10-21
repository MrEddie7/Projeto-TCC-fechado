package com.tcc.veiculotracker.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.tcc.veiculotracker.util.Constants
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP para comunicação com o backend Node.js (Securitas API).
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

    // A API valida o Firebase ID Token; não há uma segunda sessão no backend.
    var authToken: String?
        get() = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.result?.token
        set(@Suppress("UNUSED_PARAMETER") value) = Unit

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && FirebaseAuth.getInstance().currentUser != null

    // ── Auth (legado) ─────────────────────────────────────────────────────
    // O login agora é feito no Firebase Auth (AuthRepository), no próprio app.
    // A API apenas valida o ID Token; as rotas /v1/auth/register e /v1/auth/login
    // deixaram de existir na migração. Estes métodos e DTOs permanecem para não
    // quebrar referências antigas, mas falham de forma explícita.

    @Deprecated("Use Firebase Auth (AuthRepository)")
    data class AuthResponse(
        val user: UserDto,
        val token: String,
        val expiresAt: Long
    )

    @Deprecated("Use o UID do Firebase Auth")
    data class UserDto(
        val id: Long,
        val name: String,
        val email: String,
        val phone: String,
        val createdAt: Long
    )

    @Deprecated("Use Firebase Auth (AuthRepository.registerWithEmail)")
    suspend fun register(name: String, email: String, password: String, phone: String): Result<AuthResponse> =
        Result.failure(UnsupportedOperationException("Cadastro é feito no Firebase Auth"))

    @Deprecated("Use Firebase Auth (AuthRepository.signInWithEmail)")
    suspend fun login(email: String, password: String): Result<AuthResponse> =
        Result.failure(UnsupportedOperationException("Login é feito no Firebase Auth"))

    suspend fun logout(): Result<Boolean> {
        return Result.success(true)
    }

    /**
     * Envia a localização do celular para o backend.
     * Usado pelo LocationService para rastrear o dispositivo do usuário.
     */
    suspend fun sendMobileLocation(latitude: Double, longitude: Double, speed: Double, heading: Double, accuracy: Double): Result<Boolean> {
        val body = mapOf(
            "latitude" to latitude,
            "longitude" to longitude,
            "speed" to speed,
            "heading" to heading,
            "accuracy" to accuracy,
            "timestamp" to System.currentTimeMillis(),
            "source" to "mobile_app"
        )
        return post("/v1/mobile/location", body).map { true }
    }

    data class MobileLocationDto(
        val id: Long,
        val userId: Long,
        val latitude: Double,
        val longitude: Double,
        val speed: Double,
        val heading: Double,
        val accuracy: Double,
        val timestamp: Long,
        val source: String
    )

    /**
     * Busca a última localização do celular deste usuário cadastrada no backend.
     * Usada para exibir o aparelho "junto do veículo" no mapa de rastreamento.
     */
    suspend fun getLatestMobileLocation(): Result<MobileLocationDto?> {
        return get("/v1/mobile/location/latest").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            val location = obj.get("location")
            if (location.isJsonNull) null else gson.fromJson(location, MobileLocationDto::class.java)
        }
    }

    // ── Registro de dispositivo móvel ────────────────────────────────────

    data class MobileDeviceDto(
        val id: Long,
        val userId: Long,
        val vehicleId: Long?,
        val deviceName: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    /**
     * Registra (ou atualiza) o dispositivo no backend.
     * Chamado após login/registro bem-sucedido.
     */
    suspend fun registerMobileDevice(deviceName: String, vehicleId: Long? = null): Result<MobileDeviceDto> {
        val body = buildMap<String, Any?> {
            put("deviceName", deviceName)
            if (vehicleId != null) put("vehicleId", vehicleId)
        }
        return post("/v1/mobile/device", body).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("device"), MobileDeviceDto::class.java)
        }
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

    // ── Simulação do veículo ──────────────────────────────────────────────

    data class SimulationDecisionDto(
        val vehicleId: Long,
        val simulated: Boolean,
        val hasDevice: Boolean,
        val deviceSimulated: Boolean,
        val simulationAvailable: Boolean,
        val recommended: Boolean,
        val reason: String
    )

    /**
     * Pergunta ao backend se este veículo deve entrar na frota simulada.
     * Chamado pelo app logo após cadastrar um veículo.
     */
    suspend fun getSimulationDecision(vehicleId: Long): Result<SimulationDecisionDto> {
        return get("/v1/vehicles/$vehicleId/simulation").map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("simulation"), SimulationDecisionDto::class.java)
        }
    }

    /**
     * Envia ao backend a escolha sobre a simulação:
     * true = adicionar à simulação, false = manter como teste real.
     */
    suspend fun setVehicleSimulation(vehicleId: Long, simulate: Boolean): Result<SimulationDecisionDto> {
        val body = mapOf("simulate" to simulate)
        return post("/v1/vehicles/$vehicleId/simulation", body).map { json ->
            val obj = JsonParser.parseString(json).asJsonObject
            gson.fromJson(obj.getAsJsonObject("simulation"), SimulationDecisionDto::class.java)
        }
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
            .apply { firebaseBearer()?.let { header("Authorization", it) } }
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
                if (auth) firebaseBearer()?.let { header("Authorization", it) }
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
            .apply { firebaseBearer()?.let { header("Authorization", it) } }
            .put(requestBody)
            .build()
        execute(request)
    }

    private suspend fun delete(path: String): Result<String> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl$path")
            .apply { firebaseBearer()?.let { header("Authorization", it) } }
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
            val friendly = friendlyNetworkError(e)
            Log.e(TAG, "Request failed: $friendly (${e.javaClass.simpleName})")
            Result.failure(Exception(friendly, e))
        }
    }

    /**
     * Traduz erros de conexão (OkHttp/Java) em mensagens acionáveis em PT-BR.
     * O texto padrão do OkHttp ("Failed to connect to /192.168.0.10:3000") não
     * diz ao usuário o que fazer quando o backend está fora do ar ou o celular
     * está em outra rede.
     */
    private fun friendlyNetworkError(e: Exception): String {
        val root = generateSequence<Throwable>(e) { it.cause }.last()
        return when (root) {
            is java.net.UnknownHostException ->
                "Não foi possível resolver o endereço da API ($baseUrl). Confira o IP do servidor em Configurações > Vínculo de API."
            is java.net.ConnectException, is java.net.SocketTimeoutException ->
                "Sem conexão com a API em $baseUrl. Verifique se o backend está no ar (./api.sh start) e se o celular está na mesma rede Wi-Fi do servidor."
            is java.io.IOException ->
                "Falha de rede ao falar com $baseUrl (${root.javaClass.simpleName}). Tente novamente."
            else -> e.message ?: "Falha de rede ao falar com $baseUrl."
        }
    }

    /** Obtém um token atual do Firebase para cada requisição autenticada. */
    private suspend fun firebaseBearer(): String? =
        FirebaseAuth.getInstance().currentUser
            ?.getIdToken(false)
            ?.await()
            ?.token
            ?.let { "Bearer $it" }
}
