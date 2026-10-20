package com.tcc.veiculotracker.data.repository

import android.content.Context
import com.tcc.veiculotracker.util.Constants

/**
 * Gerencia a configuração da URL e token do backend Node.js.
 * Substituiu o Room (ApiConfigDao) por SharedPreferences — o dado é simples
 * e não precisa de banco de dados.
 */
class ApiConfigRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var apiUrl: String
        get() = prefs.getString(Constants.KEY_API_URL, "") ?: ""
        set(value) { prefs.edit().putString(Constants.KEY_API_URL, value).apply() }

    var apiToken: String
        get() = prefs.getString(Constants.KEY_API_TOKEN, "") ?: ""
        set(value) { prefs.edit().putString(Constants.KEY_API_TOKEN, value).apply() }

    val isConfigured: Boolean
        get() = apiUrl.isNotBlank()

    fun clear() {
        prefs.edit()
            .remove(Constants.KEY_API_URL)
            .remove(Constants.KEY_API_TOKEN)
            .apply()
    }
}
