package com.enoc.sdk.scanner.core.decoder

/**
 * Standard, publicly-specified symbology pattern tables (ISO/IEC 15420 for
 * EAN/UPC, ISO/IEC 15417 for Code128). These are numeric specification
 * tables, not anyone's original expression.
 */
object PatternTables {

    // ---- EAN-13 / UPC-A ----
    private val L_CODE_BITS = arrayOf(
        "0001101", "0011001", "0010011", "0111101", "0100011",
        "0110001", "0101111", "0111011", "0110111", "0001011"
    )

    private val R_CODE_BITS = L_CODE_BITS.map { bits ->
        bits.map { if (it == '1') '0' else '1' }.joinToString("")
    }

    private val G_CODE_BITS = R_CODE_BITS.map { it.reversed() }

    val FIRST_DIGIT_PARITY = arrayOf(
        "LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG",
        "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL"
    )

    private fun bitsToRuns(bits: String): IntArray {
        val runs = ArrayList<Int>()
        var current = bits[0]
        var count = 1
        for (i in 1 until bits.length) {
            if (bits[i] == current) count++ else {
                runs.add(count); current = bits[i]; count = 1
            }
        }
        runs.add(count)
        return runs.toIntArray()
    }

    val L_CODE_RUNS: Array<IntArray> = L_CODE_BITS.map { bitsToRuns(it) }.toTypedArray()
    val G_CODE_RUNS: Array<IntArray> = G_CODE_BITS.map { bitsToRuns(it) }.toTypedArray()
    val R_CODE_RUNS: Array<IntArray> = R_CODE_BITS.map { bitsToRuns(it) }.toTypedArray()

    val GUARD_START_END = intArrayOf(1, 1, 1)
    val GUARD_MIDDLE = intArrayOf(1, 1, 1, 1, 1)

    // ---- Code128 ----
    // 106 symbol values (0-105). 
    // Indices 103, 104, 105 are Start A, Start B, Start C.
    val CODE128_PATTERNS: Array<IntArray> = arrayOf(
        intArrayOf(2,1,2,2,2,2), intArrayOf(2,2,2,1,2,2), intArrayOf(2,2,2,2,2,1),
        intArrayOf(1,2,1,2,2,3), intArrayOf(1,2,1,3,2,2), intArrayOf(1,3,1,2,2,2),
        intArrayOf(1,2,2,2,1,3), intArrayOf(1,2,2,3,1,2), intArrayOf(1,3,2,2,1,2),
        intArrayOf(2,2,1,2,1,3), intArrayOf(2,2,1,3,1,2), intArrayOf(2,3,1,2,1,2),
        intArrayOf(1,1,2,2,3,2), intArrayOf(1,2,2,1,3,2), intArrayOf(1,2,2,2,3,1),
        intArrayOf(1,1,3,2,2,2), intArrayOf(1,2,3,1,2,2), intArrayOf(1,2,3,2,2,1),
        intArrayOf(2,2,3,2,1,1), intArrayOf(2,2,1,1,3,2), intArrayOf(2,2,1,2,3,1),
        intArrayOf(2,1,3,2,1,2), intArrayOf(2,2,3,1,1,2), intArrayOf(3,1,2,1,3,1),
        intArrayOf(3,1,1,2,2,2), intArrayOf(3,2,1,1,2,2), intArrayOf(3,2,1,2,2,1),
        intArrayOf(3,1,2,2,1,2), intArrayOf(3,2,2,1,1,2), intArrayOf(3,2,2,2,1,1),
        intArrayOf(2,1,2,1,2,3), intArrayOf(2,1,2,3,2,1), intArrayOf(2,3,2,1,2,1),
        intArrayOf(1,1,1,3,2,3), intArrayOf(1,3,1,1,2,3), intArrayOf(1,3,1,3,2,1),
        intArrayOf(1,1,2,3,1,3), intArrayOf(1,3,2,1,1,3), intArrayOf(1,3,2,3,1,1),
        intArrayOf(2,1,1,3,1,3), intArrayOf(2,3,1,1,1,3), intArrayOf(2,3,1,3,1,1),
        intArrayOf(1,1,2,1,3,3), intArrayOf(1,1,2,3,3,1), intArrayOf(1,3,2,1,3,1),
        intArrayOf(1,1,3,1,2,3), intArrayOf(1,1,3,3,2,1), intArrayOf(1,3,3,1,2,1),
        intArrayOf(3,1,3,1,2,1), intArrayOf(2,1,1,3,3,1), intArrayOf(2,3,1,1,3,1),
        intArrayOf(2,1,3,1,1,3), intArrayOf(2,1,3,3,1,1), intArrayOf(2,1,3,1,3,1),
        intArrayOf(3,1,1,1,2,3), intArrayOf(3,1,1,3,2,1), intArrayOf(3,3,1,1,2,1),
        intArrayOf(3,1,2,1,1,3), intArrayOf(3,1,2,3,1,1), intArrayOf(3,3,2,1,1,1),
        intArrayOf(3,1,4,1,1,1), intArrayOf(2,2,1,4,1,1), intArrayOf(4,3,1,1,1,1),
        intArrayOf(1,1,1,2,2,4), intArrayOf(1,1,1,4,2,2), intArrayOf(1,2,1,1,2,4),
        intArrayOf(1,2,1,4,2,1), intArrayOf(1,4,1,1,2,2), intArrayOf(1,4,1,2,2,1),
        intArrayOf(1,1,2,2,1,4), intArrayOf(1,1,2,4,1,2), intArrayOf(1,2,2,1,1,4),
        intArrayOf(1,2,2,4,1,1), intArrayOf(1,4,2,1,1,2), intArrayOf(1,4,2,2,1,1),
        intArrayOf(2,4,1,2,1,1), intArrayOf(2,2,1,1,1,4), intArrayOf(4,1,3,1,1,1),
        intArrayOf(2,4,1,1,1,2), intArrayOf(1,3,4,1,1,1), intArrayOf(1,1,1,2,4,2),
        intArrayOf(1,2,1,1,4,2), intArrayOf(1,2,1,2,4,1), intArrayOf(1,1,4,2,1,2),
        intArrayOf(1,2,4,1,1,2), intArrayOf(1,2,4,2,1,1), intArrayOf(4,1,1,2,1,2),
        intArrayOf(4,2,1,1,1,2), intArrayOf(4,2,1,2,1,1), intArrayOf(2,1,2,1,4,1),
        intArrayOf(2,1,4,1,2,1), intArrayOf(4,1,2,1,2,1), intArrayOf(1,1,1,1,4,3),
        intArrayOf(1,1,1,3,4,1), intArrayOf(1,3,1,1,4,1), intArrayOf(1,1,4,1,1,3),
        intArrayOf(1,1,4,3,1,1), intArrayOf(4,1,1,1,1,3), intArrayOf(4,1,1,3,1,1),
        intArrayOf(1,1,3,1,4,1), intArrayOf(1,1,4,1,3,1), intArrayOf(3,1,1,1,4,1),
        intArrayOf(4,1,1,1,3,1),
        intArrayOf(2,1,1,4,1,2), // 103: Start A
        intArrayOf(2,1,1,2,1,4), // 104: Start B
        intArrayOf(2,1,1,2,3,2)  // 105: Start C
    )

    val CODE128_STOP = intArrayOf(2, 3, 3, 1, 1, 1, 2)

    const val CODE128_START_A = 103
    const val CODE128_START_B = 104
    const val CODE128_START_C = 105
    const val CODE128_STOP_VALUE = 106
}
