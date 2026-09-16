package org.opensources.courses.core.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraX analyzer delivering the first decoded QR code once, then ignoring later frames. Frames are
 * analysed one at a time on a single thread, so one luminance buffer serves them all.
 */
class QrCodeAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val decoder = QrCodeDecoder()
    private val delivered = AtomicBoolean(false)
    private var luminance = ByteArray(0)

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            if (delivered.get()) return
            val size = frame.width * frame.height
            if (luminance.size != size) luminance = ByteArray(size)
            val yPlane = frame.planes[0]
            copyLuminance(yPlane.buffer, yPlane.rowStride, frame.width, frame.height, luminance)
            val text = decoder.decode(luminance, frame.width, frame.height) ?: return
            if (delivered.compareAndSet(false, true)) onDecoded(text)
        }
    }
}
