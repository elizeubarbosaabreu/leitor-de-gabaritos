package com.elizeu.gabarito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Valida o motor Kotlin contra fotos reais de gabarito (escala ~1200px,
 * igual ao decode do app). Os .raw guardam cinza uint8 (largura x altura
 * no nome do arquivo).
 */
class RealPhotoTest {

    private data class Photo(val file: String, val w: Int, val h: Int, val nx: Int, val expected: String)

    private val photos = listOf(
        Photo("IMG_20260816_154712_1262x2244.raw", 1262, 2244, 4, "CADACACBAC"),
        Photo("IMG_20260816_154722_1262x2244.raw", 1262, 2244, 5, "CBEDCABBBC"),
        Photo("IMG_20260816_154715_1262x2244.raw", 1262, 2244, 4, "CADACACBAC"),
        Photo("IMG_20260816_154726_1262x2244.raw", 1262, 2244, 5, "CBEDCABBBC")
    )

    private fun loadGray(file: String, w: Int, h: Int): IntArray {
        val res = javaClass.classLoader!!.getResource("photos/$file")!!
        val bytes = File(res.toURI()).readBytes()
        assertEquals("tamanho do .raw", w * h, bytes.size)
        return IntArray(w * h) { bytes[it].toInt() and 0xff }
    }

    @Test
    fun readsAllRealPhotos() {
        val letters = "ABCDE"
        for (p in photos) {
            val gray = loadGray(p.file, p.w, p.h)
            val read = OmrEngine.readSheetGray(gray, p.w, p.h, p.nx)
            assertTrue("${p.file}: linhas=${read.rows.size}", read.rows.isNotEmpty())
            var ok = 0
            val got = StringBuilder()
            for (i in 0 until minOf(p.expected.length, read.rows.size)) {
                val a = read.answerOf(i)
                got.append(if (a >= 0) letters[a] else '_')
                if (a >= 0 && a < letters.length && letters[a] == p.expected[i]) ok++
            }
            assertEquals("${p.file}: lido=$got esperado=${p.expected}", p.expected.length, ok)
        }
    }
}
