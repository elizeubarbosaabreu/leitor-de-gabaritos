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

    data class Blob(
        val cx: Float,
        val cy: Float,
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val area: Int,
        val dim: Int
    )

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

    /** Estatísticas dos componentes (bbox, área, centro), filtrados por aspect <= 2. */
    private fun componentStats(lab: IntArray, nc: Int, w: Int, h: Int, minArea: Int = 4): List<Blob> {
        val xs = IntArray(nc + 1) { w }
        val xe = IntArray(nc + 1) { -1 }
        val ys = IntArray(nc + 1) { h }
        val ye = IntArray(nc + 1) { -1 }
        val area = IntArray(nc + 1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = lab[y * w + x]
                if (v == 0) continue
                area[v]++
                if (x < xs[v]) xs[v] = x
                if (x > xe[v]) xe[v] = x
                if (y < ys[v]) ys[v] = y
                if (y > ye[v]) ye[v] = y
            }
        }
        val list = ArrayList<Blob>()
        for (c in 1..nc) {
            if (area[c] < minArea) continue
            val ww = xe[c] - xs[c] + 1
            val hh = ye[c] - ys[c] + 1
            val dim = maxOf(ww, hh)
            val asp = dim.toFloat() / maxOf(1, minOf(ww, hh))
            if (asp > 2.0f) continue
            list.add(
                Blob(
                    cx = (xs[c] + xe[c]) / 2f,
                    cy = (ys[c] + ye[c]) / 2f,
                    x = xs[c], y = ys[c], w = ww, h = hh, area = area[c], dim = dim
                )
            )
        }
        return list
    }

    private class Cluster1D(val means: FloatArray, val counts: IntArray)

    private fun cluster1D(vals: FloatArray, tol: Float): Cluster1D {
        val sortedVals = vals.sorted()
        val means = ArrayList<Float>()
        val counts = ArrayList<Int>()
        for (v in sortedVals) {
            if (means.isNotEmpty() && Math.abs(v - means.last()) <= tol) {
                val n = counts[counts.size - 1] + 1
                counts[counts.size - 1] = n
                means[means.size - 1] = means.last() + (v - means.last()) / n
            } else {
                means.add(v)
                counts.add(1)
            }
        }
        return Cluster1D(means.toFloatArray(), counts.toIntArray())
    }

    /** Lê as respostas de um bitmap (nx = número de alternativas: EF=4, EM=5). */
    fun readSheet(bmp: Bitmap, nx: Int = 5): SheetRead {
        return readSheetGray(toGray(bmp), bmp.width, bmp.height, nx)
    }

    /** Versão pura (JVM-testável) que opera em array de cinza. */
    fun readSheetGray(gray: IntArray, w: Int, h: Int, nx: Int = 5): SheetRead {
        val ink = bradleyThreshold(gray, w, h)
        val (lab, nc) = connectedComponents(ink, w, h)
        val comps = componentStats(lab, nc, w, h)
        if (comps.isEmpty()) return emptyRead(nx)

        val base = minOf(w, h).toFloat()
        val nxC = nx.coerceIn(2, 5)
        val factors = floatArrayOf(0.035f, 0.024f, 0.016f, 0.010f, 0.007f)

        var bestCols = FloatArray(0)
        var bestRows = FloatArray(0)
        var bestMed = 0f
        var bestCov = 0f

        for (factor in factors) {
            val thr = factor * base
            val big = ArrayList<Blob>(comps.size)
            for (c in comps) {
                if (c.dim >= thr && c.dim <= 0.5f * base) big.add(c)
            }
            if (big.size < 15) continue
            val dims = IntArray(big.size) { big[it].dim }
            dims.sort()
            val med = dims[dims.size / 2].toFloat()
            val band = ArrayList<Blob>(big.size)
            for (c in big) {
                if (c.dim >= 0.55f * med && c.dim <= 1.8f * med) band.add(c)
            }
            if (band.size < 20) continue

            val xg = cluster1D(FloatArray(band.size) { band[it].cx }, 0.5f * med)
            val xOrder = xg.means.indices.sortedByDescending { xg.counts[it] }
            if (xOrder.size < nxC) continue
            val cols = FloatArray(nxC) { xg.means[xOrder[it]] }
            cols.sort()

            val yg = cluster1D(FloatArray(band.size) { band[it].cy }, 0.4f * med)
            val mincov = maxOf(2, nxC - 1)
            val cands = ArrayList<Float>()
            for (k in yg.means.indices) {
                val ry = yg.means[k]
                var cov = 0
                for (rx in cols) {
                    var hit = false
                    for (c in band) {
                        if (Math.abs(c.cx - rx) <= 0.45f * med && Math.abs(c.cy - ry) <= 0.45f * med) {
                            hit = true
                            break
                        }
                    }
                    if (hit) cov++
                }
                if (cov >= mincov) cands.add(ry)
            }
            if (cands.size < 3) continue
            cands.sort()

            var pitch = med
            if (cands.size > 1) {
                val diffs = FloatArray(cands.size - 1) { cands[it + 1] - cands[it] }
                diffs.sort()
                pitch = diffs[diffs.size / 2]
                if (pitch < 0.5f * med) pitch = med
            }
            val rows = ArrayList<Float>()
            for (ry in cands) {
                if (rows.isNotEmpty() && ry - rows[rows.size - 1] < 0.4f * pitch) continue
                rows.add(ry)
            }
            if (rows.size < 3) continue

            var cells = 0
            for (ry in rows) {
                for (rx in cols) {
                    var hit = false
                    for (c in band) {
                        if (Math.abs(c.cx - rx) <= 0.45f * med && Math.abs(c.cy - ry) <= 0.45f * med) {
                            hit = true
                            break
                        }
                    }
                    if (hit) cells++
                }
            }
            val cov = cells.toFloat() / (rows.size * nxC)
            if (cov >= 0.8f) {
                bestCols = cols
                bestRows = rows.toFloatArray()
                bestMed = med
                bestCov = cov
                break
            }
        }

        if (bestRows.isEmpty()) return emptyRead(nxC)

        // Preenchimento: maior área de componente dentro de hw do centro da célula
        val hw = 0.45f * bestMed
        val nRow = bestRows.size
        val mat = FloatArray(nRow * nxC)
        for (c in comps) {
            for (ri in 0 until nRow) {
                if (Math.abs(c.cy - bestRows[ri]) > hw) continue
                for (ci in 0 until nxC) {
                    if (Math.abs(c.cx - bestCols[ci]) <= hw && c.area > mat[ri * nxC + ci]) {
                        mat[ri * nxC + ci] = c.area.toFloat()
                    }
                }
            }
        }

        val sortedMat = mat.copyOf()
        sortedMat.sort()
        val medCell = sortedMat[sortedMat.size / 2]
        val thrFill = 1.7f * medCell
        var maxMat = 0f
        for (v in mat) if (v > maxMat) maxMat = v
        if (maxMat <= 0f) maxMat = 1f
        val normThreshold = thrFill / maxMat

        val rowsOut = ArrayList<RowFilled>(nRow)
        for (ri in 0 until nRow) {
            val fills = FloatArray(nxC)
            for (ci in 0 until nxC) {
                fills[ci] = mat[ri * nxC + ci] / maxMat
            }
            rowsOut.add(RowFilled(bestRows[ri], fills))
        }
        return SheetRead(rowsOut, bestCols, nxC, bestCov, normThreshold)
    }

    private fun emptyRead(nx: Int): SheetRead =
        SheetRead(emptyList(), FloatArray(0), nx, 0f, 0f)
}
