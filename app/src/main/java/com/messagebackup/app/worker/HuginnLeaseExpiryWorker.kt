package com.messagebackup.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.messagebackup.app.MainActivity
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.R
import java.util.concurrent.TimeUnit

/**
 * Scheduled worker that alerts free users when their stacked Huginn lease
 * is expiring soon (e.g. 24 hours remaining or 4 hours remaining),
 * nudging them to stack more days via punch cards or unlock permanently.
 */
class HuginnLeaseExpiryWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME_24H = "HuginnLeaseExpiryWorker_24H"
        const val WORK_NAME_4H = "HuginnLeaseExpiryWorker_4H"
        private const val CHANNEL_ID = "huginn_lease_channel"
        private const val KEY_ALERT_TYPE = "alert_type"
        const val TYPE_24_HOURS = "24h"
        const val TYPE_4_HOURS = "4h"

        /**
         * Recalibrates both 24h and 4h notifications whenever a user
         * punches their card, stacks days, or activates a lease.
         */
        fun scheduleExpiryAlerts(context: Context, expiryTimestampMs: Long) {
            val workManager = WorkManager.getInstance(context)
            val now = System.currentTimeMillis()
            val totalRemainingMs = expiryTimestampMs - now

            // If lease is already expired or too short, cancel any pending alerts
            if (totalRemainingMs <= 0) {
                cancelExpiryAlerts(context)
                return
            }

            // 24-Hour Alert: trigger when remaining time reaches 24 hours
            val delayFor24h = totalRemainingMs - (24L * 60L * 60L * 1000L)
            if (delayFor24h > 0) {
                val request24h = OneTimeWorkRequestBuilder<HuginnLeaseExpiryWorker>()
                    .setInitialDelay(delayFor24h, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_ALERT_TYPE to TYPE_24_HOURS))
                    .build()

                workManager.enqueueUniqueWork(
                    WORK_NAME_24H,
                    ExistingWorkPolicy.REPLACE,
                    request24h
                )
            } else {
                workManager.cancelUniqueWork(WORK_NAME_24H)
            }

            // 4-Hour Alert: trigger when remaining time reaches 4 hours
            val delayFor4h = totalRemainingMs - (4L * 60L * 60L * 1000L)
            if (delayFor4h > 0) {
                val request4h = OneTimeWorkRequestBuilder<HuginnLeaseExpiryWorker>()
                    .setInitialDelay(delayFor4h, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(KEY_ALERT_TYPE to TYPE_4_HOURS))
                    .build()

                workManager.enqueueUniqueWork(
                    WORK_NAME_4H,
                    ExistingWorkPolicy.REPLACE,
                    request4h
                )
            } else {
                workManager.cancelUniqueWork(WORK_NAME_4H)
            }
        }

        fun cancelExpiryAlerts(context: Context) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(WORK_NAME_24H)
            workManager.cancelUniqueWork(WORK_NAME_4H)
        }
    }

    override suspend fun doWork(): Result {
        val app = MessageBackupApp.instance
        val prefs = app.appPreferences
        val billing = app.billingManager

        // If the user has Pro or has permanently unlocked Huginn, do NOT annoy them
        if (billing.isAdsRemoved.value || billing.isHuginnUnlocked.value || prefs.isHuginnPermanentUnlocked.value) {
            return Result.success()
        }

        val remainingMs = prefs.getHuginnRemainingTimeMs()
        if (remainingMs <= 0) {
            return Result.success()
        }

        val alertType = inputData.getString(KEY_ALERT_TYPE) ?: TYPE_24_HOURS
        val (title, message) = when (alertType) {
            TYPE_24_HOURS -> {
                "🪶 Huginn's Flight is Ending Soon (24h left)" to
                        "Only 24 hours left on your Huginn Live Dispatch lease. Punch your card now to stack 3 more days!"
            }
            TYPE_4_HOURS -> {
                "⏳ 4 Hours Left: Huginn Live Dispatch" to
                        "Huginn returns to Asgard in 4 hours. Tap to stack +3 days or unlock permanent access."
            }
            else -> {
                "🪶 Huginn Lease Ending Soon" to
                        "Your Huginn Live Dispatch lease is running low. Tap to stack more time!"
            }
        }

        showNotification(title, message)
        return Result.success()
    }

    private fun showNotification(title: String, message: String) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Huginn Lease Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifies when Huginn Live Dispatch lease time is running low"
            }
            manager.createNotificationChannel(channel)
        }

        val intent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("OPEN_RUNIC_ARCADE", true)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            9112,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_rune_ansuz)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        try {
            manager.notify(8711, notification)
        } catch (_: SecurityException) {
            // Notification permission not granted on Android 13+
        }
    }
}
