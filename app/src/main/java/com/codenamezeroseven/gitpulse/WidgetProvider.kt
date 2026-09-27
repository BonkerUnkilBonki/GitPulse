package com.codenamezeroseven.gitpulse

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class WidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        WidgetHelper.updateAll(context, appWidgetManager, appWidgetIds)
    }
}

object WidgetHelper {

    /** Refresh every placed widget right now. Safe to call from anywhere. */
    fun updateAll(context: Context) {
        runCatching {
            val am = AppWidgetManager.getInstance(context)
            updateAll(context, am, am.getAppWidgetIds(
                android.content.ComponentName(context, WidgetProvider::class.java)))
            for ((cls, layout, kind) in listOf(
                Triple(CommitActivityWidget::class.java, R.layout.widget_commit_activity, "commit"),
                Triple(GoalHistoryWidget::class.java, R.layout.widget_goal_history, "goal"),
                Triple(WeekGraphWidget::class.java, R.layout.widget_week_graph, "week")
            )) {
                updateCharts(context, am, am.getAppWidgetIds(android.content.ComponentName(context, cls)), kind, layout)
            }
        }
    }

    /** Chart widgets: commit activity / goal history / this week. */
    fun updateCharts(
        context: Context, am: AppWidgetManager, ids: IntArray, kind: String, layout: Int
    ) {
        if (ids.isEmpty()) return
        TaskStore.init(context)
        val daily = Prefs.dailyCommits()
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date())

        val bitmap = when (kind) {
            "commit" -> WidgetCharts.barChart(daily, days = 30, showLabels = false)
            "goal" -> WidgetCharts.goalHeatmap(daily, Prefs.dailyGoal)
            else -> WidgetCharts.barChart(daily, days = 7, showLabels = true)
        }
        val caption = when (kind) {
            "commit" -> {
                val active = (1..30).count {
                    (daily[java.time.LocalDate.now().minusDays((30 - it).toLong())] ?: 0) > 0
                }
                "$active active of last 30 days"
            }
            "goal" -> {
                var met = 0
                for (i in 0 until 35) {
                    val d = java.time.LocalDate.now().minusDays(i.toLong())
                    if ((daily[d] ?: 0) >= Prefs.dailyGoal) met++
                }
                "$met of the last 35 days met your goal"
            }
            else -> "${StatsEngine.weeklyTotal(daily)} of ${Prefs.weeklyGoal} commits this week"
        }

        val rv = android.widget.RemoteViews(context.packageName, layout).apply {
            setImageViewBitmap(R.id.wChart, bitmap)
            setTextViewText(R.id.wCaption, caption)
            setTextViewText(R.id.wUpdated, time)
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                setOnClickPendingIntent(
                    R.id.wRoot,
                    android.app.PendingIntent.getActivity(
                        context, 0, launch,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                            android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                )
            }
        }
        ids.forEach { am.updateAppWidget(it, rv) }
    }

    fun updateAll(context: Context, am: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        TaskStore.init(context)

        val daily = Prefs.dailyCommits()
        val today = daily[java.time.LocalDate.now()] ?: 0
        val week = StatsEngine.weeklyTotal(daily)
        val open = TaskStore.list().count { !it.completed }
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
            .format(java.util.Date())

        val rv = android.widget.RemoteViews(context.packageName, R.layout.widget_gitpulse).apply {
            setTextViewText(R.id.wToday, "$today of ${Prefs.dailyGoal} commits today")
            setTextViewText(R.id.wWeek, "$week of ${Prefs.weeklyGoal} commits this week")
            setTextViewText(
                R.id.wTasks,
                if (open == 1) "1 open task" else "$open open tasks"
            )
            setTextViewText(R.id.wUpdated, time)
            setProgressBar(R.id.wTodayBar, Prefs.dailyGoal, today.coerceAtMost(Prefs.dailyGoal), false)
            setProgressBar(R.id.wWeekBar, Prefs.weeklyGoal, week.coerceAtMost(Prefs.weeklyGoal), false)

            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            if (launch != null) {
                val pi = android.app.PendingIntent.getActivity(
                    context, 0, launch,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
                setOnClickPendingIntent(R.id.wRoot, pi)
            }
        }
        ids.forEach { am.updateAppWidget(it, rv) }
    }
}
