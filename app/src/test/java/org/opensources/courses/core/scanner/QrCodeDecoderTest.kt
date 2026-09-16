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

    /** A [width]×[height] frame showing the code, [size] pixels wide, whose top left corner is at [left], [top]. */
    private fun frameOf(
        text: String,
        size: Int = 480,
        inverted: Boolean = false,
        width: Int = size,
        height: Int = size,
        left: Int = 0,
        top: Int = 0,
    ): ByteArray {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        return ByteArray(width * height) { index ->
            val x = index % width - left
            val y = index / width - top
            val dark = x in 0 until size && y in 0 until size && matrix.get(x, y)
            (if (dark != inverted) 0 else 255).toByte()
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
    fun `light-on-dark codes are looked for in every other frame`() {
        val inverted = frameOf(token, inverted = true)

        assertEquals(token, decoder.decode(inverted, 480, 480))
        assertNull(decoder.decode(inverted, 480, 480))
        assertEquals(token, decoder.decode(inverted, 480, 480))
    }

    @Test
    fun `a code in the centre of a wide frame is decoded`() {
        val frame = frameOf(token, size = 400, width = 1280, height = 720, left = 440, top = 160)

        assertEquals(token, decoder.decode(frame, 1280, 720))
    }

    @Test
    fun `a code outside the centred square is left out`() {
        val frame = frameOf(token, size = 280, width = 1280, height = 720, top = 220)

        assertNull(decoder.decode(frame, 1280, 720))
    }

    @Test
    fun `luminance extraction drops row padding`() {
        // 3×2 frame stored with a row stride of 5: two padding bytes per row.
        val buffer = ByteBuffer.wrap(byteArrayOf(1, 2, 3, 9, 9, 4, 5, 6))
        val luminance = ByteArray(6)

        copyLuminance(buffer, rowStride = 5, width = 3, height = 2, target = luminance)

        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6), luminance)
    }
}
