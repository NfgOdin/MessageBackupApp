package com.messagebackup.app.domain.cloud

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.messagebackup.app.model.OutputDestination
import java.io.File

/**
 * Universal Cloud Dispatcher supporting seamless backup delivery to:
 * - Google Drive (com.google.android.apps.docs)
 * - Microsoft OneDrive (com.microsoft.skydrive)
 * - Dropbox (com.dropbox.android)
 * - WebDAV / Local NAS / Nextcloud (via Storage Access Framework or intent chooser)
 */
class CloudStorageDispatcher(private val context: Context) {

    fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isOneDriveInstalled(): Boolean = isAppInstalled("com.microsoft.skydrive")
    fun isDropboxInstalled(): Boolean = isAppInstalled("com.dropbox.android")
    fun isGoogleDriveInstalled(): Boolean = isAppInstalled("com.google.android.apps.docs")

    fun dispatchBackupToCloud(file: File, destination: OutputDestination) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        when (destination) {
            OutputDestination.GOOGLE_DRIVE -> {
                dispatchToPackage(
                    uri = uri,
                    file = file,
                    targetPackage = "com.google.android.apps.docs",
                    fallbackTitle = "Upload to Google Drive"
                )
            }
            OutputDestination.ONEDRIVE -> {
                dispatchToPackage(
                    uri = uri,
                    file = file,
                    targetPackage = "com.microsoft.skydrive",
                    fallbackTitle = "Upload to Microsoft OneDrive"
                )
            }
            OutputDestination.DROPBOX -> {
                dispatchToPackage(
                    uri = uri,
                    file = file,
                    targetPackage = "com.dropbox.android",
                    fallbackTitle = "Upload to Dropbox"
                )
            }
            OutputDestination.WEBDAV_NAS -> {
                // Open system document creator or send to WebDAV client (e.g. Nextcloud, Solid Explorer, Synology Drive)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(intent, "Save to WebDAV / NAS / Nextcloud").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
            else -> {}
        }
    }

    private fun dispatchToPackage(
        uri: Uri,
        file: File,
        targetPackage: String,
        fallbackTitle: String
    ) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            if (isAppInstalled(targetPackage)) {
                setPackage(targetPackage)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            val fallback = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(fallback, fallbackTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    fun createCloudUploadIntent(file: File, destination: OutputDestination): Intent {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val targetPackage = when (destination) {
            OutputDestination.GOOGLE_DRIVE -> "com.google.android.apps.docs"
            OutputDestination.ONEDRIVE -> "com.microsoft.skydrive"
            OutputDestination.DROPBOX -> "com.dropbox.android"
            else -> null
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, file.name)
            if (targetPackage != null && isAppInstalled(targetPackage)) {
                setPackage(targetPackage)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
