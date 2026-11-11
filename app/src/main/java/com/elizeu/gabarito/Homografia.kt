package com.elizeu.gabarito

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Homografia 3x3 (mapeamento projetivo entre dois planos) e warp bilinear.
 *
 * Usada para normalizar a foto de um gabarito tirada em ângulo: os 4
 * marcadores de canto detectados na imagem definem um quadrilátero de
 * origem, que é levado ao retângulo canônico da folha (GeometriaFolha).
 * Implementação em Kotlin puro (sem OpenCV) — DLT com normalização de
 * Hartley + eliminação de Gauss com pivotamento parcial.
 */
object Homografia {

    /** Matriz 3x3 em row-major (9 floats), normalizada para h[8] = 1. */
    private class Mat3(val m: DoubleArray = DoubleArray(9)) {
        operator fun get(r: Int, c: Int): Double = m[r * 3 + c]
        operator fun set(r: Int, c: Int, v: Double) {
            m[r * 3 + c] = v
        }
    }

    /**
     * Resolve H tal que dst ≈ H · src. Recebe 4+ correspondências.
     * Retorna null se o sistema for mal condicionado.
     */
    fun computar(src: Array<Pair<Float, Float>>, dst: Array<Pair<Float, Float>>): FloatArray? {
        if (src.size < 4 || src.size != dst.size) return null
        val n = minOf(src.size, dst.size)

        // Normalização: centróide na origem + escala média 1 (Hartley).
        val (ns, ts) = normalizar(src, n)
        val (nd, td) = normalizar(dst, n)

        // Sistema 2n x 8 com h33 = 1 (incógnitas explícitas).
        val A = Array(2 * n) { DoubleArray(8) }
        val b = DoubleArray(2 * n)
        for (i in 0 until n) {
            val x = ns[i].first.toDouble()
            val y = ns[i].second.toDouble()
            val u = nd[i].first.toDouble()
            val v = nd[i].second.toDouble()
            A[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y)
            b[2 * i] = u
            A[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y)
            b[2 * i + 1] = v
        }

        val h = gauss(A, b) ?: return null
        // H_normalizada = [h11 h12 h13; h21 h22 h23; h31 h32 1]
        val hn = Mat3()
        hn[0, 0] = h[0]; hn[0, 1] = h[1]; hn[0, 2] = h[2]
        hn[1, 0] = h[3]; hn[1, 1] = h[4]; hn[1, 2] = h[5]
        hn[2, 0] = h[6]; hn[2, 1] = h[7]; hn[2, 2] = 1.0

        // Desnormaliza: H = Tdst^-1 · Hn · Tsrc
        val invTd = inverterMat3(td) ?: return null
        val hFull = mul(mul(invTd, hn), ts)
        val out = FloatArray(9)
        if (abs(hFull[2, 2]) < 1e-12) return null
        for (i in 0 until 9) out[i] = (hFull.m[i] / hFull[2, 2]).toFloat()
        return out
    }

    /** Mapeia um ponto destino → fonte usando a inversa de H. */
    fun mapear(h: FloatArray, x: Float, y: Float): Pair<Float, Float> {
        val inv = inverter(h) ?: return Pair(x, y)
        val den = inv[6] * x + inv[7] * y + inv[8]
        if (abs(den) < 1e-6f) return Pair(x, y)
        return Pair(
            (inv[0] * x + inv[1] * y + inv[2]) / den,
            (inv[3] * x + inv[4] * y + inv[5]) / den
        )
    }

    /**
     * Aplica o warp (mapeamento destino → fonte, bilinear).
     * Retorna a imagem destino em escala de cinza (0..255).
     */
    fun warp(gray: IntArray, w: Int, h: Int, hh: FloatArray, outW: Int, outH: Int): IntArray {
        val out = IntArray(outW * outH) { 255 }
        val inv = inverter(hh) ?: return out
        val h0 = inv[0]; val h1 = inv[1]; val h2 = inv[2]
        val h3 = inv[3]; val h4 = inv[4]; val h5 = inv[5]
        val h6 = inv[6]; val h7 = inv[7]; val h8 = inv[8]
        for (yd in 0 until outH) {
            val rowBase = yd * outW
            for (xd in 0 until outW) {
                val den = h6 * xd + h7 * yd + h8
                if (abs(den) < 1e-6f) continue
                val xs = (h0 * xd + h1 * yd + h2) / den
                val ys = (h3 * xd + h4 * yd + h5) / den
                val x0 = xs.toInt()
                val y0 = ys.toInt()
                if (x0 < -1 || y0 < -1 || x0 >= w || y0 >= h) continue
                var value = 255
                if (x0 in 0 until w - 1 && y0 in 0 until h - 1) {
                    val fx = xs - x0
                    val fy = ys - y0
                    val p00 = gray[y0 * w + x0].toDouble()
                    val p01 = gray[y0 * w + x0 + 1].toDouble()
                    val p10 = gray[(y0 + 1) * w + x0].toDouble()
                    val p11 = gray[(y0 + 1) * w + x0 + 1].toDouble()
                    value = ((p00 * (1 - fx) + p01 * fx) * (1 - fy) +
                            (p10 * (1 - fx) + p11 * fx) * fy).toInt()
                } else if (x0 in 0 until w && y0 in 0 until h) {
                    value = gray[y0 * w + x0]
                }
                out[rowBase + xd] = value
            }
        }
        return out
    }

    /** Inverte matriz 3x3 (regra da adjunta). */
    private fun inverter(h: FloatArray): FloatArray? {
        val a = h[0]; val b = h[1]; val c = h[2]
        val d = h[3]; val e = h[4]; val f = h[5]
        val g = h[6]; val i = h[7]; val j = h[8]
        val A = e * j - f * i
        val B = -(d * j - f * g)
        val C = d * i - e * g
        val det = a * A + b * B + c * C
        if (abs(det) < 1e-12f) return null
        val D = -(b * j - c * i)
        val E = a * j - c * g
        val F = -(a * i - b * g)
        val G = b * f - c * e
        val H = -(a * f - c * d)
        val I = a * e - b * d
        val invDet = 1f / det
        return floatArrayOf(
            A * invDet, D * invDet, G * invDet,
            B * invDet, E * invDet, H * invDet,
            C * invDet, F * invDet, I * invDet
        )
    }

    /** Inverte matriz 3x3 em ponto flutuante (regra da adjunta). */
    private fun inverterMat3(m: Mat3): Mat3? {
        val a = m[0, 0]; val b = m[0, 1]; val c = m[0, 2]
        val d = m[1, 0]; val e = m[1, 1]; val f = m[1, 2]
        val g = m[2, 0]; val i = m[2, 1]; val j = m[2, 2]
        val A = e * j - f * i
        val B = -(d * j - f * g)
        val C = d * i - e * g
        val det = a * A + b * B + c * C
        if (abs(det) < 1e-12) return null
        val D = -(b * j - c * i)
        val E = a * j - c * g
        val F = -(a * i - b * g)
        val G = b * f - c * e
        val H = -(a * f - c * d)
        val I = a * e - b * d
        val r = Mat3()
        val invDet = 1.0 / det
        r[0, 0] = A * invDet; r[0, 1] = D * invDet; r[0, 2] = G * invDet
        r[1, 0] = B * invDet; r[1, 1] = E * invDet; r[1, 2] = H * invDet
        r[2, 0] = C * invDet; r[2, 1] = F * invDet; r[2, 2] = I * invDet
        return r
    }

    private fun mul(a: Mat3, b: Mat3): Mat3 {
        val r = Mat3()
        for (i in 0 until 3) {
            for (j in 0 until 3) {
                var s = 0.0
                for (k in 0 until 3) s += a[i, k] * b[k, j]
                r[i, j] = s
            }
        }
        return r
    }

    /** Matriz de normalização: centróide → origem, escala média → 1. */
    private fun normalizar(pts: Array<Pair<Float, Float>>, n: Int): Pair<Array<Pair<Float, Float>>, Mat3> {
        var cx = 0f
        var cy = 0f
        for (p in pts) { cx += p.first; cy += p.second }
        cx /= n
        cy /= n
        var meanDist = 0f
        for (p in pts) meanDist += sqrt((p.first - cx) * (p.first - cx) + (p.second - cy) * (p.second - cy))
        meanDist /= n
        val s = if (meanDist > 1e-6f) 1f / meanDist else 1f
        val out = Array(n) { i ->
            Pair((pts[i].first - cx) * s, (pts[i].second - cy) * s)
        }
        val t = Mat3()
        t[0, 0] = s.toDouble(); t[0, 1] = 0.0; t[0, 2] = (-s * cx).toDouble()
        t[1, 0] = 0.0; t[1, 1] = s.toDouble(); t[1, 2] = (-s * cy).toDouble()
        t[2, 0] = 0.0; t[2, 1] = 0.0; t[2, 2] = 1.0
        return Pair(out, t)
    }

    /** Eliminação de Gauss com pivotamento parcial; null se singular. */
    private fun gauss(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> A[i].copyOf() }
        val v = b.copyOf()
        for (col in 0 until n) {
            var piv = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[piv][col])) piv = r
            if (abs(m[piv][col]) < 1e-12) return null
            if (piv != col) {
                val tmp = m[piv]; m[piv] = m[col]; m[col] = tmp
                val tv = v[piv]; v[piv] = v[col]; v[col] = tv
            }
            for (r in col + 1 until n) {
                val f = m[r][col] / m[col][col]
                if (f == 0.0) continue
                for (c in col until n) m[r][c] -= f * m[col][c]
                v[r] -= f * v[col]
            }
        }
        val x = DoubleArray(n)
        for (i in n - 1 downTo 0) {
            var s = v[i]
            for (j in i + 1 until n) s -= m[i][j] * x[j]
            x[i] = s / m[i][i]
        }
        return x
    }
}
