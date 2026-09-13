package org.opensources.courses.core.scanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.nio.ByteBuffer

/**
 * Decodes QR codes from grayscale camera frames with ZXing (open source, no Google Play services).
 * QR codes are orientation independent, so frames are decoded as delivered by the camera.
 * Not thread-safe: use one instance per analysis thread.
 */
class QrCodeDecoder {
    private val reader = QRCodeReader()
    private val hints =
        mapOf(
            DecodeHintType.TRY_HARDER to true,
            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        )

    /** Text of the QR code found in the [width]×[height] luminance frame, or null. */
    fun decode(
        luminance: ByteArray,
        width: Int,
        height: Int,
    ): String? {
        val source = PlanarYUVLuminanceSource(luminance, width, height, 0, 0, width, height, false)
        // Some screens and themes display light-on-dark codes.
        return decode(source) ?: decode(source.invert())
    }

    private fun decode(source: LuminanceSource): String? =
        try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
}

/**
 * Copies the Y (luminance) plane of a YUV_420_888 frame into a tight width×height array.
 * Camera buffers may pad each row: [rowStride] can be larger than [width].
 */
fun yPlaneToLuminance(
    buffer: ByteBuffer,
    rowStride: Int,
    width: Int,
    height: Int,
): ByteArray {
    val source = buffer.duplicate()
    val luminance = ByteArray(width * height)
    for (row in 0 until height) {
        source.position(row * rowStride)
        source.get(luminance, row * width, width)
    }
    return luminance
}
