package com.shankaravam.festival.presentation.settings

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import com.shankaravam.festival.core.util.parseJoinCode
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * F2: verifies the exact zxing recipe QrInvite.kt uses (writer settings +
 * RGBLuminanceSource + HybridBinarizer + QRCodeReader) without Android —
 * pixels are built by hand, so no Bitmap is needed on the JVM.
 */
class QrCodecTest {

    private fun roundTrip(payload: String, size: Int = 256): String {
        val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                pixels[y * size + x] =
                    if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return QRCodeReader()
            .decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(size, size, pixels))))
            .text
    }

    @Test
    fun invite_payload_roundtrips_and_parses() {
        val text = roundTrip(joinQrPayload("ABC234"))
        assertEquals("shankaravam://join/ABC234", text)
        assertEquals("ABC234", parseJoinCode(text))
    }

    @Test
    fun dense_payload_roundtrips() {
        // Real-world QR screenshots carry the same payload at higher density.
        val text = roundTrip(joinQrPayload("KXQ7Z2"), 192)
        assertEquals("ABC234".length, parseJoinCode(text)?.length)
        assertEquals("KXQ7Z2", parseJoinCode(text))
    }
}
