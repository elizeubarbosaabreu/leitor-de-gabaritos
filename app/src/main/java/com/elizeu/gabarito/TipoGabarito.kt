package com.elizeu.gabarito

/** Modelos de folha de respostas aceitos pelo leitor. */
enum class TipoGabarito(
    val alternativas: Int,
    val descricao: String
) {
    A_D(4, "Gabarito A–D (até 40 questões)"),
    A_E(5, "Gabarito A–E (até 40 questões)");

    companion object {
        fun fromId(id: String?): TipoGabarito = entries.firstOrNull { it.name == id } ?: A_D

        /** Escolhe A–E quando o gabarito oficial contém a alternativa E. */
        fun fromKey(key: Map<Int, Char>): TipoGabarito =
            if (key.values.any { it == 'E' }) A_E else A_D
    }
}
