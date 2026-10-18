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
                        "conversations and package them into documents (PDF, HTML, CSV, Markdown, and TXT) with images on your device.\n\n" +
                        "• Your messages and media remain strictly on your device or in your designated personal cloud.\n" +
                        "• Nothing is uploaded to third-party developer servers or shared with unauthorized parties.\n" +
                        "• This permission is used exclusively for the backup, analytics, and export functionality."
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
