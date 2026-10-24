package edu.bu.cs501.habitable.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import edu.bu.cs501.habitable.data.local.entity.ApartmentEntity
import edu.bu.cs501.habitable.data.local.entity.IssueEntity
import edu.bu.cs501.habitable.data.local.entity.OutdoorReadingEntity
import edu.bu.cs501.habitable.data.local.entity.ReadingEntity
import edu.bu.cs501.habitable.data.local.entity.SensorEntity
import edu.bu.cs501.habitable.data.local.entity.TimelineEventEntity
import edu.bu.cs501.habitable.data.local.entity.ViolationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApartmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(apartment: ApartmentEntity): Long

    @Query("SELECT * FROM apartments ORDER BY id LIMIT 1")
    fun observeFirst(): Flow<ApartmentEntity?>

    @Query("SELECT * FROM apartments ORDER BY id LIMIT 1")
    suspend fun getFirst(): ApartmentEntity?

    @Query("DELETE FROM apartments")
    suspend fun deleteAll()
}

@Dao
interface SensorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(sensor: SensorEntity): Long

    @Query("SELECT * FROM sensors WHERE apartment_id = :apartmentId ORDER BY id")
    fun observeForApartment(apartmentId: Long): Flow<List<SensorEntity>>

    @Query("SELECT * FROM sensors WHERE ble_address = :address LIMIT 1")
    suspend fun findByAddress(address: String): SensorEntity?

    @Query("SELECT * FROM sensors ORDER BY id LIMIT 1")
    suspend fun getFirst(): SensorEntity?

    @Query("DELETE FROM sensors")
    suspend fun deleteAll()
}

@Dao
interface ReadingDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reading: ReadingEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(readings: List<ReadingEntity>): List<Long>

    @Query(
        """
        SELECT * FROM temperature_readings
        WHERE sensor_id = :sensorId AND recorded_at BETWEEN :fromMillis AND :toMillis
        ORDER BY recorded_at ASC
        """
    )
    fun observeBetween(sensorId: Long, fromMillis: Long, toMillis: Long): Flow<List<ReadingEntity>>

    @Query(
        """
        SELECT * FROM temperature_readings
        WHERE sensor_id = :sensorId AND recorded_at BETWEEN :fromMillis AND :toMillis
        ORDER BY recorded_at ASC
        """
    )
    suspend fun getBetween(sensorId: Long, fromMillis: Long, toMillis: Long): List<ReadingEntity>

    @Query("SELECT * FROM temperature_readings WHERE sensor_id = :sensorId ORDER BY recorded_at DESC LIMIT 1")
    fun observeLatest(sensorId: Long): Flow<ReadingEntity?>

    @Query("SELECT * FROM temperature_readings WHERE sensor_id = :sensorId ORDER BY recorded_at DESC LIMIT 1")
    suspend fun getLatest(sensorId: Long): ReadingEntity?

    @Query("SELECT * FROM temperature_readings WHERE synced = 0 ORDER BY recorded_at ASC LIMIT :limit")
    suspend fun getUnsynced(limit: Int): List<ReadingEntity>

    @Query("UPDATE temperature_readings SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM temperature_readings WHERE sensor_id = :sensorId")
    fun observeCount(sensorId: Long): Flow<Int>

    /** Demo reset only. Readings are otherwise insert-only. */
    @Query("DELETE FROM temperature_readings")
    suspend fun deleteAll()
}

@Dao
interface ViolationDao {
    @Insert
    suspend fun insert(violation: ViolationEntity): Long

    @Update
    suspend fun update(violation: ViolationEntity)

    @Query("SELECT * FROM heat_violations WHERE sensor_id = :sensorId AND ended_at IS NULL ORDER BY started_at DESC LIMIT 1")
    suspend fun getOpen(sensorId: Long): ViolationEntity?

    @Query(
        """
        SELECT * FROM heat_violations
        WHERE sensor_id = :sensorId AND (ended_at IS NULL OR ended_at >= :fromMillis)
        ORDER BY started_at ASC
        """
    )
    fun observeSince(sensorId: Long, fromMillis: Long): Flow<List<ViolationEntity>>

    @Query("SELECT * FROM heat_violations WHERE issue_id = :issueId ORDER BY started_at ASC")
    fun observeForIssue(issueId: Long): Flow<List<ViolationEntity>>

    @Query("DELETE FROM heat_violations")
    suspend fun deleteAll()
}

@Dao
interface IssueDao {
    @Insert
    suspend fun insert(issue: IssueEntity): Long

    @Update
    suspend fun update(issue: IssueEntity)

    @Query("SELECT * FROM issues WHERE id = :id")
    fun observeById(id: Long): Flow<IssueEntity?>

    @Query("SELECT * FROM issues WHERE id = :id")
    suspend fun getById(id: Long): IssueEntity?

    @Query("SELECT * FROM issues ORDER BY CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END, opened_at DESC")
    fun observeAll(): Flow<List<IssueEntity>>

    @Query("SELECT * FROM issues WHERE status != 'RESOLVED' ORDER BY opened_at DESC")
    fun observeOpen(): Flow<List<IssueEntity>>

    @Query("SELECT COUNT(*) FROM issues WHERE status != 'RESOLVED'")
    fun observeOpenCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM issues
        WHERE category = 'HEAT' AND opened_automatically = 1 AND status != 'RESOLVED'
        ORDER BY opened_at DESC LIMIT 1
        """
    )
    suspend fun getOpenAutoHeatIssue(): IssueEntity?

    @Query("DELETE FROM issues")
    suspend fun deleteAll()
}

@Dao
interface TimelineDao {
    @Insert
    suspend fun insert(event: TimelineEventEntity): Long

    @Query("SELECT * FROM timeline_events WHERE issue_id = :issueId ORDER BY occurred_at ASC")
    fun observeForIssue(issueId: Long): Flow<List<TimelineEventEntity>>

    @Query("DELETE FROM timeline_events")
    suspend fun deleteAll()
}

@Dao
interface OutdoorDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(readings: List<OutdoorReadingEntity>)

    @Query(
        """
        SELECT * FROM outdoor_readings
        WHERE apartment_id = :apartmentId AND time_millis BETWEEN :fromMillis AND :toMillis
        ORDER BY time_millis ASC
        """
    )
    fun observeBetween(apartmentId: Long, fromMillis: Long, toMillis: Long): Flow<List<OutdoorReadingEntity>>

    @Query("SELECT MAX(fetched_at) FROM outdoor_readings WHERE apartment_id = :apartmentId")
    fun observeLastFetched(apartmentId: Long): Flow<Long?>

    @Query("DELETE FROM outdoor_readings WHERE time_millis < :beforeMillis")
    suspend fun pruneBefore(beforeMillis: Long)

    @Query("DELETE FROM outdoor_readings")
    suspend fun deleteAll()
}
