package com.enoc.sdk.scanner.core.core

/**
 * A row reduced to alternating run lengths, e.g. white,black,white,black...
 * [startsBlack] tells you the color of runs[0]; after that colors just alternate.
 */
data class RowRuns(val runs: IntArray, val startsBlack: Boolean)

object RunLengthReader {

    /**
     * Converts a binarized row into run lengths. Trims nothing — callers are
     * responsible for locating guard/start patterns within the run list.
     */
    fun toRuns(row: BooleanArray): RowRuns {
        if (row.isEmpty()) return RowRuns(IntArray(0), false)

        val runs = ArrayList<Int>()
        var current = row[0]
        var count = 1
        for (i in 1 until row.size) {
            if (row[i] == current) {
                count++
            } else {
                runs.add(count)
                current = row[i]
                count = 1
            }
        }
        runs.add(count)
        return RowRuns(runs.toIntArray(), row[0])
    }
}
