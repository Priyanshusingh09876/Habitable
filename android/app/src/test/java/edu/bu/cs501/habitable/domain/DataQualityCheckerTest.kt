package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.ReadingFlag
import edu.bu.cs501.habitable.domain.quality.DataQualityChecker
import org.junit.Test

class DataQualityCheckerTest {

    private val checker = DataQualityChecker(expectedIntervalMillis = 10 * 60_000L, gapFactor = 3.0, maxDegreesPerMinute = 2.0)
    private fun r(temp: Double, minute: Int, flags: Set<ReadingFlag> = emptySet()) =
        Reading(sensorId = 1, tempF = temp, humidityPct = null, recordedAtMillis = minute * 60_000L, flags = flags)

    @Test fun `first reading has no flags beyond its own`() {
        assertThat(checker.flagsFor(null, r(68.0, 0))).isEmpty()
        assertThat(checker.flagsFor(null, r(68.0, 0, setOf(ReadingFlag.SIMULATED)))).containsExactly(ReadingFlag.SIMULATED)
    }

    @Test fun `a steady room is not flagged`() {
        assertThat(checker.flagsFor(r(68.0, 0), r(67.6, 10))).isEmpty()
    }

    @Test fun `a fridge-sized jump is flagged`() {
        // 70 -> 40 in ten minutes = 3 °F/min, above the 2 °F/min ceiling.
        assertThat(checker.flagsFor(r(70.0, 0), r(40.0, 10))).contains(ReadingFlag.IMPLAUSIBLE_JUMP)
    }

    @Test fun `a 35 minute silence is a gap, 25 minutes is not`() {
        assertThat(checker.flagsFor(r(68.0, 0), r(68.0, 35))).contains(ReadingFlag.GAP_BEFORE)
        assertThat(checker.flagsFor(r(68.0, 0), r(68.0, 25))).doesNotContain(ReadingFlag.GAP_BEFORE)
    }

    @Test fun `annotate sorts by time and summarize counts each flag`() {
        // Given out of order on purpose; annotate must sort before comparing neighbours.
        val out = checker.annotate(listOf(r(70.0, 20), r(68.0, 0), r(35.0, 25), r(36.0, 60)))
        assertThat(out.map { it.recordedAtMillis }).isInOrder()
        val s = checker.summarize(out)
        assertThat(s.total).isEqualTo(4)
        assertThat(s.jumps).isEqualTo(1)  // 70 -> 35 in 5 min = 7 °F/min
        assertThat(s.gaps).isEqualTo(1)   // 25 -> 60 = 35 min silence
        assertThat(s.flagged).isEqualTo(2)
        assertThat(s.allClear).isFalse()
    }

    @Test fun `summary all clear needs at least one reading`() {
        assertThat(checker.summarize(emptyList()).allClear).isFalse()
        assertThat(checker.summarize(listOf(r(68.0, 0))).allClear).isTrue()
    }
}
