package com.elizeu.gabarito

/**
 * Geometria canônica da folha de gabarito com marcadores de canto.
 *
 * Todas as medidas em MILÍMETROS, com origem no canto superior esquerdo do
 * quadro. O gerador de provas (landing-portifolio/ferramentas/gerador-de-provas.html)
 * imprime exatamente esta geometria — os dois lados precisam ficar em sincronia.
 *
 * Layout do quadro (nx = 4 ou 5 alternativas):
 *
 *   ┌─ ■ ─────────────────────────── ● ─┐   ■ = quadrado  (TL) | ● = círculo (TR)
 *   │  1   Ⓐ Ⓑ Ⓒ Ⓓ Ⓔ                   │   cy(i) = 16.5 + 8.5*i mm
 *   │  2   Ⓐ Ⓑ Ⓒ Ⓓ Ⓔ                   │   cx(j) = 18 + 7.5*j mm
 *   │  ...                             │   bolha: Ø 5.5 mm (raio 2.75)
 *   └─ ▲ ─────────────────────────── ■ ─┘   ▲ = triângulo (BL) | ■ = quadrado (BR)
 *        Preencha com caneta...            rodapé centralizado na faixa de baixo
 *
 * Marcadores: blocos sólidos de 8x8 mm com centros a 7 mm das bordas
 * (3 mm de folga da moldura = quiet zone). As faixas superior/inferior
 * são maiores que a marca + a bolha — as marcas NUNCA encostam nas bolhas.
 */
object GeometriaFolha {

    /** Lado do marcador de canto (mm). */
    const val MM_MARCA = 8f

    /** Distância do centro do marcador à borda do quadro (mm). */
    const val MM_OFFSET_MARCA = 7f

    /** Raio da bolha (mm). */
    const val MM_RAIO_BOLHA = 2.75f

    /** Centro X da primeira coluna de bolhas (mm). */
    const val MM_CX0 = 18f

    /** Passo entre colunas de bolhas (mm). */
    const val MM_PASSO_COLUNA = 7.5f

    /** Centro Y da primeira linha (mm). */
    const val MM_CY0 = 16.5f

    /** Passo entre linhas (mm). */
    const val MM_PASSO_LINHA = 8.5f

    /** Folga entre a última bolha e a faixa dos marcadores de baixo (mm). */
    const val MM_FOLGA_BASE = 8.25f

    /** Largura da caixa do número (mm). */
    const val MM_NUM_W = 8.5f

    /** Altura da caixa do número (mm). */
    const val MM_NUM_H = 5.5f

    /** Centro X da caixa do número (mm). */
    const val MM_NUM_CX = 9.5f

    /** Largura total do quadro em mm. */
    fun larguraMm(nx: Int): Float =
        MM_CX0 + (nx - 1) * MM_PASSO_COLUNA + MM_RAIO_BOLHA + 3f

    /** Altura total do quadro em mm (n linhas). */
    fun alturaMm(n: Int): Float =
        MM_CY0 + (n - 1) * MM_PASSO_LINHA + MM_PASSO_LINHA + MM_FOLGA_BASE

    /** Centro X da coluna j (mm). */
    fun cxColuna(j: Int): Float = MM_CX0 + j * MM_PASSO_COLUNA

    /** Centro Y da linha i (mm). */
    fun cyLinha(i: Int): Float = MM_CY0 + i * MM_PASSO_LINHA

    /**
     * Estima o número de linhas pela geometria detectada: mede a distância
     * entre os marcadores TL e BL (px), soma as folgas dos dois cantos para
     * ter a altura total em mm e desconta o topo/rodapé conhecidos.
     */
    fun estimarLinhas(distTL_BL_Px: Float, escalaPxPorMm: Float): Int {
        if (escalaPxPorMm <= 0.01f) return 0
        val alturaTotalMm = distTL_BL_Px / escalaPxPorMm + 2f * MM_OFFSET_MARCA
        val n = Math.round((alturaTotalMm - MM_CY0 - MM_FOLGA_BASE) / MM_PASSO_LINHA)
        return n.coerceIn(1, 40)
    }
}
