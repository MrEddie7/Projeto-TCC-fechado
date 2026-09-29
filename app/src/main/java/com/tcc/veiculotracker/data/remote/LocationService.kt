package com.tcc.veiculotracker.data.remote

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Serviço de localização do celular conectado.
 * 
 * Obtém a localização do próprio dispositivo Android e envia para o backend,
 * permitindo rastrear o celular do usuário junto com os veículos.
 */
class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val TAG = "LocationService"
        private const val UPDATE_INTERVAL_MS = 10000L // 10 segundos
        private const val FASTEST_INTERVAL_MS = 5000L // 5 segundos
    }

    // ── Permissões ────────────────────────────────────────────────────────

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ── Localização atual ─────────────────────────────────────────────────

    /**
     * Obtém a última localização conhecida do dispositivo.
     * Retorna null se não houver permissão ou localização disponível.
     */
    suspend fun getLastLocation(): Location? {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Sem permissão de localização")
            return null
        }
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao obter última localização: ${e.message}")
            null
        }
    }

    // ── Stream de localização ─────────────────────────────────────────────

    /**
     * Flow que emite a localização do dispositivo em tempo real.
     * Deve ser coletado em uma coroutine com permissão de localização.
     */
    fun requestLocationUpdates(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Sem permissão de localização"))
            return@callbackFlow
        }

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            UPDATE_INTERVAL_MS
        ).apply {
            setMinUpdateIntervalMillis(FASTEST_INTERVAL_MS)
            setWaitForAccurateLocation(true)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    trySend(location)
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                callback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
        }
    }

    // ── Envio para o backend ──────────────────────────────────────────────

    /**
     * Envia a localização do celular para o backend.
     * A localização é associada ao usuário logado.
     */
    suspend fun sendLocationToBackend(
        apiClient: ApiClient,
        location: Location
    ): Result<Boolean> {
        return apiClient.sendMobileLocation(
            latitude = location.latitude,
            longitude = location.longitude,
            speed = location.speed.toDouble(),
            heading = location.bearing.toDouble(),
            accuracy = location.accuracy.toDouble()
        )
    }
}
