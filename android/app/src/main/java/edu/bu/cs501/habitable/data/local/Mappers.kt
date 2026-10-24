package edu.bu.cs501.habitable.data.local

import edu.bu.cs501.habitable.data.local.entity.ApartmentEntity
import edu.bu.cs501.habitable.data.local.entity.IssueEntity
import edu.bu.cs501.habitable.data.local.entity.OutdoorReadingEntity
import edu.bu.cs501.habitable.data.local.entity.ReadingEntity
import edu.bu.cs501.habitable.data.local.entity.TimelineEventEntity
import edu.bu.cs501.habitable.data.local.entity.ViolationEntity
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.Issue
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.IssueStatus
import edu.bu.cs501.habitable.domain.model.OutdoorReading
import edu.bu.cs501.habitable.domain.model.Reading
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.model.Violation
import edu.bu.cs501.habitable.domain.model.ViolationKind

/** Entity <-> domain mapping. One place, so the UI never sees a Room type. */

fun ReadingEntity.toDomain() = Reading(
    id = id,
    sensorId = sensorId,
    tempF = tempF,
    humidityPct = humidityPct,
    recordedAtMillis = recordedAt,
    flags = Converters.stringToFlags(flags),
)

fun Reading.toEntity(synced: Boolean = false) = ReadingEntity(
    id = id,
    sensorId = sensorId,
    tempF = tempF,
    humidityPct = humidityPct,
    recordedAt = recordedAtMillis,
    flags = Converters.flagsToString(flags),
    synced = synced,
)

fun ViolationEntity.toDomain() = Violation(
    id = id,
    sensorId = sensorId,
    startedAtMillis = startedAt,
    endedAtMillis = endedAt,
    minTempF = minTempF,
    requiredTempF = requiredTempF,
    kind = Converters.enumOrDefault(kind, ViolationKind.BELOW_MINIMUM),
    issueId = issueId,
)

fun Violation.toEntity() = ViolationEntity(
    id = id,
    sensorId = sensorId,
    startedAt = startedAtMillis,
    endedAt = endedAtMillis,
    minTempF = minTempF,
    requiredTempF = requiredTempF,
    kind = kind.name,
    issueId = issueId,
)

fun IssueEntity.toDomain() = Issue(
    id = id,
    apartmentId = apartmentId,
    category = Converters.enumOrDefault(category, IssueCategory.OTHER),
    title = title,
    description = description,
    status = Converters.enumOrDefault(status, IssueStatus.OPEN),
    openedAtMillis = openedAt,
    closedAtMillis = closedAt,
    openedAutomatically = openedAutomatically,
)

fun Issue.toEntity() = IssueEntity(
    id = id,
    apartmentId = apartmentId,
    category = category.name,
    title = title,
    description = description,
    status = status.name,
    openedAt = openedAtMillis,
    closedAt = closedAtMillis,
    openedAutomatically = openedAutomatically,
)

fun TimelineEventEntity.toDomain() = TimelineEvent(
    id = id,
    issueId = issueId,
    type = Converters.enumOrDefault(type, TimelineEventType.NOTE),
    occurredAtMillis = occurredAt,
    note = note,
)

fun TimelineEvent.toEntity() = TimelineEventEntity(
    id = id,
    issueId = issueId,
    type = type.name,
    occurredAt = occurredAtMillis,
    note = note,
)

fun ApartmentEntity.toDomain() = Apartment(
    id = id,
    address = address,
    unit = unit,
    city = city,
    latitude = latitude,
    longitude = longitude,
    landlordName = landlordName,
    landlordEmail = landlordEmail,
)

fun Apartment.toEntity(createdAt: Long) = ApartmentEntity(
    id = id,
    address = address,
    unit = unit,
    city = city,
    latitude = latitude,
    longitude = longitude,
    landlordName = landlordName,
    landlordEmail = landlordEmail,
    createdAt = createdAt,
)

fun OutdoorReadingEntity.toDomain() = OutdoorReading(timeMillis = timeMillis, tempF = tempF)
