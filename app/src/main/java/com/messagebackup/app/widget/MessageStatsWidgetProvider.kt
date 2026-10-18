package com.messagebackup.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.messagebackup.app.MainActivity
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Android Home Screen Widget displaying live conversation analytics:
 * Total Messages, Active Conversations, Sent/Received Ratio, and Media Files count.
 */
class MessageStatsWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.messagebackup.app.ACTION_REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, MessageStatsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val widgetManager = AppWidgetManager.getInstance(context)
                val ids = widgetManager.getAppWidgetIds(
                    ComponentName(context, MessageStatsWidgetProvider::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            updateAllWidgets(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = MessageBackupApp.instance
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val threads = app.currentTelephonyRepository.getConversationThreads(resolveContacts = false).first()
                val totalThreads = threads.size
                val totalMessages = threads.sumOf { it.messageCount.toLong() }

                val cached = app.appPreferences.getCachedStats()
                val totalSent = cached?.totalSent ?: 0L
                val totalReceived = cached?.totalReceived ?: 0L
                val totalMedia = cached?.totalMediaAttachments ?: 0L
                val totalSampled = totalSent + totalReceived
                val sentRatio = if (totalSampled > 0) (totalSent.toFloat() / totalSampled.toFloat() * 100).toInt() else 50

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_message_stats).apply {
                        setTextViewText(R.id.widget_total_messages, "%,d".format(totalMessages))
                        setTextViewText(R.id.widget_total_threads, "%,d".format(totalThreads))
                        setTextViewText(R.id.widget_sent_ratio, "$sentRatio%")
                        setTextViewText(R.id.widget_total_media, "%,d".format(totalMedia))

                        val nowStr = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(java.time.Instant.now())
                        setTextViewText(R.id.widget_sync_status, "Updated at $nowStr • Tap to open")

                        // Open app on click
                        val openAppIntent = Intent(context, MainActivity::class.java)
                        val openPending = PendingIntent.getActivity(
                            context,
                            0,
                            openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        setOnClickPendingIntent(R.id.widget_root, openPending)

                        // Refresh button click
                        val refreshIntent = Intent(context, MessageStatsWidgetProvider::class.java).apply {
                            action = ACTION_REFRESH_WIDGET
                        }
                        val refreshPending = PendingIntent.getBroadcast(
                            context,
                            1,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        setOnClickPendingIntent(R.id.widget_refresh_button, refreshPending)
                    }

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                // If permission is not yet granted or telephony is idle
            } finally {
                pendingResult.finish()
            }
        }
    }
}
