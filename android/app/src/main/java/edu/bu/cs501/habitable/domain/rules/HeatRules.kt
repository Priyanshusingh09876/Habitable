package edu.bu.cs501.habitable.domain.rules

import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Massachusetts minimum heat rules, 105 CMR 410.180.
 *
 *  - Heating season: September 15 through May 31 (inclusive).
 *  - Daytime (7:00 AM to 11:00 PM): at least 68 °F.
 *  - Overnight (11:01 PM to 6:59 AM): at least 64 °F.
 *  - Never above 78 °F during the heating season.
 *
 * All decisions are made in the apartment's local time zone (America/New_York
 * for Boston), because the regulation is written in wall-clock time.
 *
 * This object is pure Kotlin with no Android dependencies so it can be unit-tested
 * on the JVM. It is the single source of truth for every limit shown in the UI,
 * the notification, and the PDF.
 */
object HeatRules {

    const val DAY_MIN_F = 68
    const val NIGHT_MIN_F = 64
    const val MAX_F = 78

    /** Daytime window starts at 7:00 AM inclusive. */
    val DAY_START: LocalTime = LocalTime.of(7, 0)

    /** Night starts at 11:01 PM; anything before that (including 11:00:59 PM) is daytime. */
    val NIGHT_START: LocalTime = LocalTime.of(23, 1)

    val SEASON_START: MonthDay = MonthDay.of(9, 15)
    val SEASON_END: MonthDay = MonthDay.of(5, 31)

    val BOSTON: ZoneId = ZoneId.of("America/New_York")

    /** True when [date] falls inside the heating season. The season wraps the new year. */
    fun isHeatingSeason(date: LocalDate): Boolean {
        val md = MonthDay.from(date)
        // Sep 15 .. Dec 31  OR  Jan 1 .. May 31
        return !md.isBefore(SEASON_START) || !md.isAfter(SEASON_END)
    }

    /** True when [time] falls inside the daytime window (7:00 AM up to, not including, 11:01 PM). */
    fun isDaytime(time: LocalTime): Boolean =
        !time.isBefore(DAY_START) && time.isBefore(NIGHT_START)

    /**
     * The minimum legal temperature at [at], or null when no minimum applies
     * (outside the heating season).
     */
    fun requiredMinimumF(at: ZonedDateTime): Int? {
        if (!isHeatingSeason(at.toLocalDate())) return null
        return if (isDaytime(at.toLocalTime())) DAY_MIN_F else NIGHT_MIN_F
    }

    /** The maximum legal temperature at [at], or null outside the heating season. */
    fun maximumF(at: ZonedDateTime): Int? =
        if (isHeatingSeason(at.toLocalDate())) MAX_F else null

    /** Convenience overload for epoch millis in the Boston zone. */
    fun requiredMinimumF(epochMillis: Long, zone: ZoneId = BOSTON): Int? =
        requiredMinimumF(ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), zone))

    /**
     * Classify a single reading.
     */
    fun evaluate(tempF: Double, at: ZonedDateTime): HeatStatus {
        val min = requiredMinimumF(at) ?: return HeatStatus.OutOfSeason
        val max = maximumF(at) ?: return HeatStatus.OutOfSeason
        return when {
            tempF < min -> HeatStatus.BelowMinimum(requiredF = min, shortfallF = min - tempF)
            tempF > max -> HeatStatus.AboveMaximum(maximumF = max, excessF = tempF - max)
            else -> HeatStatus.Compliant(requiredF = min)
        }
    }
}

/** Result of evaluating one reading against the rules. */
sealed interface HeatStatus {
    data object OutOfSeason : HeatStatus
    data class Compliant(val requiredF: Int) : HeatStatus
    data class BelowMinimum(val requiredF: Int, val shortfallF: Double) : HeatStatus
    data class AboveMaximum(val maximumF: Int, val excessF: Double) : HeatStatus

    val isViolation: Boolean
        get() = this is BelowMinimum || this is AboveMaximum
}
