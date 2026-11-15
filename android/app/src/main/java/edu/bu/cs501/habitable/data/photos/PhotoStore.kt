package edu.bu.cs501.habitable.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Where evidence files live on the phone.
 *
 * - Photos: files/evidence/photos, private to the app and excluded from cloud
 *   backup (res/xml/backup_rules.xml), so a photo leaves the phone only when the
 *   tenant shares it.
 * - Exports (the PDF packet): cache/exports, recreated on demand.
 *
 * The camera app and the share sheet reach these through a FileProvider
 * (res/xml/file_paths.xml), which grants access to one file at a time.
 */
class PhotoStore(private val context: Context) {

    private val photoDir: File get() = File(context.filesDir, "evidence/photos").apply { mkdirs() }
    private val exportDir: File get() = File(context.cacheDir, "exports").apply { mkdirs() }

    /** An empty file for the camera to write into. */
    fun newPhotoFile(nowMillis: Long): File = File(photoDir, "photo_$nowMillis.jpg")

    fun photoFile(fileName: String): File = File(photoDir, fileName)

    fun exportFile(fileName: String): File = File(exportDir, fileName)

    /** A content:// address another app may read or write for this one file. */
    fun uriFor(file: File): Uri = FileProvider.getUriForFile(context, authority(context), file)

    /** Demo reset: removes every photo file. Rows go with their issues (foreign key cascade). */
    fun deleteAllPhotos() {
        photoDir.listFiles()?.forEach { it.delete() }
    }

    companion object {
        fun authority(context: Context): String = context.packageName + ".files"

        /**
         * Decodes a photo no larger than about [maxPx] on its long side and turns it
         * upright (cameras store the rotation in EXIF). Returns null if the file is
         * missing or not an image. Call off the main thread.
         */
        fun decodeScaled(file: File, maxPx: Int): Bitmap? {
            if (!file.exists()) return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
            val degrees = try {
                when (ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) {
                0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
