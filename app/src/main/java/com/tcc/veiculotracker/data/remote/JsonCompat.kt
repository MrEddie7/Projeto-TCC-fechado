package com.tcc.veiculotracker.data.remote

import com.google.gson.JsonObject

/**
 * Leitura tolerante dos campos devolvidos pela API.
 *
 * O backend mudou a identidade do usuário para o UID do Firebase Auth
 * (string). Alguns documentos ainda carregam o `userId` numérico legado
 * (hashCode do uid) e outros carregam o próprio uid como string. O Gson
 * quebrava a desserialização ("Expected a long but was STRING"), zerando a
 * lista de veículos no app. Aqui todo campo é lido sem depender do tipo.
 */
internal fun JsonObject.optLong(name: String, default: Long = 0L): Long {
    val el = get(name) ?: return default
    if (el.isJsonNull) return default
    val p = el.asJsonPrimitive
    return when {
        p.isString -> p.asString.trim().toLongOrNull() ?: default
        p.isNumber -> p.asLong
        else -> default
    }
}

internal fun JsonObject.optInt(name: String, default: Int = 0): Int =
    optLong(name, default.toLong()).toInt()

internal fun JsonObject.optDouble(name: String, default: Double = 0.0): Double {
    val el = get(name) ?: return default
    if (el.isJsonNull) return default
    val p = el.asJsonPrimitive
    return when {
        p.isString -> p.asString.trim().toDoubleOrNull() ?: default
        p.isNumber -> p.asDouble
        else -> default
    }
}

internal fun JsonObject.optString(name: String, default: String? = null): String? {
    val el = get(name) ?: return default
    if (el.isJsonNull) return default
    val p = el.asJsonPrimitive
    return when {
        p.isString -> p.asString
        p.isNumber -> p.asString
        p.isBoolean -> p.asString
        else -> default
    }
}

internal fun JsonObject.optBool(name: String, default: Boolean = false): Boolean {
    val el = get(name) ?: return default
    if (el.isJsonNull) return default
    val p = el.asJsonPrimitive
    return when {
        p.isBoolean -> p.asBoolean
        p.isString -> p.asString.equals("true", ignoreCase = true)
        else -> default
    }
}

/**
 * Identidade numérica do usuário, no formato legado do app (hash do UID).
 * A API pode devolver `userId` numérico (veículos antigos do app) ou o UID
 * em string (veículos criados pela API) — nos dois casos o app precisa de um
 * id numérico estável.
 */
internal fun numericUserId(rawUserId: Long, ownerUid: String?): Long {
    if (rawUserId > 0L) return rawUserId
    val uid = ownerUid ?: return 0L
    val hash = uid.hashCode().toLong()
    return if (hash < 0) -hash else hash
}
