package com.enoc.sdk.scanner.core.decoder

import android.util.Log
import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult

/**
 * Decodes Code128 (code sets A, B, and C, with set-switching) from a scanned row.
 *
 * Each symbol = 6 runs = 11 modules. Structure: Start symbol, N data symbols,
 * 1 checksum symbol, Stop pattern (7 runs / 13 modules).
 *
 * NOTE: FNC1-4 are treated as no-ops here (skipped) rather than mapped to
 * their full GS1/extended-ASCII semantics. Fine for plain alphanumeric/numeric
 * payloads; extend CODE_SET_A/B mapping if you need those control functions.
 */
class Code128Decoder : Decoder {

    override fun decode(rowRuns: RowRuns): BarcodeResult? {
        val runs = rowRuns.runs
        val startsBlack = rowRuns.startsBlack

        for (startIdx in 0 until runs.size - 6) {
            val runColorIsBlack = if (startIdx % 2 == 0) startsBlack else !startsBlack
            if (!runColorIsBlack) continue

            val window = runs.copyOfRange(startIdx, startIdx + 6)
            val unit = window.sum() / 11.0
            if (unit < 1.0) continue

            // Quiet zone check: at least 10 modules of white space before the start pattern.
            // If startIdx is 0, we might be at the very edge of the frame, which is risky.
            if (startIdx > 0) {
                val quietZoneWidth = runs[startIdx - 1]
                if (quietZoneWidth < 6 * unit) { // Be slightly lenient (standard is 10)
                    continue
                }
            } else {
                // If the barcode starts at the very first run, we have no quiet zone to verify.
                // In many cases, this is just noise at the image edge.
                continue
            }

            val startValueRaw = matchSymbol(window, unit, 0.8) ?: continue
            // Value 102 (FNC1) and 105 (Start C) share the same pattern. 
            // If it's the very first symbol, it's a Start C.
            val startValue = if (startValueRaw == 102) 105 else startValueRaw

            if (startValue != PatternTables.CODE128_START_A &&
                startValue != PatternTables.CODE128_START_B &&
                startValue != PatternTables.CODE128_START_C
            ) continue

            Log.d("Code128Decoder", "Potential Start found: value=$startValue at idx=$startIdx, unit=$unit, score=${PatternMatcher.score(window, PatternTables.CODE128_PATTERNS[startValue], unit)}")
            val result = tryDecodeFrom(runs, startIdx, startValue, unit)
            if (result != null)
                return result
        }
        return null
    }

    private fun matchSymbol(window: IntArray, unit: Double, maxScore: Double = 1.2): Int? {
        val idx = PatternMatcher.bestMatchIndex(window, PatternTables.CODE128_PATTERNS, unit, maxScore = maxScore)
        return if (idx >= 0) idx else null
    }

    private fun tryDecodeFrom(runs: IntArray, startIdx: Int, startValue: Int, initialUnit: Double): BarcodeResult? {
        var idx = startIdx + 6
        var lastUnit = initialUnit

        // Collect raw symbol values first; we only know which one is the checksum
        // once we've located Stop, so nothing gets interpreted into text yet.
        val values = ArrayList<Int>()

        while (idx + 6 <= runs.size) {
            val currentWindow = runs.copyOfRange(idx, idx + 6)
            val currentUnit = currentWindow.sum() / 11.0

            // Check for Stop before assuming another data/checksum symbol follows.
            if (idx + 7 <= runs.size) {
                val stopWindow = runs.copyOfRange(idx, idx + 7)
                val stopUnit = stopWindow.sum() / 13.0
                if (PatternMatcher.matches(stopWindow, PatternTables.CODE128_STOP, stopUnit, maxScore = 1.0)) {
                    // Check for trailing quiet zone
                    if (idx + 7 < runs.size) {
                        val quietZone = runs[idx + 7]
                        if (quietZone > 4 * stopUnit) {
                            return finish(startValue, values)
                        }
                    } else {
                        return finish(startValue, values)
                    }
                }
            }

            // Try matching with both the unit from the previous symbol AND the current symbol's unit.
            // This helps if the barcode is distorted or if binarization slightly changed the width.
            val value = matchSymbol(currentWindow, currentUnit, 1.2)
                ?: matchSymbol(currentWindow, lastUnit, 1.2)
                ?: run {
                    if (values.size > 0) {
                        Log.v("Code128Decoder", "Symbol match failed at idx=$idx, after ${values.size} symbols. Units: last=$lastUnit, current=$currentUnit")
                    }
                    return null
                }
            
            values.add(value)
            idx += 6
            lastUnit = currentUnit
        }
        Log.v("Code128Decoder", "Ran out of runs at idx=$idx, values=${values.size}")
        return null // ran out of runs before finding Stop
    }

    /** [values] = all data symbols including the trailing checksum symbol, in order. */
    private fun finish(startValue: Int, values: List<Int>): BarcodeResult? {
        if (values.isEmpty()) return null

        val checksumSymbol = values.last()
        val dataValues = values.subList(0, values.size - 1)

        var checksum = startValue
        for ((i, v) in dataValues.withIndex()) {
            checksum += v * (i + 1)
        }
        if (checksum % 103 != checksumSymbol) {
            Log.d("Code128Decoder", "Checksum FAILED: expected ${checksum % 103}, got $checksumSymbol. Values=$dataValues")
            return null
        }

        var currentSet = when (startValue) {
            PatternTables.CODE128_START_A -> 'A'
            PatternTables.CODE128_START_B -> 'B'
            else -> 'C'
        }

        val output = StringBuilder()
        for (v in dataValues) {
            when {
                v == 101 && currentSet != 'A' -> currentSet = 'A'
                v == 100 && currentSet != 'B' -> currentSet = 'B'
                v == 99 && currentSet != 'C' -> currentSet = 'C'
                v == 102 -> { /* FNC1 - skip */ }
                else -> appendSymbol(output, v, currentSet)
            }
        }
        if (output.isEmpty()) return null

        return BarcodeResult(text = output.toString(), format = BarcodeFormat.CODE_128)
    }

    private fun appendSymbol(sb: StringBuilder, value: Int, set: Char) {
        when (set) {
            'B' -> if (value in 0..95) sb.append((value + 32).toChar())
            'A' -> when (value) {
                in 0..63 -> sb.append((value + 32).toChar())
                in 64..95 -> sb.append((value - 64).toChar()) // control chars
                else -> { /* function codes handled by caller */ }
            }
            'C' -> if (value in 0..99) sb.append(value.toString().padStart(2, '0'))
        }
    }
}
