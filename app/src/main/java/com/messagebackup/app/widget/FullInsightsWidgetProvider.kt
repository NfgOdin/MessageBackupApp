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
 * Android Home Screen Widget displaying comprehensive conversation analytics & behavioral insights:
 * Total Messages, Active Conversations, Sent/Received Ratio, SMS vs MMS, Peak Day & Hour,
 * and Top 3 Active Contacts with message counts.
 */
class FullInsightsWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH_INSIGHTS = "com.messagebackup.app.ACTION_REFRESH_INSIGHTS"
        const val ACTION_OPEN_INSIGHTS = "com.messagebackup.app.ACTION_OPEN_INSIGHTS"

        fun updateAllWidgets(context: Context) {
            val intent = Intent(context, FullInsightsWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                val widgetManager = AppWidgetManager.getInstance(context)
                val ids = widgetManager.getAppWidgetIds(
                    ComponentName(context, FullInsightsWidgetProvider::class.java)
                )
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            }
            context.sendBroadcast(intent)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_INSIGHTS) {
            updateAllWidgets(context)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = MessageBackupApp.instance
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Check if we have freshly cached full stats
                val cached = app.appPreferences.getCachedStats()

                val threads = app.currentTelephonyRepository.getConversationThreads(resolveContacts = true).first()
                val totalThreads = threads.size
                val totalMessages = threads.sumOf { it.messageCount.toLong() }

                val top1 = threads.getOrNull(0)
                val top2 = threads.getOrNull(1)
                val top3 = threads.getOrNull(2)

                // Fast SMS vs MMS count from system content provider without heavy MIME attachment parsing
                var totalSms = cached?.totalSms ?: 0L
                var totalMms = cached?.totalMms ?: 0L
                var totalSent = cached?.totalSent ?: 0L
                var totalReceived = cached?.totalReceived ?: 0L
                var peakHourStr = cached?.busiestHour?.let { h ->
                    if (h == 0) "12 AM" else if (h < 12) "$h AM" else if (h == 12) "12 PM" else "${h - 12} PM"
                } ?: "2 PM"
                var peakDay = cached?.busiestDayOfWeek ?: "Mon"

                if (cached == null) {
                    // Quick lightweight query on SMS inbox vs sent directly
                    try {
                        val smsCursor = context.contentResolver.query(
                            android.provider.Telephony.Sms.CONTENT_URI,
                            arrayOf(android.provider.Telephony.Sms.TYPE),
                            null,
                            null,
                            null
                        )
                        smsCursor?.use { cursor ->
                            val typeIdx = cursor.getColumnIndex(android.provider.Telephony.Sms.TYPE)
                            var count = 0
                            while (cursor.moveToNext() && count < 2000) {
                                val type = cursor.getInt(typeIdx)
                                if (type == android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX) totalReceived++ else totalSent++
                                totalSms++
                                count++
                            }
                        }
                    } catch (_: Exception) {}

                    try {
                        val mmsCursor = context.contentResolver.query(
                            android.provider.Telephony.Mms.CONTENT_URI,
                            arrayOf(android.provider.Telephony.Mms._ID),
                            null,
                            null,
                            null
                        )
                        mmsCursor?.use { cursor ->
                            totalMms = cursor.count.toLong()
                        }
                    } catch (_: Exception) {}
                }

                val totalSampled = totalSent + totalReceived
                val sentRatio = if (totalSampled > 0) (totalSent.toFloat() / totalSampled.toFloat() * 100).toInt() else 50

                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_full_insights).apply {
                        setTextViewText(R.id.insight_total_messages, "%,d".format(totalMessages))
                        setTextViewText(R.id.insight_total_threads, "%,d".format(totalThreads))
                        setTextViewText(R.id.insight_sent_ratio, "$sentRatio%")
                        setTextViewText(R.id.insight_sms_mms_ratio, "%,d / %,d".format(totalSms, totalMms))
                        setTextViewText(R.id.insight_peak_day, peakDay)
                        setTextViewText(R.id.insight_peak_hour, peakHourStr)

                        // Top Contacts list
                        if (top1 != null) {
                            setTextViewText(R.id.insight_contact_1_name, "1. ${top1.displayName}")
                            setTextViewText(R.id.insight_contact_1_count, "%,d msgs".format(top1.messageCount))
                        } else {
                            setTextViewText(R.id.insight_contact_1_name, "1. --")
                            setTextViewText(R.id.insight_contact_1_count, "--")
                        }

                        if (top2 != null) {
                            setTextViewText(R.id.insight_contact_2_name, "2. ${top2.displayName}")
                            setTextViewText(R.id.insight_contact_2_count, "%,d msgs".format(top2.messageCount))
                        } else {
                            setTextViewText(R.id.insight_contact_2_name, "2. --")
                            setTextViewText(R.id.insight_contact_2_count, "--")
                        }

                        if (top3 != null) {
                            setTextViewText(R.id.insight_contact_3_name, "3. ${top3.displayName}")
                            setTextViewText(R.id.insight_contact_3_count, "%,d msgs".format(top3.messageCount))
                        } else {
                            setTextViewText(R.id.insight_contact_3_name, "3. --")
                            setTextViewText(R.id.insight_contact_3_count, "--")
                        }

                        val nowStr = java.time.format.DateTimeFormatter.ofPattern("h:mm a")
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(java.time.Instant.now())
                        setTextViewText(R.id.insight_footer_sync, "Updated at $nowStr • Tap to open Insights")

                        // Open Insights in App
                        val openAppIntent = Intent(context, MainActivity::class.java).apply {
                            action = ACTION_OPEN_INSIGHTS
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        }
                        val openPending = PendingIntent.getActivity(
                            context,
                            widgetId + 400,
                            openAppIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        setOnClickPendingIntent(R.id.widget_insights_root, openPending)

                        // Refresh Button
                        val refreshIntent = Intent(context, FullInsightsWidgetProvider::class.java).apply {
                            action = ACTION_REFRESH_INSIGHTS
                        }
                        val refreshPending = PendingIntent.getBroadcast(
                            context,
                            widgetId + 500,
                            refreshIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                        setOnClickPendingIntent(R.id.widget_insights_refresh, refreshPending)
                    }

                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } catch (e: Exception) {
                // If permission or error, display placeholder
                for (widgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_full_insights)
                    views.setTextViewText(R.id.insight_footer_sync, "Tap to grant SMS permission")
                    val openAppIntent = Intent(context, MainActivity::class.java)
                    val openPending = PendingIntent.getActivity(
                        context,
                        widgetId + 400,
                        openAppIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_insights_root, openPending)
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
