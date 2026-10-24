package edu.bu.cs501.habitable.data.repository

import androidx.room.withTransaction
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.local.toDomain
import edu.bu.cs501.habitable.data.local.toEntity
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.NextAction
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.model.Violation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Issues and their timelines. The Escalate button is implemented here as
 * [advance]: it moves the issue one state forward and writes the matching
 * timeline event, so the UI never has to know the legal order.
 */
class IssuesRepository(
    private val db: HabitableDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val issues = db.issueDao()
    private val timeline = db.timelineDao()
    private val violations = db.violationDao()
    private val apartments = db.apartmentDao()

    val all: Flow<List<Issue>> = issues.observeAll().map { l -> l.map { it.toDomain() } }
    val open: Flow<List<Issue>> = issues.observeOpen().map { l -> l.map { it.toDomain() } }
    val openCount: Flow<Int> = issues.observeOpenCount()

    fun observe(id: Long): Flow<Issue?> = issues.observeById(id).map { it?.toDomain() }
    fun observeTimeline(id: Long): Flow<List<TimelineEvent>> = timeline.observeForIssue(id).map { l -> l.map { it.toDomain() } }
    fun observeViolations(id: Long): Flow<List<Violation>> = violations.observeForIssue(id).map { l -> l.map { it.toDomain() } }

    /** Manual "Report issue" flow. Returns the new id. */
    suspend fun create(category: IssueCategory, title: String, description: String): Long = db.withTransaction {
        val apartmentId = apartments.getFirst()?.id ?: 0L
        val now = clock()
        val id = issues.insert(
            Issue(
                apartmentId = apartmentId,
                category = category,
                title = title.trim(),
                description = description.trim(),
                status = IssueStatus.OPEN,
                openedAtMillis = now,
                closedAtMillis = null,
                openedAutomatically = false,
            ).toEntity()
        )
        timeline.insert(TimelineEvent(issueId = id, type = TimelineEventType.NOTE, occurredAtMillis = now, note = "Issue reported by tenant").toEntity())
        id
    }

    /**
     * Perform the issue's current next action. Returns the new status, or null
     * if there was nothing to do.
     */
    suspend fun advance(issueId: Long): IssueStatus? = db.withTransaction {
        val issue = issues.getById(issueId)?.toDomain() ?: return@withTransaction null
        val now = clock()
        val (next, event, note) = when (issue.status.nextAction) {
            NextAction.SEND_NOTICE -> Triple(
                IssueStatus.NOTICE_SENT, TimelineEventType.NOTICE_SENT,
                "Dated notice sent to landlord (template cites 105 CMR 410.180)",
            )
            NextAction.REQUEST_INSPECTION -> Triple(
                IssueStatus.INSPECTION_REQUESTED, TimelineEventType.INSPECTION_REQUESTED,
                "Inspection requested from ISD / Board of Health",
            )
            NextAction.MARK_CERTIFIED -> Triple(
                IssueStatus.CERTIFIED, TimelineEventType.VIOLATION_CERTIFIED,
                "Inspector certified the violation; repair timelines may now apply",
            )
            NextAction.SEND_REMINDER -> Triple(
                IssueStatus.CERTIFIED, TimelineEventType.REMINDER_SENT,
                "Reminder sent to landlord with a fresh timestamp",
            )
            NextAction.NONE -> return@withTransaction null
        }
        issues.update(issue.copy(status = next).toEntity())
        timeline.insert(TimelineEvent(issueId = issueId, type = event, occurredAtMillis = now, note = note).toEntity())
        next
    }

    suspend fun resolve(issueId: Long) = db.withTransaction {
        val issue = issues.getById(issueId)?.toDomain() ?: return@withTransaction
        val now = clock()
        issues.update(issue.copy(status = IssueStatus.RESOLVED, closedAtMillis = now).toEntity())
        timeline.insert(TimelineEvent(issueId = issueId, type = TimelineEventType.RESOLVED, occurredAtMillis = now, note = "Marked resolved by tenant").toEntity())
    }

    suspend fun addNote(issueId: Long, note: String) {
        if (note.isBlank()) return
        timeline.insert(TimelineEvent(issueId = issueId, type = TimelineEventType.NOTE, occurredAtMillis = clock(), note = note.trim()).toEntity())
    }
}
