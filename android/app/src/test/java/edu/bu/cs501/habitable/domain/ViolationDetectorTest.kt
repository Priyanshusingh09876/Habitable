package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.model.ViolationKind
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.rules.ViolationDetector
import org.junit.Test
import java.time.ZonedDateTime

class ViolationDetectorTest {

    private val detector = ViolationDetector()

    private fun t(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, HeatRules.BOSTON).toInstant().toEpochMilli()

    private fun r(tempF: Double, at: Long) = Reading(sensorId = 1, tempF = tempF, humidityPct = null, recordedAtMillis = at)

    @Test fun `a compliant reading with nothing open does nothing`() {
        assertThat(detector.step(null, r(70.0, t(11, 9, 20)))).isEqualTo(ViolationDetector.StepResult.Nothing)
    }

    @Test fun `a cold reading opens a BELOW_MINIMUM violation`() {
        val s = detector.step(null, r(62.0, t(11, 9, 20)))
        assertThat(s).isInstanceOf(ViolationDetector.StepResult.Opened::class.java)
        val v = (s as ViolationDetector.StepResult.Opened).opened
        assertThat(v.kind).isEqualTo(ViolationKind.BELOW_MINIMUM)
        assertThat(v.requiredTempF).isEqualTo(68)
        assertThat(v.minTempF).isEqualTo(62.0)
        assertThat(v.isOpen).isTrue()
    }

    @Test fun `subsequent cold readings continue and track the minimum`() {
        val open = (detector.step(null, r(62.0, t(11, 9, 20))) as ViolationDetector.StepResult.Opened).opened
        val s = detector.step(open, r(59.1, t(11, 9, 21)))
        assertThat(s).isInstanceOf(ViolationDetector.StepResult.Continue::class.java)
        assertThat((s as ViolationDetector.StepResult.Continue).violation.minTempF).isEqualTo(59.1)
    }

    @Test fun `a warm reading closes the violation with the reading time`() {
        val open = (detector.step(null, r(62.0, t(11, 9, 20))) as ViolationDetector.StepResult.Opened).opened
        val s = detector.step(open, r(69.0, t(11, 9, 22)))
        assertThat(s).isInstanceOf(ViolationDetector.StepResult.Closed::class.java)
        assertThat((s as ViolationDetector.StepResult.Closed).violation.endedAtMillis).isEqualTo(t(11, 9, 22))
    }

    @Test fun `the limit dropping to 64 at 11 PM closes a violation at 65 degrees`() {
        // 65 °F is a violation at 10 PM (limit 68) but legal at 11:30 PM (limit 64).
        val open = (detector.step(null, r(65.0, t(11, 9, 22))) as ViolationDetector.StepResult.Opened).opened
        val s = detector.step(open, r(65.0, t(11, 9, 23, 30)))
        assertThat(s).isInstanceOf(ViolationDetector.StepResult.Closed::class.java)
    }

    @Test fun `detectAll finds the cold night's violations in order`() {
        val readings = listOf(
            r(70.0, t(11, 9, 18)), r(66.0, t(11, 9, 20)), r(65.0, t(11, 9, 22)),
            r(64.5, t(11, 9, 23, 30)), // legal at night
            r(60.0, t(11, 10, 2)), r(59.0, t(11, 10, 4)),
            r(61.0, t(11, 10, 8)),     // still below 68 by day
            r(70.0, t(11, 10, 10)),
        )
        val v = detector.detectAll(readings)
        assertThat(v).hasSize(2)
        assertThat(v[0].startedAtMillis).isEqualTo(t(11, 9, 20))
        assertThat(v[0].endedAtMillis).isEqualTo(t(11, 9, 23, 30))
        assertThat(v[1].startedAtMillis).isEqualTo(t(11, 10, 2))
        assertThat(v[1].endedAtMillis).isEqualTo(t(11, 10, 10))
        assertThat(v[1].minTempF).isEqualTo(59.0)
    }

    @Test fun `an issue opens only after the minimum duration`() {
        val v = Violation(sensorId = 1, startedAtMillis = t(11, 9, 20), endedAtMillis = null, minTempF = 62.0, requiredTempF = 68, kind = ViolationKind.BELOW_MINIMUM, issueId = null)
        assertThat(detector.shouldOpenIssue(v, t(11, 9, 21, 30))).isFalse()
        assertThat(detector.shouldOpenIssue(v, t(11, 9, 22))).isTrue()
    }

    @Test fun `an issue never opens twice for the same violation`() {
        val v = Violation(sensorId = 1, startedAtMillis = t(11, 9, 20), endedAtMillis = null, minTempF = 62.0, requiredTempF = 68, kind = ViolationKind.BELOW_MINIMUM, issueId = 42)
        assertThat(detector.shouldOpenIssue(v, t(11, 10, 8))).isFalse()
    }

    @Test fun `totalBelowMillis sums closed and open violations`() {
        val closed = Violation(sensorId = 1, startedAtMillis = 0, endedAtMillis = 3_600_000, minTempF = 60.0, requiredTempF = 68, kind = ViolationKind.BELOW_MINIMUM, issueId = null)
        val open = Violation(sensorId = 1, startedAtMillis = 10_000_000, endedAtMillis = null, minTempF = 60.0, requiredTempF = 64, kind = ViolationKind.BELOW_MINIMUM, issueId = null)
        val hot = Violation(sensorId = 1, startedAtMillis = 0, endedAtMillis = 3_600_000, minTempF = 80.0, requiredTempF = 78, kind = ViolationKind.ABOVE_MAXIMUM, issueId = null)
        assertThat(detector.totalBelowMillis(listOf(closed, open, hot), nowMillis = 11_800_000)).isEqualTo(3_600_000 + 1_800_000)
    }
}
