package com.enoc.sdk.scanner.core.core

/**
 * Holds the Y-plane (luminance) bytes of a camera frame plus enough geometry
 * info to read individual rows in "upright" (post-rotation) coordinates,
 * without ever allocating a rotated copy of the whole frame.
 *
 * We only ever need a handful of horizontal rows per frame, so we resolve
 * rotation lazily, per requested row, instead of transforming the whole image.
 */
class LuminanceFrame(
    private val yBytes: ByteArray,
    private val srcWidth: Int,
    private val srcHeight: Int,
    private val rowStride: Int,
    private val pixelStride: Int,
    private val rotationDegrees: Int
) {
    /** Width of the frame once rotation is applied (i.e. "upright" width). */
    val width: Int = if (rotationDegrees == 90 || rotationDegrees == 270) srcHeight else srcWidth

    /** Height of the frame once rotation is applied. */
    val height: Int = if (rotationDegrees == 90 || rotationDegrees == 270) srcWidth else srcHeight

    /**
     * Returns one horizontal row (length == [width]) of grayscale (0-255) values,
     * in upright coordinates, where row 0 is the top of the image as the user sees it.
     */
    fun getRow(y: Int): IntArray {
        val out = IntArray(width)
        when (rotationDegrees) {
            0 -> {
                val base = y * rowStride
                for (x in 0 until width) {
                    out[x] = yBytes[base + x * pixelStride].toInt() and 0xFF
                }
            }
            180 -> {
                val sy = srcHeight - 1 - y
                val base = sy * rowStride
                for (x in 0 until width) {
                    val sx = srcWidth - 1 - x
                    out[x] = yBytes[base + sx * pixelStride].toInt() and 0xFF
                }
            }
            90 -> {
                // upright(x, y) = source(y, srcHeight - 1 - x)
                val sx = y
                for (x in 0 until width) {
                    val sy = srcHeight - 1 - x
                    out[x] = yBytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
                }
            }
            270 -> {
                // upright(x, y) = source(srcWidth - 1 - y, x)
                val sx = srcWidth - 1 - y
                for (x in 0 until width) {
                    val sy = x
                    out[x] = yBytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
                }
            }
            else -> throw IllegalArgumentException("Unsupported rotation: $rotationDegrees")
        }
        return out
    }

    /** Returns one vertical column (length == [height]) in upright coordinates. */
    fun getColumn(x: Int): IntArray {
        val out = IntArray(height)
        for (y in 0 until height) {
            // This is inefficient but clear; a better way is to inline the coordinate math
            val pixel = when (rotationDegrees) {
                0 -> yBytes[y * rowStride + x * pixelStride].toInt() and 0xFF
                180 -> {
                    val sy = srcHeight - 1 - y
                    val sx = srcWidth - 1 - x
                    yBytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
                }
                90 -> {
                    val sx = y
                    val sy = srcHeight - 1 - x
                    yBytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
                }
                270 -> {
                    val sx = srcWidth - 1 - y
                    val sy = x
                    yBytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
                }
                else -> 0
            }
            out[y] = pixel
        }
        return out
    }

    /** 
     * Returns a diagonal row from top-left-ish to bottom-right-ish or vice versa.
     * [slope] 1.0 = 45 degrees, -1.0 = -45 degrees.
     */
    fun getDiagonal(slope: Float): IntArray {
        // Simplified: just scan the main diagonal for now to see if it helps
        val length = minOf(width, height)
        val out = IntArray(length)
        for (i in 0 until length) {
            val x = i
            val y = if (slope > 0) i else height - 1 - i
            
            // upright(x, y) coordinate mapping:
            out[i] = when (rotationDegrees) {
                0 -> yBytes[y * rowStride + x * pixelStride].toInt() and 0xFF
                180 -> yBytes[(srcHeight - 1 - y) * rowStride + (srcWidth - 1 - x) * pixelStride].toInt() and 0xFF
                90 -> yBytes[(srcHeight - 1 - x) * rowStride + y * pixelStride].toInt() and 0xFF
                270 -> yBytes[x * rowStride + (srcWidth - 1 - y) * pixelStride].toInt() and 0xFF
                else -> 0
            }
        }
        return out
    }
}
