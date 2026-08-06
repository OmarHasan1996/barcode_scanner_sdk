package com.enoc.sdk.scanner.core.core

import kotlin.math.max
import kotlin.math.min

object RowBinarizer {
    private const val BLOCK_SIZE = 16
    private const val MINIMUM_DYNAMIC_RANGE = 20

    /** Increases row resolution 2x to help find narrow gaps. */
    fun upsample(row: IntArray): IntArray {
        val out = IntArray(row.size * 2)
        for (i in 0 until row.size - 1) {
            out[i * 2] = row[i]
            out[i * 2 + 1] = (row[i] + row[i+1]) / 2
        }
        out[out.size - 2] = row[row.size - 1]
        out[out.size - 1] = row[row.size - 1]
        return out
    }

    /** Tiny blocks to find high-density bars on glowing screens. */
    fun binarizeHighDensity(row: IntArray): BooleanArray {
        val width = row.size; val out = BooleanArray(width)
        val blockSize = 4
        for (i in 0 until width step blockSize) {
            val end = min(i + blockSize, width)
            var minV = 255; var maxV = 0
            for (j in i until end) {
                if (row[j] < minV) minV = row[j]
                if (row[j] > maxV) maxV = row[j]
            }
            val thresh = (minV + maxV) / 2
            for (j in i until end) out[j] = row[j] <= thresh
        }
        return out
    }

    fun smooth(row: IntArray): IntArray {
        if (row.size < 3) return row
        val out = IntArray(row.size)
        out[0] = row[0]; out[row.size - 1] = row[row.size - 1]
        for (i in 1 until row.size - 1) out[i] = (row[i-1] + row[i] + row[i+1]) / 3
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
                val n = b + i; if (neighborInBounds(n, numBlocks) && thresholds[n] != -1) { sum += thresholds[n]; count++ }
            }
            finalThresholds[b] = if (count > 0) sum / count else 127
        }
        val out = BooleanArray(width)
        for (i in 0 until width) out[i] = row[i] <= finalThresholds[i / BLOCK_SIZE]
        return out
    }

    private fun neighborInBounds(n: Int, total: Int) = n in 0 until total

    fun binarizeThin(row: IntArray): BooleanArray {
        val histogram = IntArray(256); for (v in row) histogram[v]++
        var sum = 0; var threshold = 50
        for (i in 0..255) { sum += histogram[i]; if (sum > row.size * 0.15) { threshold = i; break } }
        val out = BooleanArray(row.size)
        for (i in row.indices) out[i] = row[i] <= threshold
        return out
    }
}
