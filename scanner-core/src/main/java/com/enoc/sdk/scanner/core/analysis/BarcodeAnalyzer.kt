package com.enoc.sdk.scanner.core.analysis

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
 * Optimized analyzer for high-resolution cameras.
 * Uses intensive multi-pass binarization and targeted ROI scanning.
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
        Logger.d("Scanner reset")
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

            // Define ROI: 90% width, 20% height
            val roiHeight = (frame.height * 0.20).toInt()
            val roiWidth = (frame.width * 0.90).toInt()
            val roiTop = (frame.height - roiHeight) / 2
            val roiBottom = roiTop + roiHeight
            val roiLeft = (frame.width - roiWidth) / 2
            val roiRight = roiLeft + roiWidth

            // 1. Horizontal Passes within ROI
            val step = (roiHeight / rowsPerFrame).coerceAtLeast(1)
            val middle = frame.height / 2
            for (i in 0 until rowsPerFrame) {
                if (hasScanned) return@use
                val offset = if (i % 2 == 0) i / 2 else -(i + 1) / 2
                val y = middle + offset * step
                if (y in roiTop until roiBottom) {
                    val fullRow = frame.getRow(y)
                    val roiRow = fullRow.copyOfRange(roiLeft, roiRight)
                    if (scanRow(roiRow, y, "H")) return@use
                }
            }

            // 2. Vertical Passes (centered in ROI)
            val colsToScan = 30
            val vRoiWidth = (roiWidth * 0.4).toInt()
            val vLeft = roiLeft + (roiWidth - vRoiWidth) / 2
            val vRight = vLeft + vRoiWidth
            val colStep = (vRoiWidth / colsToScan).coerceAtLeast(1)
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

            // 3. Diagonal Passes (across the ROI rectangle)
            if (!hasScanned) {
                // Top-left to bottom-right of ROI
                val diag1 = frame.getLine(roiLeft, roiTop, roiRight, roiBottom)
                if (scanRow(diag1, 0, "D1")) return@use
                
                // Top-right to bottom-left of ROI
                val diag2 = frame.getLine(roiRight, roiTop, roiLeft, roiBottom)
                if (scanRow(diag2, 0, "D2")) return@use
            }
        }
    }

    private fun scanRow(rawRow: IntArray, y: Int, orientation: String): Boolean {
        // High-Density Screen & Industrial Passes
        val upsampled = RowBinarizer.upsample(rawRow)
        if (checkBinary(RowBinarizer.close(RowBinarizer.binarizeThin(upsampled)), y, "$orientation-U-TC")) return true
        if (checkBinary(RowBinarizer.dilate(RowBinarizer.binarizeAdaptive(upsampled, t = 30)), y, "$orientation-U-A30D")) return true
        if (checkBinary(RowBinarizer.binarizeThin(RowBinarizer.sharpen(upsampled)), y, "$orientation-U-ST")) return true

        // Standard Adaptive Passes
        if (checkBinary(RowBinarizer.binarizeAdaptive(rawRow, t = 20), y, "$orientation-A20")) return true
        if (checkBinary(RowBinarizer.binarizeAdaptive(rawRow, t = 10), y, "$orientation-A10")) return true

        // Specialized Passes
        if (checkBinary(RowBinarizer.binarizeForScreens(rawRow), y, "$orientation-Scr")) return true
        if (checkBinary(RowBinarizer.binarizeThin(rawRow), y, "$orientation-Thin")) return true

        // Robust Filtered Passes
        val sharpened = RowBinarizer.sharpen(rawRow)
        if (checkBinary(RowBinarizer.binarizeForScreens(sharpened), y, "$orientation-Sharp")) return true

        val filtered5 = RowBinarizer.medianFilter5(rawRow)
        if (checkBinary(RowBinarizer.binarizeThick(filtered5), y, "$orientation-T5")) return true

        val filtered = RowBinarizer.medianFilter(rawRow)
        if (checkBinary(RowBinarizer.binarizeHighDensity(filtered), y, "$orientation-HD")) return true

        if (checkBinary(RowBinarizer.binarizeHighContrast(rawRow), y, "$orientation-XC")) return true

        return false
    }

    private fun checkBinary(binary: BooleanArray, y: Int, label: String): Boolean {
        val runs = RunLengthReader.toRuns(RowBinarizer.deSpeckle(binary), label)
        decoder.decode(runs)?.let { result ->
            Logger.i("SUCCESS ($label): Decoded ${result.format} '${result.text}' at row $y")
            if (!continueScan) {
                hasScanned = true
            }
            onResult(result.copy(rowY = y))
            return true
        }
        return false
    }
}
