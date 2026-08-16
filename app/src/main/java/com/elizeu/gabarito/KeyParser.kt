package com.elizeu.gabarito

/**
 * Parser do gabarito digitado (ex.: "1E, 2D, 3C, 4A, 5B" ou "1E 2D 3C 4A").
 */
object KeyParser {

    /** Retorna mapa questao -> letra (A..E). Questões duplicadas mantêm a última. */
    fun parse(input: String): Map<Int, Char> {
        val out = LinkedHashMap<Int, Char>()
        val text = input.uppercase().replace(',', ' ').replace(';', ' ')
        val regex = Regex("""(\d+)\s*[.:-]?\s*([A-E])""")
        for (m in regex.findAll(text)) {
            val q = m.groupValues[1].toInt()
            val letter = m.groupValues[2][0]
            if (q > 0) out[q] = letter
        }
        return out
    }

    /** Valida se há ao menos uma resposta. */
    fun isValid(key: Map<Int, Char>): Boolean = key.isNotEmpty()

    fun toDisplay(key: Map<Int, Char>): String =
        key.entries.joinToString("  ") { "${it.key}-${it.value}" }
}
