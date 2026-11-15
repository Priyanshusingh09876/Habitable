package edu.bu.cs501.habitable.domain

import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.Photo
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.ReadingFlag
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.model.ViolationKind
import edu.bu.cs501.habitable.domain.usecase.BuildEvidenceReport
import edu.bu.cs501.habitable.domain.usecase.EvidenceReport
import org.junit.Test

class EvidenceReportTest {

    private val tenAm = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York
    private val h = 3_600_000L

    private val apartment = Apartment(
        address = "12 Pratt St", unit = "Apt 3", city = "Boston", latitude = 42.36, longitude = -71.06,
        landlordName = "Mark Rivera", landlordEmail = "landlord@example.com",
    )
    private val issue = Issue(
        id = 1, apartmentId = 1, category = IssueCategory.HEAT, title = "No heat · living room", description = "Radiator cold since last night.",
        status = IssueStatus.NOTICE_SENT, openedAtMillis = tenAm - 10 * h, closedAtMillis = null, openedAutomatically = true,
    )
    // Cold from midnight to 8 AM (8 h, lowest 59.1) and again from 9 AM, still open at 10 AM (1 h).
    private val night = Violation(id = 1, sensorId = 1, startedAtMillis = tenAm - 10 * h, endedAtMillis = tenAm - 2 * h, minTempF = 59.1, requiredTempF = 64, kind = ViolationKind.BELOW_MINIMUM, issueId = 1)
    private val morning = Violation(id = 2, sensorId = 1, startedAtMillis = tenAm - 1 * h, endedAtMillis = null, minTempF = 61.4, requiredTempF = 68, kind = ViolationKind.BELOW_MINIMUM, issueId = 1)

    private fun reading(at: Long, tempF: Double, vararg flags: ReadingFlag) =
        Reading(sensorId = 1, tempF = tempF, humidityPct = null, recordedAtMillis = at, flags = flags.toSet())
    private val readings = (0..60).map { reading(tenAm - 10 * h + it * 10 * 60_000L, 60.0 + (it % 5)) }

    private fun build(
        violations: List<Violation> = listOf(night, morning),
        photos: List<Photo> = emptyList(),
        timeline: List<TimelineEvent> = emptyList(),
        readingList: List<Reading> = readings,
        theIssue: Issue = issue,
        theApartment: Apartment? = apartment,
    ) = BuildEvidenceReport(theIssue, theApartment, timeline, violations, photos, readingList, emptyList(), tenAm)

    @Test fun `facts identify the place, the landlord and the issue`() {
        val r = build()
        assertThat(r.title).isEqualTo("No heat · living room")
        assertThat(r.generatedLine).isEqualTo("Evidence packet generated Nov 10, 2026 10:00 AM by Habitable")
        assertThat(r.facts.map { it.label }).containsExactly("Address", "Landlord", "Category", "Status", "Opened").inOrder()
        assertThat(r.facts[0].value).isEqualTo("12 Pratt St, Apt 3, Boston")
        assertThat(r.facts[1].value).isEqualTo("Mark Rivera · landlord@example.com")
        assertThat(r.facts[4].value).isEqualTo("Nov 10, 2026 12:00 AM (automatically, from sensor readings)")
        assertThat(r.description).isEqualTo("Radiator cold since last night.")
        assertThat(r.disclaimer).isEqualTo(EvidenceReport.DISCLAIMER)
    }

    @Test fun `summary adds closed and open cold periods and reports the lowest`() {
        val r = build()
        assertThat(r.rule).contains("105 CMR 410.180")
        assertThat(r.summary[0].value).isEqualTo("9 h")                       // 8 h closed + 1 h still open
        assertThat(r.summary[1].value).isEqualTo("59.1°F (limit 64°F)")
        assertThat(r.summary[2].value).isEqualTo("2, first from Nov 10, 2026 12:00 AM")
    }

    @Test fun `the chart covers the cold periods and has readings to draw`() {
        val r = build()
        assertThat(r.chart).isNotNull()
        assertThat(r.chart!!.indoor).isNotEmpty()
        assertThat(r.chart!!.toMillis).isEqualTo(tenAm)                       // open period: the chart ends now, not in the future
        assertThat(r.chart!!.fromMillis).isEqualTo(tenAm - 12 * h)            // two hours before the first cold reading
        assertThat(r.chartCaption).startsWith("Indoor temperature against the legal limit, Nov 9, 2026 10:00 PM")
    }

    @Test fun `timeline and photos are listed oldest first with full fingerprints`() {
        val hashA = "a".repeat(64)
        val hashB = "b".repeat(64)
        val r = build(
            timeline = listOf(
                TimelineEvent(issueId = 1, type = TimelineEventType.NOTICE_SENT, occurredAtMillis = tenAm, note = "Dated notice sent"),
                TimelineEvent(issueId = 1, type = TimelineEventType.SENSOR_VIOLATION_STARTED, occurredAtMillis = tenAm - 10 * h, note = ""),
            ),
            photos = listOf(
                Photo(id = 2, issueId = 1, fileName = "photo_2.jpg", takenAtMillis = tenAm, sha256 = hashB, anchoredAtMillis = null),
                Photo(id = 1, issueId = 1, fileName = "photo_1.jpg", takenAtMillis = tenAm - h, sha256 = hashA, anchoredAtMillis = tenAm - h + 60_000L),
            ),
        )
        assertThat(r.timeline.map { it.label }).containsExactly("Below the legal limit", "Notice sent").inOrder()
        assertThat(r.photos.map { it.sha256 }).containsExactly(hashA, hashB).inOrder()
        assertThat(r.photos[0].anchored).isEqualTo("Server first saw this fingerprint Nov 10, 2026 9:01 AM")
        assertThat(r.photos[1].anchored).isEqualTo("Not yet backed up to the server")
        assertThat(r.dataNotes.last()).contains("SHA-256")
    }

    @Test fun `data notes are honest about simulated readings, gaps and jumps`() {
        val flagged = listOf(
            reading(tenAm - 3 * h, 62.0, ReadingFlag.SIMULATED),
            reading(tenAm - 2 * h, 61.0, ReadingFlag.SIMULATED, ReadingFlag.GAP_BEFORE),
            reading(tenAm - 1 * h, 75.0, ReadingFlag.SIMULATED, ReadingFlag.IMPLAUSIBLE_JUMP),
        )
        val notes = build(readingList = flagged).dataNotes
        assertThat(notes[0]).isEqualTo("3 sensor readings in this record.")
        assertThat(notes[1]).contains("3 readings came from the app's simulated sensor")
        assertThat(notes[2]).contains("1 gap of more than 30 minutes")
        assertThat(notes[3]).contains("1 reading changed faster than 2°F per minute and is flagged")

        val clean = listOf(reading(tenAm - 2 * h, 62.0), reading(tenAm - 2 * h + 600_000L, 62.1))
        assertThat(build(readingList = clean).dataNotes).contains("No gaps or implausible jumps were found in the readings.")
    }

    @Test fun `a non-heat issue has no rule, summary or chart and still builds`() {
        val leak = issue.copy(category = IssueCategory.WATER, title = "Bathroom ceiling leak", openedAutomatically = false, closedAtMillis = tenAm)
        val r = build(violations = emptyList(), readingList = emptyList(), theIssue = leak, theApartment = null)
        assertThat(r.rule).isEmpty()
        assertThat(r.summary).isEmpty()
        assertThat(r.chart).isNull()
        assertThat(r.facts.map { it.label }).containsExactly("Category", "Status", "Opened", "Closed").inOrder()
        assertThat(r.dataNotes).isEmpty()
    }

    @Test fun `durations read naturally`() {
        assertThat(BuildEvidenceReport.hoursLabel(45 * 60_000L)).isEqualTo("45 min")
        assertThat(BuildEvidenceReport.hoursLabel(2 * h)).isEqualTo("2 h")
        assertThat(BuildEvidenceReport.hoursLabel(13 * h + 20 * 60_000L)).isEqualTo("13 h 20 min")
        assertThat(BuildEvidenceReport.hoursLabel(-5L)).isEqualTo("0 min")
    }
}
