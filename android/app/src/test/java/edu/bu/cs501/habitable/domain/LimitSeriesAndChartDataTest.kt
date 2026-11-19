package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.rules.LimitSeries
import edu.bu.cs501.habitable.domain.usecase.BuildChartData
import edu.bu.cs501.habitable.domain.usecase.ChartData
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

    // --- Thinning a month of readings for drawing ---

    private fun pts(values: List<Double>) = values.mapIndexed { i, v -> ChartData.Pt(i * 60_000L, v) }

    @Test fun `short series are drawn exactly as recorded`() {
        val few = pts(listOf(70.0, 69.0, 68.5))
        assertThat(BuildChartData.downsample(few, 800)).isEqualTo(few)
    }

    @Test fun `a month of readings is thinned but keeps the coldest dip, the ends and time order`() {
        // 30 days at one reading a minute, steady 70 °F with one 3-minute dip to 55 °F in the middle.
        val values = MutableList(43_200) { 70.0 }
        values[20_000] = 58.0; values[20_001] = 55.0; values[20_002] = 57.0
        val all = pts(values)

        val thin = BuildChartData.downsample(all, 800)

        assertThat(thin.size).isAtMost(800)
        assertThat(thin.first()).isEqualTo(all.first())
        assertThat(thin.last()).isEqualTo(all.last())
        assertThat(thin.minOf { it.valueF }).isEqualTo(55.0)                 // the dip survives
        assertThat(thin.map { it.timeMillis }).isInOrder()
    }

    @Test fun `cold spans and the axis use every reading, not the thinned line`() {
        // One reading a minute for 30 hours from 6 PM; a single 60 °F reading at 2 AM (limit 64 °F).
        val start = t(11, 9, 18)
        val readings = (0 until 30 * 60).map { m ->
            val at = start + m * 60_000L
            Reading(sensorId = 1, tempF = if (at == t(11, 10, 2)) 60.0 else 70.0, humidityPct = null, recordedAtMillis = at)
        }
        val data = BuildChartData(readings, emptyList(), start, start + 30 * 3_600_000L, maxPoints = 100)
        assertThat(data.indoor.size).isAtMost(100)
        assertThat(data.violationSpans).hasSize(1)
        assertThat(data.violationSpans.single().first).isEqualTo(t(11, 10, 2))
        assertThat(data.minF).isEqualTo(50.0)                                  // 60 - 2, rounded down to the 10 °F grid
    }
}
