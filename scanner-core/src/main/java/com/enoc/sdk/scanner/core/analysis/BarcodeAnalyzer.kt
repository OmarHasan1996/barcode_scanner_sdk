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
    private val rowsPerFrame: Int = 45,
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

            // Sample rows across the middle band of the frame.
            val bandTop = (frame.height * 0.20).toInt()
            val bandBottom = (frame.height * 0.80).toInt()
            val step = ((bandBottom - bandTop) / rowsPerFrame).coerceAtLeast(1)

            var y = bandTop
            while (y < bandBottom && !hasScanned) {
                val row = frame.getRow(y)
                val binary = RowBinarizer.binarize(row)
                val runs = RunLengthReader.toRuns(binary)

                decoder.decode(runs)?.let { result ->
                    Log.d("BarcodeAnalyzer", "SUCCESS: Decoded ${result.format} '${result.text}' at row $y")
                    hasScanned = true
                    onResult(result.copy(rowY = y))
                }
                y += step
            }
        }
    }
}
