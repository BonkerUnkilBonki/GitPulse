package com.codenamezeroseven.gitpulse

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class GoalHistoryWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        WidgetHelper.updateCharts(context, appWidgetManager, appWidgetIds, "goal", R.layout.widget_goal_history)
    }
}
