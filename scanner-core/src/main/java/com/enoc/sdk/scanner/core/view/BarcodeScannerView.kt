package com.barcode.sdk.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.enoc.sdk.scanner.core.analysis.BarcodeAnalyzer
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult

/**
 * Drop-in camera + decode component. Handles CameraX setup, frame analysis,
 * and decoding internally; does NOT request the CAMERA permission or draw
 * any scan UI (viewfinder box, buttons, torch control, etc.) — that's left
 * to the host app so this stays a pure, reusable building block.
 *
 * Usage:
 * ```
 * val scannerView = BarcodeScannerView(context)
 * parentLayout.addView(scannerView)
 * scannerView.startScanning(
 *     lifecycleOwner = this,
 *     formats = setOf(BarcodeFormat.EAN_13, BarcodeFormat.UPC_A, BarcodeFormat.CODE_128)
 * ) { result ->
 *     // called once, on the main thread, with the first successful decode
 * }
 * ```
 */
class BarcodeScannerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val previewView = PreviewView(context).also {
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var analyzer: BarcodeAnalyzer? = null

    /**
     * Starts the camera and begins scanning. Caller must already hold the
     * CAMERA permission. [onResult] fires exactly once per call to
     * [startScanning] (the camera is unbound automatically after a hit) —
     * call [startScanning] again (or [rescan]) to look for another barcode.
     */
    fun startScanning(
        lifecycleOwner: LifecycleOwner,
        formats: Set<BarcodeFormat> = setOf(BarcodeFormat.EAN_13, BarcodeFormat.UPC_A, BarcodeFormat.CODE_128),
        onResult: (BarcodeResult) -> Unit
    ) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val resolutionSelector = ResolutionSelector.Builder()
                .setResolutionStrategy(
                    ResolutionStrategy(
                        android.util.Size(1280, 720),
                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                    )
                )
                .build()

            val newAnalyzer = BarcodeAnalyzer(formats) { result ->
                post {
                    onResult(result)
                }
            }
            analyzer = newAnalyzer

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setResolutionSelector(resolutionSelector)
                .build()
                .also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(context), newAnalyzer)
                }

            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
            
            // Set default zoom to 1.0 for physically large/thick barcodes
            camera.cameraControl.setZoomRatio(1.0f)
            
            val factory = previewView.meteringPointFactory
            val centerPoint = factory.createPoint(0.5f, 0.5f)
            val action = androidx.camera.core.FocusMeteringAction.Builder(centerPoint)
                .addPoint(centerPoint, androidx.camera.core.FocusMeteringAction.FLAG_AF or androidx.camera.core.FocusMeteringAction.FLAG_AE)
                .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            camera.cameraControl.startFocusAndMetering(action)
        }, ContextCompat.getMainExecutor(context))
    }

    /** Resumes scanning (e.g. after a successful decode) without re-binding the camera. */
    fun rescan() {
        analyzer?.reset()
    }

    /** Stops the camera entirely. Safe to call even if never started. */
    fun stopScanning() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        analyzer = null
    }
}
