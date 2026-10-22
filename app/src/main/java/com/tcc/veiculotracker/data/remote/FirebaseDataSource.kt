package com.tcc.veiculotracker.data.remote

import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.tcc.veiculotracker.data.model.RouteModel
import com.tcc.veiculotracker.data.model.UserModel
import com.tcc.veiculotracker.data.model.VehicleModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

private const val FIRESTORE_DB_ID = "securitas"

class FirebaseDataSource {

    private val firestore = FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FIRESTORE_DB_ID)
    private val rtdb = FirebaseDatabase.getInstance().reference
    private val firebaseAuth = FirebaseAuth.getInstance()

    private val currentAccountUid: String?
        get() = firebaseAuth.currentUser?.uid

    private val currentAccountEmail: String?
        get() = firebaseAuth.currentUser?.email

    // ── Cloud Firestore: Users ──────────────────────────────────────────────

    suspend fun syncUser(user: UserModel) {
        // Chave do documento = UID do Firebase Auth. É a MESMA identidade usada
        // pelo backend (users/{uid} no requireAuth) e pelas regras de segurança
        // (request.auth.uid == userId). Usar o id numérico criaria um documento
        // paralelo e seria negado pelas regras.
        val uid = user.firebaseUid.ifBlank { null } ?: currentAccountUid ?: return
        val data = mapOf(
            "name"        to user.name,
            "email"       to user.email,
            "phone"       to user.phone,
            "createdAt"   to user.createdAt,
            "firebaseUid" to uid
        )
        firestore.collection("users")
            .document(uid)
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun getUserData(userId: String): Map<String, Any>? {
        return firestore.collection("users").document(userId).get().await().data
    }

    // ── Cloud Firestore: Vehicles ───────────────────────────────────────────

    /**
     * Publica um veículo no Firestore.
     *
     * `isNew` distingue cadastro de edição. Isso importa porque existe um
     * veículo com id=0 (ECA-3567) que tem um rastreador vinculado: tratá-lo
     * como "sem id" criava um documento novo (ECA-3567 duplicado) e o
     * rastreador continuaria ligado ao documento original.
     *
     * A gravação é MERGE: o documento é compartilhado com a API (telemetria,
     * bloqueio, `updatedAt`), e um `set` completo apagaria esses campos.
     */
    suspend fun syncVehicle(vehicle: VehicleModel, isNew: Boolean = false) {
        // Fallback sem backend: um veículo novo chega aqui com id=0. Como cada
        // usuário pode ter VÁRIOS veículos, gravar todos no documento "0"
        // faria o último sobrescrever os anteriores — gera id único no mesmo
        // formato da API (Date.now()*100 + aleatório) quando o app não tem um.
        val id = if (isNew) System.currentTimeMillis() * 100 + (0..99).random() else vehicle.id
        val data = buildMap<String, Any> {
            put("id",          id)
            put("userId",      vehicle.userId)
            put("plate",       vehicle.plate)
            put("model",       vehicle.model)
            put("brand",       vehicle.brand)
            put("year",        vehicle.year)
            put("color",       vehicle.color)
            put("isBlocked",   vehicle.isBlocked)
            currentAccountUid?.let   { put("ownerUid",   it) }
            currentAccountEmail?.let { put("ownerEmail", it) }
            // Posição e carimbo de tempo são da API/telemetria: em edição não
            // podem ser regravados com valores defasados do app.
            if (isNew) {
                put("latitude",    vehicle.latitude)
                put("longitude",   vehicle.longitude)
                put("speed",       vehicle.speed)
                put("lastUpdate",  vehicle.lastUpdate)
                put("createdAt",   vehicle.createdAt)
            }
        }
        firestore.collection("vehicles")
            .document(id.toString())
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun deleteVehicle(vehicleId: String) {
        firestore.collection("vehicles").document(vehicleId).delete().await()
    }

    fun getVehiclesByUser(userId: Long): Flow<List<VehicleModel>> = callbackFlow {
        // ownerUid é a chave de identidade compartilhada com a API. O userId
        // numérico é apenas compatibilidade com os modelos legados do app.
        val ownerUid = currentAccountUid
        if (ownerUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("vehicles")
            .whereEqualTo("ownerUid", ownerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { vehicleFromMap(doc.id, it) }
                } ?: emptyList()
                trySend(list)
            }
        awaitClose { listener.remove() }
    }

    suspend fun getVehiclesOnce(userId: Long): List<VehicleModel> {
        val ownerUid = currentAccountUid ?: return emptyList()
        return firestore.collection("vehicles")
            .whereEqualTo("ownerUid", ownerUid)
            .get().await()
            .documents.mapNotNull { doc -> doc.data?.let { vehicleFromMap(doc.id, it) } }
    }

    private fun vehicleFromMap(docId: String, data: Map<String, Any>): VehicleModel = VehicleModel(
        id         = (data["id"] as? Number)?.toLong() ?: docId.toLongOrNull() ?: 0L,
        userId     = (data["userId"] as? Number)?.toLong()
            // Veículos criados pela API guardam o UID em string: converte para
            // o id numérico legado (hash do UID), como no AuthRepository.
            ?: (data["ownerUid"] as? String)?.let { uid ->
                val h = uid.hashCode().toLong(); if (h < 0) -h else h
            }
            ?: 0L,
        ownerUid   = data["ownerUid"] as? String,
        plate      = data["plate"] as? String ?: "",
        model      = data["model"] as? String ?: "",
        brand      = data["brand"] as? String ?: "",
        year       = (data["year"] as? Number)?.toInt() ?: 0,
        color      = data["color"] as? String ?: "",
        isBlocked  = data["isBlocked"] as? Boolean ?: false,
        latitude   = (data["latitude"] as? Number)?.toDouble() ?: 0.0,
        longitude  = (data["longitude"] as? Number)?.toDouble() ?: 0.0,
        speed      = (data["speed"] as? Number)?.toDouble() ?: 0.0,
        lastUpdate = (data["lastUpdate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        createdAt  = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )

    // ── Cloud Firestore: Routes ─────────────────────────────────────────────

    suspend fun syncRoute(route: RouteModel) {
        // ownerUid (UID do Firebase) é a identidade usada pelas regras de
        // segurança; userId numérico fica como compatibilidade com o modelo.
        val data = mutableMapOf<String, Any>(
            "id"             to route.id,
            "vehicleId"      to route.vehicleId,
            "userId"         to route.userId,
            "startLatitude"  to route.startLatitude,
            "startLongitude" to route.startLongitude,
            "endLatitude"    to route.endLatitude,
            "endLongitude"   to route.endLongitude,
            "startTime"      to route.startTime,
            "endTime"        to route.endTime,
            "distance"       to route.distance,
            "status"         to route.status
        )
        currentAccountUid?.let { data["ownerUid"] = it }
        firestore.collection("routes")
            .document(route.id.toString())
            .set(data)
            .await()
    }

    suspend fun deleteRoute(routeId: String) {
        firestore.collection("routes").document(routeId).delete().await()
    }

    fun getRoutesByUser(userId: Long): Flow<List<RouteModel>> = callbackFlow {
        // Filtra pelo UID do Firebase (ownerUid): é o que as regras autorizam e
        // o que o backend grava. Sem conta logada, devolve vazio.
        val ownerUid = currentAccountUid
        if (ownerUid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val listener = firestore.collection("routes")
            .whereEqualTo("ownerUid", ownerUid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { close(error); return@addSnapshotListener }
                val list = snapshot?.documents?.mapNotNull { doc ->
                    doc.data?.let { routeFromMap(doc.id, it) }
                } ?: emptyList()
                trySend(list.sortedByDescending { it.startTime })
            }
        awaitClose { listener.remove() }
    }

    private fun routeFromMap(docId: String, data: Map<String, Any>): RouteModel = RouteModel(
        id             = (data["id"] as? Number)?.toLong() ?: docId.toLongOrNull() ?: 0L,
        vehicleId      = (data["vehicleId"] as? Number)?.toLong() ?: 0L,
        userId         = (data["userId"] as? Number)?.toLong() ?: 0L,
        startLatitude  = (data["startLatitude"] as? Number)?.toDouble() ?: 0.0,
        startLongitude = (data["startLongitude"] as? Number)?.toDouble() ?: 0.0,
        endLatitude    = (data["endLatitude"] as? Number)?.toDouble() ?: 0.0,
        endLongitude   = (data["endLongitude"] as? Number)?.toDouble() ?: 0.0,
        startTime      = (data["startTime"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        endTime        = (data["endTime"] as? Number)?.toLong() ?: 0L,
        distance       = (data["distance"] as? Number)?.toDouble() ?: 0.0,
        status         = data["status"] as? String ?: "em_andamento"
    )

    // ── Firebase Realtime Database: Telemetry ───────────────────────────────

    data class TelemetryData(
        val latitude:  Double = 0.0,
        val longitude: Double = 0.0,
        val speed:     Double = 0.0,
        val heading:   Double = 0.0,
        val timestamp: Long   = System.currentTimeMillis()
    )

    /**
     * Escuta a posição do veículo publicada pela API/hardware.
     *
     * O app NUNCA escreve neste nó: `telemetry/{vehicleId}` é o canal do
     * rastreador (a API espelha o último ponto aqui). Gravar nele de dentro do
     * app sobrescreveria a posição real do GPS do veículo.
     */
    fun listenTelemetry(vehicleId: String): Flow<TelemetryData> = callbackFlow {
        val ref = rtdb.child("telemetry").child(vehicleId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(TelemetryData::class.java)?.let { trySend(it) }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseDataSource", "listenTelemetry: ${error.message}")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    // ── Firebase Realtime Database: Commands ────────────────────────────────

    data class RemoteCommand(
        val command:   String = "",
        val timestamp: Long   = System.currentTimeMillis(),
        val status:    String = "pending"
    )

    fun listenCommandResponse(vehicleId: String): Flow<RemoteCommand> = callbackFlow {
        val ref = rtdb.child("commands").child(vehicleId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(RemoteCommand::class.java)?.let { trySend(it) }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("FirebaseDataSource", "listenCommandResponse: ${error.message}")
                close(error.toException())
            }
        }
        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    suspend fun sendCommand(vehicleId: String, command: String) {
        val cmd = RemoteCommand(
            command   = command,
            timestamp = System.currentTimeMillis(),
            status    = "pending"
        )
        rtdb.child("commands").child(vehicleId).setValue(cmd).await()
    }
}
