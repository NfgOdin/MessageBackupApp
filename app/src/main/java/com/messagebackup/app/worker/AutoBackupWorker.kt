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

        fun scheduleAutoBackup(context: Context, intervalDays: Long = 1L) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresBatteryNotLow(true)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<AutoBackupWorker>(intervalDays, TimeUnit.DAYS)
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
        val isPro = app.billingManager.isAdsRemoved.value
        if (!isPro) {
            // Free users do not run automatic background backups
            return Result.success()
        }

        val safUriString = app.appPreferences.safDirectoryUri.value
        if (safUriString == null) {
            return Result.failure()
        }

        return try {
            val threads = app.realTelephonyRepository.getConversationThreads(
                resolveContacts = app.appPreferences.resolveContacts.value
            ).first()

            if (threads.isEmpty()) {
                return Result.success()
            }

            val options = ExportOptions(
                format = app.appPreferences.defaultFormat.value,
                includeMedia = true,
                maxMessagesPerFile = app.appPreferences.maxMessagesPerFile.value,
                dateRange = DateRangeFilter.LAST_30_DAYS,
                outputType = OutputDestination.PERSISTENT_DIRECTORY
            )

            val result = app.exportEngine.exportThreads(
                threads = threads,
                options = options,
                resolveContacts = app.appPreferences.resolveContacts.value,
                customSafTreeUri = Uri.parse(safUriString)
            )

            if (result.success) {
                showBackupNotification("Auto-Backup Complete", "Successfully backed up ${result.exportedThreadCount} conversations.")
                Result.success()
            } else {
                Result.retry()
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun showBackupNotification(title: String, message: String) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backup Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            manager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        manager.notify(1001, notification)
    }
}
