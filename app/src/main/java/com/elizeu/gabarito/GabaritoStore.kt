package com.elizeu.gabarito

import android.content.Context

/** Persistência do gabarito oficial em SharedPreferences. */
object GabaritoStore {
    private const val PREFS = "gabarito"
    private const val KEY_DATA = "data"
    private const val KEY_VALOR = "valor"
    private const val KEY_TIPO = "tipo"
    const val VALOR_PADRAO = 10.0

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Valor total que a prova vale (em pontos). Padrão: 10. */
    fun saveValor(context: Context, valor: Double) {
        prefs(context).edit().putString(KEY_VALOR, valor.toString()).apply()
    }

    fun loadValor(context: Context): Double {
        val s = prefs(context).getString(KEY_VALOR, null)
            ?.replace(',', '.')
            ?: return VALOR_PADRAO
        return s.toDoubleOrNull()?.takeIf { it > 0.0 } ?: VALOR_PADRAO
    }

    fun saveTipo(context: Context, tipo: TipoGabarito) {
        prefs(context).edit().putString(KEY_TIPO, tipo.name).apply()
    }

    fun loadTipo(context: Context): TipoGabarito =
        TipoGabarito.fromId(prefs(context).getString(KEY_TIPO, null))

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
