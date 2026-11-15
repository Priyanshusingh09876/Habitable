package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.usecase.ReminderRules
import org.junit.Test

class ReminderRulesTest {

    private val tenAm = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York
    private val day = 24 * 3_600_000L

    private fun issue(id: Long, status: IssueStatus) = Issue(
        id = id, apartmentId = 1, category = IssueCategory.HEAT, title = "No heat · living room", description = "",
        status = status, openedAtMillis = tenAm - 30 * day, closedAtMillis = null, openedAutomatically = true,
    )
    private fun ev(issueId: Long, type: TimelineEventType, daysAgo: Long) =
        TimelineEvent(issueId = issueId, type = type, occurredAtMillis = tenAm - daysAgo * day, note = "")

    @Test fun `a notice with no update for a week asks for an inspection`() {
        val due = ReminderRules.due(
            listOf(issue(1, IssueStatus.NOTICE_SENT)),
            mapOf(1L to listOf(ev(1, TimelineEventType.NOTICE_SENT, 7))),
            tenAm,
        )
        assertThat(due).hasSize(1)
        assertThat(due[0].issueId).isEqualTo(1L)
        assertThat(due[0].title).isEqualTo("No heat · living room")
        assertThat(due[0].text).isEqualTo("Notice sent 7 days ago with no update. Next step: ask the city for an inspection.")
    }

    @Test fun `nothing before the threshold and then once a week, not daily`() {
        fun dueAfter(days: Long) = ReminderRules.due(
            listOf(issue(1, IssueStatus.NOTICE_SENT)),
            mapOf(1L to listOf(ev(1, TimelineEventType.NOTICE_SENT, days))),
            tenAm,
        ).size
        assertThat(dueAfter(0)).isEqualTo(0)
        assertThat(dueAfter(6)).isEqualTo(0)
        assertThat(dueAfter(7)).isEqualTo(1)
        assertThat(dueAfter(8)).isEqualTo(0)
        assertThat(dueAfter(13)).isEqualTo(0)
        assertThat(dueAfter(14)).isEqualTo(1)
    }

    @Test fun `each step has its own wait and next step`() {
        val due = ReminderRules.due(
            listOf(issue(2, IssueStatus.INSPECTION_REQUESTED), issue(3, IssueStatus.CERTIFIED)),
            mapOf(
                2L to listOf(ev(2, TimelineEventType.NOTICE_SENT, 20), ev(2, TimelineEventType.INSPECTION_REQUESTED, 7)),
                3L to listOf(ev(3, TimelineEventType.VIOLATION_CERTIFIED, 3)),
            ),
            tenAm,
        )
        assertThat(due.map { it.issueId }).containsExactly(2L, 3L).inOrder()
        assertThat(due[0].text).contains("Has the inspector visited?")
        assertThat(due[1].text).isEqualTo("Violation certified 3 days ago with no update. Next step: send the landlord a reminder.")
    }

    @Test fun `sending a reminder letter restarts the clock for a certified issue`() {
        val events = listOf(ev(3, TimelineEventType.VIOLATION_CERTIFIED, 10), ev(3, TimelineEventType.REMINDER_SENT, 1))
        assertThat(ReminderRules.due(listOf(issue(3, IssueStatus.CERTIFIED)), mapOf(3L to events), tenAm)).isEmpty()
    }

    @Test fun `open, resolved and issues without the step event are left alone`() {
        val due = ReminderRules.due(
            listOf(issue(4, IssueStatus.OPEN), issue(5, IssueStatus.RESOLVED), issue(6, IssueStatus.NOTICE_SENT)),
            mapOf(
                4L to listOf(ev(4, TimelineEventType.NOTE, 30)),
                5L to listOf(ev(5, TimelineEventType.NOTICE_SENT, 7)),
                // issue 6 has no NOTICE_SENT event recorded
            ),
            tenAm,
        )
        assertThat(due).isEmpty()
    }
}
