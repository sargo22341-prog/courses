package org.opensources.courses.core.scanner

import android.util.Size
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.lifecycle.awaitInstance
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.Executors

/**
 * Back camera preview analysed for QR codes. The camera is bound to the current lifecycle while
 * this composable is on screen and released as soon as it leaves. Frames are analysed in memory
 * only; nothing is stored or sent. Requires the CAMERA permission to be granted beforehand.
 */
@Composable
fun CameraQrPreview(
    onDecoded: (String) -> Unit,
    onCameraError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnDecoded by rememberUpdatedState(onDecoded)
    val currentOnCameraError by rememberUpdatedState(onCameraError)
    var surfaceRequest by remember { mutableStateOf<SurfaceRequest?>(null) }

    LaunchedEffect(lifecycleOwner) {
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analysisExecutor = Executors.newSingleThreadExecutor()
        val preview = Preview.Builder().build().apply { setSurfaceProvider { surfaceRequest = it } }
        val analysis =
            ImageAnalysis
                .Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(
                    ResolutionSelector
                        .Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(ANALYSIS_SIZE, ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER),
                        ).build(),
                ).build()
                .apply { setAnalyzer(analysisExecutor, QrCodeAnalyzer { text -> mainExecutor.execute { currentOnDecoded(text) } }) }
        var provider: ProcessCameraProvider? = null
        try {
            provider = ProcessCameraProvider.awaitInstance(context)
            provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            awaitCancellation()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // No back camera, camera used by another app, or binding refused.
            currentOnCameraError()
        } finally {
            analysis.clearAnalyzer()
            provider?.unbind(preview, analysis)
            analysisExecutor.shutdown()
        }
    }

    surfaceRequest?.let { CameraXViewfinder(surfaceRequest = it, modifier = modifier) }
}

/** Enough detail for a token QR code without decoding full-resolution frames. */
private val ANALYSIS_SIZE = Size(1280, 720)
