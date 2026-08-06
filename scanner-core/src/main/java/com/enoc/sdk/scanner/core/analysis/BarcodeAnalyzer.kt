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

            // Scan from middle outwards (prioritize the red viewfinder line)
            val middle = frame.height / 2
            val step = (frame.height / rowsPerFrame).coerceAtLeast(1)
            
            for (i in 0 until rowsPerFrame) {
                if (hasScanned) return@use
                
                // Zig-zag out from middle: 0, 1, -1, 2, -2...
                val offset = if (i % 2 == 0) i / 2 else -(i + 1) / 2
                val y = middle + offset * step
                
                if (y in 0 until frame.height) {
                    if (scanRow(frame, y)) return@use
                }
            }
        }
    }

    private fun scanRow(frame: LuminanceFrame, y: Int): Boolean {
        val rawRow = frame.getRow(y)
        
        // 1. Target Screen Glow (Median Filter + Thick Bars + Dilation)
        val filtered = RowBinarizer.medianFilter(rawRow)
        if (checkBinary(RowBinarizer.dilate(RowBinarizer.binarizeThick(filtered)), y, "AntiGlow")) return true
        
        // 2. High-Density Screen Pass
        val upsampled = RowBinarizer.upsample(filtered)
        if (checkBinary(RowBinarizer.binarizeHighDensity(upsampled), y, "HD-Up")) return true

        // 3. Screen-Specific Adaptive Pass (NEW)
        if (checkBinary(RowBinarizer.binarizeForScreens(rawRow), y, "Screen-Spec")) return true
        
        // 4. Standard Screen-Scan
        val smoothed = RowBinarizer.smooth(filtered)
        if (checkBinary(RowBinarizer.binarize(smoothed), y, "Smoothed")) return true
        
        // 5. Adaptive Pass (Best for varied lighting/screens)
        return checkBinary(RowBinarizer.dilate(RowBinarizer.binarizeAdaptive(rawRow)), y, "Adaptive")
    }

    private fun checkBinary(binary: BooleanArray, y: Int, label: String): Boolean {
        val runs = RunLengthReader.toRuns(RowBinarizer.deSpeckle(binary))
        decoder.decode(runs)?.let { result ->
            Log.d("BarcodeAnalyzer", "SUCCESS ($label): Decoded ${result.format} '${result.text}' at row $y")
            hasScanned = true
            onResult(result.copy(rowY = y))
            return true
        }
        return false
    }
}
