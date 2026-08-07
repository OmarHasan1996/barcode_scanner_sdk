package com.enoc.sdk.scanner.core.decoder

import com.enoc.sdk.scanner.core.core.RowRuns
import com.enoc.sdk.scanner.core.model.BarcodeFormat
import com.enoc.sdk.scanner.core.model.BarcodeResult

/**
 * Decodes EAN-13 from a single scanned row.
 *
 * Structure: start guard (3 modules) + 6 left digits (7 modules each, L or G
 * code) + middle guard (5 modules) + 6 right digits (7 modules each, R code)
 * + end guard (3 modules) = 95 modules total.
 */
open class EAN13Decoder : Decoder {

    override fun decode(rowRuns: RowRuns): BarcodeResult? {
        val runs = rowRuns.runs
        val startsBlack = rowRuns.startsBlack

        // Guards are bars (black), so the first run of the guard must be black.
        // Try every plausible starting index where colors line up (black,white,black,...).
        for (startIdx in 0 until runs.size - 3) {
            val runColorIsBlack = if (startIdx % 2 == 0) startsBlack else !startsBlack
            if (!runColorIsBlack) continue

            val guard = runs.copyOfRange(startIdx, startIdx + 3)
            val unit = (guard[0] + guard[1] + guard[2]) / 3.0
            if (unit < 1.0) continue
            if (!PatternMatcher.matches(guard, PatternTables.GUARD_START_END, unit, maxScore = 0.6)) continue

            val result = tryDecodeFrom(runs, startIdx + 3, unit)
            if (result != null) return result
        }
        return null
    }

    private fun tryDecodeFrom(runs: IntArray, afterStartGuard: Int, initialUnit: Double): BarcodeResult? {
        var idx = afterStartGuard
        var unit = initialUnit
        val parity = StringBuilder()
        val leftDigits = IntArray(6)

        // 6 left-hand digits, 4 runs (7 modules) each
        for (d in 0 until 6) {
            if (idx + 4 > runs.size) return null
            val window = runs.copyOfRange(idx, idx + 4)

            val lMatch = PatternMatcher.bestMatchIndex(window, PatternTables.L_CODE_RUNS, unit)
            val gMatch = PatternMatcher.bestMatchIndex(window, PatternTables.G_CODE_RUNS, unit)

            val lScore = if (lMatch >= 0) PatternMatcher.score(window, PatternTables.L_CODE_RUNS[lMatch], unit) else Double.MAX_VALUE
            val gScore = if (gMatch >= 0) PatternMatcher.score(window, PatternTables.G_CODE_RUNS[gMatch], unit) else Double.MAX_VALUE

            when {
                lMatch >= 0 && lScore <= gScore -> { parity.append('L'); leftDigits[d] = lMatch }
                gMatch >= 0 -> { parity.append('G'); leftDigits[d] = gMatch }
                else -> return null
            }
            idx += 4
            // Re-estimate unit width using the modules we just consumed for drift tolerance.
            unit = window.sum() / 7.0
        }

        // Middle guard: 5 modules, space/bar/space/bar/space
        if (idx + 5 > runs.size) return null
        val middle = runs.copyOfRange(idx, idx + 5)
        if (!PatternMatcher.matches(middle, PatternTables.GUARD_MIDDLE, unit, maxScore = 0.6)) return null
        idx += 5

        // 6 right-hand digits, R-code only
        val rightDigits = IntArray(6)
        for (d in 0 until 6) {
            if (idx + 4 > runs.size) return null
            val window = runs.copyOfRange(idx, idx + 4)
            val rMatch = PatternMatcher.bestMatchIndex(window, PatternTables.R_CODE_RUNS, unit)
            if (rMatch < 0) return null
            rightDigits[d] = rMatch
            idx += 4
            unit = window.sum() / 7.0
        }

        // End guard
        if (idx + 3 > runs.size) return null
        val end = runs.copyOfRange(idx, idx + 3)
        if (!PatternMatcher.matches(end, PatternTables.GUARD_START_END, unit, maxScore = 0.6)) return null

        val firstDigit = PatternTables.FIRST_DIGIT_PARITY.indexOf(parity.toString())
        if (firstDigit < 0) return null

        val digits = StringBuilder()
        digits.append(firstDigit)
        leftDigits.forEach { digits.append(it) }
        rightDigits.forEach { digits.append(it) }

        val code = digits.toString()
        if (!checksumValid(code)) return null

        return finalizeResult(code)
    }

    /** Subclasses (UPC-A) can post-process/reformat before returning. */
    protected open fun finalizeResult(ean13: String): BarcodeResult? =
        BarcodeResult(text = ean13, format = BarcodeFormat.EAN_13)

    companion object {
        fun checksumValid(digits13: String): Boolean {
            if (digits13.length != 13 || digits13.any { !it.isDigit() }) return false
            var sum = 0
            for (i in 0 until 12) {
                val d = digits13[i] - '0'
                sum += if (i % 2 == 0) d else d * 3
            }
            val check = (10 - (sum % 10)) % 10
            return check == (digits13[12] - '0')
        }
    }
}
