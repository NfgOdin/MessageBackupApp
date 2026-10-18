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
 * 3x1 Action Widget offering 1-tap shortcuts for:
 * 1. Immediate Backup Now
 * 2. Instant O.D.I.N. AI Prompt
 * 3. Document / Export Vault
 */
class QuickActionsWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (widgetId in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_actions)

            // 1. Backup Action
            val backupIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_TRIGGER_BACKUP
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val backupPending = PendingIntent.getActivity(
                context,
                widgetId + 301,
                backupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.action_backup_now, backupPending)

            // 2. Ask O.D.I.N. Action
            val odinIntent = Intent(context, MainActivity::class.java).apply {
                action = OdinSearchWidgetProvider.ACTION_OPEN_ODIN
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val odinPending = PendingIntent.getActivity(
                context,
                widgetId + 302,
                odinIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.action_ask_odin, odinPending)

            // 3. Open Vault Action
            val vaultIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_OPEN_VAULT
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val vaultPending = PendingIntent.getActivity(
                context,
                widgetId + 303,
                vaultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.action_open_vault, vaultPending)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    companion object {
        const val ACTION_TRIGGER_BACKUP = "com.messagebackup.app.ACTION_TRIGGER_BACKUP"
        const val ACTION_OPEN_VAULT = "com.messagebackup.app.ACTION_OPEN_VAULT"
    }
}
