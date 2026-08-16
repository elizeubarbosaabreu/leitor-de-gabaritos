package com.elizeu.gabarito

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtils {

    /** Decodifica uma imagem de arquivo em escala máxima (respeitando EXIF). */
    fun decodeFile(path: String, targetWidth: Int = 1200, maxHeight: Int = 2400): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        while (bounds.outHeight / (sample * 2) >= maxHeight) sample *= 2

        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeFile(path, opts) ?: return null
        return applyExifRotation(path, bmp)
    }

    /** Copia uma Uri para arquivo cache e decodifica. */
    fun decodeUri(context: Context, uri: Uri, targetWidth: Int = 1200): Bitmap? {
        val file = File(context.cacheDir, "input_" + System.currentTimeMillis() + ".jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            }
        } catch (_: Exception) {
            return null
        }
        return decodeFile(file.absolutePath, targetWidth)
    }

    fun createCameraFile(context: Context): File {
        return File(context.cacheDir, "camera_" + System.currentTimeMillis() + ".jpg")
    }

    private fun applyExifRotation(path: String, bmp: Bitmap): Bitmap {
        val exif = try {
            ExifInterface(path)
        } catch (_: Exception) {
            return bmp
        }
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bmp, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bmp, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bmp, 270f)
            else -> bmp
        }
    }

    private fun rotate(bmp: Bitmap, degrees: Float): Bitmap {
        val m = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }
}
