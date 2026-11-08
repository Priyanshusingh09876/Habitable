package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.usecase.TimelineExport
import org.junit.Test

class TimelineExportTest {

    private val tenAm = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York
    private val h = 3_600_000L

    private val apartment = Apartment(
        address = "12 Pratt St", unit = "Apt 3", city = "Boston", latitude = 42.36, longitude = -71.06,
        landlordName = "Mark Rivera", landlordEmail = "landlord@example.com",
    )
    private val issue = Issue(
        id = 1, apartmentId = 1, category = IssueCategory.HEAT, title = "No heat · living room", description = "",
        status = IssueStatus.NOTICE_SENT, openedAtMillis = tenAm - 12 * h, closedAtMillis = null, openedAutomatically = true,
    )
    private fun ev(type: TimelineEventType, at: Long, note: String = "") = TimelineEvent(issueId = 1, type = type, occurredAtMillis = at, note = note)

    @Test fun `header has title, address, status and opened time`() {
        val text = TimelineExport.build(issue, apartment, emptyList(), tenAm)
        assertThat(text).startsWith("Habitable record: No heat · living room")
        assertThat(text).contains("Address: 12 Pratt St, Apt 3, Boston")
        assertThat(text).contains("Category: Heat · Status: Notice sent")
        assertThat(text).contains("Opened: Nov 9, 2026 10:00 PM (opened automatically by the sensor)")
        assertThat(text).contains("- No events yet")
        assertThat(text).endsWith("Documentation only, not legal advice.")
    }

    @Test fun `events are listed oldest first with labels and notes`() {
        val text = TimelineExport.build(
            issue, apartment,
            listOf(
                ev(TimelineEventType.NOTICE_SENT, tenAm),
                ev(TimelineEventType.SENSOR_VIOLATION_STARTED, tenAm - 12 * h, "Below 64 °F"),
            ),
            tenAm,
        )
        assertThat(text).contains("Timeline (2 events):")
        val first = text.indexOf("Below the legal limit: Below 64 °F")
        val second = text.indexOf("Notice sent", startIndex = text.indexOf("Timeline"))
        assertThat(first).isGreaterThan(0)
        assertThat(second).isGreaterThan(first)
    }

    @Test fun `missing apartment and a closed issue are handled`() {
        val closed = issue.copy(status = IssueStatus.RESOLVED, closedAtMillis = tenAm)
        val text = TimelineExport.build(closed, null, listOf(ev(TimelineEventType.RESOLVED, tenAm)), tenAm)
        assertThat(text).doesNotContain("Address:")
        assertThat(text).contains("Closed: Nov 10, 2026 10:00 AM")
        assertThat(text).contains("Timeline (1 event):")
    }
}
