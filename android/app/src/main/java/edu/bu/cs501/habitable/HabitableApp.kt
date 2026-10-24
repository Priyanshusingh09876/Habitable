package edu.bu.cs501.habitable

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import edu.bu.cs501.habitable.di.AppContainer
import edu.bu.cs501.habitable.di.DefaultAppContainer
import edu.bu.cs501.habitable.work.HabitableWorkerFactory
import edu.bu.cs501.habitable.work.SyncWorker
import java.util.concurrent.TimeUnit

class HabitableApp : Application(), Configuration.Provider {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        createNotificationChannels()
        scheduleSync()
    }

    /** WorkManager asks us for its config because we removed the default initializer in the manifest. */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(HabitableWorkerFactory(container))
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.WARN)
            .build()

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITORING, getString(R.string.notif_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notif_channel_desc)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_VIOLATIONS, getString(R.string.notif_violation_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun scheduleSync() {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(SyncWorker.constraints())
            .build()
        WorkManager.getInstance(this)
            .enqueueUniquePeriodicWork(SyncWorker.UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    companion object {
        const val CHANNEL_MONITORING = "monitoring"
        const val CHANNEL_VIOLATIONS = "violations"

        fun container(context: Context): AppContainer = (context.applicationContext as HabitableApp).container
    }
}
