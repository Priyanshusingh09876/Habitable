package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.rules.LimitSeries
import edu.bu.cs501.habitable.domain.usecase.BuildChartData
import org.junit.Test
import java.time.ZonedDateTime

class LimitSeriesAndChartDataTest {

    private fun t(month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, HeatRules.BOSTON).toInstant().toEpochMilli()

    @Test fun `limit series steps at 11 01 PM and 7 AM`() {
        val pts = LimitSeries.build(t(11, 9, 18), t(11, 10, 10))
        // First point at 6 PM = 68, last at 10 AM = 68.
        assertThat(pts.first().limitF).isEqualTo(68)
        assertThat(pts.last().limitF).isEqualTo(68)
        // Exactly two steps: down at 23:01, up at 07:00 -> values go 68,68,64,64,68,68
        assertThat(pts.map { it.limitF }).containsExactly(68, 68, 64, 64, 68, 68).inOrder()
        val stepDown = pts[2].timeMillis
        val stepUp = pts[4].timeMillis
        assertThat(stepDown).isEqualTo(t(11, 9, 23, 1))
        assertThat(stepUp).isEqualTo(t(11, 10, 7))
        // The point just before each step shares the step's time minus 1 ns -> same millisecond.
        assertThat(pts[1].timeMillis).isEqualTo(stepDown - 1)
    }

    @Test fun `limit series is empty for an inverted window`() {
        assertThat(LimitSeries.build(t(11, 10, 10), t(11, 9, 18))).isEmpty()
    }

    @Test fun `nextBoundary moves day start to night start to next day start`() {
        val sixPm = ZonedDateTime.of(2026, 11, 9, 18, 0, 0, 0, HeatRules.BOSTON)
        val nightStart = LimitSeries.nextBoundary(sixPm)
        assertThat(nightStart.hour).isEqualTo(23)
        assertThat(nightStart.minute).isEqualTo(1)
        val dayStart = LimitSeries.nextBoundary(nightStart)
        assertThat(dayStart.hour).isEqualTo(7)
        assertThat(dayStart.dayOfMonth).isEqualTo(10)
        assertThat(LimitSeries.nextBoundary(ZonedDateTime.of(2026, 11, 10, 3, 0, 0, 0, HeatRules.BOSTON)).hour).isEqualTo(7)
    }

    @Test fun `chart data finds violation spans and axis bounds`() {
        val readings = listOf(
            Reading(sensorId = 1, tempF = 70.0, humidityPct = null, recordedAtMillis = t(11, 9, 18)),
            Reading(sensorId = 1, tempF = 66.0, humidityPct = null, recordedAtMillis = t(11, 9, 20)),
            Reading(sensorId = 1, tempF = 65.0, humidityPct = null, recordedAtMillis = t(11, 9, 22)),
            Reading(sensorId = 1, tempF = 64.5, humidityPct = null, recordedAtMillis = t(11, 9, 23, 30)),
            Reading(sensorId = 1, tempF = 60.0, humidityPct = null, recordedAtMillis = t(11, 10, 2)),
            Reading(sensorId = 1, tempF = 70.0, humidityPct = null, recordedAtMillis = t(11, 10, 10)),
        )
        val outdoor = listOf(OutdoorReading(t(11, 9, 18), 38.0), OutdoorReading(t(11, 10, 4), 27.0), OutdoorReading(t(11, 12, 4), 50.0))
        val data = BuildChartData(readings, outdoor, t(11, 9, 18), t(11, 10, 10))

        assertThat(data.indoor).hasSize(6)
        assertThat(data.outdoor).hasSize(2) // the Nov 12 point is outside the window
        assertThat(data.violationSpans).containsExactly(
            t(11, 9, 20) to t(11, 9, 23, 30),
            t(11, 10, 2) to t(11, 10, 10),
        ).inOrder()
        assertThat(data.minF).isEqualTo(20.0)
        assertThat(data.maxF).isEqualTo(80.0)
        assertThat(data.isEmpty).isFalse()
    }

    @Test fun `chart data with no readings is empty but still has a limit line`() {
        val data = BuildChartData(emptyList(), emptyList(), t(11, 9, 18), t(11, 10, 10))
        assertThat(data.isEmpty).isTrue()
        assertThat(data.limit).isNotEmpty()
        assertThat(data.violationSpans).isEmpty()
    }
}
