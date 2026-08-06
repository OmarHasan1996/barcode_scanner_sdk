package com.enoc.sdk.scanner.core.core

import kotlin.math.max
import kotlin.math.min

/**
 * Turns a grayscale row into a black/white row using a hybrid local thresholding
 * approach. This handles uneven lighting and low contrast much better than
 * global Otsu thresholding.
 */
object RowBinarizer {

    private const val BLOCK_SIZE = 16
    private const val MINIMUM_DYNAMIC_RANGE = 24

    /** true == "black" (bar), false == "white" (space) */
    fun binarize(row: IntArray): BooleanArray {
        val width = row.size
        val numBlocks = (width + BLOCK_SIZE - 1) / BLOCK_SIZE
        val thresholds = IntArray(numBlocks)

        // 1. Calculate min/max and initial threshold for each block
        for (b in 0 until numBlocks) {
            var minVal = 255
            var maxVal = 0
            val start = b * BLOCK_SIZE
            val end = min(start + BLOCK_SIZE, width)
            
            for (i in start until end) {
                val v = row[i]
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }

            if (maxVal - minVal > MINIMUM_DYNAMIC_RANGE) {
                thresholds[b] = (minVal + maxVal) / 2
            } else {
                // Low contrast block - will be smoothed in next step
                thresholds[b] = -1 
            }
        }

        // 2. Smooth thresholds and fill low-contrast blocks
        val finalThresholds = IntArray(numBlocks)
        for (b in 0 until numBlocks) {
            var sum = 0
            var count = 0
            // Average over 5 blocks (self + 2 on each side)
            for (i in -2..2) {
                val neighbor = b + i
                if (neighbor in 0 until numBlocks) {
                    val t = thresholds[neighbor]
                    if (t != -1) {
                        sum += t
                        count++
                    }
                }
            }
            finalThresholds[b] = if (count > 0) sum / count else 127
        }

        // 3. Apply thresholds
        val out = BooleanArray(width)
        for (i in 0 until width) {
            val b = i / BLOCK_SIZE
            out[i] = row[i] <= finalThresholds[b]
        }
        return out
    }
}
