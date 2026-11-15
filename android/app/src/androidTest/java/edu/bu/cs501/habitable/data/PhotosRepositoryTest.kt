package edu.bu.cs501.habitable.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.photos.PhotoStore
import edu.bu.cs501.habitable.data.repository.ApartmentRepository
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.data.repository.PhotosRepository
import edu.bu.cs501.habitable.domain.model.Apartment
import edu.bu.cs501.habitable.domain.model.IssueCategory
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.usecase.Sha256
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Photos on a real Room database and the app's real private storage:
 * the fingerprint is taken when the photo is added, the timeline records it,
 * and an empty capture leaves nothing behind.
 */
@RunWith(AndroidJUnit4::class)
class PhotosRepositoryTest {

    private lateinit var db: HabitableDatabase
    private lateinit var store: PhotoStore
    private lateinit var photos: PhotosRepository
    private lateinit var issues: IssuesRepository
    private var issueId = 0L

    private val tenAm = 1_794_322_800_000L // 2026-11-10 10:00 America/New_York

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HabitableDatabase::class.java).allowMainThreadQueries().build()
        store = PhotoStore(context)
        store.deleteAllPhotos()
        photos = PhotosRepository(db, store, clock = { tenAm })
        issues = IssuesRepository(db, clock = { tenAm })
        ApartmentRepository(db).save(
            Apartment(address = "12 Pratt St", unit = "3", city = "Boston", latitude = 42.36, longitude = -71.06, landlordName = "M. Rivera", landlordEmail = "m@example.com")
        )
        issueId = issues.create(IssueCategory.MOLD, "Mold behind the radiator", "")
    }

    @After
    fun tearDown() {
        store.deleteAllPhotos()
        db.close()
    }

    @Test
    fun addingAPhoto_storesItsFingerprint_andWritesATimelineEvent() = runTest {
        // Stand in for the camera: write some bytes into the file it was given.
        val bytes = ByteArray(20_000) { (it % 97).toByte() }
        val file = photos.newCapture().apply { writeBytes(bytes) }

        val photo = photos.add(issueId, file)

        assertThat(photo).isNotNull()
        assertThat(photo!!.sha256).isEqualTo(Sha256.of(bytes))
        assertThat(photo.takenAtMillis).isEqualTo(tenAm)
        assertThat(photo.anchoredAtMillis).isNull()
        assertThat(photos.fileFor(photo).exists()).isTrue()

        val stored = photos.observeForIssue(issueId).first()
        assertThat(stored).hasSize(1)
        assertThat(stored.single().fileName).isEqualTo(file.name)

        val event = issues.observeTimeline(issueId).first().last()
        assertThat(event.type).isEqualTo(TimelineEventType.PHOTO_ADDED)
        assertThat(event.note).contains(photo.shortHash)
    }

    @Test
    fun anEmptyCapture_isDiscarded_andLeavesNoRowOrFile() = runTest {
        // The user opened the camera and backed out: the file exists but is empty.
        val file = photos.newCapture().apply { createNewFile() }

        assertThat(photos.add(issueId, file)).isNull()

        assertThat(file.exists()).isFalse()
        assertThat(photos.observeForIssue(issueId).first()).isEmpty()
        assertThat(issues.observeTimeline(issueId).first().none { it.type == TimelineEventType.PHOTO_ADDED }).isTrue()
    }
}
