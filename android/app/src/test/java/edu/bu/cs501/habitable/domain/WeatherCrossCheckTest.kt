package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.quality.WeatherCrossCheck
import org.junit.Test

class WeatherCrossCheckTest {

    private val check = WeatherCrossCheck()
    private val h = 3_600_000L
    private fun r(temp: Double, t: Long) = Reading(sensorId = 1, tempF = temp, humidityPct = null, recordedAtMillis = t)

    /** A cold Boston night: 38 °F at 6 PM down to 27 °F, back to 34 °F. */
    private val coldNightOutdoor = (0..16).map { i -> OutdoorReading(i * h, 27 + 11 * Math.pow((i - 10) / 10.0, 2.0) * (if (i < 10) 1.0 else 0.68)) }

    @Test fun `an honest cold apartment is not flagged`() {
        // The proposal's cold night: 70 -> 59 °F indoors while it is ~30 °F outside. Always > 4 °F apart.
        val indoor = SimulatedSensorSource.coldNight(0L).map { r(it.tempF, it.recordedAtMillis) }
        assertThat(check.suspiciousReadings(indoor, coldNightOutdoor)).isEmpty()
        assertThat(check.spans(indoor, coldNightOutdoor)).isEmpty()
    }

    @Test fun `a sensor hung out of the window tracks outdoors and is flagged as a span`() {
        // Indoor = outdoor + 1 °F for three hours in the middle of the night.
        val indoor = (0..16).map { i -> r(if (i in 6..8) coldNightOutdoor[i].tempF + 1.0 else 68.0, i * h) }
        val spans = check.spans(indoor, coldNightOutdoor)
        assertThat(spans).hasSize(1)
        assertThat(spans[0].startMillis).isEqualTo(6 * h)
        assertThat(spans[0].endMillis).isEqualTo(8 * h)
        assertThat(spans[0].minDeltaF).isWithin(0.01).of(1.0)
    }

    @Test fun `one odd sample is not a span`() {
        val indoor = (0..16).map { i -> r(if (i == 6) coldNightOutdoor[i].tempF else 68.0, i * h) }
        assertThat(check.suspiciousReadings(indoor, coldNightOutdoor)).hasSize(1)
        assertThat(check.spans(indoor, coldNightOutdoor)).isEmpty() // under the 1 h minimum
    }

    @Test fun `mild weather disables the comparison`() {
        // 62 °F outside, 62 °F inside: a cold room, but outdoors is not cold enough to say anything.
        val mild = (0..16).map { i -> OutdoorReading(i * h, 62.0) }
        val indoor = (0..16).map { i -> r(62.0, i * h) }
        assertThat(check.suspiciousReadings(indoor, mild)).isEmpty()
    }

    @Test fun `no outdoor data means no flags`() {
        val indoor = (0..16).map { i -> r(30.0, i * h) }
        assertThat(check.suspiciousReadings(indoor, emptyList())).isEmpty()
    }

    @Test fun `readings far from any outdoor hour are skipped`() {
        val sparse = listOf(OutdoorReading(0L, 30.0))
        val indoor = listOf(r(30.0, 0L), r(30.0, 5 * h))
        assertThat(check.suspiciousReadings(indoor, sparse).map { it.recordedAtMillis }).containsExactly(0L)
    }
}
