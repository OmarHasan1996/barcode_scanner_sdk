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

    /**
     * Edge-to-Edge (T-pattern) scoring. 
     * More robust than standard scoring for Code 128 as it's independent of Bar Width Growth.
     * Uses the distance between 4 consecutive edges (t1, t2, t3, t4).
     */
    fun scoreT(observed: IntArray, ideal: IntArray, unit: Double): Double {
        if (observed.size < 6 || ideal.size < 6) return Double.MAX_VALUE
        
        val totalObserved = observed.sum().toDouble()
        val totalIdeal = ideal.sum().toDouble()
        
        var totalScore = 0.0
        // Calculate 4 T-measurements (t1..t4)
        // t_i = (observed[i] + observed[i+1]) / totalObserved
        for (i in 0..3) {
            val observedT = (observed[i] + observed[i + 1]) / totalObserved
            val idealT = (ideal[i] + ideal[i + 1]) / totalIdeal
            val diff = observedT - idealT
            totalScore += (diff * diff)
        }
        
        // Final module sum check (is this symbol roughly the right total width?)
        val unitScore = abs(totalObserved - totalIdeal * unit) / (totalIdeal * unit + 1.0)
        
        return totalScore * 100.0 + unitScore
    }

    fun bestMatchIndex(observed: IntArray, candidates: Array<IntArray>, unit: Double, maxScore: Double = 0.5): Int {
        var bestIdx = -1
        var bestScore = maxScore
        for (i in candidates.indices) {
            // Mix T-score and standard score for best results
            val sT = scoreT(observed, candidates[i], unit)
            val s = score(observed, candidates[i], unit)
            
            val combined = if (sT < 0.15) sT * 0.5 + s * 0.5 else s
            
            if (combined < bestScore) {
                bestScore = combined
                bestIdx = i
            }
        }
        return bestIdx
    }

    fun matches(observed: IntArray, ideal: IntArray, unit: Double, maxScore: Double = 0.5): Boolean {
        return score(observed, ideal, unit) < maxScore
    }

    /**
     * Combines multiple scoring techniques for maximum robustness on screen scans.
     * Uses T-patterns (edge-to-edge) for drift-independence and
     * BWR-compensation for noise-independence.
     */
    fun scoreHybrid(observed: IntArray, ideal: IntArray, unit: Double): Double {
        if (observed.size < 6 || ideal.size < 6) return Double.MAX_VALUE
        
        // 1. T-Pattern Score (Edge-to-Edge)
        // Code 128 is highly structured in 2-module-sum pairs.
        val totalObserved = observed.sum().toDouble()
        val totalIdeal = ideal.sum().toDouble()
        
        var tScore = 0.0
        for (i in 0..3) {
            val obsT = (observed[i] + observed[i + 1]) / totalObserved
            val idlT = (ideal[i] + ideal[i + 1]) / totalIdeal
            val diff = obsT - idlT
            tScore += diff * diff
        }
        
        // 2. BWR Score (Standard with compensation)
        val bwrScore = score(observed, ideal, unit)
        
        // 3. Final weighting:
        // If T-score is excellent, it's very likely a match regardless of BWR noise.
        return if (tScore < 0.001) tScore * 100.0 else bwrScore
    }
}
