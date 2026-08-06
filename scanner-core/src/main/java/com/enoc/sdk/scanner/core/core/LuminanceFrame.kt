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
}
