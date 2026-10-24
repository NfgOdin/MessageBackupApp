package com.messagebackup.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messagebackup.app.R
import java.util.concurrent.TimeUnit

/**
 * Background worker that reliably dispatches scheduled SMS text messages via WorkManager.
 */
class ScheduledSmsWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_RECIPIENT = "scheduled_recipient"
        const val KEY_DISPLAY_NAME = "scheduled_display_name"
        const val KEY_BODY = "scheduled_body"
        const val KEY_SUB_ID = "scheduled_sub_id"
        private const val CHANNEL_ID = "scheduled_sms_notifications"

        fun scheduleSms(
            context: Context,
            recipientAddress: String,
            displayName: String,
            body: String,
            delayMillis: Long,
            subscriptionId: Int = -1
        ) {
            val data = Data.Builder()
                .putString(KEY_RECIPIENT, recipientAddress)
                .putString(KEY_DISPLAY_NAME, displayName)
                .putString(KEY_BODY, body)
                .putInt(KEY_SUB_ID, subscriptionId)
                .build()

            val work = OneTimeWorkRequestBuilder<ScheduledSmsWorker>()
                .setInitialDelay(delayMillis.coerceAtLeast(1000L), TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag("SCHEDULED_SMS")
                .build()

            WorkManager.getInstance(context).enqueue(work)
        }
    }

    override suspend fun doWork(): Result {
        val recipient = inputData.getString(KEY_RECIPIENT) ?: return Result.failure()
        val displayName = inputData.getString(KEY_DISPLAY_NAME) ?: recipient
        val body = inputData.getString(KEY_BODY) ?: return Result.failure()
        val subId = inputData.getInt(KEY_SUB_ID, -1)

        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.SEND_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            showNotification("Scheduled Message Failed", "SEND_SMS permission was missing when dispatching to $displayName.")
            return Result.failure()
        }

        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (subId != -1) {
                    appContext.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                } else {
                    appContext.getSystemService(SmsManager::class.java)
                }
            } else {
                @Suppress("DEPRECATION")
                if (subId != -1) {
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                } else {
                    SmsManager.getDefault()
                }
            }

            val parts = smsManager.divideMessage(body)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(recipient, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(recipient, null, body, null, null)
            }

            // Record message in system sent table
            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, recipient)
                    put(Telephony.Sms.BODY, body)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.DATE_SENT, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    if (subId != -1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                        put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                    }
                }
                appContext.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (_: Exception) {}

            showNotification("🦅 Huginn Sent Scheduled Message", "Dispatched to $displayName: \"${body.take(40)}\"")
            return Result.success()
        } catch (e: Exception) {
            showNotification("Scheduled Message Error", "Failed to dispatch scheduled text to $displayName: ${e.message}")
            return Result.failure()
        }
    }

    private fun showNotification(title: String, content: String) {
        val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Huginn Scheduled Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status updates for scheduled Huginn SMS dispatches"
            }
            nm.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        nm.notify((System.currentTimeMillis() % 100000).toInt(), notification)
    }
}
