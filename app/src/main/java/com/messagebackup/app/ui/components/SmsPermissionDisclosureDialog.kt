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
                text = "Muninn requires SMS and MMS access to read your existing message " +
                        "conversations and package them into documents (PDF, HTML, CSV, Markdown, and TXT) with images on your device.\n\n" +
                        "• 100% User Choice & Local Privacy: Your messages stay strictly on your device by default.\n" +
                        "• Zero Developer Servers: Nothing is ever uploaded to developer servers or shared with third parties.\n" +
                        "• Optional Cloud & AI: Cloud Drive sync and O.D.I.N. AI (Local Ollama or BYOK Gemini) are 100% opt-in and only run when you explicitly choose to use them."
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
