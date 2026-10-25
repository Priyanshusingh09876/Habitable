package edu.bu.cs501.habitable.domain.usecase

import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.model.ViolationKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Builds the dated written notice a tenant sends to the landlord.
 *
 * Pure Kotlin so the wording is unit-tested. The app never sends anything on
 * its own: the text is handed to the user's email app, where the tenant reads
 * it and presses Send.
 */
object NoticeTemplate {

    data class Notice(val to: String, val subject: String, val body: String)

    enum class Kind { NOTICE, REMINDER }

    fun build(
        issue: Issue,
        apartment: Apartment?,
        violations: List<Violation>,
        nowMillis: Long,
        kind: Kind = Kind.NOTICE,
        zone: ZoneId = ZoneId.of("America/New_York"),
    ): Notice {
        val date = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.US).withZone(zone)
        val dateTime = DateTimeFormatter.ofPattern("MMM d 'at' h:mm a", Locale.US).withZone(zone)
        fun d(millis: Long) = date.format(Instant.ofEpochMilli(millis))
        fun dt(millis: Long) = dateTime.format(Instant.ofEpochMilli(millis))

        val address = apartment?.let { listOf(it.address, it.unit, it.city).filter { s -> s.isNotBlank() }.joinToString(", ") }
            ?: "my apartment"
        val greeting = apartment?.landlordName?.takeIf { it.isNotBlank() }?.let { "Dear $it," } ?: "Dear Landlord,"

        val subject = when (kind) {
            Kind.NOTICE -> "Repair request: ${issue.title} at $address"
            Kind.REMINDER -> "Reminder: ${issue.title} at $address is not yet repaired"
        }

        val problem = if (issue.category == IssueCategory.HEAT) heatParagraph(violations, nowMillis, ::dt) else
            "I am writing to notify you in writing of the following problem in my apartment: ${issue.title.lowercase(Locale.US)}." +
                (if (issue.description.isNotBlank()) " ${issue.description.trim()}" else "")

        val opening = when (kind) {
            Kind.NOTICE -> "This is a dated written notice about a condition at $address that needs repair."
            Kind.REMINDER -> "I am following up on my earlier written notice about a condition at $address. The problem has not yet been repaired."
        }

        val body = buildString {
            appendLine(d(nowMillis))
            appendLine()
            appendLine(greeting)
            appendLine()
            appendLine(opening)
            appendLine()
            appendLine(problem)
            appendLine()
            appendLine("Please arrange for the repair as soon as possible and let me know when someone will come to fix it.")
            appendLine()
            appendLine("I have kept a dated record of this problem and can share it on request.")
            appendLine()
            appendLine("Sincerely,")
            appendLine("Tenant, $address")
            appendLine()
            appendLine("--")
            append("Sent with Habitable. This letter is documentation only, not legal advice. For your rights as a tenant, see masslegalhelp.org.")
        }

        return Notice(to = apartment?.landlordEmail.orEmpty(), subject = subject, body = body)
    }

    private fun heatParagraph(violations: List<Violation>, nowMillis: Long, dt: (Long) -> String): String {
        val below = violations.filter { it.kind == ViolationKind.BELOW_MINIMUM }
        if (below.isEmpty()) {
            return "The heat in my apartment is not meeting the minimum temperatures required by the Massachusetts State Sanitary Code, 105 CMR 410.180 (68°F from 7 AM to 11 PM and 64°F overnight, September 15 to May 31)."
        }
        val first = below.minOf { it.startedAtMillis }
        val lowest = below.minOf { it.minTempF }
        val hours = below.sumOf { it.durationMillis(nowMillis) } / 3_600_000.0
        return "Since ${dt(first)}, the temperature in my apartment has been below the minimum required by the Massachusetts State Sanitary Code, 105 CMR 410.180 (68°F from 7 AM to 11 PM and 64°F overnight, September 15 to May 31). " +
            "My temperature log shows ${String.format(Locale.US, "%.0f", hours)} hours below the legal limit, with a low of ${String.format(Locale.US, "%.1f", lowest)}°F."
    }
}
