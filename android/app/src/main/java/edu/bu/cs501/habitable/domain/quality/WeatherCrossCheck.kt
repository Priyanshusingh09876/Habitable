package edu.bu.cs501.habitable.domain.quality

import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Reading
import kotlin.math.abs

/**
 * The one "smart" tamper detector: does the indoor sensor behave like it is
 * outdoors?
 *
 * An honest cold apartment is still much warmer than a Boston November night;
 * a sensor hung out of a window, or left on a sill with the window open,
 * tracks the outdoor temperature within a few degrees. So: for every reading,
 * find the nearest outdoor hour and, when it is genuinely cold outside, flag
 * readings that sit within [maxDeltaF] of outdoors. A flag only becomes a
 * suspicious *span* once it has lasted [minSpanMillis], so a single odd sample
 * does not accuse anyone.
 *
 * Like every other check, this never deletes or hides data. Spans are shown
 * on the Sensors screen and printed in the PDF. Pure Kotlin; unit-tested.
 */
class WeatherCrossCheck(
    /** Only compare when it is this cold or colder outside; otherwise indoor ≈ outdoor is normal. */
    private val coldOutsideF: Double = 50.0,
    /** Indoor within this many °F of outdoor looks like an outdoor sensor. */
    private val maxDeltaF: Double = 4.0,
    /** How long the pattern must persist before it counts. */
    private val minSpanMillis: Long = 60 * 60 * 1000L,
    /** Outdoor hour must be within this of the reading to be compared at all. */
    private val maxMatchMillis: Long = 90 * 60 * 1000L,
) {

    data class Span(val startMillis: Long, val endMillis: Long, val minDeltaF: Double) {
        val durationMillis: Long get() = endMillis - startMillis
    }

    /** Readings whose indoor temperature tracks a cold outdoor temperature. */
    fun suspiciousReadings(readings: List<Reading>, outdoor: List<OutdoorReading>): List<Reading> {
        if (outdoor.isEmpty()) return emptyList()
        val sortedOut = outdoor.sortedBy { it.timeMillis }
        return readings.filter { r ->
            val o = nearest(sortedOut, r.recordedAtMillis) ?: return@filter false
            o.tempF <= coldOutsideF && abs(r.tempF - o.tempF) <= maxDeltaF
        }
    }

    /** Contiguous suspicious runs at least [minSpanMillis] long. */
    fun spans(readings: List<Reading>, outdoor: List<OutdoorReading>): List<Span> {
        val flagged = suspiciousReadings(readings, outdoor).map { it.recordedAtMillis }.toSet()
        val sorted = readings.sortedBy { it.recordedAtMillis }
        val sortedOut = outdoor.sortedBy { it.timeMillis }
        val out = ArrayList<Span>()
        var start: Long? = null
        var minDelta = Double.MAX_VALUE
        var lastT = 0L
        fun close(end: Long) {
            val s = start ?: return
            if (end - s >= minSpanMillis) out += Span(s, end, minDelta)
            start = null; minDelta = Double.MAX_VALUE
        }
        for (r in sorted) {
            val isFlagged = r.recordedAtMillis in flagged
            if (isFlagged) {
                if (start == null) start = r.recordedAtMillis
                nearest(sortedOut, r.recordedAtMillis)?.let { minDelta = minOf(minDelta, abs(r.tempF - it.tempF)) }
            } else if (start != null) {
                close(lastT)
            }
            lastT = r.recordedAtMillis
        }
        if (start != null) close(lastT)
        return out
    }

    private fun nearest(sortedOutdoor: List<OutdoorReading>, t: Long): OutdoorReading? {
        var lo = 0; var hi = sortedOutdoor.size - 1
        while (lo < hi) {
            val mid = (lo + hi) / 2
            if (sortedOutdoor[mid].timeMillis < t) lo = mid + 1 else hi = mid
        }
        val candidates = listOfNotNull(sortedOutdoor.getOrNull(lo - 1), sortedOutdoor.getOrNull(lo))
        val best = candidates.minByOrNull { abs(it.timeMillis - t) } ?: return null
        return if (abs(best.timeMillis - t) <= maxMatchMillis) best else null
    }
}
