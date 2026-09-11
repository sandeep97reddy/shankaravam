package com.shankaravam.festival.core.export

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Expense receipts squeezed to ~100KB WebP (plan §18, Rule #2) before any
 * optional cloud upload. Pure local files — never blocks the entry form:
 * callers compress on Dispatchers.IO and show Attaching state meanwhile.
 */
object ReceiptCompressor {

    const val MAX_BYTES = 100 * 1024
    private const val MAX_DIMENSION = 1280

    suspend fun compressToWebP(
        contentResolver: ContentResolver,
        source: Uri,
        destFile: File
    ): File = withContext(Dispatchers.IO) {
        destFile.parentFile?.mkdirs()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }

        var sampleSize = 1
        while (
            bounds.outWidth / sampleSize > MAX_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_DIMENSION
        ) {
            sampleSize *= 2
        }

        var bitmap = decode(contentResolver, source, sampleSize)
            ?: throw IllegalArgumentException("Could not decode receipt image")
        try {
            var quality = 80
            var bytes = encode(bitmap, quality)
            // Shrink quality first (fast), then dimensions (slower) until we fit.
            while (bytes.size > MAX_BYTES) {
                if (quality > 25) {
                    quality -= 15
                } else {
                    val scaled = Bitmap.createScaledBitmap(
                        bitmap,
                        (bitmap.width * 0.75).toInt().coerceAtLeast(1),
                        (bitmap.height * 0.75).toInt().coerceAtLeast(1),
                        true
                    )
                    if (scaled.width < 200) break
                    bitmap.recycle()
                    bitmap = scaled
                    quality = 70
                }
                bytes = encode(bitmap, quality)
            }
            destFile.writeBytes(bytes)
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        destFile
    }

    private fun decode(contentResolver: ContentResolver, source: Uri, sampleSize: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, opts) }
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            @Suppress("DEPRECATION")
            Bitmap.CompressFormat.WEBP
        }
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.toByteArray()
        }
    }
}
