package org.opensources.courses.core.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX analyzer delivering the first decoded QR code once, then ignoring later frames. */
class QrCodeAnalyzer(
    private val onDecoded: (String) -> Unit,
) : ImageAnalysis.Analyzer {
    private val decoder = QrCodeDecoder()
    private val delivered = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        image.use { frame ->
            if (delivered.get()) return
            val yPlane = frame.planes[0]
            val luminance = yPlaneToLuminance(yPlane.buffer, yPlane.rowStride, frame.width, frame.height)
            val text = decoder.decode(luminance, frame.width, frame.height) ?: return
            if (delivered.compareAndSet(false, true)) onDecoded(text)
        }
    }
}
