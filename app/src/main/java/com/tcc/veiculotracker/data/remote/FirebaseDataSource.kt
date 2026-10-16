package com.tcc.veiculotracker.data.remote

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.tcc.veiculotracker.data.local.entity.Route
import com.tcc.veiculotracker.data.local.entity.User
import com.tcc.veiculotracker.data.local.entity.Vehicle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

// Bug 1 fix: getInstance(String) abre um FirebaseApp nomeado, não um banco Firestore.
// Para apontar ao banco "securitas" usa-se getInstance(FirebaseApp, databaseId).
// Bug 10 fix: RTDB usa a URL padrão do google-services.json automaticamente;
// getInstance() sem argumento garante que app e backend escutem o mesmo banco.
private const val FIRESTORE_DB_ID = "securitas"

class FirebaseDataSource {

    // Bug 1: abre o banco Firestore "securitas" dentro do FirebaseApp padrão.
    private val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DB_ID)

    // Bug 10: a URL padrão registrada no google-services.json é usada automaticamente;
    // getInstance() sem argumento usa essa URL, evitando hard-code.
    private val rtdb = FirebaseDatabase.getInstance().reference

    private val firebaseAuth = FirebaseAuth.getInstance()

    // Conta logada (Firebase Auth): usada para vincular os documentos enviados
    // pelo app à conta que os criou (firebaseUid/ownerUid/ownerEmail), permitindo
    // que o backend identifique de quem é cada usuário e cada veículo.
    private val currentAccountUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val currentAccountEmail: String?
        get() = firebaseAuth.currentUser?.email

    // ── Cloud Firestore: Users ──────────────────────────────────────────

    suspend fun syncUser(user: User) {
        val userData = buildMap {
            put("name", user.name)
            put("email", user.email)
            put("phone", user.phone)
            put("createdAt", user.createdAt)
            // Bug 11 fix: firebaseUid é obrigatório para o cloud-import ligar veículos
            // ao dono. Usa o uid do usuário, com fallback para a conta logada atualmente.
            // Se nenhum estiver disponível, grava string vazia em vez de omitir o campo,
            // assim o backend pode filtrar o valor mas nunca recebe um documento sem a chave.
            val uid = user.firebaseUid.ifBlank { null } ?: currentAccountUid ?: ""
            put("firebaseUid", uid)
        }
        firestore.collection("users")
            .document(user.id.toString())
            .set(userData)
            .await()
    }

    suspend fun getUserData(userId: String): Map<String, Any>? {
        val doc = firestore.collection("users").document(userId).get().await()
        return doc.data
    }

    // ── Cloud Firestore: Vehicles ───────────────────────────────────────

    suspend fun syncVehicle(vehicle: Vehicle) {
        val vehicleData = buildMap {
            put("id", vehicle.id)
            put("userId", vehicle.userId)
            put("plate", vehicle.plate)
            put("model", vehicle.model)
            put("brand", vehicle.brand)
            put("year", vehicle.year)
            put("color", vehicle.color)
            put("isBlocked", vehicle.isBlocked)
            put("latitude", vehicle.latitude)
            put("longitude", vehicle.longitude)
            put("speed", vehicle.speed)
            put("lastUpdate", vehicle.lastUpdate)
            put("createdAt", vehicle.createdAt)
            // Dono da conta logada no Firebase Auth: é o vínculo que permite ao
            // backend exibir este veículo na conta que o cadastrou no app.
            currentAccountUid?.let { put("ownerUid", it) }
            currentAccountEmail?.let { put("ownerEmail", it) }
        }
        firestore.collection("vehicles")
            .document(vehicle.id.toString())
            .set(vehicleData)
            .await()
    }

    suspend fun deleteVehicle(vehicleId: String) {
        firestore.collection("vehicles").document(vehicleId).delete().await()
    }

    // Bug 2 fix: userId armazenado no Firestore é Long; a query deve usar Long,
    // não String. Recebe Long diretamente para evitar NumberFormatException com UIDs string.
    fun getVehiclesByUser(userId: Long): Flow<List<Vehicle>> = callbackFlow {
        val listener = firestore.collection("vehicles")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val vehicles = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { data -> vehicleFromDocument(doc.id, data) }
                } ?: emptyList()
                trySend(vehicles)
            }
        awaitClose { listener.remove() }
    }

    /**
     * Consulta única (one-shot) dos veículos do usuário, usada para importar
     * do Firestore para o banco local ao abrir o app.
     */
    suspend fun getVehiclesOnce(userId: Long): List<Vehicle> {
        val snapshot = firestore.collection("vehicles")
            .whereEqualTo("userId", userId)
            .get()
            .await()
        return snapshot.documents.mapNotNull { doc ->
            doc.data?.let { data -> vehicleFromDocument(doc.id, data) }
        }
    }

    private fun vehicleFromDocument(docId: String, data: Map<String, Any>): Vehicle {
        return Vehicle(
            id = (data["id"] as? Number)?.toLong() ?: docId.toLongOrNull() ?: 0L,
            userId = (data["userId"] as? Number)?.toLong() ?: 0L,
            plate = data["plate"] as? String ?: "",
            model = data["model"] as? String ?: "",
            brand = data["brand"] as? String ?: "",
            year = (data["year"] as? Number)?.toInt() ?: 0,
            color = data["color"] as? String ?: "",
            isBlocked = data["isBlocked"] as? Boolean ?: false,
            latitude = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
            speed = (data["speed"] as? Number)?.toDouble() ?: 0.0,
            lastUpdate = (data["lastUpdate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }

    // ── Cloud Firestore: Routes ─────────────────────────────────────────

    suspend fun syncRoute(route: Route) {
        val routeData = mapOf(
            "id" to route.id,
            "vehicleId" to route.vehicleId,
            "userId" to route.userId,
            "startLatitude" to route.startLatitude,
            "startLongitude" to route.startLongitude,
            "endLatitude" to route.endLatitude,
            "endLongitude" to route.endLongitude,
            "startTime" to route.startTime,
            "endTime" to route.endTime,
            "distance" to route.distance,
            "status" to route.status
        )
        firestore.collection("routes")
            .document(route.id.toString())
            .set(routeData)
            .await()
    }

    suspend fun deleteRoute(routeId: String) {
        firestore.collection("routes").document(routeId).delete().await()
    }

    // Bug 2 fix: mesmo problema — userId é Long no Firestore. Recebe Long diretamente.
    fun getRoutesByUser(userId: Long): Flow<List<Route>> = callbackFlow {
        val listener = firestore.collection("routes")
            .whereEqualTo("userId", userId)
            .orderBy("startTime", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val routes = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { data ->
                        Route(
                            id = (data["id"] as? Number)?.toLong() ?: doc.id.toLongOrNull() ?: 0L,
                            vehicleId = (data["vehicleId"] as? Number)?.toLong() ?: 0L,
                            userId = (data["userId"] as? Number)?.toLong() ?: 0L,
                            startLatitude = (data["startLatitude"] as? Number)?.toDouble() ?: 0.0,
                            startLongitude = (data["startLongitude"] as? Number)?.toDouble() ?: 0.0,
                            endLatitude = (data["endLatitude"] as? Number)?.toDouble() ?: 0.0,
                            endLongitude = (data["endLongitude"] as? Number)?.toDouble() ?: 0.0,
                            startTime = (data["startTime"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                            endTime = (data["endTime"] as? Number)?.toLong() ?: 0L,
                            distance = (data["distance"] as? Number)?.toDouble() ?: 0.0,
                            status = data["status"] as? String ?: "em_andamento"
                        )
                    }
                } ?: emptyList()
                trySend(routes)
            }
        awaitClose { listener.remove() }
    }

    // ── Firebase Realtime Database: Telemetry ───────────────────────────

    data class TelemetryData(
        val latitude: Double = 0.0,
        val longitude: Double = 0.0,
        val speed: Double = 0.0,
        val heading: Double = 0.0,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun listenTelemetry(vehicleId: String): Flow<TelemetryData> = callbackFlow {
        val ref = rtdb.child("telemetry").child(vehicleId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(TelemetryData::class.java)
                if (data != null) {
                    trySend(data)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseDataSource", "listenTelemetry onCancelled: ${error.message}")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendTelemetry(vehicleId: String, data: TelemetryData) {
        rtdb.child("telemetry").child(vehicleId).setValue(data).await()
    }

    // ── Firebase Realtime Database: Commands ────────────────────────────

    data class RemoteCommand(
        val command: String = "",
        val timestamp: Long = System.currentTimeMillis(),
        val status: String = "pending"
    )

    fun listenCommandResponse(vehicleId: String): Flow<RemoteCommand> = callbackFlow {
        val ref = rtdb.child("commands").child(vehicleId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val data = snapshot.getValue(RemoteCommand::class.java)
                if (data != null) {
                    trySend(data)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseDataSource", "listenCommandResponse onCancelled: ${error.message}")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendCommand(vehicleId: String, command: String) {
        val cmd = RemoteCommand(
            command = command,
            timestamp = System.currentTimeMillis(),
            status = "pending"
        )
        rtdb.child("commands").child(vehicleId).setValue(cmd).await()
    }
}
