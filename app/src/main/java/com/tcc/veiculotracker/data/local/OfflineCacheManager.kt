package com.tcc.veiculotracker.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.tcc.veiculotracker.data.model.VehicleModel
import java.io.File

/**
 * Cache offline simples baseado em arquivos JSON gravados no armazenamento
 * interno do app (não precisa de permissão). Substitui o Room para o caso de
 * uso de "últimas localizações dos veículos disponíveis sem internet".
 *
 * Estrutura de arquivos:
 *   vehicles.json  — lista de VehicleModel do usuário logado (inclui lat/lng)
 *
 * O cache é atualizado de forma periódica pelo SyncManager (a cada N minutos)
 * e lido imediatamente quando o app detecta ausência de rede.
 */
object OfflineCacheManager {

    private const val VEHICLES_FILE = "vehicles_cache.json"
    private val gson = Gson()

    // ── Veículos ────────────────────────────────────────────────────────────

    fun saveVehicles(context: Context, vehicles: List<VehicleModel>) {
        writeFile(context, VEHICLES_FILE, gson.toJson(vehicles))
    }

    fun loadVehicles(context: Context): List<VehicleModel> {
        val json = readFile(context, VEHICLES_FILE) ?: return emptyList()
        return runCatching {
            val type = object : TypeToken<List<VehicleModel>>() {}.type
            gson.fromJson<List<VehicleModel>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /** Retorna o timestamp da última atualização do cache, ou 0 se não houver cache. */
    fun vehiclesCacheAge(context: Context): Long {
        val file = File(context.filesDir, VEHICLES_FILE)
        if (!file.exists()) return 0L
        return System.currentTimeMillis() - file.lastModified()
    }

    // ── Utilitários internos ────────────────────────────────────────────────

    private fun writeFile(context: Context, name: String, content: String) {
        runCatching {
            File(context.filesDir, name).writeText(content, Charsets.UTF_8)
        }
    }

    private fun readFile(context: Context, name: String): String? {
        return runCatching {
            val file = File(context.filesDir, name)
            if (file.exists()) file.readText(Charsets.UTF_8) else null
        }.getOrNull()
    }
}
