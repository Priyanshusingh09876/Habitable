package edu.bu.cs501.habitable.domain.usecase

import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.rules.HeatRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Turns an issue and its timeline into plain text the tenant can share with
 * legal aid, a housing inspector or a friend (Share button on Issue detail).
 * Events are listed oldest first with exact dates and times. Pure Kotlin.
 */
object TimelineExport {

    fun build(
        issue: Issue,
        apartment: Apartment?,
        timeline: List<TimelineEvent>,
        nowMillis: Long,
        zone: ZoneId = HeatRules.BOSTON,
    ): String {
        val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US).withZone(zone)
        fun at(millis: Long) = fmt.format(Instant.ofEpochMilli(millis))

        val address = apartment?.let { listOf(it.address, it.unit, it.city).filter(String::isNotBlank).joinToString(", ") }
        return buildString {
            appendLine("Habitable record: ${issue.title}")
            if (!address.isNullOrBlank()) appendLine("Address: $address")
            appendLine("Category: ${issue.category.label} · Status: ${issue.status.label}")
            appendLine("Opened: ${at(issue.openedAtMillis)}" + if (issue.openedAutomatically) " (opened automatically by the sensor)" else "")
            issue.closedAtMillis?.let { appendLine("Closed: ${at(it)}") }
            if (issue.description.isNotBlank()) {
                appendLine()
                appendLine(issue.description)
            }
            appendLine()
            appendLine("Timeline (${timeline.size} ${if (timeline.size == 1) "event" else "events"}):")
            if (timeline.isEmpty()) appendLine("- No events yet")
            timeline.sortedBy { it.occurredAtMillis }.forEach { e ->
                val note = if (e.note.isBlank()) "" else ": ${e.note}"
                appendLine("- ${at(e.occurredAtMillis)} · ${label(e.type)}$note")
            }
            appendLine()
            append("Exported ${at(nowMillis)} from Habitable. Documentation only, not legal advice.")
        }
    }

    fun label(type: TimelineEventType): String = when (type) {
        TimelineEventType.SENSOR_VIOLATION_STARTED -> "Below the legal limit"
        TimelineEventType.SENSOR_VIOLATION_ENDED -> "Back within the limit"
        TimelineEventType.PHOTO_ADDED -> "Photo added"
        TimelineEventType.NOTICE_SENT -> "Notice sent"
        TimelineEventType.INSPECTION_REQUESTED -> "Inspection requested"
        TimelineEventType.VIOLATION_CERTIFIED -> "Violation certified"
        TimelineEventType.REMINDER_SENT -> "Reminder sent"
        TimelineEventType.LANDLORD_REPLY -> "Landlord replied"
        TimelineEventType.NOTE -> "Note"
        TimelineEventType.RESOLVED -> "Resolved"
    }
}
