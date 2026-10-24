package edu.bu.cs501.habitable.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import edu.bu.cs501.habitable.HabitableApp
import edu.bu.cs501.habitable.MainActivity
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.data.sensor.BleSensorSource
import edu.bu.cs501.habitable.data.sensor.SensorSource
import edu.bu.cs501.habitable.data.sensor.SimulatedSensorSource
import edu.bu.cs501.habitable.domain.rules.HeatRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps the thermometer sampling while the app is in the background.
 *
 * Android 12+ kills silent background Bluetooth scanning, so a foreground
 * service with a persistent low-priority notification is the supported way to
 * log temperature overnight. In simulated mode the same service runs the
 * simulator, so the pipeline we demo is the pipeline we ship.
 *
 * Start/stop from the Sensors screen. The service reads settings once at start:
 * changing sensor mode restarts it.
 */
class SensorForegroundService : LifecycleService() {

    private var job: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        startAsForeground(buildMonitoringNotification(null, null))
        if (job == null) job = lifecycleScope.launch { run() }
        return START_STICKY
    }

    private suspend fun run() {
        val container = HabitableApp.container(this)
        val settings = container.settings.settings.first()
        val apartment = container.apartments.getApartment()
        if (apartment == null) {
            Log.w(TAG, "No apartment configured; stopping")
            stopSelf(); return
        }

        val source: SensorSource = if (settings.simulatedSensor || settings.bleAddress.isNullOrBlank()) {
            SimulatedSensorSource(SimulatedSensorSource.Mode.Live(), intervalMillis = LIVE_INTERVAL_MILLIS)
        } else {
            BleSensorSource(this, settings.bleAddress)
        }
        val sensorId = container.apartments.ensureSensor(apartment.id, source.address, source.model)
        container.settings.setMonitoring(true)

        source.samples()
            .catch { e ->
                Log.e(TAG, "Sensor stream failed: ${e.message}")
                container.settings.setMonitoring(false)
                stopSelf()
            }
            .collect { sample ->
                val result = container.readings.ingest(sensorId, sample)
                val limit = HeatRules.requiredMinimumF(sample.recordedAtMillis)
                updateNotification(buildMonitoringNotification(sample.tempF, limit))
                result.issueOpened?.let { postViolationNotification(sample.tempF) }
            }
    }

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun updateNotification(n: Notification) {
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied: the foreground notification still shows; nothing else to do.
        }
    }

    private fun buildMonitoringNotification(tempF: Double?, limitF: Int?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, SensorForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = if (tempF != null && limitF != null) getString(R.string.notif_text, tempF, limitF) else getString(R.string.notif_channel_desc)
        return NotificationCompat.Builder(this, HabitableApp.CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(0, getString(R.string.sensors_stop), stop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun postViolationNotification(tempF: Double) {
        val open = PendingIntent.getActivity(
            this, 2, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, HabitableApp.CHANNEL_VIOLATIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notif_violation_title))
            .setContentText(getString(R.string.notif_violation_text, tempF))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_VIOLATION_ID, n)
        } catch (_: SecurityException) {
        }
    }

    override fun onDestroy() {
        job?.cancel()
        job = null
        lifecycleScope.launch { HabitableApp.container(this@SensorForegroundService).settings.setMonitoring(false) }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SensorService"
        const val ACTION_STOP = "edu.bu.cs501.habitable.STOP_MONITORING"
        const val NOTIF_ID = 1001
        const val NOTIF_VIOLATION_ID = 1002

        /** Faster than the 10-minute production cadence so a live demo shows motion. */
        const val LIVE_INTERVAL_MILLIS = 30_000L

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SensorForegroundService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, SensorForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
