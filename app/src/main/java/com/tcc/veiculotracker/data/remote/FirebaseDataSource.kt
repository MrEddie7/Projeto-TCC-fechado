package com.tcc.veiculotracker.data.remote

import android.util.Log
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

class FirebaseDataSource {

    private val firestore = FirebaseFirestore.getInstance("securitas")
    private val rtdb = FirebaseDatabase.getInstance().reference

    // ── Cloud Firestore: Users ──────────────────────────────────────────

    suspend fun syncUser(user: User) {
        val userData = mapOf(
            "name" to user.name,
            "email" to user.email,
            "phone" to user.phone,
            "createdAt" to user.createdAt
        )
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
        val vehicleData = mapOf(
            "id" to vehicle.id,
            "userId" to vehicle.userId,
            "plate" to vehicle.plate,
            "model" to vehicle.model,
            "brand" to vehicle.brand,
            "year" to vehicle.year,
            "color" to vehicle.color,
            "isBlocked" to vehicle.isBlocked,
            "latitude" to vehicle.latitude,
            "longitude" to vehicle.longitude,
            "speed" to vehicle.speed,
            "lastUpdate" to vehicle.lastUpdate,
            "createdAt" to vehicle.createdAt
        )
        firestore.collection("vehicles")
            .document(vehicle.id.toString())
            .set(vehicleData)
            .await()
    }

    suspend fun deleteVehicle(vehicleId: String) {
        firestore.collection("vehicles").document(vehicleId).delete().await()
    }

    fun getVehiclesByUser(userId: String): Flow<List<Vehicle>> = callbackFlow {
        val listener = firestore.collection("vehicles")
            .whereEqualTo("userId", userId.toLong())
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

    fun getRoutesByUser(userId: String): Flow<List<Route>> = callbackFlow {
        val listener = firestore.collection("routes")
            .whereEqualTo("userId", userId.toLong())
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
                Log.e("FirebaseDataSource", "listenTelemetry onCancelled: ${error.message}")
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
