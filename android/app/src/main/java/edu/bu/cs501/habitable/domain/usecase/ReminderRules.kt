package edu.bu.cs501.habitable.domain.usecase

import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.rules.HeatRules
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Decides which open issues deserve a nudge today. An issue that has sat in the
 * same step for a while gets a reminder naming the next step, then one more each
 * week, never daily. Pure Kotlin: the worker only fetches data and posts what
 * this returns.
 *
 * - Notice sent, nothing since for [NOTICE_DAYS] days  -> ask the city to inspect
 * - Inspection requested, [INSPECTION_DAYS] days       -> record what the inspector found
 * - Violation certified, [CERTIFIED_DAYS] days         -> send the landlord a reminder
 */
object ReminderRules {
    const val NOTICE_DAYS = 7L
    const val INSPECTION_DAYS = 7L
    const val CERTIFIED_DAYS = 3L
    const val REPEAT_DAYS = 7L

    data class Reminder(val issueId: Long, val title: String, val text: String)

    fun due(
        issues: List<Issue>,
        timelines: Map<Long, List<TimelineEvent>>,
        nowMillis: Long,
        zone: ZoneId = HeatRules.BOSTON,
    ): List<Reminder> = issues.mapNotNull { issue ->
        val events = timelines[issue.id].orEmpty()
        val (stepType, threshold, nextStep) = when (issue.status) {
            IssueStatus.NOTICE_SENT -> Triple(TimelineEventType.NOTICE_SENT, NOTICE_DAYS, "Next step: ask the city for an inspection.")
            IssueStatus.INSPECTION_REQUESTED -> Triple(TimelineEventType.INSPECTION_REQUESTED, INSPECTION_DAYS, "Has the inspector visited? Record the result.")
            // A reminder letter restarts the clock, so the tenant is not nagged right after sending one.
            IssueStatus.CERTIFIED -> Triple(TimelineEventType.VIOLATION_CERTIFIED, CERTIFIED_DAYS, "Next step: send the landlord a reminder.")
            else -> return@mapNotNull null // open-without-notice and resolved issues are left alone
        }
        val since = events
            .filter { it.type == stepType || (issue.status == IssueStatus.CERTIFIED && it.type == TimelineEventType.REMINDER_SENT) }
            .maxOfOrNull { it.occurredAtMillis } ?: return@mapNotNull null
        val days = days(since, nowMillis, zone)
        if (days < threshold || (days - threshold) % REPEAT_DAYS != 0L) return@mapNotNull null
        Reminder(
            issueId = issue.id,
            title = issue.title,
            text = "${issue.status.label} ${plural(days)} ago with no update. $nextStep",
        )
    }

    /** Calendar days in Boston, the same way IssueClock counts them. */
    private fun days(from: Long, to: Long, zone: ZoneId): Long {
        val a = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val b = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(a, b).coerceAtLeast(0)
    }

    private fun plural(n: Long) = if (n == 1L) "1 day" else "$n days"
}
