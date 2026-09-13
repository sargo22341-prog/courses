package org.opensources.courses.core.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Returns a function that opens the Google Play services QR code scanner.
 *
 * The scanner UI runs in Google Play services: the app never gets camera access and needs no
 * CAMERA permission. It only receives the decoded text, handed to [onScanned] unchanged —
 * interpreting it is the caller's business logic. Closing the scanner calls nothing.
 * [onUnavailable] is called when the scanner cannot run (no Google Play services, module not
 * downloadable, camera error).
 */
@Composable
fun rememberQrCodeScanner(
    onScanned: (String) -> Unit,
    onUnavailable: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val currentOnScanned by rememberUpdatedState(onScanned)
    val currentOnUnavailable by rememberUpdatedState(onUnavailable)
    val scanner =
        remember(context) {
            val options =
                GmsBarcodeScannerOptions
                    .Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .enableAutoZoom()
                    .build()
            GmsBarcodeScanning.getClient(context, options)
        }
    return remember(scanner) {
        {
            scanner
                .startScan()
                .addOnSuccessListener { barcode -> currentOnScanned(barcode.rawValue.orEmpty()) }
                .addOnFailureListener { currentOnUnavailable() }
        }
    }
}
