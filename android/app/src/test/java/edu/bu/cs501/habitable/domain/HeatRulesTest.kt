package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.rules.HeatRules
import edu.bu.cs501.habitable.domain.rules.HeatStatus
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * The rules engine at its edges. A wrong answer here would put a false number
 * in a tenant's PDF, so these are the most important tests in the project.
 */
class HeatRulesTest {

    private fun at(month: Int, day: Int, hour: Int, minute: Int, year: Int = 2026): ZonedDateTime =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, HeatRules.BOSTON)

    // ---- heating season ------------------------------------------------

    @Test fun `season starts on September 15`() {
        assertThat(HeatRules.isHeatingSeason(LocalDate.of(2026, 9, 14))).isFalse()
        assertThat(HeatRules.isHeatingSeason(LocalDate.of(2026, 9, 15))).isTrue()
    }

    @Test fun `season ends on May 31`() {
        assertThat(HeatRules.isHeatingSeason(LocalDate.of(2027, 5, 31))).isTrue()
        assertThat(HeatRules.isHeatingSeason(LocalDate.of(2027, 6, 1))).isFalse()
    }

    @Test fun `season wraps the new year`() {
        assertThat(HeatRules.isHeatingSeason(LocalDate.of(2026, 12, 31))).isTrue()
        assertThat(HeatRules.isHeatingSeason(LocalDate.of(2027, 1, 1))).isTrue()
        assertThat(HeatRules.isHeatingSeason(LocalDate.of(2026, 7, 15))).isFalse()
    }

    // ---- day / night boundaries ----------------------------------------

    @Test fun `7 00 AM is daytime, 6 59 AM is night`() {
        assertThat(HeatRules.isDaytime(LocalTime.of(6, 59))).isFalse()
        assertThat(HeatRules.isDaytime(LocalTime.of(7, 0))).isTrue()
    }

    @Test fun `11 00 PM is still daytime, 11 01 PM is night`() {
        assertThat(HeatRules.isDaytime(LocalTime.of(23, 0))).isTrue()
        assertThat(HeatRules.isDaytime(LocalTime.of(23, 1))).isFalse()
        assertThat(HeatRules.isDaytime(LocalTime.of(10, 59))).isTrue()
    }

    @Test fun `required minimum is 68 by day and 64 at night in season`() {
        assertThat(HeatRules.requiredMinimumF(at(11, 9, 20, 0))).isEqualTo(68)
        assertThat(HeatRules.requiredMinimumF(at(11, 10, 3, 0))).isEqualTo(64)
    }

    @Test fun `no minimum outside the season`() {
        assertThat(HeatRules.requiredMinimumF(at(7, 4, 12, 0))).isNull()
        assertThat(HeatRules.maximumF(at(7, 4, 12, 0))).isNull()
    }

    @Test fun `epoch millis overload agrees with ZonedDateTime overload`() {
        val z = at(1, 15, 2, 30, year = 2027)
        assertThat(HeatRules.requiredMinimumF(z.toInstant().toEpochMilli())).isEqualTo(HeatRules.requiredMinimumF(z))
    }

    // ---- evaluate ------------------------------------------------------

    @Test fun `evaluate flags below minimum with the shortfall`() {
        val s = HeatRules.evaluate(61.4, at(11, 10, 10, 0))
        assertThat(s).isInstanceOf(HeatStatus.BelowMinimum::class.java)
        s as HeatStatus.BelowMinimum
        assertThat(s.requiredF).isEqualTo(68)
        assertThat(s.shortfallF).isWithin(0.001).of(6.6)
        assertThat(s.isViolation).isTrue()
    }

    @Test fun `evaluate is compliant at exactly the limit`() {
        assertThat(HeatRules.evaluate(68.0, at(11, 10, 10, 0))).isInstanceOf(HeatStatus.Compliant::class.java)
        assertThat(HeatRules.evaluate(64.0, at(11, 10, 3, 0))).isInstanceOf(HeatStatus.Compliant::class.java)
    }

    @Test fun `65 degrees is legal at night but not by day`() {
        assertThat(HeatRules.evaluate(65.0, at(11, 10, 3, 0))).isInstanceOf(HeatStatus.Compliant::class.java)
        assertThat(HeatRules.evaluate(65.0, at(11, 10, 9, 0))).isInstanceOf(HeatStatus.BelowMinimum::class.java)
    }

    @Test fun `evaluate flags above the 78 maximum`() {
        val s = HeatRules.evaluate(80.5, at(11, 10, 10, 0))
        assertThat(s).isInstanceOf(HeatStatus.AboveMaximum::class.java)
        assertThat((s as HeatStatus.AboveMaximum).excessF).isWithin(0.001).of(2.5)
    }

    @Test fun `evaluate is out of season in July regardless of temperature`() {
        assertThat(HeatRules.evaluate(40.0, at(7, 4, 12, 0))).isEqualTo(HeatStatus.OutOfSeason)
        assertThat(HeatStatus.OutOfSeason.isViolation).isFalse()
    }
}
