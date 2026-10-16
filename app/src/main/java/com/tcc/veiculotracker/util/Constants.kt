package com.tcc.veiculotracker.util

object Constants {
    const val PREFS_NAME = "securitas_prefs"
    // ID do usuário no banco local (Room). Usado por toda a camada de repositório
    // para filtrar veículos, rotas etc. Nunca deve ser substituído pelo ID do backend.
    const val KEY_USER_ID = "user_id"
    const val KEY_LOGGED_IN = "logged_in"
    const val KEY_USER_NAME = "user_name"
    const val KEY_USER_EMAIL = "user_email"
    const val KEY_DARK_MODE = "dark_mode"
    const val KEY_NOTIFICATIONS = "notifications_enabled"
    const val KEY_TRACKING_INTERVAL = "tracking_interval"
    const val KEY_API_URL = "api_url"
    const val KEY_API_TOKEN = "api_token"
    // Bug 5 fix: ID do usuário no backend (SQLite do servidor). Separado de
    // KEY_USER_ID para não corromper a chave usada pelo Room local.
    const val KEY_BACKEND_USER_ID = "backend_user_id"
}
