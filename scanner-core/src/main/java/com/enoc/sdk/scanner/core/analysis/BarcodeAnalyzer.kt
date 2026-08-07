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
    private val continueScan: Boolean = false,
    private val rowsPerFrame: Int = 200,
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
                    if (scanRow(frame.getRow(y), y, "H")) return@use
                }
            }

            // 2. Vertical Pass (for 90-degree rotated barcodes)
            val colsToScan = 30 
            val colStep = (frame.width / colsToScan).coerceAtLeast(1)
            val colMiddle = frame.width / 2
            for (i in 0 until colsToScan) {
                if (hasScanned) return@use
                val offset = if (i % 2 == 0) i / 2 else -(i + 1) / 2
                val x = colMiddle + offset * colStep
                if (x in 0 until frame.width) {
                    if (scanRow(frame.getColumn(x), x, "V")) return@use
                }
            }

            // 3. Diagonal Pass (for 45-degree rotated barcodes)
            if (!hasScanned) {
                if (scanRow(frame.getDiagonal(1.0f), 0, "D1")) return@use
                if (scanRow(frame.getDiagonal(-1.0f), 0, "D2")) return@use
            }
        }
    }

    private fun scanRow(rawRow: IntArray, y: Int, orientation: String): Boolean {
        // 0. Specialized High-Density Screen Passes (The "Yes" Card Killer)
        // These passes use upsampling + aggressive thinning/dilation to find bars in glow.
        val upsampled = RowBinarizer.upsample(rawRow)
        if (checkBinary(RowBinarizer.close(RowBinarizer.binarizeThin(upsampled)), y, "$orientation-Up-ThinClose")) return true
        if (checkBinary(RowBinarizer.dilate(RowBinarizer.binarizeAdaptive(upsampled, t = 30)), y, "$orientation-Up-Adap30D")) return true
        if (checkBinary(RowBinarizer.binarizeThin(RowBinarizer.sharpen(upsampled)), y, "$orientation-Up-SharpenThin")) return true

        // 1. High Density Upsampled Passes
        if (checkBinary(RowBinarizer.binarizeAdaptive(upsampled, t = 25), y, "$orientation-Up-Adap25")) return true
        if (checkBinary(RowBinarizer.binarizeAdaptive(upsampled, t = 15), y, "$orientation-Up-Adap15")) return true
        if (checkBinary(RowBinarizer.erode(RowBinarizer.binarizeAdaptive(upsampled, t = 15)), y, "$orientation-Up-Erode")) return true
        if (checkBinary(RowBinarizer.binarizeThick(upsampled), y, "$orientation-Up-Thick")) return true
        if (checkBinary(RowBinarizer.binarizeThin(upsampled), y, "$orientation-Up-Thin")) return true

        // 1. Balanced Adaptive Passes
        if (checkBinary(RowBinarizer.binarizeAdaptive(rawRow, t = 20), y, "$orientation-Adap20")) return true
        if (checkBinary(RowBinarizer.binarizeAdaptive(rawRow, t = 10), y, "$orientation-Adap10")) return true

        // 2. Specialized Screen Pass (Optimized threshold + Closing)
        if (checkBinary(RowBinarizer.binarizeForScreens(rawRow), y, "$orientation-Screen-Spec")) return true

        // 3. Blooming Pass (Thinning bars to recover spaces)
        if (checkBinary(RowBinarizer.binarizeThin(rawRow), y, "$orientation-Thin")) return true

        // 4. Sharpened Pass (Focuses on bar edges)
        val sharpened = RowBinarizer.sharpen(rawRow)
        if (checkBinary(RowBinarizer.binarizeForScreens(sharpened), y, "$orientation-Sharpen")) return true

        // 5. Robust Adaptive Pass (Threshold + Erode)
        if (checkBinary(RowBinarizer.erode(RowBinarizer.binarizeAdaptive(rawRow, t = 15)), y, "$orientation-Adaptive-Erode")) return true

        // 6. Strong Noise Pass (Median 5 + Thick)
        val filtered5 = RowBinarizer.medianFilter5(rawRow)
        if (checkBinary(RowBinarizer.binarizeThick(filtered5), y, "$orientation-Thick5")) return true

        // 7. High Density Pass (Small blocks + Median)
        val filtered = RowBinarizer.medianFilter(rawRow)
        if (checkBinary(RowBinarizer.binarizeHighDensity(filtered), y, "$orientation-HD")) return true

        // 8. Extreme Blooming Pass (Very high threshold)
        if (checkBinary(RowBinarizer.binarizeHighContrast(rawRow), y, "$orientation-X-Thick")) return true

        // 9. Standard passes for completeness
        if (checkBinary(RowBinarizer.binarizeThick(filtered), y, "$orientation-Thick")) return true
        if (checkBinary(RowBinarizer.binarize(RowBinarizer.smooth(filtered)), y, "$orientation-Smooth")) return true

        return false
    }

    private fun checkBinary(binary: BooleanArray, y: Int, label: String): Boolean {
        val runs = RunLengthReader.toRuns(RowBinarizer.deSpeckle(binary), label)
        decoder.decode(runs)?.let { result ->
            Log.i("BarcodeAnalyzer", "SUCCESS ($label): Decoded ${result.format} '${result.text}' at row $y")
            if (!continueScan) {
                hasScanned = true
            }
            onResult(result.copy(rowY = y))
            return true
        }
        return false
    }
}
