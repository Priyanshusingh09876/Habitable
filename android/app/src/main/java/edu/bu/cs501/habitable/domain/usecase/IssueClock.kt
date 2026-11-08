package edu.bu.cs501.habitable.domain.usecase

import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.rules.HeatRules
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * How long a problem has gone unfixed, in calendar days (Boston time):
 * days since the issue opened (until it was resolved) and days since the
 * first written notice. Shown on Issue detail so the tenant can tell their
 * landlord, legal aid or an inspector exactly how long they have waited.
 */
object IssueClock {

    data class Summary(val daysOpen: Long, val daysSinceNotice: Long?, val resolved: Boolean)

    fun summarize(
        issue: Issue,
        timeline: List<TimelineEvent>,
        nowMillis: Long,
        zone: ZoneId = HeatRules.BOSTON,
    ): Summary {
        val end = issue.closedAtMillis ?: nowMillis
        val notice = timeline
            .filter { it.type == TimelineEventType.NOTICE_SENT }
            .minOfOrNull { it.occurredAtMillis }
        return Summary(
            daysOpen = days(issue.openedAtMillis, end, zone),
            daysSinceNotice = notice?.let { days(it, end, zone) },
            resolved = issue.closedAtMillis != null,
        )
    }

    /** "Open 3 days · notice sent 2 days ago", "Open since today", "Was open 5 days". */
    fun label(s: Summary): String {
        val open = when {
            s.resolved -> "Was open ${plural(s.daysOpen)}"
            s.daysOpen == 0L -> "Open since today"
            else -> "Open ${plural(s.daysOpen)}"
        }
        val notice = when {
            s.daysSinceNotice == null -> ""
            s.resolved -> ""
            s.daysSinceNotice == 0L -> " · notice sent today"
            else -> " · notice sent ${plural(s.daysSinceNotice)} ago"
        }
        return open + notice
    }

    private fun days(from: Long, to: Long, zone: ZoneId): Long {
        val a = Instant.ofEpochMilli(from).atZone(zone).toLocalDate()
        val b = Instant.ofEpochMilli(to).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(a, b).coerceAtLeast(0)
    }

    private fun plural(n: Long) = if (n == 1L) "1 day" else "$n days"
}
