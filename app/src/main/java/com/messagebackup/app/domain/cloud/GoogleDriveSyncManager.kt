package com.messagebackup.app.domain.cloud

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.messagebackup.app.MessageBackupApp
import java.io.File

/**
 * Handles seamless integration with Google Drive.
 *
 * Provides two pathways:
 * 1. Targeted Google Drive Intent sync (works immediately with zero API keys / OAuth setup required).
 * 2. Status verification for background sync.
 */
class GoogleDriveSyncManager(private val context: Context) {

    /**
     * Checks if Google Drive is installed on this device.
     */
    fun isGoogleDriveInstalled(): Boolean {
        val packageManager = context.packageManager
        return try {
            packageManager.getPackageInfo("com.google.android.apps.docs", 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Builds an Intent that targets Google Drive (or falls back to the share sheet) for a backup archive.
     */
    fun createDriveUploadIntent(file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        return if (isGoogleDriveInstalled()) {
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                setPackage("com.google.android.apps.docs")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Intent.createChooser(fallback, "Upload Backup to Google Drive").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Directs an exported file into Google Drive with "Save to Drive" targeting.
     */
    fun uploadFileToDrive(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val driveIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            setPackage("com.google.android.apps.docs")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(driveIntent)
        } catch (e: Exception) {
            // Fallback to generic chooser if Drive package isn't directly launchable
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(fallback, "Upload Backup to Google Drive").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }
}
