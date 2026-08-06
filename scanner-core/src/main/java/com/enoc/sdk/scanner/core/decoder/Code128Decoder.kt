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
        val result = decodeInternal(rowRuns)
        if (result != null) return result
        
        // Code 128 is NOT symmetric. If we are scanning a row from right-to-left
        // (which happens in vertical or diagonal passes depending on orientation),
        // we must manually reverse the runs and try again.
        val reversedRuns = rowRuns.runs.reversedArray()
        // Note: if the original row started with Black, the reversed row 
        // starts with whatever the LAST run's color was.
        val lastRunWasBlack = if (rowRuns.runs.size % 2 == 1) rowRuns.startsBlack else !rowRuns.startsBlack
        
        return decodeInternal(RowRuns(reversedRuns, lastRunWasBlack))
    }

    private fun decodeInternal(rowRuns: RowRuns): BarcodeResult? {
        val runs = rowRuns.runs
        val startsBlack = rowRuns.startsBlack

        // CODE 128 Optimization: The "Yes" card barcode is usually centered. 
        // We look for the start pattern (3-run sequence 103, 104, 105) but we must 
        // be very careful about the quiet zone.
        if (runs.size < 20) return null // Minimum runs for any valid Code 128

        for (startIdx in 0 until runs.size - 13) { // Must have at least Start + Checksum + Stop
            val runColorIsBlack = if (startIdx % 2 == 0) startsBlack else !startsBlack
            if (!runColorIsBlack) continue

            val window = runs.copyOfRange(startIdx, startIdx + 6)
            val unit = window.sum() / 11.0
            if (unit < 0.8 || unit > 15.0) continue // Limit to reasonable phone screen modules

            // Quiet zone check: at least 10 modules of white space before the start pattern.
            if (startIdx > 0) {
                val quietZoneWidth = runs[startIdx - 1]
                // For large barcodes (high unit), noise can break the quiet zone. 
                // Be slightly more lenient if the unit is high.
                val minQuietZone = if (unit < 3.0) 10 * unit else 7 * unit
                if (quietZoneWidth < minQuietZone) {
                    continue
                }
            } else {
                continue
            }

            val startValue = matchSymbol(window, unit, 0.4, 0.02) ?: continue
            val startScore = PatternMatcher.scoreHybrid(window, PatternTables.CODE128_PATTERNS[startValue], unit)

            if (startValue != PatternTables.CODE128_START_A &&
                startValue != PatternTables.CODE128_START_B &&
                startValue != PatternTables.CODE128_START_C
            ) continue

            Log.d("Code128Decoder", "Potential Start found: value=$startValue at idx=$startIdx, unit=${"%.2f".format(unit)}, score=${"%.3f".format(startScore)}")
            
            // Try decoding with a dense sweep of unit sizes.
            // On bright screens, the initial unit (from 6 runs) can be off by 15-20% due to aliasing.
            val sweepRatios = doubleArrayOf(1.0, 0.95, 1.05, 0.9, 1.1, 0.85, 1.15, 0.8, 1.2)
            for (ratio in sweepRatios) {
                val result = tryDecodeFrom(runs, startIdx, startValue, unit * ratio)
                if (result != null) return result
            }
            
            // If it failed, log the surrounding runs to see why
            if (startIdx + 20 < runs.size) {
                val context = runs.copyOfRange(startIdx, minOf(startIdx + 50, runs.size))
                Log.d("Code128Decoder", "Full Row Context from Start: ${context.joinToString(",")}")
            }
        }
        return null
    }

    private fun matchSymbol(window: IntArray, unit: Double, maxScore: Double = 0.8, ratioMaxScore: Double = 0.02): Int? {
        // Quantization makes small units very noisy. Increase tolerance for thin bars.
        val adjustedMaxScore = if (unit < 1.5) maxScore * 1.5 else maxScore
        
        var bestIdx = PatternMatcher.bestMatchIndex(window, PatternTables.CODE128_PATTERNS, unit, maxScore = adjustedMaxScore)
        
        // Fallback: Ratio-based matching (independent of unit, handles distortion better)
        if (bestIdx == -1) {
            // Be very strict with ratios as they can match noise easily.
            bestIdx = matchSymbolByRatios(window, ratioMaxScore)
            if (bestIdx != -1) {
                Log.v("Code128Decoder", "Match found via RATIOS fallback: val=$bestIdx, unit=${"%.2f".format(unit)}")
            }
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
        var rollingAvgUnit = initialUnit
        var unitCount = 1

        val values = ArrayList<Int>()

        while (idx + 6 <= runs.size) {
            val currentWindow = runs.copyOfRange(idx, idx + 6)
            val currentUnit = currentWindow.sum() / 11.0

            // Try matching with both the rollingAvgUnit and the current symbol's unit.
            // We use a high tolerance (1.5) because Hybrid scoring (T-pattern) is safer.
            val value = matchSymbol(currentWindow, currentUnit, 1.5)
                ?: matchSymbol(currentWindow, rollingAvgUnit, 1.5)
            
            val matchScore = if (value != null) {
                PatternMatcher.scoreHybrid(currentWindow, PatternTables.CODE128_PATTERNS[value], currentUnit)
            } else 2.0

            // Log detailed symbol attempt for diagnostic purposes
            if (value != null) {
                Log.v("Code128Decoder", "Symbol candidate: val=$value, unit=${"%.2f".format(currentUnit)}, rolling=${"%.2f".format(rollingAvgUnit)}, score=${"%.3f".format(matchScore)} | Window: ${currentWindow.joinToString(",")}")
            } else {
                Log.v("Code128Decoder", "Symbol match FAILED at idx=$idx, unit=${"%.2f".format(currentUnit)} | Window: ${currentWindow.joinToString(",")}")
            }

            // 1. Check for wild unit drift (max 75-90% change from rolling average for extreme perspective)
            val maxAllowedDrift = if (matchScore < 0.1) 0.9 else 0.75
            if (abs(currentUnit - rollingAvgUnit) > rollingAvgUnit * maxAllowedDrift) {
                // If it's an EXCELLENT match (score < 0.15), we accept it despite the drift.
                // This handles extreme perspective or lens distortion on screens.
                if (matchScore > 0.15) {
                    // Before giving up, see if this is actually the STOP pattern (which is 13 units, not 11)
                    if (isStopPattern(runs, idx, rollingAvgUnit)) {
                        // Quiet zone after Stop
                        val stopUnit = runs.copyOfRange(idx, idx + 7).sum() / 13.0
                        if (idx + 7 < runs.size) {
                            val quietZone = runs[idx + 7]
                            if (quietZone > 2.0 * stopUnit) {
                                return finish(startValue, values)
                            }
                        } else {
                            return finish(startValue, values)
                        }
                    }
                    
                    Log.v("Code128Decoder", "Rejected symbol: unit drift too high ($currentUnit vs rolling $rollingAvgUnit, score $matchScore)")
                    return null
                }
            }

            // 2. Parity check: sum of bar-modules MUST be even for Code 128
            // On screens, blooming often pushes the bar sum towards an odd number (e.g. 4.7 -> 5).
            val barModules = (currentWindow[0] + currentWindow[2] + currentWindow[4]) / currentUnit
            val distToEven = abs(barModules - (barModules / 2.0).roundToInt() * 2.0)
            
            // Relaxed Parity: High-confidence matches (low score) are more likely to be correct
            // even if blooming has heavily distorted the bar/space parity.
            val parityThreshold = when {
                matchScore < 0.1 -> 1.0  // Excellent match: ignore parity
                matchScore < 0.4 -> 0.85 // Good match: be very lenient
                else -> 0.7             // Average match: standard safety
            }
            
            if (distToEven > parityThreshold) {
                // If local parity fails, check against rolling average unit too
                val barModulesAvg = (currentWindow[0] + currentWindow[2] + currentWindow[4]) / rollingAvgUnit
                val distToEvenAvg = abs(barModulesAvg - (barModulesAvg / 2.0).roundToInt() * 2.0)
                if (distToEvenAvg > parityThreshold && matchScore > 0.4) {
                    Log.v("Code128Decoder", "Rejected symbol: parity check failed (local=$barModules, avg=$barModulesAvg)")
                    return null
                }
            }

            // Check for Stop before assuming another data/checksum symbol follows.
            if (isStopPattern(runs, idx, rollingAvgUnit)) {
                val stopUnit = runs.copyOfRange(idx, idx + 7).sum() / 13.0
                Log.v("Code128Decoder", "STOP pattern match found at idx=$idx, stopUnit=${"%.2f".format(stopUnit)}")
                // Quiet zone after Stop (Standard is 10x, but on screens we allow less)
                if (idx + 7 < runs.size) {
                    val quietZone = runs[idx + 7]
                    if (quietZone > 2.0 * stopUnit) {
                        return finish(startValue, values)
                    } else {
                        Log.v("Code128Decoder", "STOP found but quiet zone too small: $quietZone vs ${2.0 * stopUnit}")
                    }
                } else {
                    return finish(startValue, values)
                }
            }

            if (value == null) return null
            
            values.add(value)
            idx += 6
            // Update rolling average with 30% weight for responsiveness to perspective
            rollingAvgUnit = (rollingAvgUnit * 0.7) + (currentUnit * 0.3)
            unitCount++
        }

        Log.v("Code128Decoder", "Ran out of runs at idx=$idx, values=${values.size}")
        return null // ran out of runs before finding Stop
    }

    private fun isStopPattern(runs: IntArray, idx: Int, rollingUnit: Double): Boolean {
        if (idx + 7 > runs.size) return false
        val window = runs.copyOfRange(idx, idx + 7)
        val stopUnit = window.sum() / 13.0

        // Use the better of local stopUnit or the rollingUnit
        val unitToUse = if (abs(stopUnit - rollingUnit) < rollingUnit * 0.3) stopUnit else rollingUnit

        // 1. Structural check on first 6 runs (same as data symbols but with 13-module scale)
        val first6 = window.copyOfRange(0, 6)
        val idealFirst6 = PatternTables.CODE128_STOP.copyOfRange(0, 6)
        val score = PatternMatcher.scoreHybrid(first6, idealFirst6, unitToUse)

        // 2. Check the 7th run (must be a bar of roughly 2 units)
        val lastBarMatch = abs(window[6] - 2.0 * unitToUse) / unitToUse < 0.7

        return score < 0.7 && lastBarMatch
    }

    /** [values] = all data symbols including the trailing checksum symbol, in order. */
    private fun finish(startValue: Int, values: List<Int>): BarcodeResult? {
        // "Yes" card 10-digit numeric (Set C) = 5 data + 1 checksum = 6 symbols.
        // We allow 4 data symbols as a minimum to filter noise while keeping flex for shorter codes.
        if (values.size < 5) {
            Log.v("Code128Decoder", "Rejected: too few symbols (${values.size})")
            return null
        }

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
            when (currentSet) {
                'A' -> when (v) {
                    101 -> currentSet = 'B'
                    100 -> currentSet = 'C'
                    102 -> { /* FNC1 - skip */ }
                    else -> appendSymbol(output, v, 'A')
                }
                'B' -> when (v) {
                    101 -> currentSet = 'A'
                    99 -> currentSet = 'C'
                    102 -> { /* FNC1 - skip */ }
                    else -> appendSymbol(output, v, 'B')
                }
                'C' -> when (v) {
                    101 -> currentSet = 'A'
                    100 -> currentSet = 'B'
                    102 -> { /* FNC1 - skip */ }
                    else -> appendSymbol(output, v, 'C')
                }
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
            'C' -> if (value in 0..99) {
                val s = value.toString()
                sb.append(if (s.length == 1) "0$s" else s)
            }
        }
    }
}
