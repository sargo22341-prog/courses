package org.opensources.courses.core.scanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.nio.ByteBuffer

class QrCodeDecoderTest {
    private val decoder = QrCodeDecoder()
    private val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJhYmMxMjMifQ.dGVzdC1zaWduYXR1cmVfLQ"

    private fun frameOf(
        text: String,
        size: Int = 480,
        inverted: Boolean = false,
    ): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return ByteArray(size * size) { index ->
            val dark = matrix.get(index % size, index / size) != inverted
            (if (dark) 0 else 255).toByte()
        }
    }

    @Test
    fun `decodes the Home Assistant token QR code`() {
        assertEquals(token, decoder.decode(frameOf(token), 480, 480))
    }

    @Test
    fun `decodes light-on-dark QR codes`() {
        assertEquals(token, decoder.decode(frameOf(token, inverted = true), 480, 480))
    }

    @Test
    fun `returns null when the frame has no QR code`() {
        assertNull(decoder.decode(ByteArray(480 * 480) { 127 }, 480, 480))
    }

    @Test
    fun `luminance extraction drops row padding`() {
        // 3×2 frame stored with a row stride of 5: two padding bytes per row.
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 9, 9, 4, 5, 6))

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), yPlaneToLuminance(buffer, rowStride = 5, width = 3, height = 2))
    }
}
