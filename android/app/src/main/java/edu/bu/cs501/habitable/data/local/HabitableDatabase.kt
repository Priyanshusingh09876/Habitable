package edu.bu.cs501.habitable.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import edu.bu.cs501.habitable.data.local.dao.ApartmentDao
import edu.bu.cs501.habitable.data.local.dao.IssueDao
import edu.bu.cs501.habitable.data.local.dao.OutdoorDao
import edu.bu.cs501.habitable.data.local.dao.PhotoDao
import edu.bu.cs501.habitable.data.local.dao.ReadingDao
import edu.bu.cs501.habitable.data.local.dao.SensorDao
import edu.bu.cs501.habitable.data.local.dao.TimelineDao
import edu.bu.cs501.habitable.data.local.dao.ViolationDao
import edu.bu.cs501.habitable.data.local.entity.ApartmentEntity
import edu.bu.cs501.habitable.data.local.entity.IssueEntity
import edu.bu.cs501.habitable.data.local.entity.OutdoorReadingEntity
import edu.bu.cs501.habitable.data.local.entity.PhotoEntity
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
        PhotoEntity::class,
    ],
    version = 2,
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
    abstract fun photoDao(): PhotoDao

    companion object {
        const val NAME = "habitable.db"

        /**
         * Update 2 adds the photos table. Nothing else changes, so existing readings,
         * issues and timelines are kept. The SQL is exactly what Room generates for
         * PhotoEntity (see schemas/…/2.json); Room checks it when the database opens.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `photos` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`issue_id` INTEGER NOT NULL, " +
                        "`file_name` TEXT NOT NULL, " +
                        "`taken_at` INTEGER NOT NULL, " +
                        "`sha256` TEXT NOT NULL, " +
                        "`anchored_at` INTEGER, " +
                        "FOREIGN KEY(`issue_id`) REFERENCES `issues`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_issue_id` ON `photos` (`issue_id`)")
            }
        }

        @Volatile
        private var instance: HabitableDatabase? = null

        fun get(context: Context): HabitableDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    HabitableDatabase::class.java,
                    NAME,
                )
                    // Version 1 -> 2 keeps every reading and issue (see MIGRATION_1_2).
                    .addMigrations(MIGRATION_1_2)
                    // Anything we have no migration for is a development build: wipe rather than crash.
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
