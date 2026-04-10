package com.deedeedev.ytreader.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import kotlinx.coroutines.launch

class ReaderWidgetProviderIcon : ReaderWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val result = goAsync()
        Companion.widgetScope.launch {
            try {
                for (appWidgetId in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, appWidgetId)
                }
            } finally {
                result.finish()
            }
        }
    }
}
