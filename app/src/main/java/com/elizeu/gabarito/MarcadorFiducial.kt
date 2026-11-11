package com.elizeu.gabarito

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Detecção das 4 marcas de referência (marcadores fiduciais) impressas nos
 * cantos da folha de gabarito — o mesmo layout do gerador de provas:
 *
 *   TL = QUADRADO | TR = CÍRCULO | BL = TRIÂNGULO (base no topo) | BR = QUADRADO
 *
 * Formas distintas permitem identificar cada canto (e a rotação da foto:
 * as marcas são reconhecidas pela forma, não pela posição). A detecção
 * reutiliza o limiar adaptativo e os componentes conexos do OmrEngine.
 */
object MarcadorFiducial {

    enum class Forma { QUADRADO, CIRCULO, TRIANGULO }

    /** Componente conexo classificado como marcador candidato. */
    data class Marca(
        val cx: Float,
        val cy: Float,
        val w: Int,
        val h: Int,
        val area: Int,
        val fill: Float,
        val circ: Float,
        val forma: Forma
    )

    /** Os 4 cantos do quadro identificados pela forma. */
    data class Quadro(val tl: Marca, val tr: Marca, val bl: Marca, val br: Marca)

    /**
     * Procura os 4 marcadores. Retorna null se não encontrar um conjunto
     * consistente (quadrilátero convexo com as formas □ ○ △ □).
     */
    fun detectar(gray: IntArray, w: Int, h: Int): Quadro? {
        if (w < 50 || h < 50) return null
        // Janela grande (151px): o Bradley com janela pequena "come" o interior
        // de blocos grandes e uniformes — as marcas de 8mm ficariam ocas e não
        // seriam classificadas. Com 151px a janela sempre enxerga o fundo ao
        // redor da marca, e o bloco permanece sólido.
        val ink = OmrEngine.bradleyThreshold(gray, w, h, s = 151)
        val (lab, nc) = OmrEngine.connectedComponents(ink, w, h)
        val comps = OmrEngine.componentStatsFull(lab, nc, w, h)
        if (comps.isEmpty()) return null

        val base = minOf(w, h).toFloat()
        val minDim = 0.014f * base
        val maxDim = 0.150f * base
        val top = ArrayList<Marca>()
        for (c in comps) {
            val dim = maxOf(c.w, c.h)
            if (dim < minDim || dim > maxDim) continue
            if (c.area < 150) continue
            val bboxArea = (c.w.toLong() * c.h.toLong()).toFloat()
            val fill = c.area / bboxArea
            // 0.40 cobre o triângulo (fill ~0.49); quadrados/círculos passam
            // pela classificação de forma logo depois.
            if (fill < 0.40f) continue
            val asp = dim.toFloat() / maxOf(1, minOf(c.w, c.h))
            if (asp > 1.7f) continue
            val per = c.perimetro.coerceAtLeast(1)
            val circ = (4.0 * Math.PI * c.area / (per.toDouble() * per.toDouble())).toFloat()
            val forma = classificar(fill, circ) ?: continue
            // Quiet zone: a marca impressa é um bloco isolado (2,5mm de folga
            // até a moldura). Bolha pintada tem o anel a ~0,5mm — reprovada aqui.
            if (!isolado(lab, w, h, c, maxOf(6, (0.25f * dim).toInt()))) continue
            top.add(
                Marca(
                    cx = c.cx, cy = c.cy, w = c.w, h = c.h,
                    area = c.area, fill = fill, circ = circ, forma = forma
                )
            )
        }
        if (top.size < 4) return null
        val sel = arrayOfNulls<Marca>(4)
        for (a in 0 until top.size - 3) {
            sel[0] = top[a]
            for (b in a + 1 until top.size - 2) {
                sel[1] = top[b]
                for (c in b + 1 until top.size - 1) {
                    sel[2] = top[c]
                    for (d in c + 1 until top.size) {
                        sel[3] = top[d]
                        val quad = avaliar(arrayOf(sel[0]!!, sel[1]!!, sel[2]!!, sel[3]!!), w, h)
                        if (quad != null) return quad
                    }
                }
            }
        }
        return null
    }

    /**
     * Verdadeiro se o componente é um bloco isolado: nenhuma outra tinta
     * na faixa de [margem] px ao redor do bounding box. As marcas impressas
     * têm ~2,5 mm de folga; bolhas/letras/números encostam em outras tintas.
     */
    private fun isolado(
        lab: IntArray, w: Int, h: Int, c: OmrEngine.Comp, margem: Int
    ): Boolean {
        val x0 = (c.x - margem).coerceAtLeast(0)
        val x1 = (c.x + c.w - 1 + margem).coerceAtMost(w - 1)
        val y0 = (c.y - margem).coerceAtLeast(0)
        val y1 = (c.y + c.h - 1 + margem).coerceAtMost(h - 1)
        // Rótulo do componente: o canto do bbox pode ser fundo (círculo).
        var rotulo = 0
        for (yy in c.y until c.y + c.h) {
            for (xx in c.x until c.x + c.w) {
                val v = lab[yy * w + xx]
                if (v != 0) {
                    rotulo = v
                    break
                }
            }
            if (rotulo != 0) break
        }
        if (rotulo == 0) return false
        // percorre apenas a "moldura" externa ao bbox original
        for (y in y0..y1) {
            for (x in x0..x1) {
                if (x >= c.x && x <= c.x + c.w - 1 && y >= c.y && y <= c.y + c.h - 1) continue
                if (lab[y * w + x] != 0 && lab[y * w + x] != rotulo) return false
            }
        }
        return true
    }

    /** Classifica um componente sólido por fill ratio e circularidade. */
    fun classificar(fill: Float, circ: Float): Forma? = when {
        fill in 0.66f..0.92f && circ >= 0.86f -> Forma.CIRCULO
        fill >= 0.88f && circ in 0.60f..0.92f -> Forma.QUADRADO
        fill in 0.38f..0.66f && circ in 0.35f..0.88f -> Forma.TRIANGULO
        else -> null
    }

    /** Verifica um conjunto de 4 marcas e atribui TL/TR/BL/BR pela forma. */
    private fun avaliar(sel: Array<Marca>, imgW: Int, imgH: Int): Quadro? {
        val formas = sel.map { it.forma }
        if (formas.count { it == Forma.QUADRADO } != 2) return null
        if (formas.count { it == Forma.CIRCULO } != 1) return null
        if (formas.count { it == Forma.TRIANGULO } != 1) return null

        // Ordena por ângulo no centróide → polígono convexo simples.
        val cx0 = sel.map { it.cx }.average().toFloat()
        val cy0 = sel.map { it.cy }.average().toFloat()
        val ordenados = sel.sortedBy { atan2((it.cy - cy0).toDouble(), (it.cx - cx0).toDouble()) }
        val pts = ordenados.map { Pair(it.cx, it.cy) }

        // Ângulos internos ~90°.
        for (i in 0 until 4) {
            val prev = pts[(i + 3) % 4]
            val cur = pts[i]
            val next = pts[(i + 1) % 4]
            val v1x = prev.first - cur.first
            val v1y = prev.second - cur.second
            val v2x = next.first - cur.first
            val v2y = next.second - cur.second
            val n1 = sqrt(v1x * v1x + v1y * v1y)
            val n2 = sqrt(v2x * v2x + v2y * v2y)
            if (n1 < 1f || n2 < 1f) return null
            val cosA = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1f, 1f)
            val ang = Math.toDegrees(Math.acos(cosA.toDouble()))
            if (ang < 58.0 || ang > 122.0) return null
        }

        // Diagonais de tamanhos compatíveis.
        val d1 = dist(pts[0], pts[2])
        val d2 = dist(pts[1], pts[3])
        if (abs(d1 - d2) > 0.35f * maxOf(d1, d2)) return null

        // O quadro precisa ser uma fração significativa da imagem.
        val minX = pts.minOf { it.first }
        val maxX = pts.maxOf { it.first }
        val minY = pts.minOf { it.second }
        val maxY = pts.maxOf { it.second }
        val bw = maxX - minX
        val bh = maxY - minY
        if (bw < 10f || bh < 10f) return null
        val area = bw * bh
        if (area < 0.03f * imgW * imgH || area > 0.92f * imgW * imgH) return null
        val aspecto = bw / bh
        if (aspecto < 0.15f || aspecto > 5.0f) return null

        // Atribuição: ○ = TR (topo-direita), △ = BL (base-esquerda).
        val circulo = sel.first { it.forma == Forma.CIRCULO }
        val triangulo = sel.first { it.forma == Forma.TRIANGULO }
        val quadrados = sel.filter { it.forma == Forma.QUADRADO }
        // cross(BL - TR, cand - TR) > 0 → TL; < 0 → BR (ver GeometriaFolha).
        val dx = triangulo.cx - circulo.cx
        val dy = triangulo.cy - circulo.cy
        var tl: Marca? = null
        var br: Marca? = null
        for (q in quadrados) {
            val vx = q.cx - circulo.cx
            val vy = q.cy - circulo.cy
            val cross = dx * vy - dy * vx
            if (cross > 0f) tl = q else br = q
        }
        if (tl == null || br == null) return null
        return Quadro(tl, circulo, triangulo, br)
    }

    private fun dist(a: Pair<Float, Float>, b: Pair<Float, Float>): Float {
        val dx = a.first - b.first
        val dy = a.second - b.second
        return sqrt(dx * dx + dy * dy)
    }
}
