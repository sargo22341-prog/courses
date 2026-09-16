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
    private var looksForInvertedCode = false

    /**
     * Text of the QR code found in the [width]×[height] luminance frame, or null.
     *
     * Only the centred square is searched: it holds the frame the scanner shows, whatever the
     * orientation, and leaves out the rest of a wide frame. Light-on-dark codes, shown by some screens
     * and themes, are looked for in every other frame only: most frames hold no code at all.
     */
    fun decode(
        luminance: ByteArray,
        width: Int,
        height: Int,
    ): String? {
        val side = minOf(width, height)
        val source = PlanarYUVLuminanceSource(luminance, width, height, (width - side) / 2, (height - side) / 2, side, side, false)
        looksForInvertedCode = !looksForInvertedCode
        return decode(source) ?: if (looksForInvertedCode) decode(source.invert()) else null
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
 * Copies the Y (luminance) plane of a YUV_420_888 frame into [target], a tight width×height array.
 * Camera buffers may pad each row: [rowStride] can be larger than [width].
 */
fun copyLuminance(
    buffer: ByteBuffer,
    rowStride: Int,
    width: Int,
    height: Int,
    target: ByteArray,
) {
    val source = buffer.duplicate()
    for (row in 0 until height) {
        source.position(row * rowStride)
        source.get(target, row * width, width)
    }
}
