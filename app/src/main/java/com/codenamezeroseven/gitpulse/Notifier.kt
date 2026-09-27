package com.codenamezeroseven.gitpulse

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object Notifier {
    private const val CHANNEL_ID = "task_completions"
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
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
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
