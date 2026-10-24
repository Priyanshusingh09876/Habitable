package edu.bu.cs501.habitable.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import edu.bu.cs501.habitable.data.local.dao.ApartmentDao
import edu.bu.cs501.habitable.data.local.dao.IssueDao
import edu.bu.cs501.habitable.data.local.dao.OutdoorDao
import edu.bu.cs501.habitable.data.local.dao.ReadingDao
import edu.bu.cs501.habitable.data.local.dao.SensorDao
import edu.bu.cs501.habitable.data.local.dao.TimelineDao
import edu.bu.cs501.habitable.data.local.dao.ViolationDao
import edu.bu.cs501.habitable.data.local.entity.ApartmentEntity
import edu.bu.cs501.habitable.data.local.entity.IssueEntity
import edu.bu.cs501.habitable.data.local.entity.OutdoorReadingEntity
import edu.bu.cs501.habitable.data.local.entity.ReadingEntity
import edu.bu.cs501.habitable.data.local.entity.SensorEntity
import edu.bu.cs501.habitable.data.local.entity.TimelineEventEntity
import edu.bu.cs501.habitable.data.local.entity.ViolationEntity

/**
 * Room is the source of truth on the phone. Everything the UI shows comes from
 * here; the network only *adds* rows (weather cache) or marks rows synced.
 */
@Database(
    entities = [
        ApartmentEntity::class,
        SensorEntity::class,
        ReadingEntity::class,
        ViolationEntity::class,
        IssueEntity::class,
        TimelineEventEntity::class,
        OutdoorReadingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class HabitableDatabase : RoomDatabase() {
    abstract fun apartmentDao(): ApartmentDao
    abstract fun sensorDao(): SensorDao
    abstract fun readingDao(): ReadingDao
    abstract fun violationDao(): ViolationDao
    abstract fun issueDao(): IssueDao
    abstract fun timelineDao(): TimelineDao
    abstract fun outdoorDao(): OutdoorDao

    companion object {
        const val NAME = "habitable.db"

        @Volatile
        private var instance: HabitableDatabase? = null

        fun get(context: Context): HabitableDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitableDatabase::class.java,
                    NAME,
                )
                    // Schema is still moving during Update 1; wipe rather than crash on a version bump.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
