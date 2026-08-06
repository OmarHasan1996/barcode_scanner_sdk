package com.enoc.sdk.scanner.core.analysis


import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.enoc.sdk.scanner.core.core.LuminanceFrame
import com.enoc.sdk.scanner.core.core.RowBinarizer
import com.enoc.sdk.scanner.core.core.RunLengthReader
import com.enoc.sdk.scanner.core.decoder.CompositeDecoder
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult

/**
 * Scans a handful of horizontal rows per frame (barcodes are usually held
 * roughly level, so we don't need every row) and stops calling back after
 * the first successful decode. Call [reset] if you want to scan again.
 */
class BarcodeAnalyzer(
    formats: Set<BarcodeFormat>,
    private val rowsPerFrame: Int = 100,
    private val onResult: (BarcodeResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val decoder = CompositeDecoder(formats)

    @Volatile
    private var hasScanned = false

    fun reset() {
        Log.d("BarcodeAnalyzer", "Scanner reset")
        hasScanned = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasScanned) {
            imageProxy.close()
            return
        }

        imageProxy.use { imageProxy ->
            val image = imageProxy.image ?: return
            val yPlane = image.planes[0]

            val frame = LuminanceFrame(
                yBytes = yPlane.buffer.let { buf ->
                    ByteArray(buf.remaining()).also { buf.get(it) }
                },
                srcWidth = image.width,
                srcHeight = image.height,
                rowStride = yPlane.rowStride,
                pixelStride = yPlane.pixelStride,
                rotationDegrees = imageProxy.imageInfo.rotationDegrees
            )

            // Denser sampling near the middle (where the red line is)
            val step = (frame.height / rowsPerFrame).toInt().coerceAtLeast(1)
            
            for (y in 0 until frame.height step step) {
                if (hasScanned) return@use
                if (scanRow(frame, y)) return@use
            }
        }
    }

    private fun scanRow(frame: LuminanceFrame, y: Int): Boolean {
        val rawRow = frame.getRow(y)
        
        // 1. High-Density Pass (Upsample + Micro-Blocks)
        // Best for screens where backlight swallows gaps
        val upsampled = RowBinarizer.upsample(rawRow)
        if (checkBinary(RowBinarizer.binarizeHighDensity(upsampled), y, "HD-Up")) return true
        
        // 2. Standard Screen-Scan
        val smoothed = RowBinarizer.smooth(rawRow)
        if (checkBinary(RowBinarizer.binarizeThin(smoothed), y, "ThinGlow")) return true
        if (checkBinary(RowBinarizer.binarize(smoothed), y, "Smoothed")) return true
        
        return false
    }

    private fun checkBinary(binary: BooleanArray, y: Int, label: String): Boolean {
        val runs = RunLengthReader.toRuns(binary)
        decoder.decode(runs)?.let { result ->
            Log.d("BarcodeAnalyzer", "SUCCESS ($label): Decoded ${result.format} '${result.text}' at row $y")
            hasScanned = true
            onResult(result.copy(rowY = y))
            return true
        }
        return false
    }
}
