package com.enoc.sdk.scanner.core.decoder

import android.util.Log
import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult
import kotlin.math.abs
import kotlin.math.roundToInt

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

            val startValue = matchSymbol(window, unit, 0.8) ?: continue
            val startScore = PatternMatcher.score(window, PatternTables.CODE128_PATTERNS[startValue], unit)

            if (startValue != PatternTables.CODE128_START_A &&
                startValue != PatternTables.CODE128_START_B &&
                startValue != PatternTables.CODE128_START_C
            ) continue

            Log.d("Code128Decoder", "Potential Start found: value=$startValue at idx=$startIdx, unit=$unit, score=$startScore")
            val result = tryDecodeFrom(runs, startIdx, startValue, unit)
            if (result != null) return result
            
            // If it failed, log the surrounding runs to see why
            if (startIdx + 20 < runs.size) {
                val context = runs.copyOfRange(startIdx, minOf(startIdx + 50, runs.size))
                Log.d("Code128Decoder", "Full Row Context from Start: ${context.joinToString(",")}")
            }
        }
        return null
    }

    private fun matchSymbol(window: IntArray, unit: Double, maxScore: Double = 1.2): Int? {
        // Quantization makes small units very noisy. Increase tolerance for thin bars.
        val adjustedMaxScore = if (unit < 1.5) maxScore * 1.5 else maxScore
        
        var bestIdx = PatternMatcher.bestMatchIndex(window, PatternTables.CODE128_PATTERNS, unit, maxScore = adjustedMaxScore)
        
        // Fallback: Ratio-based matching (independent of unit, handles distortion better)
        if (bestIdx == -1) {
            bestIdx = matchSymbolByRatios(window, adjustedMaxScore * 0.1)
        }
        
        return if (bestIdx >= 0) bestIdx else null
    }

    private fun matchSymbolByRatios(window: IntArray, maxScore: Double): Int {
        val totalObserved = window.sum().toDouble()
        if (totalObserved == 0.0) return -1
        
        var bestIdx = -1
        var bestScore = maxScore
        
        for (i in PatternTables.CODE128_PATTERNS.indices) {
            val ideal = PatternTables.CODE128_PATTERNS[i]
            var score = 0.0
            for (j in window.indices) {
                val observedRatio = window[j] / totalObserved
                val idealRatio = ideal[j] / 11.0
                val diff = observedRatio - idealRatio
                score += diff * diff
            }
            if (score < bestScore) {
                bestScore = score
                bestIdx = i
            }
        }
        return bestIdx
    }

    private fun tryDecodeFrom(runs: IntArray, startIdx: Int, startValue: Int, initialUnit: Double): BarcodeResult? {
        var idx = startIdx + 6
        var unitSum = initialUnit
        var unitCount = 1

        val values = ArrayList<Int>()

        while (idx + 6 <= runs.size) {
            val avgUnit = unitSum / unitCount
            val currentWindow = runs.copyOfRange(idx, idx + 6)
            val currentUnit = currentWindow.sum() / 11.0

            // 1. Check for wild unit drift (max 30% change from average)
            if (abs(currentUnit - avgUnit) > avgUnit * 0.35) {
                Log.v("Code128Decoder", "Rejected symbol: unit drift too high ($currentUnit vs avg $avgUnit)")
                return null
            }

            // 2. Parity check: sum of bar-modules MUST be even for Code 128
            val barModules = (currentWindow[0] + currentWindow[2] + currentWindow[4]) / currentUnit
            if (barModules.roundToInt() % 2 != 0) {
                Log.v("Code128Decoder", "Rejected symbol: parity check failed")
                return null
            }

            // Check for Stop before assuming another data/checksum symbol follows.
            if (idx + 7 <= runs.size) {
                val stopWindow = runs.copyOfRange(idx, idx + 7)
                val stopUnit = stopWindow.sum() / 13.0
                
                // Stop pattern check must be relatively strict to avoid early termination.
                // We try matching with both currentUnit and avgUnit.
                if (PatternMatcher.matches(stopWindow, PatternTables.CODE128_STOP, stopUnit, maxScore = 0.8) ||
                    PatternMatcher.matches(stopWindow, PatternTables.CODE128_STOP, avgUnit, maxScore = 0.8)) {
                    
                    // Quiet zone after Stop
                    if (idx + 7 < runs.size) {
                        val quietZone = runs[idx + 7]
                        if (quietZone > 3 * stopUnit) {
                            return finish(startValue, values)
                        }
                    } else {
                        return finish(startValue, values)
                    }
                }
            }

            // Try matching with both the avgUnit and the current symbol's unit.
            val value = matchSymbol(currentWindow, currentUnit, 1.2)
                ?: matchSymbol(currentWindow, avgUnit, 1.2)
            
            if (value == null) {
                if (values.size > 0) {
                    Log.v("Code128Decoder", "Symbol match failed at idx=$idx, after ${values.size} symbols. Units: avg=$avgUnit, current=$currentUnit")
                    Log.v("Code128Decoder", "Raw failing window runs: ${currentWindow.joinToString(",")}")
                }
                return null
            }
            
            values.add(value)
            idx += 6
            unitSum += currentUnit
            unitCount++
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
