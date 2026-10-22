package com.messagebackup.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.model.DateRangeFilter
import com.messagebackup.app.model.ExportFormat
import com.messagebackup.app.model.ExportOptions
import com.messagebackup.app.model.OutputDestination
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Background worker that executes automated nightly or weekly backups
 * when the phone is charging and idle, writing to the user's selected SAF directory.
 */
class AutoBackupWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "MessageBackupPeriodicWorker"
        private const val CHANNEL_ID = "backup_notifications"

        fun scheduleAutoBackup(
            context: Context,
            intervalDays: Long = 1L,
            requiresWifi: Boolean = true
        ) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .apply {
                    if (requiresWifi) {
                        setRequiredNetworkType(NetworkType.UNMETERED)
                    }
                }
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalDays.coerceAtLeast(1L), TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelAutoBackup(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }

    override suspend fun doWork(): Result {
        val app = MessageBackupApp.instance
        if (!app.appPreferences.autoBackupEnabled.value) {
            return Result.success()
        }

        val isAutoBackupUnlocked = app.billingManager.isAutoBackupUnlocked.value
        val hasCredit = isAutoBackupUnlocked || app.appPreferences.autoBackupCredits.value > 0

        if (!hasCredit) {
            // User does not have active Pro/Standalone unlock and has 0 auto-backup credits
            return Result.success()
        }

        val destination = app.appPreferences.outputDestination.value
        val safUriString = app.appPreferences.safDirectoryUri.value
        if (destination == OutputDestination.PERSISTENT_DIRECTORY && safUriString == null) {
            return Result.failure()
        }

        return try {
            val allThreads = app.realTelephonyRepository.getConversationThreads(
                resolveContacts = app.appPreferences.resolveContacts.value
            ).first()

            if (allThreads.isEmpty()) {
                return Result.success()
            }

            // Filter threads based on user preference (All vs Selected)
            val threads = if (app.appPreferences.autoBackupAllThreads.value) {
                allThreads
            } else {
                val selectedIds = app.appPreferences.autoBackupSelectedThreadIds.value
                val filtered = allThreads.filter { selectedIds.contains(it.threadId) }
                if (filtered.isEmpty()) allThreads else filtered
            }

            val options = ExportOptions(
                format = app.appPreferences.defaultFormat.value,
                includeMedia = true,
                maxMessagesPerFile = app.appPreferences.maxMessagesPerFile.value,
                dateRange = DateRangeFilter.LAST_30_DAYS,
                outputType = destination
            )

            val customSafUri = if (destination == OutputDestination.PERSISTENT_DIRECTORY && safUriString != null) {
                Uri.parse(safUriString)
            } else null

            val result = app.exportEngine.exportThreads(
                threads = threads,
                options = options,
                resolveContacts = app.appPreferences.resolveContacts.value,
                customSafTreeUri = customSafUri
            )

            if (result.success && result.outputFile != null) {
                if (!isAutoBackupUnlocked) {
                    app.appPreferences.consumeAutoBackupCredit()
                }
                if (destination == OutputDestination.GOOGLE_DRIVE ||
                    destination == OutputDestination.ONEDRIVE ||
                    destination == OutputDestination.DROPBOX ||
                    destination == OutputDestination.WEBDAV_NAS) {
                    val destName = when (destination) {
                        OutputDestination.GOOGLE_DRIVE -> "Google Drive"
                        OutputDestination.ONEDRIVE -> "Microsoft OneDrive"
                        OutputDestination.DROPBOX -> "Dropbox"
                        else -> "WebDAV / NAS"
                    }
                    val cloudIntent = app.cloudStorageDispatcher.createCloudUploadIntent(result.outputFile, destination)
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        appContext,
                        1002,
                        cloudIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    showBackupNotification(
                        title = "$destName Backup Ready",
                        message = "Archived ${result.exportedThreadCount} conversations. Tap to upload to $destName.",
                        contentIntent = pendingIntent
                    )
                } else {
                    val openIntent = android.content.Intent(appContext, com.messagebackup.app.MainActivity::class.java)
                    val pendingIntent = android.app.PendingIntent.getActivity(
                        appContext,
                        1001,
                        openIntent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
                    )
                    showBackupNotification(
                        title = "Auto-Backup Complete",
                        message = "Successfully backed up ${result.exportedThreadCount} conversations.",
                        contentIntent = pendingIntent
                    )
                }
                app.appPreferences.recordAutoBackupCompleted()
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showBackupNotification(
        title: String,
        message: String,
        contentIntent: android.app.PendingIntent? = null
    ) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }

        try {
            manager.notify(1001, builder.build())
        } catch (e: SecurityException) {
            // Notification permission not granted on Android 13+
        }
    }
}
