package com.enoc.sdk.scanner.core.core

/**
 * A row reduced to alternating run lengths, e.g. white,black,white,black...
 * [startsBlack] tells you the color of runs[0]; after that colors just alternate.
 * [label] is a diagnostic tag indicating which binarizer created these runs.
 */
data class RowRuns(val runs: IntArray, val startsBlack: Boolean, val label: String = "") {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as RowRuns

        if (startsBlack != other.startsBlack) return false
        if (!runs.contentEquals(other.runs)) return false
        if (label != other.label) return false

        return true
    }

    override fun hashCode(): Int {
        var result = startsBlack.hashCode()
        result = 31 * result + runs.contentHashCode()
        result = 31 * result + label.hashCode()
        return result
    }
}

object RunLengthReader {

    /**
     * Converts a binarized row into run lengths. Trims nothing — callers are
     * responsible for locating guard/start patterns within the run list.
     */
    fun toRuns(row: BooleanArray, label: String = ""): RowRuns {
        if (row.isEmpty()) return RowRuns(IntArray(0), false, label)

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
        return RowRuns(runs.toIntArray(), row[0], label)
    }
}
