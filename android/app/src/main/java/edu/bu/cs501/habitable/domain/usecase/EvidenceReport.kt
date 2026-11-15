package edu.bu.cs501.habitable.domain.usecase

import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Photo
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.model.ViolationKind
import edu.bu.cs501.habitable.domain.quality.DataQualityChecker
import edu.bu.cs501.habitable.domain.rules.HeatRules
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything the PDF evidence packet says, as plain text and numbers.
 *
 * The Android side (PdfEvidenceWriter) only lays this out on pages; every
 * sentence, total and date is decided here, in pure Kotlin, so the content of
 * the packet is unit-tested without a device.
 */
data class EvidenceReport(
    val title: String,
    val generatedLine: String,
    /** Label / value rows under the title: address, landlord, category, status, opened, closed. */
    val facts: List<Row>,
    val description: String,
    /** The legal rule this record is measured against; empty for non-heat issues. */
    val rule: String,
    /** Heat summary: hours below the limit, lowest temperature, number of cold periods. Empty for non-heat issues. */
    val summary: List<Row>,
    /** What the chart should show, or null when there are no sensor readings for this issue. */
    val chart: ChartData?,
    val chartCaption: String,
    val timeline: List<TimelineRow>,
    val photos: List<PhotoRow>,
    /** Honest notes about the data: gaps, jumps, simulated readings. */
    val dataNotes: List<String>,
    val disclaimer: String,
) {
    data class Row(val label: String, val value: String)
    data class TimelineRow(val time: String, val label: String, val note: String)
    data class PhotoRow(val fileName: String, val taken: String, val sha256: String, val anchored: String)

    companion object {
        const val DISCLAIMER = "Habitable documents conditions. It is not legal advice."
        const val RULE =
            "Massachusetts State Sanitary Code, 105 CMR 410.180: from September 15 to May 31 the owner must provide heat of at " +
                "least 68°F between 7:00 AM and 11:00 PM and at least 64°F at all other hours, and never more than 78°F."
    }
}

object BuildEvidenceReport {

    /** The chart shows at most this much time, ending at the last cold period. */
    const val MAX_CHART_MILLIS = 7L * 24 * 60 * 60 * 1000
    private const val HOUR = 60L * 60 * 1000

    operator fun invoke(
        issue: Issue,
        apartment: Apartment?,
        timeline: List<TimelineEvent>,
        violations: List<Violation>,
        photos: List<Photo>,
        readings: List<Reading>,
        outdoor: List<OutdoorReading>,
        nowMillis: Long,
        zone: ZoneId = HeatRules.BOSTON,
    ): EvidenceReport {
        val fmt = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US).withZone(zone)
        fun at(millis: Long) = fmt.format(Instant.ofEpochMilli(millis))

        val facts = buildList {
            apartment?.let { a ->
                val address = listOf(a.address, a.unit, a.city).filter(String::isNotBlank).joinToString(", ")
                if (address.isNotBlank()) add(EvidenceReport.Row("Address", address))
                val landlord = listOf(a.landlordName, a.landlordEmail).filter(String::isNotBlank).joinToString(" · ")
                if (landlord.isNotBlank()) add(EvidenceReport.Row("Landlord", landlord))
            }
            add(EvidenceReport.Row("Category", issue.category.label))
            add(EvidenceReport.Row("Status", issue.status.label))
            add(EvidenceReport.Row("Opened", at(issue.openedAtMillis) + if (issue.openedAutomatically) " (automatically, from sensor readings)" else ""))
            issue.closedAtMillis?.let { add(EvidenceReport.Row("Closed", at(it))) }
        }

        val cold = violations.filter { it.kind == ViolationKind.BELOW_MINIMUM }.sortedBy { it.startedAtMillis }
        val isHeat = issue.category == IssueCategory.HEAT || cold.isNotEmpty()

        val summary = if (cold.isEmpty()) emptyList() else {
            val belowMillis = cold.sumOf { it.durationMillis(nowMillis) }
            val lowest = cold.minBy { it.minTempF }
            listOf(
                EvidenceReport.Row("Time below the legal limit", hoursLabel(belowMillis)),
                EvidenceReport.Row("Lowest temperature", "${one(lowest.minTempF)}°F (limit ${lowest.requiredTempF}°F)"),
                EvidenceReport.Row("Cold periods", "${cold.size}, first from ${at(cold.first().startedAtMillis)}"),
            )
        }

        // Chart window: around the cold periods, padded by two hours, at most a week.
        val chart: ChartData?
        val caption: String
        if (cold.isEmpty() || readings.isEmpty()) {
            chart = null
            caption = ""
        } else {
            val to = minOf(nowMillis, (cold.last().endedAtMillis ?: nowMillis) + 2 * HOUR)
            val from = maxOf(cold.first().startedAtMillis - 2 * HOUR, to - MAX_CHART_MILLIS)
            val inWindow = readings.filter { it.recordedAtMillis in from..to }
            chart = if (inWindow.isEmpty()) null else BuildChartData(inWindow, outdoor.filter { it.timeMillis in from..to }, from, to)
            caption = if (chart == null) "" else "Indoor temperature against the legal limit, ${at(from)} to ${at(to)}. Shaded: below the limit."
        }

        val quality = DataQualityChecker().summarize(readings)
        val dataNotes = buildList {
            if (quality.total > 0) add("${quality.total} sensor readings in this record.")
            if (quality.simulated > 0) add("${quality.simulated} readings came from the app's simulated sensor (demo data), not a physical thermometer.")
            if (quality.gaps > 0) add("${quality.gaps} ${if (quality.gaps == 1) "gap" else "gaps"} of more than 30 minutes between readings. Time inside a gap is not counted as below the limit.")
            if (quality.jumps > 0) add("${quality.jumps} ${if (quality.jumps == 1) "reading" else "readings"} changed faster than 2°F per minute and ${if (quality.jumps == 1) "is" else "are"} flagged as implausible.")
            if (quality.total > 0 && quality.flagged == 0 && quality.simulated == 0) add("No gaps or implausible jumps were found in the readings.")
            if (photos.isNotEmpty()) add("Photo fingerprints (SHA-256) were taken at capture. Recompute the hash of a photo file to confirm it has not been changed.")
        }

        return EvidenceReport(
            title = issue.title,
            generatedLine = "Evidence packet generated ${at(nowMillis)} by Habitable",
            facts = facts,
            description = issue.description.trim(),
            rule = if (isHeat) EvidenceReport.RULE else "",
            summary = summary,
            chart = chart,
            chartCaption = caption,
            timeline = timeline.sortedBy { it.occurredAtMillis }.map {
                EvidenceReport.TimelineRow(time = at(it.occurredAtMillis), label = TimelineExport.label(it.type), note = it.note)
            },
            photos = photos.sortedBy { it.takenAtMillis }.map {
                EvidenceReport.PhotoRow(
                    fileName = it.fileName,
                    taken = at(it.takenAtMillis),
                    sha256 = it.sha256,
                    anchored = it.anchoredAtMillis?.let { t -> "Server first saw this fingerprint ${at(t)}" } ?: "Not yet backed up to the server",
                )
            },
            dataNotes = dataNotes,
            disclaimer = EvidenceReport.DISCLAIMER,
        )
    }

    /** "13 h 20 min", "45 min", "2 h". */
    fun hoursLabel(millis: Long): String {
        val minutes = (millis / 60_000L).coerceAtLeast(0)
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
    }

    private fun one(v: Double): String = String.format(Locale.US, "%.1f", v)
}
