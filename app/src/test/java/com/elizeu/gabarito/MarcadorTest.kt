package com.elizeu.gabarito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin

/**
 * Valida o pipeline novo (marcadores de canto + homografia): desenha a folha
 * sintética com a geometria canônica de GeometriaFolha — igual à que o
 * gerador de provas imprime — e confere a leitura nos casos limpo, com
 * rotação, com perspectiva, com muitas questões e em branco.
 */
class MarcadorTest {

    private data class Folha(val gray: IntArray, val w: Int, val h: Int)

    /** px por milímetro do desenho sintético. */
    private val pxmm = 8

    private fun desenhaFolha(
        n: Int,
        truth: IntArray,
        cols: Int = 5,
        rotacao: Double = 0.0,
        perspectiva: Float = 0f,
        preencher: Boolean = true
    ): Folha {
        val g = GeometriaFolha
        val wmm = g.larguraMm(cols)
        val hmm = g.alturaMm(n)
        val W = 210 * pxmm
        val H = 297 * pxmm
        val gray = IntArray(W * H) { 245 }
        val x0 = (W - (wmm * pxmm).toInt()) / 2
        val y0 = (H - (hmm * pxmm).toInt()) / 2

        fun X(mm: Float) = x0 + mm * pxmm
        fun Y(mm: Float) = y0 + mm * pxmm
        fun offX(yPx: Float) = perspectiva * (yPx - y0)

        val rBolha = g.MM_RAIO_BOLHA * pxmm
        val espAnel = 0.5f * pxmm

        // Zebra clara nas linhas ímpares (não encosta nos marcadores).
        for (i in 0 until n) {
            if (i % 2 == 1) {
                val yTop = Y(g.cyLinha(i) - 4.5f).toInt()
                val yBot = Y(g.cyLinha(i) + 4.5f).toInt()
                for (y in yTop until yBot) {
                    for (x in x0 until x0 + (wmm * pxmm).toInt()) {
                        if (x in 0 until W && y in 0 until H) gray[y * W + x] = 242
                    }
                }
            }
        }

        // 4 marcadores: TL quadrado, TR círculo, BL triângulo, BR quadrado.
        // (sofrem a mesma perspectiva que as linhas: a foto real deforma o
        // quadro inteiro, e a homografia ancora justamente nesses 4 pontos)
        val meioMarca = g.MM_MARCA * pxmm / 2f
        val off = g.MM_OFFSET_MARCA
        quadrado(gray, W, H, X(off) + offX(Y(off)), Y(off), meioMarca)
        circulo(gray, W, H, X(wmm - off) + offX(Y(off)), Y(off), meioMarca)
        triangulo(gray, W, H, X(off) + offX(Y(hmm - off)), Y(hmm - off), meioMarca)
        quadrado(gray, W, H, X(wmm - off) + offX(Y(hmm - off)), Y(hmm - off), meioMarca)

        // Linhas: caixa do número + bolhas.
        for (i in 0 until n) {
            val cy = Y(g.cyLinha(i))
            contorno(gray, W, H, X(g.MM_NUM_CX) + offX(cy), cy, g.MM_NUM_W * pxmm / 2, g.MM_NUM_H * pxmm / 2, espAnel, 60)
            for (j in 0 until cols) {
                val cx = X(g.cxColuna(j)) + offX(cy)
                anel(gray, W, H, cx, cy, rBolha, espAnel, 30)
                if (preencher && truth[i] == j) {
                    disco(gray, W, H, cx, cy, rBolha - espAnel, 25)
                }
            }
        }

        val folha = Folha(gray, W, H)
        return if (rotacao != 0.0) rotacionar(folha, rotacao) else folha
    }

    /* ---------- primitivas de desenho ---------- */

    private fun disco(g: IntArray, W: Int, H: Int, cx: Float, cy: Float, r: Float, valor: Int) {
        val r2 = r * r
        for (y in (cy - r).toInt() until (cy + r).toInt()) {
            if (y < 0 || y >= H) continue
            for (x in (cx - r).toInt() until (cx + r).toInt()) {
                if (x < 0 || x >= W) continue
                val dx = x + 0.5f - cx
                val dy = y + 0.5f - cy
                if (dx * dx + dy * dy <= r2) g[y * W + x] = valor
            }
        }
    }

    private fun anel(
        g: IntArray, W: Int, H: Int, cx: Float, cy: Float,
        r: Float, esp: Float, valor: Int
    ) {
        val r2 = r * r
        val ri = (r - esp) * (r - esp)
        for (y in (cy - r).toInt() until (cy + r).toInt()) {
            if (y < 0 || y >= H) continue
            for (x in (cx - r).toInt() until (cx + r).toInt()) {
                if (x < 0 || x >= W) continue
                val dx = x + 0.5f - cx
                val dy = y + 0.5f - cy
                val d2 = dx * dx + dy * dy
                if (d2 <= r2 && d2 >= ri) g[y * W + x] = valor
            }
        }
    }

    /** Marcador círculo = disco cheio (mesmo raio do quadrado). */
    private fun circulo(g: IntArray, W: Int, H: Int, cx: Float, cy: Float, meio: Float) {
        disco(g, W, H, cx, cy, meio, 22)
    }

    private fun quadrado(g: IntArray, W: Int, H: Int, cx: Float, cy: Float, meio: Float) {
        for (y in (cy - meio).toInt() until (cy + meio).toInt()) {
            if (y < 0 || y >= H) continue
            for (x in (cx - meio).toInt() until (cx + meio).toInt()) {
                if (x < 0 || x >= W) continue
                g[y * W + x] = 22
            }
        }
    }

    private fun contorno(
        g: IntArray, W: Int, H: Int, cx: Float, cy: Float,
        meioW: Float, meioH: Float, esp: Float, valor: Int
    ) {
        for (y in (cy - meioH).toInt() until (cy + meioH).toInt()) {
            if (y < 0 || y >= H) continue
            for (x in (cx - meioW).toInt() until (cx + meioW).toInt()) {
                if (x < 0 || x >= W) continue
                if (x < cx - meioW + esp || x > cx + meioW - esp ||
                    y < cy - meioH + esp || y > cy + meioH - esp
                ) g[y * W + x] = valor
            }
        }
    }

    /** Triângulo equilátero apontando para baixo (base no topo). */
    private fun triangulo(g: IntArray, W: Int, H: Int, cx: Float, cy: Float, meio: Float) {
        val topo = cy - meio
        val altura = 2f * meio
        for (y in topo.toInt() until (topo + altura).toInt()) {
            if (y < 0 || y >= H) continue
            val f = (y + 0.5f - topo) / altura
            val half = meio * (1f - f)
            for (x in (cx - half).toInt() until (cx + half).toInt()) {
                if (x < 0 || x >= W) continue
                g[y * W + x] = 22
            }
        }
    }

    private fun rotacionar(f: Folha, graus: Double): Folha {
        val a = Math.toRadians(graus)
        val ca = cos(a)
        val sa = sin(a)
        val cx = f.w / 2.0
        val cy = f.h / 2.0
        val out = IntArray(f.gray.size) { 245 }
        for (y in 0 until f.h) {
            for (x in 0 until f.w) {
                val dx = x - cx
                val dy = y - cy
                val sx = (dx * ca + dy * sa + cx).toInt()
                val sy = (-dx * sa + dy * ca + cy).toInt()
                if (sx in 0 until f.w && sy in 0 until f.h) {
                    out[y * f.w + x] = f.gray[sy * f.w + sx]
                }
            }
        }
        return Folha(out, f.w, f.h)
    }

    /* ---------- testes ---------- */


    @Test
    fun leNovoFormatoEM() {
        val n = 10
        val truth = IntArray(n) { Random(it.toLong()).nextInt(5) }
        val folha = desenhaFolha(n, truth, cols = 5)
        val quad = MarcadorFiducial.detectar(folha.gray, folha.w, folha.h)
            ?: return fail("marcadores não detectados")
        val read = OmrEngine.readSheetComMarcadores(folha.gray, folha.w, folha.h, quad, nx = 5)
        assertEquals("linhas", n, read.rows.size)
        for (i in 0 until n) {
            assertEquals("questão ${i + 1}", truth[i], read.answerOf(i))
        }
    }

    @Test
    fun leNovoFormatoEF() {
        val n = 10
        val truth = IntArray(n) { Random(it.toLong()).nextInt(4) }
        val folha = desenhaFolha(n, truth, cols = 4)
        val quad = MarcadorFiducial.detectar(folha.gray, folha.w, folha.h)
            ?: return fail("marcadores não detectados")
        val read = OmrEngine.readSheetComMarcadores(folha.gray, folha.w, folha.h, quad, nx = 4)
        assertEquals("linhas", n, read.rows.size)
        for (i in 0 until n) {
            assertEquals("questão ${i + 1}", truth[i], read.answerOf(i))
        }
    }

    @Test
    fun leVinteQuestoesMuitoPrenchidas() {
        val n = 20
        val rng = Random(99)
        // Todas as linhas preenchidas de propósito: as bolhas marcadas não
        // podem confundir os marcadores de canto.
        val truth = IntArray(n) { rng.nextInt(5) }
        val folha = desenhaFolha(n, truth, cols = 5)
        val quad = MarcadorFiducial.detectar(folha.gray, folha.w, folha.h)
            ?: return fail("marcadores não detectados com folha cheia")
        val read = OmrEngine.readSheetComMarcadores(folha.gray, folha.w, folha.h, quad, nx = 5)
        assertEquals("linhas", n, read.rows.size)
        var ok = 0
        for (i in 0 until n) if (read.answerOf(i) == truth[i]) ok++
        assertEquals("acertos $ok/$n", n, ok)
    }

    @Test
    fun leComRotacao3Graus() {
        val n = 10
        val truth = IntArray(n) { Random(it.toLong() * 3).nextInt(5) }
        val folha = desenhaFolha(n, truth, cols = 5, rotacao = 3.0)
        val quad = MarcadorFiducial.detectar(folha.gray, folha.w, folha.h)
            ?: return fail("marcadores não detectados após rotação")
        val read = OmrEngine.readSheetComMarcadores(folha.gray, folha.w, folha.h, quad, nx = 5)
        assertEquals("linhas", n, read.rows.size)
        var ok = 0
        for (i in 0 until n) if (read.answerOf(i) == truth[i]) ok++
        assertTrue("acertos $ok/$n", ok == n)
    }

    @Test
    fun leComPerspectiva() {
        val n = 10
        val truth = IntArray(n) { Random(it.toLong() * 7).nextInt(5) }
        val folha = desenhaFolha(n, truth, cols = 5, perspectiva = 0.05f)
        val quad = MarcadorFiducial.detectar(folha.gray, folha.w, folha.h)
            ?: return fail("marcadores não detectados com perspectiva")
        val read = OmrEngine.readSheetComMarcadores(folha.gray, folha.w, folha.h, quad, nx = 5)
        assertEquals("linhas", n, read.rows.size)
        var ok = 0
        for (i in 0 until n) if (read.answerOf(i) == truth[i]) ok++
        assertTrue("acertos $ok/$n", ok == n)
    }

    @Test
    fun folhaBrancaNaoGeraRespostas() {
        val n = 10
        val folha = desenhaFolha(n, IntArray(n), cols = 5, preencher = false)
        val quad = MarcadorFiducial.detectar(folha.gray, folha.w, folha.h)
        assertTrue("marcadores detectados", quad != null)
        val read = OmrEngine.readSheetComMarcadores(folha.gray, folha.w, folha.h, quad!!, nx = 5)
        assertEquals("linhas", n, read.rows.size)
        for (i in 0 until n) {
            assertEquals("questão ${i + 1} deve ficar em branco", -1, read.answerOf(i))
        }
    }

    @Test
    fun detectaCantosPelaForma() {
        val n = 10
        val truth = IntArray(n) { Random(it.toLong()).nextInt(5) }
        val folha = desenhaFolha(n, truth, cols = 5)
        val quad = MarcadorFiducial.detectar(folha.gray, folha.w, folha.h)
            ?: return fail("marcadores não detectados")
        assertEquals(MarcadorFiducial.Forma.QUADRADO, quad.tl.forma)
        assertEquals(MarcadorFiducial.Forma.CIRCULO, quad.tr.forma)
        assertEquals(MarcadorFiducial.Forma.TRIANGULO, quad.bl.forma)
        assertEquals(MarcadorFiducial.Forma.QUADRADO, quad.br.forma)
        assertTrue("TL acima do BL", quad.tl.cy < quad.bl.cy)
    }

    private fun fail(msg: String): Nothing {
        throw AssertionError(msg)
    }
}
