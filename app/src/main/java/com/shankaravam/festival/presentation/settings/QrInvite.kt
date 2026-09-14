package com.shankaravam.festival.presentation.settings

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** QR payload contract (F1/F2): scanners, gallery picks and typed codes converge here. */
const val JOIN_DEEP_LINK_PREFIX = "shankaravam://join/"

fun joinQrPayload(code: String): String = "$JOIN_DEEP_LINK_PREFIX${code.trim().uppercase()}"

/**
 * Render the invite QR off the main thread (F2 perf: a 512² `setPixel` loop
 * inside `remember` janked composition at 60/120fps — AGENTS.md §5).
 */
suspend fun renderInviteQr(content: String, sizePx: Int = 512): Bitmap =
    withContext(Dispatchers.Default) {
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val row = y * sizePx
            for (x in 0 until sizePx) {
                pixels[row + x] = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        Bitmap.createBitmap(pixels, sizePx, sizePx, Bitmap.Config.RGB_565)
    }

/**
 * Decode QR text from a bitmap. Uses the `zxing:core`-bundled
 * [RGBLuminanceSource] — no new dependency. Null when no QR is present.
 * Never throws (call off the main thread; see [decodeJoinCodeFromUri]).
 */
fun decodeQrText(bitmap: Bitmap): String? = runCatching {
    val w = bitmap.width
    val h = bitmap.height
    if (w <= 0 || h <= 0) return null
    val pixels = IntArray(w * h)
    bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
    QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(w, h, pixels)))).text
}.getOrNull()

/** Memory-safe gallery load, bounded to [maxDim] via inSampleSize. Never throws. */
suspend fun loadBoundedBitmap(
    resolver: ContentResolver,
    uri: Uri,
    maxDim: Int = 1024
): Bitmap? = withContext(Dispatchers.IO) {
    runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / (sample * 2) >= maxDim && sample < 16) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }.getOrNull()
}

/**
 * Full gallery-pick pipeline (F2): load → decode → [parseJoinCode].
 * Returns the normalized 6-char code, or null with no exception.
 */
suspend fun decodeJoinCodeFromUri(resolver: ContentResolver, uri: Uri): String? {
    val bitmap = loadBoundedBitmap(resolver, uri) ?: return null
    val text = withContext(Dispatchers.Default) { decodeQrText(bitmap) } ?: return null
    return com.shankaravam.festival.core.util.parseJoinCode(text)
}

/**
 * Stage the invite QR as PNG under `cacheDir/share/` (FileProvider-configured,
 * P0) and share image + code text via WhatsApp-capable chooser. The text
 * fallback carries the code when the receiver has no QR scanner. Never throws.
 */
fun shareInviteQr(context: Context, code: String, bitmap: Bitmap) {
    runCatching {
        val clean = code.trim().uppercase()
        val dir = File(context.cacheDir, "share").apply { if (!exists()) mkdirs() }
        val file = File(dir, "invite_$clean.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "Join our festival team on ShankaRavam — code $clean (or scan the QR)."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share invite").apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
