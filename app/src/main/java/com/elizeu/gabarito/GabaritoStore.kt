package com.elizeu.gabarito

import android.content.Context

/** Persistência do gabarito oficial em SharedPreferences. */
object GabaritoStore {
    private const val PREFS = "gabarito"
    private const val KEY_DATA = "data"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(context: Context, key: Map<Int, Char>) {
        val data = key.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs(context).edit().putString(KEY_DATA, data).apply()
    }

    fun load(context: Context): Map<Int, Char> {
        val data = prefs(context).getString(KEY_DATA, "") ?: return emptyMap()
        val out = LinkedHashMap<Int, Char>()
        for (part in data.split(";")) {
            if (part.isBlank()) continue
            val idx = part.indexOf(':')
            if (idx <= 0) continue
            val q = part.substring(0, idx).toIntOrNull() ?: continue
            val l = part.substring(idx + 1).firstOrNull() ?: continue
            out[q] = l
        }
        return out
    }

    fun clear(context: Context) {
        prefs(context).edit().remove(KEY_DATA).apply()
    }
}
