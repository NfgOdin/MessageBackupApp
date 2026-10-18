package com.messagebackup.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * Prominent In-App Disclosure modal strictly adhering to Google Play SMS/Call Log Policy.
 * Must be shown BEFORE requesting the android.permission.READ_SMS runtime permission.
 */
@Composable
fun SmsPermissionDisclosureDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(imageVector = Icons.Default.Security, contentDescription = "Security & Privacy")
        },
        title = {
            Text(text = "SMS Access Required")
        },
        text = {
            Text(
                text = "Message Backup & Export requires SMS and MMS access to read your existing message " +
                        "threads and package them into documents (Markdown, HTML, TXT) with images on your device.\n\n" +
                        "• Your messages and media remain strictly on your device.\n" +
                        "• Nothing is uploaded to external servers or transmitted without your explicit action.\n" +
                        "• This permission is used exclusively for the backup and export functionality."
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(text = "Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Not Now")
            }
        }
    )
}
