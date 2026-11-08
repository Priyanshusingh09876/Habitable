package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.usecase.IssueClock
import org.junit.Test

class IssueClockTest {

    private val tenAm = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York
    private val h = 3_600_000L
    private val day = 24 * h

    private val issue = Issue(
        id = 1, apartmentId = 1, category = IssueCategory.HEAT, title = "No heat", description = "",
        status = IssueStatus.NOTICE_SENT, openedAtMillis = tenAm - 3 * day, closedAtMillis = null, openedAutomatically = true,
    )
    private fun notice(at: Long) = TimelineEvent(issueId = 1, type = TimelineEventType.NOTICE_SENT, occurredAtMillis = at, note = "")

    @Test fun `counts calendar days open and since the first notice`() {
        val s = IssueClock.summarize(issue, listOf(notice(tenAm - day), notice(tenAm - 2 * day)), tenAm)
        assertThat(s.daysOpen).isEqualTo(3L)
        assertThat(s.daysSinceNotice).isEqualTo(2L) // the first notice counts, not the latest
        assertThat(IssueClock.label(s)).isEqualTo("Open 3 days · notice sent 2 days ago")
    }

    @Test fun `same day and no notice read naturally`() {
        val today = issue.copy(openedAtMillis = tenAm - 2 * h)
        assertThat(IssueClock.label(IssueClock.summarize(today, emptyList(), tenAm))).isEqualTo("Open since today")
        assertThat(IssueClock.label(IssueClock.summarize(today, listOf(notice(tenAm - h)), tenAm)))
            .isEqualTo("Open since today · notice sent today")
    }

    @Test fun `a resolved issue stops counting at its close date`() {
        val closed = issue.copy(status = IssueStatus.RESOLVED, closedAtMillis = tenAm - 2 * day)
        val s = IssueClock.summarize(closed, listOf(notice(tenAm - 3 * day)), tenAm + 10 * day)
        assertThat(s.daysOpen).isEqualTo(1L)
        assertThat(IssueClock.label(s)).isEqualTo("Was open 1 day")
    }
}
