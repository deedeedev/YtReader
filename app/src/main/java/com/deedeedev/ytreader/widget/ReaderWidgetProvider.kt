package com.deedeedev.ytreader.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.deedeedev.ytreader.MainActivity
import com.deedeedev.ytreader.R
import com.deedeedev.ytreader.YtReaderApplication
import kotlinx.coroutines.runBlocking

open class ReaderWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
    }

    override fun onDisabled(context: Context) {
    }

    companion object {
        private const val TAG = "ReaderWidgetProvider"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val application = context.applicationContext as YtReaderApplication
            val subtitleDao = application.container.subtitleDao

            val recentSubtitle = runBlocking { subtitleDao.getMostRecentlyOpened() }
            val hasRecentSubtitle = recentSubtitle != null && recentSubtitle.lastOpenedAt > 0

            val layoutId = if (hasRecentSubtitle) R.layout.widget_reader else R.layout.widget_reader_icon
            val views = RemoteViews(context.packageName, layoutId)

            val pendingIntent = if (hasRecentSubtitle) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = MainActivity.ACTION_OPEN_READER
                    putExtra(MainActivity.EXTRA_SUBTITLE_ID, recentSubtitle.id)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                PendingIntent.getActivity(
                    context,
                    appWidgetId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            } else {
                null
            }

            if (pendingIntent != null) {
                views.setOnClickPendingIntent(R.id.widget_container, pendingIntent)
                views.setInt(R.id.widget_container, "setBackgroundColor", 0xFF1A1A1A.toInt())
            } else {
                views.setOnClickPendingIntent(R.id.widget_container, null)
                views.setInt(R.id.widget_container, "setBackgroundColor", 0xFF2A2A2A.toInt())
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun notifyWidgetChanged(context: Context) {
            val intent = Intent(context, ReaderWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            }
            val widgetManager = AppWidgetManager.getInstance(context)
            val ids = widgetManager.getAppWidgetIds(
                ComponentName(context, ReaderWidgetProvider::class.java)
            )
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            context.sendBroadcast(intent)
        }
    }
}
