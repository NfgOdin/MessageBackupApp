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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
 * Manages periodic background backups (Daily, 3 Days, Weekly), Wi-Fi constraints,
 * destination indicators, credit balances, and one-tap manual run triggers.
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
    context: Context
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUnlocked || credits > 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Automated Cloud & Scheduled Backups",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isUnlocked)
                    "Automatically archives SMS/MMS messages and cloud-syncs in the background while charging and idle."
                else
                    "Automatically archive SMS/MMS messages nightly. Use free Reward Arcade credits, unlock standalone ($autoBackupPrice), or get Pro ($productPrice)!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

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
                val destinationText = when (destination) {
                    OutputDestination.GOOGLE_DRIVE -> "Google Drive"
                    OutputDestination.ONEDRIVE -> "Microsoft OneDrive"
                    OutputDestination.DROPBOX -> "Dropbox"
                    OutputDestination.WEBDAV_NAS -> "WebDAV / Local NAS"
                    OutputDestination.PERSISTENT_DIRECTORY -> safFolderDisplay
                    OutputDestination.SHARE_SHEET -> "Share Sheet"
                }

                val frequencyText = if (frequencyDays == 1) "Every Night" else "Every $frequencyDays Days"
                val lastRunText = if (lastRunTimestamp > 0) {
                    val sdf = SimpleDateFormat("MMM dd, h:mm a", Locale.getDefault())
                    "Last run: ${sdf.format(Date(lastRunTimestamp))}"
                } else {
                    "Pending next charging cycle"
                }

                Text(
                    text = if (enabled) "✓ Active: Syncing $frequencyText to $destinationText • $lastRunText"
                    else "⏸ Paused: Toggle switch above to schedule backups to $destinationText",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (enabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

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
