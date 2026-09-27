package com.codenamezeroseven.gitpulse

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class CommitActivityWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        WidgetHelper.updateCharts(context, appWidgetManager, appWidgetIds, "commit", R.layout.widget_commit_activity)
    }
}
