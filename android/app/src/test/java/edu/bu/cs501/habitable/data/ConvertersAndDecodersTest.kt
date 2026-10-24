package edu.bu.cs501.habitable.data

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.local.Converters
import edu.bu.cs501.habitable.data.sensor.BleSensorSource
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.ReadingFlag
import edu.bu.cs501.habitable.ui.setup.SetupViewModel
import org.junit.Test

class ConvertersAndDecodersTest {

    @Test fun `flags round-trip through the string column`() {
        val flags = setOf(ReadingFlag.GAP_BEFORE, ReadingFlag.SIMULATED)
        val s = Converters.flagsToString(flags)
        assertThat(s).isEqualTo("GAP_BEFORE,SIMULATED")
        assertThat(Converters.stringToFlags(s)).isEqualTo(flags)
        assertThat(Converters.stringToFlags("")).isEmpty()
        assertThat(Converters.stringToFlags("BOGUS,SIMULATED")).containsExactly(ReadingFlag.SIMULATED)
    }

    @Test fun `unknown enum strings fall back to the default`() {
        assertThat(Converters.enumOrDefault("NOTICE_SENT", IssueStatus.OPEN)).isEqualTo(IssueStatus.NOTICE_SENT)
        assertThat(Converters.enumOrDefault("garbage", IssueStatus.OPEN)).isEqualTo(IssueStatus.OPEN)
    }

    @Test fun `issue status next actions follow the legal order`() {
        assertThat(IssueStatus.OPEN.nextAction.name).isEqualTo("SEND_NOTICE")
        assertThat(IssueStatus.NOTICE_SENT.nextAction.name).isEqualTo("REQUEST_INSPECTION")
        assertThat(IssueStatus.INSPECTION_REQUESTED.nextAction.name).isEqualTo("MARK_CERTIFIED")
        assertThat(IssueStatus.CERTIFIED.nextAction.name).isEqualTo("SEND_REMINDER")
        assertThat(IssueStatus.RESOLVED.nextAction.name).isEqualTo("NONE")
    }

    @Test fun `govee payload decodes to fahrenheit and humidity`() {
        // 21.5 °C, 45.0 % RH  ->  raw = 215000 + 450 = 215450 = 0x034 99A
        val raw = 215_450
        val payload = byteArrayOf(0x00, (raw shr 16).toByte(), (raw shr 8).toByte(), raw.toByte(), 0x64)
        val (tempF, rh) = BleSensorSource.decodeGovee(payload)!!
        assertThat(tempF).isWithin(0.1).of(70.7)
        assertThat(rh).isWithin(0.01).of(45.0)
    }

    @Test fun `govee decoder rejects short or absurd payloads`() {
        assertThat(BleSensorSource.decodeGovee(byteArrayOf(1, 2))).isNull()
        val absurd = byteArrayOf(0x00, 0x7F, 0xFF.toByte(), 0xFF.toByte())
        assertThat(BleSensorSource.decodeGovee(absurd)).isNull()
    }

    @Test fun `setup email validation`() {
        assertThat(SetupViewModel.isValidEmail("landlord@example.com")).isTrue()
        assertThat(SetupViewModel.isValidEmail("not an email")).isFalse()
        assertThat(SetupViewModel.isValidEmail("a@b")).isFalse()
    }
}
