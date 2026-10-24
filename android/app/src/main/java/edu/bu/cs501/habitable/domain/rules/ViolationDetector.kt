package edu.bu.cs501.habitable.domain.rules

import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.model.ViolationKind
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Turns a stream of readings into violation periods.
 *
 * A violation opens when a reading breaks the rule and closes when a reading
 * is back inside the legal range. [minDurationMillis] stops a single noisy
 * sample from opening an issue: the period has to persist for that long before
 * [shouldOpenIssue] returns true.
 *
 * Pure Kotlin so it is unit-testable.
 */
class ViolationDetector(
    private val zone: ZoneId = HeatRules.BOSTON,
    private val minDurationMillis: Long = DEFAULT_MIN_DURATION_MILLIS,
) {

    /**
     * Fold a reading into the current open violation (if any).
     *
     * @return the new state: an updated/opened violation, a closed violation, or nothing.
     */
    fun step(open: Violation?, reading: Reading): StepResult {
        val at = ZonedDateTime.ofInstant(Instant.ofEpochMilli(reading.recordedAtMillis), zone)
        val status = HeatRules.evaluate(reading.tempF, at)

        return when (status) {
            is HeatStatus.BelowMinimum -> {
                if (open != null && open.kind == ViolationKind.BELOW_MINIMUM) {
                    StepResult.Continue(
                        open.copy(
                            minTempF = minOf(open.minTempF, reading.tempF),
                            requiredTempF = status.requiredF,
                        )
                    )
                } else {
                    // Close a different-kind violation first if one is open.
                    val closed = open?.copy(endedAtMillis = reading.recordedAtMillis)
                    StepResult.Opened(
                        closed = closed,
                        opened = Violation(
                            sensorId = reading.sensorId,
                            startedAtMillis = reading.recordedAtMillis,
                            endedAtMillis = null,
                            minTempF = reading.tempF,
                            requiredTempF = status.requiredF,
                            kind = ViolationKind.BELOW_MINIMUM,
                            issueId = null,
                        )
                    )
                }
            }

            is HeatStatus.AboveMaximum -> {
                if (open != null && open.kind == ViolationKind.ABOVE_MAXIMUM) {
                    StepResult.Continue(open.copy(minTempF = maxOf(open.minTempF, reading.tempF)))
                } else {
                    val closed = open?.copy(endedAtMillis = reading.recordedAtMillis)
                    StepResult.Opened(
                        closed = closed,
                        opened = Violation(
                            sensorId = reading.sensorId,
                            startedAtMillis = reading.recordedAtMillis,
                            endedAtMillis = null,
                            minTempF = reading.tempF, // for ABOVE_MAXIMUM this field holds the peak
                            requiredTempF = status.maximumF,
                            kind = ViolationKind.ABOVE_MAXIMUM,
                            issueId = null,
                        )
                    )
                }
            }

            is HeatStatus.Compliant, HeatStatus.OutOfSeason -> {
                if (open != null) StepResult.Closed(open.copy(endedAtMillis = reading.recordedAtMillis))
                else StepResult.Nothing
            }
        }
    }

    /** Run [step] over a whole ordered list and return every violation found. */
    fun detectAll(readings: List<Reading>): List<Violation> {
        val result = ArrayList<Violation>()
        var open: Violation? = null
        for (r in readings.sortedBy { it.recordedAtMillis }) {
            when (val s = step(open, r)) {
                is StepResult.Continue -> open = s.violation
                is StepResult.Opened -> {
                    s.closed?.let { result += it }
                    open = s.opened
                }
                is StepResult.Closed -> {
                    result += s.violation
                    open = null
                }
                StepResult.Nothing -> Unit
            }
        }
        open?.let { result += it }
        return result
    }

    /**
     * An issue should be opened automatically once a violation has lasted at
     * least [minDurationMillis] and no issue is linked yet.
     */
    fun shouldOpenIssue(violation: Violation, nowMillis: Long): Boolean =
        violation.issueId == null &&
            violation.kind == ViolationKind.BELOW_MINIMUM &&
            violation.durationMillis(nowMillis) >= minDurationMillis

    /** Total time spent in BELOW_MINIMUM violations, for the "13 h below limit" stat. */
    fun totalBelowMillis(violations: List<Violation>, nowMillis: Long): Long =
        violations.filter { it.kind == ViolationKind.BELOW_MINIMUM }
            .sumOf { it.durationMillis(nowMillis) }

    sealed interface StepResult {
        data class Continue(val violation: Violation) : StepResult
        data class Opened(val closed: Violation?, val opened: Violation) : StepResult
        data class Closed(val violation: Violation) : StepResult
        data object Nothing : StepResult
    }

    companion object {
        /** Two hours, as agreed in the proposal's acceptance criteria for US-5. */
        const val DEFAULT_MIN_DURATION_MILLIS: Long = 2 * 60 * 60 * 1000L
    }
}
