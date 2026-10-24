package edu.bu.cs501.habitable.data

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.rules.ViolationDetector
import edu.bu.cs501.habitable.domain.model.Reading
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class SimulatedSensorSourceTest {

    private val nov10TenAm = ZonedDateTime.of(2026, 11, 10, 10, 0, 0, 0, HeatRules.BOSTON).toInstant().toEpochMilli()

    @Test fun `cold night has one sample every ten minutes for sixteen hours`() {
        val start = nov10TenAm - 16 * 3_600_000L
        val samples = SimulatedSensorSource.coldNight(start)
        assertThat(samples).hasSize(97)
        assertThat(samples.first().recordedAtMillis).isEqualTo(start)
        assertThat(samples.last().recordedAtMillis).isEqualTo(nov10TenAm)
        assertThat(samples.all { it.simulated }).isTrue()
        assertThat(samples.zipWithNext().all { (a, b) -> b.recordedAtMillis - a.recordedAtMillis == 600_000L }).isTrue()
    }

    @Test fun `cold night starts warm, bottoms near 59 and ends at 61 point 4`() {
        val samples = SimulatedSensorSource.coldNight(nov10TenAm - 16 * 3_600_000L)
        assertThat(samples.first().tempF).isWithin(0.3).of(70.4)
        assertThat(samples.minOf { it.tempF }).isWithin(0.5).of(59.1)
        assertThat(samples.last().tempF).isEqualTo(61.4)
    }

    @Test fun `cold night produces about thirteen hours below the legal limit`() {
        val samples = SimulatedSensorSource.coldNight(nov10TenAm - 16 * 3_600_000L)
        val readings = samples.map { Reading(sensorId = 1, tempF = it.tempF, humidityPct = it.humidityPct, recordedAtMillis = it.recordedAtMillis) }
        val violations = ViolationDetector().detectAll(readings)
        val belowHours = ViolationDetector().totalBelowMillis(violations, nov10TenAm) / 3_600_000.0
        assertThat(belowHours).isWithin(1.0).of(13.0)
        // The limit drop to 64 °F at 11 PM briefly makes the room legal, so there are two runs.
        assertThat(violations.size).isAtLeast(2)
    }

    @Test fun `replay mode with speedup zero emits everything immediately`() = runTest {
        val source = SimulatedSensorSource(SimulatedSensorSource.Mode.Replay(endMillis = nov10TenAm, speedup = 0))
        val all = source.samples().toList()
        assertThat(all).hasSize(97)
    }

    @Test fun `live mode emits on the configured interval`() = runTest {
        var now = 1_000_000L
        val source = SimulatedSensorSource(SimulatedSensorSource.Mode.Live(69.5), intervalMillis = 1_000L, clock = { now })
        source.samples().test {
            val first = awaitItem()
            assertThat(first.tempF).isWithin(1.5).of(69.5)
            now += 1_000
            advanceTimeBy(1_001)
            val second = awaitItem()
            assertThat(second.recordedAtMillis).isEqualTo(1_001_000L)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun `interpolation is continuous at the knots`() {
        assertThat(SimulatedSensorSource.interpolate(0.0)).isWithin(0.001).of(70.4)
        assertThat(SimulatedSensorSource.interpolate(10.0)).isWithin(0.001).of(59.2)
        assertThat(SimulatedSensorSource.interpolate(16.0)).isWithin(0.001).of(61.4)
        assertThat(SimulatedSensorSource.interpolate(99.0)).isWithin(0.001).of(61.4)
    }
}
