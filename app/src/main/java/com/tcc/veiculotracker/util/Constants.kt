package com.tcc.veiculotracker.util

object Constants {
    const val PREFS_NAME = "securitas_prefs"
    // ID numérico estável do usuário (derivado do UID do Firebase Auth).
    const val KEY_USER_ID = "user_id"
    // UID string do Firebase Auth — usado para identificar a conta sem Room.
    const val KEY_FIREBASE_UID = "firebase_uid"
    const val KEY_LOGGED_IN = "logged_in"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_DARK_MODE = "dark_mode"
    const val KEY_NOTIFICATIONS = "notifications_enabled"
    const val KEY_TRACKING_INTERVAL = "tracking_interval"
    const val KEY_API_URL = "api_url"
    const val KEY_API_TOKEN = "api_token"
    // ID do usuário no backend Node.js (SQLite do servidor).
    const val KEY_BACKEND_USER_ID = "backend_user_id"
}
