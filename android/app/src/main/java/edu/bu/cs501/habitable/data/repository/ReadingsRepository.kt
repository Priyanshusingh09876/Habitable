package edu.bu.cs501.habitable.data.repository

import androidx.room.withTransaction
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.local.toDomain
import edu.bu.cs501.habitable.data.local.toEntity
import edu.bu.cs501.habitable.data.sensor.SensorSample
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.ReadingFlag
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.quality.DataQualityChecker
import edu.bu.cs501.habitable.domain.rules.ViolationDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The heart of the vertical slice.
 *
 * ingest(): sample -> quality flags -> Room insert -> violation step -> auto issue.
 *
 * Everything the Home and Sensors screens show is a Flow from Room, so the UI
 * updates the moment a row lands, whether it came from the simulator, the BLE
 * service, or the "Replay cold night" button.
 */
class ReadingsRepository(
    private val db: HabitableDatabase,
    private val quality: DataQualityChecker = DataQualityChecker(),
    private val detector: ViolationDetector = ViolationDetector(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val readings = db.readingDao()
    private val violations = db.violationDao()
    private val issues = db.issueDao()
    private val timeline = db.timelineDao()
    private val sensors = db.sensorDao()

    // ---------------------------------------------------------------- observe

    fun observeReadings(sensorId: Long, fromMillis: Long, toMillis: Long): Flow<List<Reading>> =
        readings.observeBetween(sensorId, fromMillis, toMillis).map { list -> list.map { it.toDomain() } }

    fun observeLatest(sensorId: Long): Flow<Reading?> =
        readings.observeLatest(sensorId).map { it?.toDomain() }

    fun observeViolationsSince(sensorId: Long, fromMillis: Long): Flow<List<Violation>> =
        violations.observeSince(sensorId, fromMillis).map { list -> list.map { it.toDomain() } }

    fun observeCount(sensorId: Long): Flow<Int> = readings.observeCount(sensorId)

    // ----------------------------------------------------------------- ingest

    /**
     * Persist one sample and run the rules. Returns what happened so the
     * foreground service can post a notification when an issue opens.
     */
    suspend fun ingest(sensorId: Long, sample: SensorSample): IngestResult = db.withTransaction {
        val previous = readings.getLatest(sensorId)?.toDomain()
        val baseFlags = if (sample.simulated) setOf(ReadingFlag.SIMULATED) else emptySet()
        val candidate = Reading(
            sensorId = sensorId,
            tempF = sample.tempF,
            humidityPct = sample.humidityPct,
            recordedAtMillis = sample.recordedAtMillis,
            flags = baseFlags,
        )
        val flagged = candidate.copy(flags = quality.flagsFor(previous, candidate))
        val readingId = readings.insert(flagged.toEntity())
        val stored = flagged.copy(id = readingId)

        val open = violations.getOpen(sensorId)?.toDomain()
        var issueOpened: Issue? = null
        var violationNow: Violation? = open

        when (val step = detector.step(open, stored)) {
            is ViolationDetector.StepResult.Continue -> {
                violations.update(step.violation.toEntity())
                violationNow = step.violation
            }
            is ViolationDetector.StepResult.Opened -> {
                step.closed?.let { closeViolation(it) }
                val id = violations.insert(step.opened.toEntity())
                violationNow = step.opened.copy(id = id)
            }
            is ViolationDetector.StepResult.Closed -> {
                closeViolation(step.violation)
                violationNow = null
            }
            ViolationDetector.StepResult.Nothing -> Unit
        }

        // Open an issue once the cold has lasted long enough to be worth recording.
        val v = violationNow
        if (v != null && detector.shouldOpenIssue(v, stored.recordedAtMillis)) {
            val (issue, created) = linkOrOpenIssue(sensorId, v, stored.recordedAtMillis)
            if (created) issueOpened = issue
            violationNow = v.copy(issueId = issue.id)
            violations.update(violationNow.toEntity())
        }

        IngestResult(reading = stored, openViolation = violationNow, issueOpened = issueOpened)
    }

    private suspend fun closeViolation(v: Violation) {
        violations.update(v.toEntity())
        val issueId = v.issueId ?: return
        timeline.insert(
            TimelineEvent(
                issueId = issueId,
                type = TimelineEventType.SENSOR_VIOLATION_ENDED,
                occurredAtMillis = v.endedAtMillis ?: clock(),
                note = "Temperature back above ${v.requiredTempF}°F",
            ).toEntity()
        )
    }

    /**
     * One cold night can dip, recover briefly when the limit drops to 64 °F at
     * 11 PM, then dip again. That is one problem, not two, so later violations
     * attach to the open automatic heat issue instead of opening another.
     */
    private suspend fun linkOrOpenIssue(sensorId: Long, v: Violation, nowMillis: Long): Pair<Issue, Boolean> {
        issues.getOpenAutoHeatIssue()?.toDomain()?.let { existing ->
            timeline.insert(
                TimelineEvent(
                    issueId = existing.id,
                    type = TimelineEventType.SENSOR_VIOLATION_STARTED,
                    occurredAtMillis = v.startedAtMillis,
                    note = "Below ${v.requiredTempF}°F limit again (${fmt(v.minTempF)}°F)",
                ).toEntity()
            )
            return existing to false
        }
        return openIssueFor(sensorId, v, nowMillis) to true
    }

    private suspend fun openIssueFor(sensorId: Long, v: Violation, nowMillis: Long): Issue {
        val sensor = sensors.getFirst()
        val apartmentId = sensor?.apartmentId ?: 0L
        val room = sensor?.room ?: "Living room"
        val issue = Issue(
            apartmentId = apartmentId,
            category = IssueCategory.HEAT,
            title = "No heat · ${room.lowercase()}",
            description = "Temperature fell to ${fmt(v.minTempF)}°F, below the ${v.requiredTempF}°F limit, starting ${timeLabel(v.startedAtMillis)}.",
            status = IssueStatus.OPEN,
            openedAtMillis = nowMillis,
            closedAtMillis = null,
            openedAutomatically = true,
        )
        val id = issues.insert(issue.toEntity())
        timeline.insert(
            TimelineEvent(
                issueId = id,
                type = TimelineEventType.SENSOR_VIOLATION_STARTED,
                occurredAtMillis = v.startedAtMillis,
                note = "Below ${v.requiredTempF}°F limit (opened automatically from readings)",
            ).toEntity()
        )
        return issue.copy(id = id)
    }

    // ------------------------------------------------------------------- demo

    /**
     * Insert the proposal's cold night ending now. Runs every sample through the
     * same ingest() path, so violations and the auto-issue appear exactly as they
     * would with a real sensor. Used by the "Replay cold night" button and tests.
     */
    suspend fun replayColdNight(sensorId: Long, endMillis: Long = clock()): ReplaySummary {
        val start = endMillis - SimulatedSensorSource.REPLAY_HOURS * 3_600_000L
        val samples = SimulatedSensorSource.coldNight(start)
        var opened = 0
        var last: IngestResult? = null
        for (s in samples) {
            val r = ingest(sensorId, s)
            if (r.issueOpened != null) opened++
            last = r
        }
        return ReplaySummary(samples = samples.size, issuesOpened = opened, lastReading = last?.reading)
    }

    /** Demo-only reset so a presentation can start from a clean slate. */
    suspend fun clearAll() = db.withTransaction {
        timeline.deleteAll()
        violations.deleteAll()
        issues.deleteAll()
        readings.deleteAll()
        db.outdoorDao().deleteAll()
    }

    // ------------------------------------------------------------------ stats

    suspend fun qualitySummary(sensorId: Long, fromMillis: Long, toMillis: Long) =
        quality.summarize(readings.getBetween(sensorId, fromMillis, toMillis).map { it.toDomain() })

    fun totalBelowMillis(violationsList: List<Violation>, nowMillis: Long): Long =
        detector.totalBelowMillis(violationsList, nowMillis)

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.1f", v)

    private fun timeLabel(millis: Long): String =
        DateTimeFormatter.ofPattern("MMM d, h:mm a").format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

data class IngestResult(
    val reading: Reading,
    val openViolation: Violation?,
    val issueOpened: Issue?,
)

data class ReplaySummary(val samples: Int, val issuesOpened: Int, val lastReading: Reading?)
