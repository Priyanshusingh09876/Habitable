package edu.bu.cs501.habitable.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import edu.bu.cs501.habitable.HabitableApp
import edu.bu.cs501.habitable.MainActivity
import edu.bu.cs501.habitable.R

/**
 * The "no heat" alert, posted when the 2-hour rule opens a heat issue.
 * Shared by the monitoring service (live readings) and Replay cold night,
 * so both paths tell the tenant the same way.
 */
object ViolationAlert {
    const val NOTIF_ID = 1002

    fun post(context: Context, tempF: Double) {
        val app = context.applicationContext
        val open = PendingIntent.getActivity(
            app, 2, Intent(app, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(app, HabitableApp.CHANNEL_VIOLATIONS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(app.getString(R.string.notif_violation_title))
            .setContentText(app.getString(R.string.notif_violation_text, tempF))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(app).notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
            // Notifications not allowed: the issue is still opened and shown in the app.
        }
    }
}
