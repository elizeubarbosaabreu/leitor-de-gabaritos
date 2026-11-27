package com.elizeu.gabarito

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPInputStream

/**
 * Matriz de precisão: cada arquivo <caso>_<LxA>.gray.gz é uma variação
 * (distância, rotação, perspectiva, iluminação, compressão) de um gabarito
 * com resposta conhecida, listada em manifest.tsv (arquivo, nx, respostas).
 *
 * Dataset: diretório apontado por OMR_DATASET (não versionado, ver README).
 * Gera build/reports/omr-accuracy.txt com a precisão por caso.
 */
class OmrAccuracyTest {

    private fun loadGray(file: File): Triple<IntArray, Int, Int> {
        val m = Regex("_(\\d+)x(\\d+)\\.gray\\.gz$").find(file.name)!!
        val w = m.groupValues[1].toInt()
        val h = m.groupValues[2].toInt()
        val bytes = GZIPInputStream(file.inputStream()).use { it.readBytes() }
        require(bytes.size == w * h) { "${file.name}: tamanho ${bytes.size} != ${w * h}" }
        return Triple(IntArray(w * h) { bytes[it].toInt() and 0xff }, w, h)
    }

    @Test
    fun accuracyMatrix() {
        val dir = System.getenv("OMR_DATASET")?.let { File(it) }
        assumeTrue("defina OMR_DATASET para rodar a matriz", dir != null && dir.isDirectory)
        val letters = "ABCDE"
        val rows = File(dir!!, "manifest.tsv").readLines().filter { it.isNotBlank() }
        val report = StringBuilder()
        var totalOk = 0
        var total = 0
        var casesOk = 0
        for (line in rows) {
            val (name, nxS, expected) = line.split("\t")
            val (gray, w, h) = loadGray(File(dir, name))
            val read = OmrEngine.readSheetGray(gray, w, h, nxS.toInt())
            var ok = 0
            val got = StringBuilder()
            for (i in expected.indices) {
                val a = if (i < read.rows.size) read.answerOf(i) else -1
                got.append(if (a in 0 until letters.length) letters[a] else '_')
                if (a in 0 until letters.length && letters[a] == expected[i]) ok++
            }
            totalOk += ok
            total += expected.length
            val perfect = ok == expected.length && read.rows.size == expected.length
            if (perfect) casesOk++
            report.append(if (perfect) "OK   " else "FAIL ")
                .append(name.padEnd(40))
                .append(" linhas=${read.rows.size.toString().padStart(2)} acertos=$ok/${expected.length}")
                .append(" lido=$got esperado=$expected\n")
        }
        report.append("\nCASOS PERFEITOS: $casesOk/${rows.size}\n")
        report.append("PRECISAO TOTAL: $totalOk/$total = ${"%.2f".format(100.0 * totalOk / total)}%\n")
        File("build/reports").mkdirs()
        File("build/reports/omr-accuracy.txt").writeText(report.toString())
    }
}
