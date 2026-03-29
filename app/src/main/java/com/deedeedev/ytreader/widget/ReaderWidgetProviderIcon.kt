package com.deedeedev.ytreader.widget

import android.content.Context
import android.appwidget.AppWidgetManager

class ReaderWidgetProviderIcon : ReaderWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }
}
