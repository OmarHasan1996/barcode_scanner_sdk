package com.enoc.sdk.scanner.core.decoder

/**
 * Scores observed pixel run-lengths against an idealized module-width pattern
 * (e.g. EAN digit [3,2,1,1] or Code128 symbol [2,1,2,2,2,2]) for a given
 * estimated module unit width. Lower score = better match. This is the core
 * "fuzzy matching" every 1D decoder needs, since printed/camera-captured
 * bars are never pixel-perfect multiples of the unit width.
 */
object PatternMatcher {

    /** Sum of squared relative errors between observed runs and ideal*unit. Lower is better. */
    fun score(observed: IntArray, ideal: IntArray, unit: Double): Double {
        if (observed.size != ideal.size || unit <= 0.0) return Double.MAX_VALUE
        var total = 0.0
        for (i in observed.indices) {
            val expected = ideal[i] * unit
            val diff = observed[i] - expected
            total += (diff * diff) / (expected * expected + 1e-6)
        }
        return total
    }

    /** Returns the index of the best-scoring pattern in [candidates], or -1 if all exceed [maxScore]. */
    fun bestMatchIndex(observed: IntArray, candidates: Array<IntArray>, unit: Double, maxScore: Double = 0.5): Int {
        var bestIdx = -1
        var bestScore = maxScore
        for (i in candidates.indices) {
            val s = score(observed, candidates[i], unit)
            if (s < bestScore) {
                bestScore = s
                bestIdx = i
            }
        }
        return bestIdx
    }

    /** Checks that [observed] plausibly represents [ideal] scaled by [unit], within [maxScore]. */
    fun matches(observed: IntArray, ideal: IntArray, unit: Double, maxScore: Double = 0.5): Boolean {
        return score(observed, ideal, unit) < maxScore
    }
}
