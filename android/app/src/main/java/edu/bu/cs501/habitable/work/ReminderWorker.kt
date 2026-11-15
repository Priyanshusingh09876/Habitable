package edu.bu.cs501.habitable.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import edu.bu.cs501.habitable.HabitableApp
import edu.bu.cs501.habitable.MainActivity
import edu.bu.cs501.habitable.R
import edu.bu.cs501.habitable.data.repository.IssuesRepository
import edu.bu.cs501.habitable.domain.usecase.ReminderRules

/**
 * Once a day, reminds the tenant about issues that have been waiting on the
 * same step for too long (backlog US-6). Which issues, and what the reminder
 * says, is decided by [ReminderRules]; this worker only reads Room and posts
 * notifications. Needs no network.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
    private val issues: IssuesRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val open = issues.openIssues()
        val timelines = open.associate { it.id to issues.timelineOf(it.id) }
        ReminderRules.due(open, timelines, clock()).forEach { post(it) }
        return Result.success()
    }

    private fun post(reminder: ReminderRules.Reminder) {
        val open = PendingIntent.getActivity(
            applicationContext, 3, Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(applicationContext, HabitableApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(reminder.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            // One notification per issue: a newer reminder for the same issue replaces the old one.
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_BASE_ID + (reminder.issueId % 1000).toInt(), n)
        } catch (_: SecurityException) {
            // Notifications not allowed: the issue screen still shows how long it has been waiting.
        }
    }

    companion object {
        const val UNIQUE_NAME = "habitable-reminders"
        const val NOTIF_BASE_ID = 2000
    }
}
