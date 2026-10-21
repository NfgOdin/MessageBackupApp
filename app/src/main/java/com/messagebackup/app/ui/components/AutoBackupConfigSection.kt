package com.messagebackup.app.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.messagebackup.app.data.preferences.AppPreferences
import com.messagebackup.app.model.OutputDestination
import com.messagebackup.app.worker.AutoBackupWorker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Interactive Automated Cloud & Scheduled Backup Configuration Card.
 * Designed with a collapsible dropdown, status bubble, conversation selector (All vs Selected chats),
 * periodic frequencies, Wi-Fi constraints, destination indicators, and one-tap manual run triggers.
 */
@Composable
fun AutoBackupConfigSection(
    prefs: AppPreferences,
    isUnlocked: Boolean,
    credits: Int,
    enabled: Boolean,
    frequencyDays: Int,
    requiresWifi: Boolean,
    lastRunTimestamp: Long,
    destination: OutputDestination,
    safFolderDisplay: String,
    autoBackupPrice: String?,
    productPrice: String?,
    onClaimCredits: () -> Unit,
    onPurchaseUnlimited: () -> Unit,
    onPurchasePro: () -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenThreadPicker: () -> Unit,
    context: Context
) {
    var isExpanded by remember { mutableStateOf(false) }

    val allThreadsActive by prefs.autoBackupAllThreads.collectAsState()
    val selectedThreadIds by prefs.autoBackupSelectedThreadIds.collectAsState()

    val destinationText = when (destination) {
        OutputDestination.GOOGLE_DRIVE -> "Google Drive"
        OutputDestination.ONEDRIVE -> "Microsoft OneDrive"
        OutputDestination.DROPBOX -> "Dropbox"
        OutputDestination.WEBDAV_NAS -> "WebDAV / Local NAS"
        OutputDestination.PERSISTENT_DIRECTORY -> safFolderDisplay
        OutputDestination.SHARE_SHEET -> "Share Sheet"
    }

    val frequencyText = if (frequencyDays == 1) "Nightly" else "Every $frequencyDays Days"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked || credits > 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Icon, Title, Status Bubble, and Expand/Collapse Chevron
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Cloud Sync",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 10.dp)
                    )
                    Column {
                        Text(
                            text = "Automated Cloud & Scheduled Backups",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        // Status Bubble / Subtitle
                        val statusText = if (enabled && (isUnlocked || credits > 0)) {
                            "● Active: $frequencyText to $destinationText"
                        } else {
                            "○ Paused (Tap to configure)"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (enabled && (isUnlocked || credits > 0)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { isExpanded = !isExpanded }) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Expanded Settings Content
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(10.dp))

                // Master Toggle Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Automated Backups",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Runs silently while phone is idle and charging",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enabled && (isUnlocked || credits > 0),
                        onCheckedChange = { isChecked ->
                            if (isChecked && !isUnlocked && credits <= 0) {
                                onClaimCredits()
                            } else {
                                prefs.setAutoBackupEnabled(isChecked)
                                if (isChecked) {
                                    onRequestNotifications()
                                    AutoBackupWorker.scheduleAutoBackup(
                                        context = context,
                                        intervalDays = frequencyDays.toLong(),
                                        requiresWifi = requiresWifi
                                    )
                                } else {
                                    AutoBackupWorker.cancelAutoBackup(context)
                                }
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!isUnlocked) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Credit: $credits Night(s)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Claim Free Nights >",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { onClaimCredits() }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onPurchaseUnlimited,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Unlimited ($autoBackupPrice)")
                        }
                        OutlinedButton(
                            onClick = onPurchasePro,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Get Pro ($productPrice)")
                        }
                    }
                } else {
                    val lastRunText = if (lastRunTimestamp > 0) {
                        val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
                        "Last backup completed: ${sdf.format(Date(lastRunTimestamp))}"
                    } else {
                        "Pending next scheduled charging window"
                    }

                    Text(
                        text = "📅 $lastRunText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Conversation Scope: All vs Selected Conversations
                    Text(
                        text = "Conversations to Back Up",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { prefs.setAutoBackupAllThreads(true) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = allThreadsActive,
                            onClick = { prefs.setAutoBackupAllThreads(true) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "All Conversations (Full Device Backup)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                prefs.setAutoBackupAllThreads(false)
                                onOpenThreadPicker()
                            }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !allThreadsActive,
                            onClick = {
                                prefs.setAutoBackupAllThreads(false)
                                onOpenThreadPicker()
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Selected Conversations Only",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = if (selectedThreadIds.isEmpty()) "Tap to pick specific chats..." else "${selectedThreadIds.size} chat(s) selected (Tap to change)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = onOpenThreadPicker) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Pick Threads",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Frequency Selector
                    Text(
                        text = "Backup Frequency",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(1 to "Nightly", 3 to "Every 3 Days", 7 to "Weekly").forEach { (days, label) ->
                            FilterChip(
                                selected = frequencyDays == days,
                                onClick = {
                                    prefs.setAutoBackupFrequencyDays(days)
                                    AutoBackupWorker.scheduleAutoBackup(
                                        context = context,
                                        intervalDays = days.toLong(),
                                        requiresWifi = requiresWifi
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Wi-Fi Constraint
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = !requiresWifi
                                prefs.setAutoBackupRequiresWifi(next)
                                AutoBackupWorker.scheduleAutoBackup(
                                    context = context,
                                    intervalDays = frequencyDays.toLong(),
                                    requiresWifi = next
                                )
                            }
                    ) {
                        Checkbox(
                            checked = requiresWifi,
                            onCheckedChange = { next ->
                                prefs.setAutoBackupRequiresWifi(next)
                                AutoBackupWorker.scheduleAutoBackup(
                                    context = context,
                                    intervalDays = frequencyDays.toLong(),
                                    requiresWifi = next
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "Only backup over Wi-Fi",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Save mobile cellular data when archiving large attachments",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
