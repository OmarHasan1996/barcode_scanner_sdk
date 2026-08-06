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
        // 1. NEW High-Contrast Screen Pass (Targeting blooming on OLED/Bright screens)
        val filtered5 = RowBinarizer.medianFilter5(rawRow)
        if (checkBinary(RowBinarizer.binarizeHighContrast(filtered5), y, "$orientation-HighContrast")) return true

        // 2. Contrast Stretch Pass (helps with screen glare)
        val stretched = RowBinarizer.stretchContrast(rawRow)
        if (checkBinary(RowBinarizer.binarizeHighDensity(stretched), y, "$orientation-Contrast-HD")) return true

        // 3. Target Screen Glow (Median Filter + Thick Bars + Dilation)
        val filtered = RowBinarizer.medianFilter(rawRow)
        if (checkBinary(RowBinarizer.close(RowBinarizer.binarizeThick(filtered)), y, "$orientation-Robust-Close")) return true
        
        // 3. High-Density Screen Pass
        val upsampled = RowBinarizer.upsample(filtered)
        if (checkBinary(RowBinarizer.binarizeHighDensity(upsampled), y, "$orientation-HD-Up")) return true

        // 4. Screen-Specific Adaptive Pass
        if (checkBinary(RowBinarizer.binarizeForScreens(rawRow), y, "$orientation-Screen-Spec")) return true
        
        // 5. Standard Screen-Scan
        val smoothed = RowBinarizer.smooth(filtered)
        if (checkBinary(RowBinarizer.binarize(smoothed), y, "$orientation-Smoothed")) return true
        
        // 6. Adaptive Pass
        return checkBinary(RowBinarizer.dilate(RowBinarizer.binarizeAdaptive(rawRow)), y, "$orientation-Adaptive")
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
