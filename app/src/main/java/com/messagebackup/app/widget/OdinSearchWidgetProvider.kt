package com.messagebackup.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.messagebackup.app.MainActivity
import com.messagebackup.app.R

/**
 * 4x1 Pill-style home screen widget providing 1-tap instant launch
 * directly into the O.D.I.N. AI Message Copilot.
 */
class OdinSearchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_odin_search)

            val openOdinIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_ODIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                widgetId + 200,
                openOdinIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_odin_root, pendingIntent)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        const val ACTION_OPEN_ODIN = "com.messagebackup.app.ACTION_OPEN_ODIN"
    }
}
