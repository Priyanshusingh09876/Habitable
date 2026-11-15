package edu.bu.cs501.habitable.data.repository

import androidx.room.withTransaction
import edu.bu.cs501.habitable.data.local.HabitableDatabase
import edu.bu.cs501.habitable.data.local.entity.PhotoEntity
import edu.bu.cs501.habitable.data.local.toDomain
import edu.bu.cs501.habitable.data.local.toEntity
import edu.bu.cs501.habitable.data.photos.PhotoStore
import edu.bu.cs501.habitable.domain.model.Photo
import edu.bu.cs501.habitable.domain.model.TimelineEvent
import edu.bu.cs501.habitable.domain.model.TimelineEventType
import edu.bu.cs501.habitable.domain.usecase.Sha256
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos attached to issues (backlog US-4).
 *
 * The fingerprint is computed here, the moment the camera hands the file back,
 * and stored with the row. Photos are never edited in the app, so the hash in
 * the timeline, in the PDF and on the server always describes the same bytes.
 */
class PhotosRepository(
    private val db: HabitableDatabase,
    private val store: PhotoStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val photos = db.photoDao()
    private val timeline = db.timelineDao()

    fun observeForIssue(issueId: Long): Flow<List<Photo>> =
        photos.observeForIssue(issueId).map { list -> list.map { it.toDomain() } }

    suspend fun forIssue(issueId: Long): List<Photo> = photos.getForIssue(issueId).map { it.toDomain() }

    /** An empty file for the camera, and the address to hand to the camera app. */
    fun newCapture(): File = store.newPhotoFile(clock())
    fun uriFor(file: File) = store.uriFor(file)
    fun fileFor(photo: Photo): File = store.photoFile(photo.fileName)
    fun fileNamed(fileName: String): File = store.photoFile(fileName)

    /** Where an exported file (the PDF packet) is written before it is shared. */
    fun exportFile(fileName: String): File = store.exportFile(fileName)

    /**
     * Record a photo the camera just wrote to [file]: hash it, save the row and
     * add a timeline event, all or nothing. Returns null (and removes the file)
     * if the camera wrote nothing, for example because the user backed out.
     */
    suspend fun add(issueId: Long, file: File): Photo? {
        val hash = withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() == 0L) {
                file.delete()
                null
            } else {
                Sha256.of(file)
            }
        } ?: return null
        val now = clock()
        return db.withTransaction {
            val entity = PhotoEntity(issueId = issueId, fileName = file.name, takenAt = now, sha256 = hash, anchoredAt = null)
            val id = photos.insert(entity)
            timeline.insert(
                TimelineEvent(
                    issueId = issueId,
                    type = TimelineEventType.PHOTO_ADDED,
                    occurredAtMillis = now,
                    note = "Fingerprint ${hash.take(12)}… taken at capture",
                ).toEntity()
            )
            entity.copy(id = id).toDomain()
        }
    }

    /** The camera was cancelled: throw the empty file away. */
    suspend fun discard(file: File) = withContext(Dispatchers.IO) { file.delete(); Unit }

    /** Demo reset. The rows are removed with their issues by ReadingsRepository.clearAll(). */
    suspend fun deleteAllFiles() = withContext(Dispatchers.IO) { store.deleteAllPhotos() }
}
