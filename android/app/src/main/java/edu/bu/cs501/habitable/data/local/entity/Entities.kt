package edu.bu.cs501.habitable.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities. Field names mirror the data model in docs/ARCHITECTURE.md.
 *
 * Readings, violations and timeline events are insert-only from the UI's point
 * of view: the DAOs expose no update/delete for them except the demo reset.
 */

@Entity(tableName = "apartments")
data class ApartmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    val unit: String,
    val city: String,
    val latitude: Double,
    val longitude: Double,
    @ColumnInfo(name = "landlord_name") val landlordName: String,
    @ColumnInfo(name = "landlord_email") val landlordEmail: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

@Entity(
    tableName = "sensors",
    foreignKeys = [
        ForeignKey(
            entity = ApartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["apartment_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("apartment_id")],
)
data class SensorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "apartment_id") val apartmentId: Long,
    /** "SIM" for the simulator, otherwise the BLE MAC address. */
    @ColumnInfo(name = "ble_address") val bleAddress: String,
    val model: String,
    val room: String,
    @ColumnInfo(name = "placement_photo_path") val placementPhotoPath: String?,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(
    tableName = "temperature_readings",
    foreignKeys = [
        ForeignKey(
            entity = SensorEntity::class,
            parentColumns = ["id"],
            childColumns = ["sensor_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sensor_id"), Index("recorded_at")],
)
data class ReadingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sensor_id") val sensorId: Long,
    @ColumnInfo(name = "temp_f") val tempF: Double,
    @ColumnInfo(name = "humidity_pct") val humidityPct: Double?,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    /** Comma-separated ReadingFlag names. Converted by [Converters]. */
    val flags: String,
    /** True once the backend has acknowledged this reading. */
    val synced: Boolean = false,
)

@Entity(
    tableName = "heat_violations",
    foreignKeys = [
        ForeignKey(
            entity = SensorEntity::class,
            parentColumns = ["id"],
            childColumns = ["sensor_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("sensor_id"), Index("issue_id")],
)
data class ViolationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "sensor_id") val sensorId: Long,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    @ColumnInfo(name = "ended_at") val endedAt: Long?,
    @ColumnInfo(name = "min_temp_f") val minTempF: Double,
    @ColumnInfo(name = "required_temp_f") val requiredTempF: Int,
    val kind: String,
    @ColumnInfo(name = "issue_id") val issueId: Long?,
)

@Entity(
    tableName = "issues",
    foreignKeys = [
        ForeignKey(
            entity = ApartmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["apartment_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("apartment_id"), Index("status")],
)
data class IssueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "apartment_id") val apartmentId: Long,
    val category: String,
    val title: String,
    val description: String,
    val status: String,
    @ColumnInfo(name = "opened_at") val openedAt: Long,
    @ColumnInfo(name = "closed_at") val closedAt: Long?,
    @ColumnInfo(name = "opened_automatically") val openedAutomatically: Boolean,
)

@Entity(
    tableName = "timeline_events",
    foreignKeys = [
        ForeignKey(
            entity = IssueEntity::class,
            parentColumns = ["id"],
            childColumns = ["issue_id"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("issue_id"), Index("occurred_at")],
)
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "issue_id") val issueId: Long,
    val type: String,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    val note: String,
)

/** Cached hourly outdoor temperature so the chart works offline. */
@Entity(
    tableName = "outdoor_readings",
    primaryKeys = ["apartment_id", "time_millis"],
    indices = [Index("time_millis")],
)
data class OutdoorReadingEntity(
    @ColumnInfo(name = "apartment_id") val apartmentId: Long,
    @ColumnInfo(name = "time_millis") val timeMillis: Long,
    @ColumnInfo(name = "temp_f") val tempF: Double,
    @ColumnInfo(name = "fetched_at") val fetchedAt: Long,
)
