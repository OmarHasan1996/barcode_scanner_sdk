package com.enoc.sdk.scanner.core.decoder

import android.util.Log
import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult
import kotlin.math.abs

/**
 * Decodes Code128 (code sets A, B, and C, with set-switching) from a scanned row.
 *
 * Implements a global range search (finding Start and Stop pairs) and interpolation
 * to handle perspective and screen blooming, similar to high-performance SDKs.
 */
class Code128Decoder : Decoder {

    private data class Candidate(val idx: Int, val value: Int, val unit: Double)

    override fun decode(rowRuns: RowRuns): BarcodeResult? {
        val result = decodeInternal(rowRuns)
        if (result != null) return result
        
        // Code 128 is NOT symmetric. 
        val reversedRuns = rowRuns.runs.reversedArray()
        val lastRunWasBlack = if (rowRuns.runs.size % 2 == 1) rowRuns.startsBlack else !rowRuns.startsBlack
        
        return decodeInternal(RowRuns(reversedRuns, lastRunWasBlack, "${rowRuns.label}-Rev"))
    }

    private fun decodeInternal(rowRuns: RowRuns): BarcodeResult? {
        val runs = rowRuns.runs
        val startsBlack = rowRuns.startsBlack
        val label = rowRuns.label
        if (runs.size < 20) return null 

        val starts = ArrayList<Candidate>()
        val stops = ArrayList<Candidate>()

        // 1. Locate all candidate Start patterns
        for (i in 0 until runs.size - 13) {
            val isBlack = if (i % 2 == 0) startsBlack else !startsBlack
            if (!isBlack) continue

            val window = runs.copyOfRange(i, i + 6)
            val unit = window.sum() / 11.0
            if (unit < 0.7 || unit > 15.0) continue 

            // Quiet zone check: at least 4 modules of white space before the start pattern.
            // Relaxed from 6.0 for noisy screens.
            if (i > 0) {
                if (runs[i - 1] < 4.0 * unit) continue
            } else continue 

            val startValue = matchSymbol(window, unit, 0.8, 0.05) ?: continue
            if (startValue in 103..105) {
                starts.add(Candidate(i, startValue, unit))
                Log.d("Code128Decoder", "[$label] START candidate at idx=$i, val=$startValue, unit=${"%.2f".format(unit)}")
            }
        }

        if (starts.isEmpty()) return null

        // 2. Locate all candidate Stop patterns
        for (i in 13 until runs.size - 1) {
            val isBlack = if (i % 2 == 0) startsBlack else !startsBlack
            if (!isBlack) continue

            if (isStopPattern(runs, i, 0.0, label)) {
                val window = runs.copyOfRange(i, minOf(i + 7, runs.size))
                val unit = if (window.size == 7) window.sum() / 13.0 else window.sum() / 11.0
                stops.add(Candidate(i, -1, unit))
                Log.d("Code128Decoder", "[$label] STOP candidate at idx=$i, unit=${"%.2f".format(unit)}")
            }
        }

        // 3. Try pairing Starts and Stops with global range search
        for (start in starts) {
            for (stop in stops) {
                if (stop.idx <= start.idx + 6) continue
                
                val runsBetween = stop.idx - (start.idx + 6)
                if (runsBetween < 6 || runsBetween % 6 != 0) continue
                
                val numSymbols = runsBetween / 6
                if (numSymbols < 4) continue 

                Log.d("Code128Decoder", "[$label] Attempting pairing: Start@${start.idx} -> Stop@${stop.idx} ($numSymbols data+chk symbols)")
                val result = tryDecodeRange(runs, start, stop, numSymbols, label)
                if (result != null) {
                    Log.i("Code128Decoder", "SUCCESS: Found barcode via range search. Symbols=$numSymbols, Pass=$label")
                    return result
                }
            }
        }

        // 4. Fallback: Greedy Decoding
        for (start in starts) {
            val sweepRatios = doubleArrayOf(1.0, 0.95, 1.05, 0.9, 1.1)
            for (ratio in sweepRatios) {
                val result = tryDecodeGreedy(runs, start.idx, start.value, start.unit * ratio)
                if (result != null) return result
            }
        }

        return null
    }

    private fun tryDecodeRange(runs: IntArray, start: Candidate, stop: Candidate, numSymbols: Int, label: String): BarcodeResult? {
        val values = ArrayList<Int>()
        for (s in 0 until numSymbols) {
            val windowIdx = start.idx + 6 + s * 6
            val window = runs.copyOfRange(windowIdx, windowIdx + 6)
            
            val progress = s.toDouble() / numSymbols
            val interpolatedUnit = start.unit * (1.0 - progress) + stop.unit * progress
            
            // Match with interpolated unit; relaxed tolerance for internal symbols
            val value = matchSymbol(window, interpolatedUnit, 1.2, 0.05)
            if (value == null) {
                Log.d("Code128Decoder", "[$label] Range pairing fail at sym $s (idx=$windowIdx, unit=${"%.2f".format(interpolatedUnit)}). Window: ${window.joinToString(",")}")
                return null
            }
            values.add(value)
        }
        return finish(start.value, values)
    }

    private fun tryDecodeGreedy(runs: IntArray, startIdx: Int, startValue: Int, initialUnit: Double): BarcodeResult? {
        var idx = startIdx + 6
        var rollingAvgUnit = initialUnit
        val values = ArrayList<Int>()

        while (idx + 6 <= runs.size) {
            val currentWindow = runs.copyOfRange(idx, idx + 6)
            val currentUnit = currentWindow.sum() / 11.0

            if (isStopPattern(runs, idx, rollingAvgUnit)) {
                return finish(startValue, values)
            }

            val value = matchSymbol(currentWindow, currentUnit, 1.2, 0.05)
                ?: matchSymbol(currentWindow, rollingAvgUnit, 1.2, 0.05)
            
            if (value == null) return null
            if (abs(currentUnit - rollingAvgUnit) > rollingAvgUnit * 0.5) return null
            
            values.add(value)
            idx += 6
            rollingAvgUnit = (rollingAvgUnit * 0.8) + (currentUnit * 0.2)
        }
        return null
    }

    private fun matchSymbol(window: IntArray, unit: Double, maxScore: Double, ratioMaxScore: Double = 0.05): Int? {
        val adjustedMaxScore = if (unit < 1.5) maxScore * 1.5 else maxScore
        var bestIdx = PatternMatcher.bestMatchIndex(window, PatternTables.CODE128_PATTERNS, unit, maxScore = adjustedMaxScore)
        if (bestIdx == -1) {
            bestIdx = matchSymbolByRatios(window, ratioMaxScore)
        }
        return if (bestIdx >= 0) bestIdx else null
    }

    private fun matchSymbolByRatios(window: IntArray, maxScore: Double): Int {
        val totalObserved = window.sum().toDouble()
        if (totalObserved == 0.0) return -1
        var bestIdx = -1; var bestScore = maxScore
        for (i in PatternTables.CODE128_PATTERNS.indices) {
            val ideal = PatternTables.CODE128_PATTERNS[i]
            var score = 0.0
            for (j in window.indices) {
                val observedRatio = window[j] / totalObserved
                val idealRatio = ideal[j] / 11.0
                val diff = observedRatio - idealRatio
                score += diff * diff
            }
            if (score < bestScore) { bestScore = score; bestIdx = i }
        }
        return bestIdx
    }

    private fun isStopPattern(runs: IntArray, idx: Int, rollingUnit: Double, label: String = ""): Boolean {
        if (idx + 6 > runs.size) return false
        val window = runs.copyOfRange(idx, minOf(idx + 7, runs.size))
        val stopUnit = if (window.size == 7) window.sum() / 13.0 else window.sum() / 11.0

        val unitToUse = if (rollingUnit > 0 && abs(stopUnit - rollingUnit) < rollingUnit * 0.4) rollingUnit else stopUnit

        val first6 = window.copyOfRange(0, 6)
        val idealFirst6 = PatternTables.CODE128_STOP.copyOfRange(0, 6)
        val score = PatternMatcher.scoreHybrid(first6, idealFirst6, unitToUse)

        // Stop pattern needs a bit more leniency for screen glare
        if (score > 1.0) return false

        if (window.size == 7) {
            val lastBarMatch = abs(window[6] - 2.0 * unitToUse) / unitToUse < 1.0 
            if (!lastBarMatch) return false
        }
        return true
    }

    /** [values] = all data symbols including the trailing checksum symbol, in order. */
    private fun finish(startValue: Int, values: List<Int>): BarcodeResult? {
        if (values.size < 5) return null

        val checksumSymbol = values.last()
        val dataValues = values.subList(0, values.size - 1)

        var checksum = startValue
        for ((i, v) in dataValues.withIndex()) {
            checksum += v * (i + 1)
        }
        if (checksum % 103 != checksumSymbol) return null

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
                in 64..95 -> sb.append((value - 64).toChar())
            }
            'C' -> if (value in 0..99) {
                val s = value.toString()
                sb.append(if (s.length == 1) "0$s" else s)
            }
        }
    }
}
