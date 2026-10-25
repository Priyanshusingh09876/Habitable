package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.model.ViolationKind
import edu.bu.cs501.habitable.domain.usecase.NoticeTemplate
import org.junit.Test

class NoticeTemplateTest {

    private val tenAm = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York
    private val h = 3_600_000L

    private val apartment = Apartment(
        address = "12 Pratt St", unit = "Apt 3", city = "Boston", latitude = 42.36, longitude = -71.06,
        landlordName = "Mark Rivera", landlordEmail = "landlord@example.com",
    )
    private val heatIssue = Issue(
        id = 1, apartmentId = 1, category = IssueCategory.HEAT, title = "No heat · living room", description = "",
        status = IssueStatus.OPEN, openedAtMillis = tenAm, closedAtMillis = null, openedAutomatically = true,
    )
    private val violations = listOf(
        Violation(sensorId = 1, startedAtMillis = tenAm - 14 * h, endedAtMillis = tenAm - 11 * h, minTempF = 64.6, requiredTempF = 68, kind = ViolationKind.BELOW_MINIMUM, issueId = 1),
        Violation(sensorId = 1, startedAtMillis = tenAm - 10 * h, endedAtMillis = null, minTempF = 59.1, requiredTempF = 64, kind = ViolationKind.BELOW_MINIMUM, issueId = 1),
    )

    @Test fun `heat notice is addressed to the landlord with the record`() {
        val n = NoticeTemplate.build(heatIssue, apartment, violations, tenAm)
        assertThat(n.to).isEqualTo("landlord@example.com")
        assertThat(n.subject).isEqualTo("Repair request: No heat · living room at 12 Pratt St, Apt 3, Boston")
        assertThat(n.body).startsWith("November 10, 2026")
        assertThat(n.body).contains("Dear Mark Rivera,")
        assertThat(n.body).contains("105 CMR 410.180")
        assertThat(n.body).contains("13 hours below the legal limit") // 3 h + 10 h
        assertThat(n.body).contains("a low of 59.1°F")
        assertThat(n.body).contains("not legal advice")
    }

    @Test fun `reminder uses follow-up wording`() {
        val n = NoticeTemplate.build(heatIssue, apartment, violations, tenAm, NoticeTemplate.Kind.REMINDER)
        assertThat(n.subject).startsWith("Reminder:")
        assertThat(n.body).contains("following up on my earlier written notice")
    }

    @Test fun `non-heat issue quotes the description`() {
        val leak = heatIssue.copy(category = IssueCategory.WATER, title = "Leak", description = "Water drips from the bathroom ceiling.")
        val n = NoticeTemplate.build(leak, apartment, emptyList(), tenAm)
        assertThat(n.body).contains("following problem in my apartment: leak. Water drips from the bathroom ceiling.")
        assertThat(n.body).doesNotContain("105 CMR")
    }

    @Test fun `missing apartment still produces a usable letter`() {
        val n = NoticeTemplate.build(heatIssue, null, emptyList(), tenAm)
        assertThat(n.to).isEmpty()
        assertThat(n.body).contains("Dear Landlord,")
        assertThat(n.body).contains("68°F from 7 AM to 11 PM")
    }
}
