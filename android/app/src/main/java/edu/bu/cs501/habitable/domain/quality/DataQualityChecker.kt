package edu.bu.cs501.habitable.domain.quality

import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.ReadingFlag
import kotlin.math.abs

/**
 * Flags readings that look wrong instead of hiding them.
 *
 * We cannot stop someone putting the thermometer in a fridge, so the record
 * says when the data looks implausible and the PDF prints those flags.
 *
 * Two checks, both deliberately simple so they are easy to explain:
 *  - IMPLAUSIBLE_JUMP: the temperature moved faster than [maxDegreesPerMinute].
 *    Indoor air in an apartment does not change 10 °F in a couple of minutes.
 *  - GAP_BEFORE: more than [expectedIntervalMillis] * [gapFactor] passed since
 *    the previous reading, which usually means Android stopped the sensor
 *    service or the phone was out of Bluetooth range.
 */
class DataQualityChecker(
    private val expectedIntervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val gapFactor: Double = 3.0,
    private val maxDegreesPerMinute: Double = 2.0,
) {

    /** Compute the flags for [current] given the reading that came just before it. */
    fun flagsFor(previous: Reading?, current: Reading): Set<ReadingFlag> {
        if (previous == null) return current.flags
        val flags = current.flags.toMutableSet()

        val deltaMillis = current.recordedAtMillis - previous.recordedAtMillis
        if (deltaMillis > expectedIntervalMillis * gapFactor) {
            flags += ReadingFlag.GAP_BEFORE
        }

        val minutes = deltaMillis / 60_000.0
        if (minutes > 0) {
            val ratePerMinute = abs(current.tempF - previous.tempF) / minutes
            if (ratePerMinute > maxDegreesPerMinute) flags += ReadingFlag.IMPLAUSIBLE_JUMP
        }
        return flags
    }

    /** Re-flag an ordered list of readings from scratch. */
    fun annotate(readings: List<Reading>): List<Reading> {
        val sorted = readings.sortedBy { it.recordedAtMillis }
        val out = ArrayList<Reading>(sorted.size)
        var prev: Reading? = null
        for (r in sorted) {
            val flagged = r.copy(flags = flagsFor(prev, r))
            out += flagged
            prev = flagged
        }
        return out
    }

    /** Summary for the "Data checks" card. */
    fun summarize(readings: List<Reading>): QualitySummary {
        var jumps = 0
        var gaps = 0
        var simulated = 0
        for (r in readings) {
            if (ReadingFlag.IMPLAUSIBLE_JUMP in r.flags) jumps++
            if (ReadingFlag.GAP_BEFORE in r.flags) gaps++
            if (ReadingFlag.SIMULATED in r.flags) simulated++
        }
        return QualitySummary(total = readings.size, jumps = jumps, gaps = gaps, simulated = simulated)
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS: Long = 10 * 60 * 1000L
    }
}

data class QualitySummary(val total: Int, val jumps: Int, val gaps: Int, val simulated: Int) {
    val flagged: Int get() = jumps + gaps
    val allClear: Boolean get() = flagged == 0 && total > 0
}
