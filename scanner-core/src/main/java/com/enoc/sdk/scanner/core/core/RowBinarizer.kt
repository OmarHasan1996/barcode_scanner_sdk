package com.enoc.sdk.scanner.core.core

import kotlin.math.max
import kotlin.math.min

object RowBinarizer {
    private const val BLOCK_SIZE = 16
    private const val MINIMUM_DYNAMIC_RANGE = 20

    /** Removes subpixel "sparks" while keeping bar edges sharp. */
    fun medianFilter(row: IntArray): IntArray {
        if (row.size < 3) return row
        val out = IntArray(row.size)
        for (i in 1 until row.size - 1) {
            val a = row[i-1]; val b = row[i]; val c = row[i+1]
            out[i] = max(min(a, b), min(max(a, b), c)) // Median of 3
        }
        out[0] = row[0]; out[row.size-1] = row[row.size-1]
        return out
    }

    /** Combats white glow by artificially thickening the black bars. */
    fun binarizeThick(row: IntArray): BooleanArray {
        if (row.isEmpty()) return BooleanArray(0)
        var minVal = 255; var maxVal = 0
        for (v in row) { if (v < minVal) minVal = v; if (v > maxVal) maxVal = v }
        
        // If low contrast, assume no barcode here
        if (maxVal - minVal < MINIMUM_DYNAMIC_RANGE) return BooleanArray(row.size)
        
        // Threshold closer to max (white) makes the black bars "grow" in the binary output.
        val threshold = minVal + (maxVal - minVal) * 0.70

        val out = BooleanArray(row.size)
        for (i in row.indices) out[i] = row[i] <= threshold
        return out
    }

    fun binarize(row: IntArray): BooleanArray {
        val width = row.size; val numBlocks = (width + BLOCK_SIZE - 1) / BLOCK_SIZE
        val thresholds = IntArray(numBlocks)
        for (b in 0 until numBlocks) {
            var minVal = 255; var maxVal = 0
            val start = b * BLOCK_SIZE; val end = min(start + BLOCK_SIZE, width)
            for (i in start until end) {
                val v = row[i]; if (v < minVal) minVal = v; if (v > maxVal) maxVal = v
            }
            thresholds[b] = if (maxVal - minVal > MINIMUM_DYNAMIC_RANGE) (minVal + maxVal) / 2 else -1 
        }
        val finalThresholds = IntArray(numBlocks)
        for (b in 0 until numBlocks) {
            var sum = 0; var count = 0
            for (i in -2..2) {
                val n = b + i; if (n in 0 until numBlocks && thresholds[n] != -1) { sum += thresholds[n]; count++ }
            }
            finalThresholds[b] = if (count > 0) sum / count else 127
        }
        val out = BooleanArray(width)
        for (i in 0 until width) out[i] = row[i] <= finalThresholds[i / BLOCK_SIZE]
        return out
    }

    fun binarizeAdaptive(row: IntArray, t: Int = 15): BooleanArray {
        val width = row.size; val out = BooleanArray(width)
        val s = width / 16
        val integral = LongArray(width); var sum: Long = 0
        for (i in 0 until width) { sum += row[i]; integral[i] = sum }
        for (i in 0 until width) {
            val x1 = max(0, i - s / 2); val x2 = min(width - 1, i + s / 2)
            val count = x2 - x1 + 1
            val blockSum = integral[x2] - (if (x1 > 0) integral[x1 - 1] else 0)
            out[i] = row[i].toLong() * count * 100 < blockSum * (100 - t)
        }
        return out
    }
    
    fun invert(binary: BooleanArray): BooleanArray {
        val out = BooleanArray(binary.size)
        for (i in binary.indices) out[i] = !binary[i]; return out
    }

    fun deSpeckle(row: BooleanArray): BooleanArray {
        if (row.size < 3) return row
        for (i in 1 until row.size - 1) { if (row[i-1] == row[i+1] && row[i] != row[i-1]) row[i] = row[i-1] }
        return row
    }

    /** 
     * Fills small white gaps (1-2 pixels) in black bars. 
     * Essential for screen scans where aliasing or moire patterns cause "ghost" white lines.
     */
    fun dilate(row: BooleanArray): BooleanArray {
        if (row.size < 3) return row
        val out = BooleanArray(row.size)
        for (i in 0 until row.size) {
            val prev = if (i > 0) row[i - 1] else false
            val next = if (i < row.size - 1) row[i + 1] else false
            out[i] = row[i] || prev || next
        }
        return out
    }

    fun erode(row: BooleanArray): BooleanArray {
        if (row.size < 3) return row
        val out = BooleanArray(row.size)
        for (i in 0 until row.size) {
            val prev = if (i > 0) row[i - 1] else true
            val next = if (i < row.size - 1) row[i + 1] else true
            out[i] = row[i] && prev && next
        }
        return out
    }

    /** Morphological "Closing" (Dilate then Erode). Fills gaps in black bars. */
    fun close(row: BooleanArray): BooleanArray {
        return erode(dilate(row))
    }

    /** Morphological "Opening" (Erode then Dilate). Removes speckles. */
    fun open(row: BooleanArray): BooleanArray {
        return dilate(erode(row))
    }

    /** Aggressive sharpening filter to enhance bar edges. */
    fun sharpen(row: IntArray): IntArray {
        if (row.size < 3) return row
        val out = IntArray(row.size)
        out[0] = row[0]; out[row.size-1] = row[row.size-1]
        for (i in 1 until row.size - 1) {
            // Unsharp mask: center * 2 - (prev + next) / 2
            val sharpened = row[i] * 2 - (row[i - 1] + row[i + 1]) / 2
            out[i] = sharpened.coerceIn(0, 255)
        }
        return out
    }

    /** Stretches contrast of a row to 0-255 range. */
    fun stretchContrast(row: IntArray): IntArray {
        if (row.isEmpty()) return row
        var minVal = 255; var maxVal = 0
        for (v in row) { if (v < minVal) minVal = v; if (v > maxVal) maxVal = v }
        if (maxVal <= minVal) return row
        
        val out = IntArray(row.size)
        val range = (maxVal - minVal).toDouble()
        for (i in row.indices) {
            out[i] = (((row[i] - minVal) / range) * 255).toInt()
        }
        return out
    }

    /** Smooths the row with a 3-sample moving average. */
    fun smooth(row: IntArray): IntArray {
        if (row.size < 3) return row
        val out = IntArray(row.size)
        out[0] = row[0]
        out[row.size - 1] = row[row.size - 1]
        for (i in 1 until row.size - 1) {
            out[i] = (row[i - 1] + row[i] + row[i + 1]) / 3
        }
        return out
    }

    /**
     * Specialized binarizer for electronic screens. 
     * Combines a higher threshold (to fight glow) with a 2-pixel dilation to stabilize thin bars.
     */
    fun binarizeForScreens(row: IntArray): BooleanArray {
        if (row.isEmpty()) return BooleanArray(0)
        val width = row.size
        
        // 1. Adaptive thresholding with a bias towards black (lower threshold)
        // to ignore screen glow/haze.
        val s = width / 8
        val t = 15 // threshold percentage
        val integral = LongArray(width)
        var sum: Long = 0
        for (i in 0 until width) { sum += row[i]; integral[i] = sum }
        
        val binary = BooleanArray(width)
        for (i in 0 until width) {
            val x1 = max(0, i - s / 2); val x2 = min(width - 1, i + s / 2)
            val count = x2 - x1 + 1
            val blockSum = integral[x2] - (if (x1 > 0) integral[x1 - 1] else 0)
            // Stricter check: row[i] must be significantly darker than the local average
            binary[i] = row[i].toLong() * count * 100 < blockSum * (100 - t)
        }
        
        // 2. Close to fill screen-induced gaps without thickening bars
        return close(binary)
    }

    /** 
     * Aggressive binarization specifically for high-glare screens.
     * Uses an 85% threshold to keep spaces open even when black bars "bloom".
     */
    fun binarizeHighContrast(row: IntArray): BooleanArray {
        if (row.isEmpty()) return BooleanArray(0)
        var minVal = 255; var maxVal = 0
        for (v in row) { if (v < minVal) minVal = v; if (v > maxVal) maxVal = v }
        if (maxVal - minVal < MINIMUM_DYNAMIC_RANGE) return BooleanArray(row.size)
        
        val threshold = minVal + (maxVal - minVal) * 0.85
        val out = BooleanArray(row.size)
        for (i in row.indices) out[i] = row[i] <= threshold
        return out
    }

    /** 5-sample median filter for smoother lines without blurring edges. */
    fun medianFilter5(row: IntArray): IntArray {
        if (row.size < 5) return row
        val out = IntArray(row.size)
        val window = IntArray(5)
        for (i in 2 until row.size - 2) {
            for (j in 0..4) window[j] = row[i - 2 + j]
            window.sort()
            out[i] = window[2]
        }
        // Pad edges
        out[0] = row[0]; out[1] = row[1]
        out[row.size-2] = row[row.size-2]; out[row.size-1] = row[row.size-1]
        return out
    }

    /** 2x linear upsampling. */
    fun upsample(row: IntArray): IntArray {
        if (row.isEmpty()) return row
        val out = IntArray(row.size * 2)
        for (i in 0 until row.size - 1) {
            out[i * 2] = row[i]
            out[i * 2 + 1] = (row[i] + row[i + 1]) / 2
        }
        out[out.size - 2] = row[row.size - 1]
        out[out.size - 1] = row[row.size - 1]
        return out
    }

    /** Like [binarizeHighDensity], but uses a smaller block size for high-density codes. */
    fun binarizeHighDensity(row: IntArray): BooleanArray {
        val width = row.size
        val hdBlockSize = 8
        val numBlocks = (width + hdBlockSize - 1) / hdBlockSize
        val thresholds = IntArray(numBlocks)
        for (b in 0 until numBlocks) {
            var minVal = 255; var maxVal = 0
            val start = b * hdBlockSize; val end = min(start + hdBlockSize, width)
            for (i in start until end) {
                val v = row[i]; if (v < minVal) minVal = v; if (v > maxVal) maxVal = v
            }
            thresholds[b] = if (maxVal - minVal > MINIMUM_DYNAMIC_RANGE) (minVal + maxVal) / 2 else -1
        }
        val finalThresholds = IntArray(numBlocks)
        for (b in 0 until numBlocks) {
            var sum = 0; var count = 0
            for (i in -2..2) {
                val n = b + i; if (n in 0 until numBlocks && thresholds[n] != -1) { sum += thresholds[n]; count++ }
            }
            finalThresholds[b] = if (count > 0) sum / count else 127
        }
        val out = BooleanArray(width)
        for (i in 0 until width) out[i] = row[i] <= finalThresholds[i / hdBlockSize]
        return out
    }

    /** Aggressive thinning binarizer (25% threshold) to fight blooming. */
    fun binarizeThin(row: IntArray): BooleanArray {
        if (row.isEmpty()) return BooleanArray(0)
        var minVal = 255; var maxVal = 0
        for (v in row) { if (v < minVal) minVal = v; if (v > maxVal) maxVal = v }
        if (maxVal - minVal < MINIMUM_DYNAMIC_RANGE) return BooleanArray(row.size)
        val threshold = minVal + (maxVal - minVal) * 0.25
        val out = BooleanArray(row.size)
        for (i in row.indices) out[i] = row[i] <= threshold
        return out
    }
}
