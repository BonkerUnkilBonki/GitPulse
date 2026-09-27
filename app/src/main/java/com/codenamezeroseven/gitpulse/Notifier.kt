package com.codenamezeroseven.gitpulse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL_ID = "task_completions"
    private const val SYNC_CHANNEL = "sync_status"
    private const val ACTIVITY_CHANNEL = "github_activity"
    private const val SYNC_NOTIF_ID = 2001
    lateinit var appContext: Context

    fun createChannel() {
        if (!::appContext.isInitialized) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Task completions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies you when GitHub activity auto-completes one of your tasks"
        }
        val syncChannel = NotificationChannel(
            SYNC_CHANNEL,
            "Task sync",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows a notification whenever your tasks sync with GitHub"
        }
        val activityChannel = NotificationChannel(
            ACTIVITY_CHANNEL,
            "GitHub activity",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Notifies when things happen on your GitHub: commits, new repos, PRs, issues, releases, stars"
        }
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
        nm.createNotificationChannel(syncChannel)
        nm.createNotificationChannel(activityChannel)
    }

    /** One notification per new GitHub event (max a few per check). */
    fun notifyActivity(events: List<com.codenamezeroseven.gitpulse.GhEvent>) {
        if (events.isEmpty() || !::appContext.isInitialized) return
        val nm = NotificationManagerCompat.from(appContext)
        if (!nm.areNotificationsEnabled()) return
        val launchIntent = appContext.packageManager
            .getLaunchIntentForPackage(appContext.packageName)
        val contentIntent = android.app.PendingIntent.getActivity(
            appContext, 0, launchIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        for (e in events) {
            val style = com.codenamezeroseven.gitpulse.ui.EventVisual.of(e.type)
            val extra = e.title?.let { " - " + it } ?: ""
            val notif = NotificationCompat.Builder(appContext, ACTIVITY_CHANNEL)
                .setSmallIcon(style.icon)
                .setContentTitle(e.detail + extra)
                .setContentText(e.repoName)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify((e.id % 100000L).toInt(), notif) }
        }
    }

    fun notifySync(text: String) {
        if (!::appContext.isInitialized) return
        val nm = NotificationManagerCompat.from(appContext)
        if (!nm.areNotificationsEnabled()) return
        val notif = NotificationCompat.Builder(appContext, SYNC_CHANNEL)
            .setSmallIcon(R.drawable.ic_nav_tasks)
            .setContentTitle("GitPulse synced")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(SYNC_NOTIF_ID, notif) }
    }

    fun notifySyncProblem(text: String) {
        if (!::appContext.isInitialized) return
        val nm = NotificationManagerCompat.from(appContext)
        if (!nm.areNotificationsEnabled()) return
        val notif = NotificationCompat.Builder(appContext, SYNC_CHANNEL)
            .setSmallIcon(R.drawable.ic_nav_tasks)
            .setContentTitle("Task sync problem")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(SYNC_NOTIF_ID, notif) }
    }

    fun notifyCompleted(tasks: List<Task>) {
        if (tasks.isEmpty() || !::appContext.isInitialized) return
        val nm = NotificationManagerCompat.from(appContext)
        if (!nm.areNotificationsEnabled()) return
        for (t in tasks) {
            val text = if (t.completionSource.startsWith("GitHub:")) {
                "Auto-completed from ${t.completionSource.removePrefix("GitHub: ")}"
            } else {
                "Completed"
            }
            val notif = NotificationCompat.Builder(appContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_nav_tasks)
                .setContentTitle("Task complete: ${t.title}")
                .setContentText(text)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(text + "\nKeywords: ${t.keywords}")
                )
                .setAutoCancel(true)
                .build()
            runCatching { nm.notify(t.id.toInt(), notif) }
        }
    }
}
