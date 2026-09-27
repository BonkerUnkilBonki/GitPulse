package com.codenamezeroseven.gitpulse

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context

class WeekGraphWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray
    ) {
        WidgetHelper.updateCharts(context, appWidgetManager, appWidgetIds, "week", R.layout.widget_week_graph)
    }
}
