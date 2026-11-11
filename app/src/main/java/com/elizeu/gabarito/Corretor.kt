package com.elizeu.gabarito

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import android.os.Parcelable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs

@Parcelize
data class ItemProva(
    val questao: Int,
    val resposta: Int,
    val fills: FloatArray
) : Parcelable

/**
 * Corretor: combina o motor OMR (bolhas) com OCR do ML Kit
 * (números das questões) para montar a resposta por questão.
 */
object Corretor {

    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

    /** Corrige uma prova fotografada: retorna itens por questão.
     *  nx = número de alternativas (EF=4, EM=5). */
    suspend fun corrigir(bmp: Bitmap, nx: Int = 5): List<ItemProva> = withContext(Dispatchers.Default) {
        val read = OmrEngine.readSheet(bmp, nx)
        val cols = read.cols
        val rows = read.rows
        if (rows.isEmpty()) return@withContext emptyList()

        val pitch = if (rows.size > 1) rows[1].cy - rows[0].cy else 50f
        val rowQ = IntArray(rows.size) { it + 1 }
        val hasQ = BooleanArray(rows.size)

        try {
            val text = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
            for (block in text.textBlocks) {
                val digits = block.text.filter { it.isDigit() }
                val q = digits.toIntOrNull() ?: continue
                if (q < 1 || q > 500) continue
                val box = block.boundingBox ?: continue
                val cx = box.exactCenterX()
                val cy = box.exactCenterY()
                if (cols.isNotEmpty() && cx >= cols[0] - 30f) continue
                val ri = rows.indices.minByOrNull { abs(rows[it].cy - cy) } ?: continue
                val tolerance = Math.max(0.6f * Math.abs(pitch), 30f)
                if (Math.abs(rows[ri].cy - cy) <= tolerance) {
                    rowQ[ri] = q
                    hasQ[ri] = true
                }
            }
        } catch (_: Exception) {
            // OCR opcional: em falha, usa numeração sequencial
        }

        // Preenche lacunas entre números reconhecidos (sentido cima->baixo)
        var last: Int? = null
        for (i in rows.indices) {
            if (hasQ[i]) {
                last = rowQ[i]
            } else if (last != null) {
                last += 1
                rowQ[i] = last
            } else {
                rowQ[i] = i + 1
            }
        }

        rows.indices.map { i ->
            ItemProva(
                questao = rowQ[i],
                resposta = read.answerOf(i),
                fills = rows[i].fills.copyOf()
            )
        }
    }

    /** Lê um gabarito impresso ("1E 2D 3C...") direto de uma foto via OCR. */
    suspend fun lerGabaritoDaFoto(bmp: Bitmap): Map<Int, Char> {
        val text = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        val joined = text.textBlocks.joinToString(" ") { it.text }
        return KeyParser.parse(joined)
    }
}
