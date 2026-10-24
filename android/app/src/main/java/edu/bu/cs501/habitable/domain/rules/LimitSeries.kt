package edu.bu.cs501.habitable.domain.rules

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Produces the step-shaped legal-limit line that the chart draws.
 *
 * The limit changes at exactly 7:00 AM and 11:01 PM, so between two readings
 * the line may step. We emit one point per reading time *and* one extra point
 * right at each boundary so the chart draws a vertical step rather than a slope.
 */
object LimitSeries {

    data class Point(val timeMillis: Long, val limitF: Int?)

    fun build(fromMillis: Long, toMillis: Long, zone: ZoneId = HeatRules.BOSTON): List<Point> {
        if (toMillis <= fromMillis) return emptyList()
        val points = ArrayList<Point>()
        var t = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fromMillis), zone)
        val end = ZonedDateTime.ofInstant(Instant.ofEpochMilli(toMillis), zone)

        points += Point(fromMillis, HeatRules.requiredMinimumF(t))

        // Walk boundary to boundary.
        var next = nextBoundary(t)
        while (next.isBefore(end)) {
            val before = next.minusNanos(1)
            points += Point(before.toInstant().toEpochMilli(), HeatRules.requiredMinimumF(before))
            points += Point(next.toInstant().toEpochMilli(), HeatRules.requiredMinimumF(next))
            t = next
            next = nextBoundary(t)
        }
        points += Point(toMillis, HeatRules.requiredMinimumF(end))
        return points
    }

    /** The next instant at which the required minimum can change. */
    internal fun nextBoundary(t: ZonedDateTime): ZonedDateTime {
        val dayStart = t.toLocalDate().atTime(HeatRules.DAY_START).atZone(t.zone)
        val nightStart = t.toLocalDate().atTime(HeatRules.NIGHT_START).atZone(t.zone)
        return when {
            t.isBefore(dayStart) -> dayStart
            t.isBefore(nightStart) -> nightStart
            else -> dayStart.plusDays(1)
        }
    }
}
