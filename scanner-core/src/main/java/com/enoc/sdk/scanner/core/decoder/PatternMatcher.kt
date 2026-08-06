package com.enoc.sdk.scanner.core.decoder

import kotlin.math.abs

object PatternMatcher {

    /** 
     * Standard scoring with "Bar-Width Growth" compensation. 
     * It attempts to match by "shaving" pixels from bars and giving them to spaces.
     */
    fun score(observed: IntArray, ideal: IntArray, unit: Double): Double {
        if (observed.size != ideal.size || unit <= 0.0) return Double.MAX_VALUE
        
        // Strategy: First, calculate a "Growth" factor (how much thicker bars are than they should be)
        var barSum = 0.0; var idealBarSum = 0.0
        var spaceSum = 0.0; var idealSpaceSum = 0.0
        
        for (i in observed.indices) {
            if (i % 2 == 0) { // Bar
                barSum += observed[i]
                idealBarSum += ideal[i] * unit
            } else { // Space
                spaceSum += observed[i]
                idealSpaceSum += ideal[i] * unit
            }
        }
        
        // Calculate Bar Width Growth (BWR)
        val growthPerBar = (barSum - idealBarSum) / (observed.size / 2.0)
        
        var totalScore = 0.0
        for (i in observed.indices) {
            val expected = ideal[i] * unit
            // Adjust observed by the calculated growth
            val adjustedObserved = if (i % 2 == 0) observed[i] - growthPerBar else observed[i] + growthPerBar
            val diff = adjustedObserved - expected
            totalScore += (diff * diff) / (expected * expected + expected + 1.0)
        }
        
        return totalScore
    }

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

    fun matches(observed: IntArray, ideal: IntArray, unit: Double, maxScore: Double = 0.5): Boolean {
        return score(observed, ideal, unit) < maxScore
    }
}
