package edu.bu.cs501.habitable.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import edu.bu.cs501.habitable.data.repository.SyncRepository
import edu.bu.cs501.habitable.di.AppContainer

/**
 * Uploads unsynced readings to the backend. Runs every 6 hours (see HabitableApp)
 * and on demand from the More screen. Requires a network; otherwise WorkManager
 * simply waits, which is exactly the offline-first behaviour we want.
 */
class SyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val sync: SyncRepository,
) : CoroutineWorker(appContext, params) {

    /**
     * Readings first, then issues, so the background backup carries the same things
     * as the Sync now button. Anything that fails is retried by WorkManager.
     */
    override suspend fun doWork(): Result {
        val readings = sync.syncReadings()
        if (readings == SyncRepository.Result.NotSignedIn) return Result.success() // nothing to do until the user signs in
        val issues = sync.syncIssues()
        val photos = sync.syncPhotoAnchors() // fingerprints only; photos never leave the phone
        val failed = readings is SyncRepository.Result.Failed || issues is SyncRepository.Result.Failed ||
            photos is SyncRepository.Result.Failed
        return when {
            !failed -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "habitable-sync"

        fun constraints(): Constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun enqueueNow(context: Context) {
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints()).build()
            )
        }
    }
}

/** Lets workers receive repositories instead of reaching for globals. */
class HabitableWorkerFactory(private val container: AppContainer) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters,
    ): ListenableWorker? = when (workerClassName) {
        SyncWorker::class.java.name -> SyncWorker(appContext, workerParameters, container.sync)
        ReminderWorker::class.java.name -> ReminderWorker(appContext, workerParameters, container.issues)
        else -> null // fall back to the default reflective factory
    }
}
