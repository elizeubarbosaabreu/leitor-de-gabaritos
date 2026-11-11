package com.elizeu.gabarito

import android.graphics.Bitmap
import android.graphics.Color

/**
 * Motor de leitura OMR (leitura óptica de gabaritos).
 * Portado e validado do protótipo em Python com fotos reais de gabarito
 * (close-up e página inteira) e folhas sintéticas com rotação, perspectiva
 * e ruído.
 *
 * Algoritmo:
 *  - Limiar adaptativo Bradley (s=45, t=0.12) sem desfoque/dilatação.
 *  - Componentes conexos com aspect ratio <= 2.0.
 *  - Grade: busca em camadas de limiar; banda de tamanho pela mediana dos
 *    componentes; colunas = clusters de cx; linhas = clusters de cy com
 *    cobertura de colunas; fusão de linhas fragmentadas por pitch.
 *  - Preenchimento: área do maior componente dentro de hw=0.45*med do
 *    centro da célula; preenchida se área > 1.7 * mediana das células.
 */
object OmrEngine {

    data class RowFilled(val cy: Float, val fills: FloatArray)

    data class SheetRead(
        val rows: List<RowFilled>,
        val cols: FloatArray,
        val nx: Int,
        val covered: Float,
        val threshold: Float
    ) {
        /** Resposta por linha: índice da coluna mais preenchida, ou -1. */
        fun answerOf(row: Int, t: Float = threshold): Int {
            if (row < 0 || row >= rows.size) return -1
            val fills = rows[row].fills
            var best = -1
            var bestF = 0f
            for (i in fills.indices) {
                if (fills[i] > bestF) {
                    bestF = fills[i]
                    best = i
                }
            }
            return if (bestF < t) -1 else best
        }
    }

    /** Converte Bitmap em escala de cinza (intensidade 0..255). */
    fun toGray(bmp: Bitmap): IntArray {
        val w = bmp.width
        val h = bmp.height
        val out = IntArray(w * h)
        val px = IntArray(w * h)
        bmp.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) {
            val c = px[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            out[i] = (0.299f * r + 0.587f * g + 0.114f * b + 0.5f).toInt()
        }
        return out
    }

    /** Limiar adaptativo (Bradley) - retorna mask de "tinta" (true = escuro). */
    fun bradleyThreshold(gray: IntArray, w: Int, h: Int, s: Int = 45, t: Double = 0.12): BooleanArray {
        val sw = w + 1
        val ii = LongArray(sw * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val rowOff = y * w
            for (x in 0 until w) {
                rowSum += gray[rowOff + x]
                ii[(y + 1) * sw + (x + 1)] = ii[y * sw + (x + 1)] + rowSum
            }
        }
        val ink = BooleanArray(w * h)
        val half = s / 2
        for (y in 0 until h) {
            val y0 = (y - half).coerceAtLeast(0)
            val y1 = (y + half + 1).coerceAtMost(h)
            for (x in 0 until w) {
                val x0 = (x - half).coerceAtLeast(0)
                val x1 = (x + half + 1).coerceAtMost(w)
                val area = (y1 - y0) * (x1 - x0)
                val sum = ii[y1 * sw + x1] - ii[y0 * sw + x1] - ii[y1 * sw + x0] + ii[y0 * sw + x0]
                val mean = sum.toDouble() / area
                ink[y * w + x] = gray[y * w + x] < mean * (1 - t)
            }
        }
        return ink
    }

    /** Rótulos de componentes conexos (8-vizinhança). Retorna (labels, contagem). */
    fun connectedComponents(ink: BooleanArray, w: Int, h: Int): Pair<IntArray, Int> {
        val lab = IntArray(w * h)
        var parent = IntArray(32)
        parent[0] = 0
        var nlab = 0

        fun find(a: Int): Int {
            var x = a
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            val ra = find(a)
            val rb = find(b)
            if (ra != rb) parent[ra] = rb
        }

        fun ensureParent(maxIndex: Int) {
            if (maxIndex >= parent.size) {
                var cap = parent.size
                while (cap <= maxIndex) cap *= 2
                val np = IntArray(cap)
                System.arraycopy(parent, 0, np, 0, parent.size)
                for (i in parent.size until cap) np[i] = i
                parent = np
            }
        }

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (!ink[y * w + x]) continue
                val up = if (y > 0) lab[(y - 1) * w + x] else 0
                val left = if (x > 0) lab[y * w + x - 1] else 0
                val ul = if (y > 0 && x > 0) lab[(y - 1) * w + x - 1] else 0
                val ur = if (y > 0 && x + 1 < w) lab[(y - 1) * w + x + 1] else 0
                var mn = 0
                for (v in intArrayOf(up, left, ul, ur)) {
                    if (v > 0 && (mn == 0 || v < mn)) mn = v
                }
                if (mn > 0) {
                    lab[y * w + x] = mn
                    for (v in intArrayOf(up, left, ul, ur)) {
                        if (v > 0 && v != mn) union(v, mn)
                    }
                } else {
                    nlab++
                    ensureParent(nlab)
                    parent[nlab] = nlab
                    lab[y * w + x] = nlab
                }
            }
        }

        val remap = HashMap<Int, Int>()
        val out = IntArray(w * h)
        var count = 0
        for (i in lab.indices) {
            val v = lab[i]
            if (v == 0) continue
            val r = find(v)
            val m = remap.getOrPut(r) { ++count }
            out[i] = m
        }
        return Pair(out, count)
    }

    /** Componente com perímetro (usado p/ classificar formas dos marcadores). */
    data class Comp(
        val cx: Float,
        val cy: Float,
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val area: Int,
        val perimetro: Int
    )

    /**
     * Como [componentStats], mas SEM filtro de aspecto e com o perímetro
     * (contagem de pixels de tinta com vizinho de fundo). Serve à detecção
     * dos marcadores fiduciais, que precisam distinguir quadrado/círculo/triângulo.
     */
    fun componentStatsFull(lab: IntArray, nc: Int, w: Int, h: Int, minArea: Int = 4): List<Comp> {
        val xs = IntArray(nc + 1) { w }
        val xe = IntArray(nc + 1) { -1 }
        val ys = IntArray(nc + 1) { h }
        val ye = IntArray(nc + 1) { -1 }
        val area = IntArray(nc + 1)
        val per = IntArray(nc + 1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = lab[y * w + x]
                if (v == 0) continue
                area[v]++
                if (x < xs[v]) xs[v] = x
                if (x > xe[v]) xe[v] = x
                if (y < ys[v]) ys[v] = y
                if (y > ye[v]) ye[v] = y
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    per[v]++
                } else if (lab[y * w + x - 1] == 0 || lab[y * w + x + 1] == 0 ||
                    lab[(y - 1) * w + x] == 0 || lab[(y + 1) * w + x] == 0
                ) {
                    per[v]++
                }
            }
        }
        val list = ArrayList<Comp>()
        for (c in 1..nc) {
            if (area[c] < minArea) continue
            val ww = xe[c] - xs[c] + 1
            val hh = ye[c] - ys[c] + 1
            list.add(
                Comp(
                    cx = (xs[c] + xe[c]) / 2f,
                    cy = (ys[c] + ye[c]) / 2f,
                    x = xs[c], y = ys[c], w = ww, h = hh,
                    area = area[c], perimetro = per[c]
                )
            )
        }
        return list
    }

    /**
     * Leitura pelo formato NOVO (marcadores de canto): normaliza a foto por
     * homografia e amostra as bolhas pela geometria canônica (GeometriaFolha).
     * Robusto a rotação e perspectiva; as posições de retorno vêm convertidas
     * para o espaço da imagem original (o Corretor usa cy/cols para o OCR dos
     * números das questões).
     *
     * @param quad cantos identificados por [MarcadorFiducial]
     * @param linhas nº de questões (se <= 0, estimado pela altura do quadro)
     */
    fun readSheetComMarcadores(
        gray: IntArray,
        w: Int,
        h: Int,
        quad: MarcadorFiducial.Quadro,
        nx: Int = 5,
        linhas: Int = -1
    ): SheetRead {
        val nxC = nx.coerceIn(2, 6)
        val wmm = GeometriaFolha.larguraMm(nxC)
        // Escala px/mm: a distância TL↔TR corresponde a (Wmm - 2*offsetMarca) mm.
        val refMm = wmm - 2f * GeometriaFolha.MM_OFFSET_MARCA
        val escala = dist(quad.tl.cx, quad.tl.cy, quad.tr.cx, quad.tr.cy) / refMm
        if (escala < 0.5f) return emptyRead(nxC)
        val n = if (linhas > 0) linhas
        else GeometriaFolha.estimarLinhas(dist(quad.tl.cx, quad.tl.cy, quad.bl.cx, quad.bl.cy), escala)
        if (n < 1) return emptyRead(nxC)
        val hmm = GeometriaFolha.alturaMm(n)
        val outW = Math.round(wmm * escala).coerceAtLeast(50)
        val outH = Math.round(hmm * escala).coerceAtLeast(50)

        val src = arrayOf(
            Pair(quad.tl.cx, quad.tl.cy), Pair(quad.tr.cx, quad.tr.cy),
            Pair(quad.bl.cx, quad.bl.cy), Pair(quad.br.cx, quad.br.cy)
        )
        val dst = arrayOf(
            Pair(GeometriaFolha.MM_OFFSET_MARCA * escala, GeometriaFolha.MM_OFFSET_MARCA * escala),
            Pair((wmm - GeometriaFolha.MM_OFFSET_MARCA) * escala, GeometriaFolha.MM_OFFSET_MARCA * escala),
            Pair(GeometriaFolha.MM_OFFSET_MARCA * escala, (hmm - GeometriaFolha.MM_OFFSET_MARCA) * escala),
            Pair((wmm - GeometriaFolha.MM_OFFSET_MARCA) * escala, (hmm - GeometriaFolha.MM_OFFSET_MARCA) * escala)
        )
        val hm = Homografia.computar(src, dst) ?: return emptyRead(nxC)
        val warped = Homografia.warp(gray, w, h, hm, outW, outH)

        val rAmo = GeometriaFolha.MM_RAIO_BOLHA * escala * 0.70f
        val fills = Array(n) { FloatArray(nxC) }
        for (i in 0 until n) {
            val cy = GeometriaFolha.cyLinha(i) * escala
            for (j in 0 until nxC) {
                val cx = GeometriaFolha.cxColuna(j) * escala
                fills[i][j] = tintaNoDisco(warped, outW, outH, cx, cy, rAmo)
            }
        }

        val todas = fills.flatMap { it.toList() }.sorted()
        val med = todas[todas.size / 2]
        val threshold = (1.7f * med).coerceIn(0.35f, 0.55f)

        val rowsOut = ArrayList<RowFilled>(n)
        for (i in 0 until n) {
            val cyCanon = GeometriaFolha.cyLinha(i) * escala
            val p = Homografia.mapear(hm, GeometriaFolha.cxColuna(0) * escala, cyCanon)
            rowsOut.add(RowFilled(p.second, fills[i]))
        }
        val cols = FloatArray(nxC) { j ->
            val p = Homografia.mapear(hm, GeometriaFolha.cxColuna(j) * escala, 16f * escala)
            p.first
        }
        return SheetRead(rowsOut, cols, nxC, 1f, threshold)
    }

    /** Fração de pixels escuros dentro de um disco (interior da bolha). */
    private fun tintaNoDisco(g: IntArray, w: Int, h: Int, cx: Float, cy: Float, r: Float): Float {
        if (r < 1f) return 0f
        val x0 = (cx - r).toInt().coerceAtLeast(0)
        val x1 = (cx + r).toInt().coerceAtMost(w - 1)
        val y0 = (cy - r).toInt().coerceAtLeast(0)
        val y1 = (cy + r).toInt().coerceAtMost(h - 1)
        var total = 0
        var ink = 0
        val r2 = r * r
        for (y in y0..y1) {
            for (x in x0..x1) {
                val dx = x + 0.5f - cx
                val dy = y + 0.5f - cy
                if (dx * dx + dy * dy <= r2) {
                    total++
                    if (g[y * w + x] < 120) ink++
                }
            }
        }
        return if (total == 0) 0f else ink.toFloat() / total
    }

    private fun dist(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun emptyRead(nx: Int): SheetRead =
        SheetRead(emptyList(), FloatArray(0), nx, 0f, 0f)
}
