package com.enoc.sdk.scanner.core.analysis

import android.graphics.Rect
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
import com.enoc.sdk.scanner.core.utils.Logger

/**
 * Optimized analyzer for high-resolution (e.g. 5MP) cameras.
 * Limits scanning to a specific Region of Interest (ROI) to maximize CPU efficiency.
 */
class BarcodeAnalyzer(
    formats: Set<BarcodeFormat>,
    private val continueScan: Boolean = false,
    private val rowsPerFrame: Int = 120, // Reduced for faster per-frame turnaround
    private val onResult: (BarcodeResult) -> Unit
) : ImageAnalysis.Analyzer {

    private val decoder = CompositeDecoder(formats)

    @Volatile
    private var hasScanned = false

    fun reset() {
        Logger.d("BarcodeAnalyzer", "Scanner reset")
        hasScanned = false
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (hasScanned) {
            imageProxy.close()
            return
        }

        imageProxy.use { proxy ->
            val image = proxy.image ?: return
            val yPlane = image.planes[0]

            val frame = LuminanceFrame(
                yBytes = yPlane.buffer.let { buf ->
                    ByteArray(buf.remaining()).also { buf.get(it) }
                },
                srcWidth = image.width,
                srcHeight = image.height,
                rowStride = yPlane.rowStride,
                pixelStride = yPlane.pixelStride,
                rotationDegrees = proxy.imageInfo.rotationDegrees
            )

            // OPTIMIZATION: Scan ONLY the ROI (Region of Interest)
            // Based on our UI: 90% width, 20% height
            val roiHeight = (frame.height * 0.20).toInt()
            val roiWidth = (frame.width * 0.90).toInt()
            
            val roiTop = (frame.height - roiHeight) / 2
            val roiBottom = roiTop + roiHeight
            
            val roiLeft = (frame.width - roiWidth) / 2
            val roiRight = roiLeft + roiWidth

            // Scan horizontal rows within the ROI
            val step = (roiHeight / rowsPerFrame).coerceAtLeast(1)
            val middle = frame.height / 2
            
            for (i in 0 until rowsPerFrame) {
                if (hasScanned) return@use
                
                // Zig-zag out from middle, but stay within ROI
                val offset = if (i % 2 == 0) i / 2 else -(i + 1) / 2
                val y = middle + offset * step
                
                if (y in roiTop until roiBottom) {
                    // Extract only the part of the row that is inside the ROI width
                    val fullRow = frame.getRow(y)
                    val roiRow = fullRow.copyOfRange(roiLeft, roiRight)
                    if (scanRow(roiRow, y, "H")) return@use
                }
            }

            // Vertical Pass: Focus on the center 30% of the ROI width
            val colsToScan = 20
            val verticalRoiWidth = (roiWidth * 0.3).toInt()
            val vLeft = roiLeft + (roiWidth - verticalRoiWidth) / 2
            val vRight = vLeft + verticalRoiWidth
            
            val colStep = (verticalRoiWidth / colsToScan).coerceAtLeast(1)
            val colMiddle = frame.width / 2
            for (i in 0 until colsToScan) {
                if (hasScanned) return@use
                val offset = if (i % 2 == 0) i / 2 else -(i + 1) / 2
                val x = colMiddle + offset * colStep
                if (x in vLeft until vRight) {
                    val fullCol = frame.getColumn(x)
                    val roiCol = fullCol.copyOfRange(roiTop, roiBottom)
                    if (scanRow(roiCol, x, "V")) return@use
                }
            }
        }
    }

    private fun scanRow(rawRow: IntArray, y: Int, orientation: String): Boolean {
        // High-density optimization: If row is long, sub-sample it for initial passes
        // to save CPU on 5MP high-res frames.
        
        // 1. Standard Balanced Passes
        if (checkBinary(RowBinarizer.binarizeAdaptive(rawRow, t = 20), y, "$orientation-Adap20")) return true
        if (checkBinary(RowBinarizer.binarizeAdaptive(rawRow, t = 10), y, "$orientation-Adap10")) return true

        // 2. Specialized Screen Pass
        if (checkBinary(RowBinarizer.binarizeForScreens(rawRow), y, "$orientation-Screen")) return true

        // 3. Upsampled High-Density Passes (Crucial for 5MP sensors seeing small codes)
        val upsampled = RowBinarizer.upsample(rawRow)
        if (checkBinary(RowBinarizer.binarizeThin(upsampled), y, "$orientation-Up-Thin")) return true
        if (checkBinary(RowBinarizer.binarizeAdaptive(upsampled, t = 15), y, "$orientation-Up-Adap15")) return true

        // 4. Blooming/Blur Pass (For out-of-focus or low-light)
        if (checkBinary(RowBinarizer.binarizeThin(rawRow), y, "$orientation-Thin")) return true

        return false
    }

    private fun checkBinary(binary: BooleanArray, y: Int, label: String): Boolean {
        val runs = RunLengthReader.toRuns(RowBinarizer.deSpeckle(binary), label)
        decoder.decode(runs)?.let { result ->
            Logger.i("BarcodeAnalyzer", "SUCCESS ($label): Decoded ${result.format} '${result.text}' at row $y")
            if (!continueScan) {
                hasScanned = true
            }
            onResult(result.copy(rowY = y))
            return true
        }
        return false
    }
}
