package com.enoc.sdk.scanner.core.core

import kotlin.math.*

/**
 * Holds the Y-plane (luminance) bytes of a camera frame plus enough geometry
 * info to read individual rows in "upright" (post-rotation) coordinates,
 * without ever allocating a rotated copy of the whole frame.
 *
 * Supports arbitrary rotation degrees. Standard 90-degree increments are highly optimized.
 */
class LuminanceFrame(
    private val yBytes: ByteArray,
    private val srcWidth: Int,
    private val srcHeight: Int,
    private val rowStride: Int,
    private val pixelStride: Int,
    private val rotationDegrees: Int
) {
    private val radians = Math.toRadians(rotationDegrees.toDouble())
    private val cosR = cos(radians)
    private val sinR = sin(radians)

    /** Width of the frame once rotation is applied. */
    val width: Int = if (rotationDegrees % 90 == 0) {
        if (rotationDegrees % 180 == 0) srcWidth else srcHeight
    } else {
        (abs(srcWidth * cosR) + abs(srcHeight * sinR)).toInt()
    }

    /** Height of the frame once rotation is applied. */
    val height: Int = if (rotationDegrees % 90 == 0) {
        if (rotationDegrees % 180 == 0) srcHeight else srcWidth
    } else {
        (abs(srcWidth * sinR) + abs(srcHeight * cosR)).toInt()
    }

    private val cx = width / 2.0
    private val cy = height / 2.0
    private val scx = srcWidth / 2.0
    private val scy = srcHeight / 2.0

    /**
     * Returns one horizontal row (length == [width]) of grayscale (0-255) values,
     * in upright coordinates.
     */
    fun getRow(y: Int): IntArray {
        val out = IntArray(width)
        
        // Fast paths for standard rotations
        when (rotationDegrees % 360) {
            0 -> {
                val base = y * rowStride
                for (x in 0 until width) {
                    out[x] = yBytes[base + x * pixelStride].toInt() and 0xFF
                }
                return out
            }
            90 -> {
                val sx = y
                val base = sx * pixelStride
                for (x in 0 until width) {
                    val sy = srcHeight - 1 - x
                    out[x] = yBytes[sy * rowStride + base].toInt() and 0xFF
                }
                return out
            }
            180 -> {
                val sy = srcHeight - 1 - y
                val base = sy * rowStride
                for (x in 0 until width) {
                    val sx = srcWidth - 1 - x
                    out[x] = yBytes[base + sx * pixelStride].toInt() and 0xFF
                }
                return out
            }
            270 -> {
                val sx = srcWidth - 1 - y
                val base = sx * pixelStride
                for (x in 0 until width) {
                    val sy = x
                    out[x] = yBytes[sy * rowStride + base].toInt() and 0xFF
                }
                return out
            }
        }

        // Generic path for arbitrary rotation
        for (x in 0 until width) {
            out[x] = pixelAt(x, y)
        }
        return out
    }

    /** Returns one vertical column (length == [height]) in upright coordinates. */
    fun getColumn(x: Int): IntArray {
        val out = IntArray(height)
        
        // Fast paths for standard rotations
        when (rotationDegrees % 360) {
            0 -> {
                val xOffset = x * pixelStride
                for (y in 0 until height) {
                    out[y] = yBytes[y * rowStride + xOffset].toInt() and 0xFF
                }
                return out
            }
            90 -> {
                val sy = srcHeight - 1 - x
                val base = sy * rowStride
                for (y in 0 until height) {
                    val sx = y
                    out[y] = yBytes[base + sx * pixelStride].toInt() and 0xFF
                }
                return out
            }
            180 -> {
                val sx = srcWidth - 1 - x
                val xOffset = sx * pixelStride
                for (y in 0 until height) {
                    val sy = srcHeight - 1 - y
                    out[y] = yBytes[sy * rowStride + xOffset].toInt() and 0xFF
                }
                return out
            }
            270 -> {
                val sy = x
                val base = sy * rowStride
                for (y in 0 until height) {
                    val sx = srcWidth - 1 - y
                    out[y] = yBytes[base + sx * pixelStride].toInt() and 0xFF
                }
                return out
            }
        }

        // Generic path for arbitrary rotation
        for (y in 0 until height) {
            out[y] = pixelAt(x, y)
        }
        return out
    }

    /**
     * Extracts a line of pixels from (x1, y1) to (x2, y2) in upright coordinates.
     */
    fun getLine(x1: Int, y1: Int, x2: Int, y2: Int): IntArray {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = sqrt((dx * dx + dy * dy).toDouble()).toInt()
        val out = IntArray(length)
        
        if (length == 0) return out
        
        for (i in 0 until length) {
            val t = i.toDouble() / length
            val px = (x1 + t * dx).toInt()
            val py = (y1 + t * dy).toInt()
            out[i] = pixelAt(px, py)
        }
        return out
    }

    fun pixelAt(x: Int, y: Int): Int {
        val dx = x - cx
        val dy = y - cy
        val sx = (dx * cosR + dy * sinR + scx).toInt()
        val sy = (-dx * sinR + dy * cosR + scy).toInt()
        
        return if (sx in 0 until srcWidth && sy in 0 until srcHeight) {
            yBytes[sy * rowStride + sx * pixelStride].toInt() and 0xFF
        } else {
            0
        }
    }
}
