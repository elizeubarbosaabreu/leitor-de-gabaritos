package com.elizeu.gabarito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Valida o motor OMR (porta Kotlin) com folhas sintéticas desenhadas
 * programaticamente: casos limpos (EM 5 colunas e EF 4 colunas) e caso
 * com rotação + ruído. O algoritmo foi calibrado também com fotos reais.
 */
class OmrEngineTest {

    private data class Sheet(val gray: IntArray, val w: Int, val h: Int)

    /** Desenha a folha como array de cinza. */
    private fun makeSheet(n: Int, truth: IntArray, cols: Int = 5, seed: Long = 7, fillAnswers: Boolean = true): Sheet {
        val spacing = 62
        val r = 24
        val ring = 5
        val x0 = 420
        val y0 = 240
        val W = x0 + (cols - 1) * 120 + 2 * r + 160
        val H = y0 + n * spacing + 160
        val gray = IntArray(W * H) { 245 }
        val rng = Random(seed)

        for (row in 0 until n) {
            drawCircle(gray, W, H, x0 - 150 + rng.nextInt(6), y0 + row * spacing, 9, false, 4, 60)
        }

        for (row in 0 until n) {
            for (col in 0 until cols) {
                drawCircle(gray, W, H, x0 + col * 120, y0 + row * spacing, r, filled = false, ring = ring, value = 30)
            }
            if (fillAnswers) {
                drawCircle(gray, W, H, x0 + truth[row] * 120, y0 + row * spacing, r, filled = true, ring = ring, value = 25)
            }
        }
        return Sheet(gray, W, H)
    }

    private fun drawCircle(
        gray: IntArray, W: Int, H: Int,
        cx: Int, cy: Int, r: Int, filled: Boolean, ring: Int, value: Int
    ) {
        val innerR = r - ring
        for (y in (cy - r) until (cy + r)) {
            if (y < 0 || y >= H) continue
            for (x in (cx - r) until (cx + r)) {
                if (x < 0 || x >= W) continue
                val d2 = (x - cx) * (x - cx) + (y - cy) * (y - cy)
                val rr = r * r
                val ir = innerR * innerR
                val inside = if (filled) d2 <= rr else d2 <= rr && d2 >= ir
                if (inside) gray[y * W + x] = value
            }
        }
    }

    private fun rotate(sheet: Sheet, degrees: Double): Sheet {
        val a = Math.toRadians(degrees)
        val ca = cos(a)
        val sa = sin(a)
        val cx = sheet.w / 2.0
        val cy = sheet.h / 2.0
        val out = IntArray(sheet.gray.size) { 245 }
        for (y in 0 until sheet.h) {
            for (x in 0 until sheet.w) {
                val dx = x - cx
                val dy = y - cy
                val sx = (dx * ca + dy * sa + cx).toInt()
                val sy = (-dx * sa + dy * ca + cy).toInt()
                if (sx in 0 until sheet.w && sy in 0 until sheet.h) {
                    out[y * sheet.w + x] = sheet.gray[sy * sheet.w + sx]
                }
            }
        }
        return Sheet(out, sheet.w, sheet.h)
    }

    private fun addNoise(sheet: Sheet, amount: Int, seed: Long): Sheet {
        val rng = Random(seed)
        val out = IntArray(sheet.gray.size) { i ->
            (sheet.gray[i] + rng.nextInt(2 * amount + 1) - amount).coerceIn(0, 255)
        }
        return Sheet(out, sheet.w, sheet.h)
    }

    @Test
    fun readsCleanSheetEM() {
        val n = 20
        val truth = IntArray(n) { Random(it.toLong()).nextInt(5) }
        val sheet = makeSheet(n, truth, cols = 5)
        val read = OmrEngine.readSheetGray(sheet.gray, sheet.w, sheet.h, nx = 5)
        assertEquals(n, read.rows.size)
        for (i in 0 until n) {
            assertEquals("questão ${i + 1}", truth[i], read.answerOf(i))
        }
    }

    @Test
    fun readsCleanSheetEF() {
        val n = 10
        val truth = IntArray(n) { Random(it.toLong()).nextInt(4) }
        val sheet = makeSheet(n, truth, cols = 4)
        val read = OmrEngine.readSheetGray(sheet.gray, sheet.w, sheet.h, nx = 4)
        assertEquals(n, read.rows.size)
        for (i in 0 until n) {
            assertEquals("questão ${i + 1}", truth[i], read.answerOf(i))
        }
    }

    @Test
    fun readsRotatedNoisySheet() {
        val n = 20
        val truth = IntArray(n) { Random(it.toLong() * 3).nextInt(5) }
        val sheet = makeSheet(n, truth, cols = 5)
        val noisy = addNoise(rotate(sheet, 2.0), 12, 42L)
        val read = OmrEngine.readSheetGray(noisy.gray, noisy.w, noisy.h, nx = 5)
        assertTrue("linhas detectadas = ${read.rows.size}", read.rows.size >= n)
        var ok = 0
        for (i in 0 until minOf(n, read.rows.size)) {
            if (read.answerOf(i) == truth[i]) ok++
        }
        assertTrue("acertos $ok/$n", ok >= n - 2)
    }

    @Test
    fun blankSheetYieldsNoAnswers() {
        val n = 10
        val sheet = makeSheet(n, IntArray(n) { 0 }, cols = 4, fillAnswers = false)
        val read = OmrEngine.readSheetGray(sheet.gray, sheet.w, sheet.h, nx = 4)
        assertTrue("linhas detectadas = ${read.rows.size}", read.rows.isNotEmpty())
        for (i in 0 until n) {
            assertEquals("questão ${i + 1} deve ficar em branco", -1, read.answerOf(i))
        }
    }
}
