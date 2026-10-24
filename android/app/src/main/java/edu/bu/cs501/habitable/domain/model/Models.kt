package edu.bu.cs501.habitable.domain.model

/**
 * Plain domain models used by the rules engine and the UI.
 * The Room entities in data/local map to and from these.
 */

data class Reading(
    val id: Long = 0,
    val sensorId: Long,
    val tempF: Double,
    val humidityPct: Double?,
    val recordedAtMillis: Long,
    val flags: Set<ReadingFlag> = emptySet(),
)

enum class ReadingFlag {
    /** Temperature changed implausibly fast compared with the previous reading. */
    IMPLAUSIBLE_JUMP,

    /** More than the expected interval elapsed since the previous reading. */
    GAP_BEFORE,

    /** Produced by the simulator, not a physical sensor. */
    SIMULATED,
}

/** A contiguous run of readings below (or above) the legal limit. */
data class Violation(
    val id: Long = 0,
    val sensorId: Long,
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val minTempF: Double,
    val requiredTempF: Int,
    val kind: ViolationKind,
    val issueId: Long?,
) {
    val isOpen: Boolean get() = endedAtMillis == null
    fun durationMillis(nowMillis: Long): Long = (endedAtMillis ?: nowMillis) - startedAtMillis
}

enum class ViolationKind { BELOW_MINIMUM, ABOVE_MAXIMUM }

enum class IssueCategory(val label: String) {
    HEAT("Heat"),
    WATER("Water / leak"),
    MOLD("Mold"),
    PESTS("Pests"),
    ELECTRICAL("Electrical"),
    NOISE("Noise"),
    LOCKS("Locks / security"),
    OTHER("Other"),
}

/**
 * Lifecycle of an issue. The order of these states is the order in which the
 * Escalate button moves the tenant through MGL c.111 §127L: notify in writing,
 * get an inspection, then the statutory repair timelines may apply.
 */
enum class IssueStatus(val label: String) {
    OPEN("Open"),
    NOTICE_SENT("Notice sent"),
    INSPECTION_REQUESTED("Inspection requested"),
    CERTIFIED("Violation certified"),
    RESOLVED("Resolved");

    /** What the primary action button should say for an issue in this state. */
    val nextAction: NextAction
        get() = when (this) {
            OPEN -> NextAction.SEND_NOTICE
            NOTICE_SENT -> NextAction.REQUEST_INSPECTION
            INSPECTION_REQUESTED -> NextAction.MARK_CERTIFIED
            CERTIFIED -> NextAction.SEND_REMINDER
            RESOLVED -> NextAction.NONE
        }
}

enum class NextAction { SEND_NOTICE, REQUEST_INSPECTION, MARK_CERTIFIED, SEND_REMINDER, NONE }

data class Issue(
    val id: Long = 0,
    val apartmentId: Long,
    val category: IssueCategory,
    val title: String,
    val description: String,
    val status: IssueStatus,
    val openedAtMillis: Long,
    val closedAtMillis: Long?,
    val openedAutomatically: Boolean,
)

enum class TimelineEventType {
    SENSOR_VIOLATION_STARTED,
    SENSOR_VIOLATION_ENDED,
    PHOTO_ADDED,
    NOTICE_SENT,
    INSPECTION_REQUESTED,
    VIOLATION_CERTIFIED,
    REMINDER_SENT,
    LANDLORD_REPLY,
    NOTE,
    RESOLVED,
}

data class TimelineEvent(
    val id: Long = 0,
    val issueId: Long,
    val type: TimelineEventType,
    val occurredAtMillis: Long,
    val note: String,
)

data class Apartment(
    val id: Long = 0,
    val address: String,
    val unit: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    val landlordName: String,
    val landlordEmail: String,
)

/** One hour of outdoor weather from Open-Meteo. */
data class OutdoorReading(
    val timeMillis: Long,
    val tempF: Double,
)
