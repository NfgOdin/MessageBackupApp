package com.messagebackup.app.domain.mesh

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages Raven Mesh QR code generation and Google Play Services Code Scanner orchestration.
 */
class RavenQrManager(private val context: Context) {

    private val cryptoEngine = RavenCryptoEngine(context)

    /**
     * Generates a high-contrast QR code bitmap containing the cryptographic handshake payload.
     */
    suspend fun generateHandshakeQrBitmap(displayName: String, sizePx: Int = 512): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val payload = cryptoEngine.buildHandshakePayload(displayName = displayName)
            val bitMatrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, sizePx, sizePx)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Launches the Google Play Services Code Scanner to scan a peer's Raven QR code without
     * needing the app to request runtime CAMERA permissions.
     */
    fun startPeerScanner(
        activity: Activity,
        onPeerScanned: (VerifiedPeerHandshake) -> Unit,
        onError: (String) -> Unit
    ) {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(com.google.mlkit.vision.barcode.common.Barcode.FORMAT_QR_CODE)
            .enableAutoZoom()
            .build()

        val scanner = GmsBarcodeScanning.getClient(activity, options)

        scanner.startScan()
            .addOnSuccessListener { barcode ->
                val rawValue = barcode.rawValue
                if (rawValue.isNullOrBlank()) {
                    onError("Scanned code was empty.")
                    return@addOnSuccessListener
                }

                val verified = cryptoEngine.parseAndVerifyHandshake(rawValue)
                if (verified != null) {
                    onPeerScanned(verified)
                } else {
                    onError("Invalid or unverified Raven Mesh QR code.")
                }
            }
            .addOnCanceledListener {
                // User dismissed scanner
            }
            .addOnFailureListener { e ->
                onError("Scanner failed: ${e.message ?: "Unknown error"}")
            }
    }
}
