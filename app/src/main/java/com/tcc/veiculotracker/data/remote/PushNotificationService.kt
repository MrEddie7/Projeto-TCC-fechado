package com.tcc.veiculotracker.data.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.tcc.veiculotracker.App
import com.tcc.veiculotracker.MainActivity
import com.tcc.veiculotracker.R
import com.tcc.veiculotracker.util.Constants
import kotlinx.coroutines.tasks.await

private const val TAG = "PushNotification"

const val NOTIFICATION_CHANNEL_ALERTS = "alerts"

// ── Canal de notificações ───────────────────────────────────────────────────

/**
 * Cria o canal "Alertas do veículo" (idempotente). É o canal usado nos
 * alertas de bloqueio/desbloqueio confirmados pelo rastreador.
 */
fun ensureNotificationChannels(context: Context) {
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val alerts = NotificationChannel(
        NOTIFICATION_CHANNEL_ALERTS,
        "Alertas do veículo",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Bloqueio e desbloqueio confirmados pelo rastreador"
        enableVibration(true)
    }
    manager.createNotificationChannel(alerts)
}

// ── Exibição do alerta ──────────────────────────────────────────────────────

/**
 * Mostra o alerta de estado do veículo. No Android 13+ a notificação exige a
 * permissão POST_NOTIFICATIONS — sem ela o alerta é suprimido (e nada quebra).
 */
fun showVehicleStateNotification(context: Context, title: String, body: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        Log.i(TAG, "POST_NOTIFICATIONS negada: alerta não exibido")
        return
    }
    ensureNotificationChannels(context)
    val tapIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
    }
    val contentIntent = PendingIntent.getActivity(
        context, 0, tapIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ALERTS)
        .setSmallIcon(R.drawable.ic_stat_notification)
        .setContentTitle(title)
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setAutoCancel(true)
        .setContentIntent(contentIntent)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setCategory(NotificationCompat.CATEGORY_ALARM)
        .build()
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    try {
        manager.notify(1001, notification)
    } catch (e: SecurityException) {
        Log.w(TAG, "notify recusado: ${e.message}")
    }
}

// ── Registro do token FCM ───────────────────────────────────────────────────

/**
 * Guarda o token FCM localmente e o envia à API assim que existir sessão
 * (Firebase Auth) e backend configurado. O backend grava em userTokens/{uid}
 * e usa a lista para disparar o push de bloqueio/desbloqueio.
 */
object PushTokenManager {

    private const val KEY_TOKEN = "push.fcm_token"
    private const val KEY_SENT = "push.fcm_sent"

    private fun prefs(context: Context) =
        context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    fun storedToken(context: Context): String? =
        prefs(context).getString(KEY_TOKEN, null)

    /** Guarda o token; token novo invalida o flag de envio. */
    fun storeToken(context: Context, token: String) {
        if (prefs(context).getString(KEY_TOKEN, null) == token) return
        prefs(context).edit()
            .putString(KEY_TOKEN, token)
            .putBoolean(KEY_SENT, false)
            .apply()
    }

    /**
     * Sincroniza o token corrente com a API. Falha (rede, sem API, sem sessão)
     * é silenciosa: ocorre de novo no próximo login/boot.
     */
    suspend fun registerCurrentToken(context: Context) {
        val app = context.applicationContext as App
        try {
            val token = storedToken(context) ?: FirebaseMessaging.getInstance().token.await()
            storeToken(context, token)
            if (FirebaseAuth.getInstance().currentUser == null ||
                !app.backendRepository.isConfigured
            ) return
            if (prefs(context).getBoolean(KEY_SENT, false)) return
            app.backendRepository.registerPushToken(token)
                .onSuccess { prefs(context).edit().putBoolean(KEY_SENT, true).apply() }
        } catch (e: Exception) {
            Log.w(TAG, "registerCurrentToken: ${e.message}")
        }
    }
}

// ── Serviço de push ─────────────────────────────────────────────────────────

/**
 * Recebe os push enviados pela API (blocking/desbloqueio confirmado).
 * A mensagem chega como data-only: onMessageReceived é chamado em qualquer
 * estado do app (inclusive com o app fechado) e quem renderiza é este app.
 */
class PushNotificationService : FirebaseMessagingService() {

    /** Token novo/renovado do FCM: guarda local; o envio à API acontece no próximo boot/login. */
    override fun onNewToken(token: String) {
        PushTokenManager.storeToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "vehicle_state") return
        val title = data["title"] ?: return
        val body = data["body"] ?: return
        showVehicleStateNotification(this, title, body)
    }
}
