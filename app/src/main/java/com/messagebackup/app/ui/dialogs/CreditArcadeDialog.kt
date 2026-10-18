package com.messagebackup.app.ui.dialogs

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.draw.clip
import com.messagebackup.app.ads.AdManager
import com.messagebackup.app.data.preferences.AppPreferences

enum class RewardOption(val title: String, val subtitle: String, val iconVector: androidx.compose.ui.graphics.vector.ImageVector) {
    ODIN_AI("+2 O.D.I.N. AI Queries", "Watch 1 ad -> Instant +2 message intelligence queries", Icons.Default.AutoAwesome),
    AUTO_BACKUP("+1 Auto-Backup Night (3 Ads)", "Watch 3 ads -> Unlocks 1 automated backup night", Icons.Default.Schedule)
}

/**
 * Interactive Reward Center / Arcade where free users can select their preferred reward
 * and watch a quick rewarded ad to earn credits.
 */
@Composable
fun CreditArcadeDialog(
    prefs: AppPreferences,
    adManager: AdManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as Activity

    val aiCredits by prefs.aiCredits.collectAsState()
    val autoBackupCredits by prefs.autoBackupCredits.collectAsState()
    val backupAdProgress by prefs.backupAdProgress.collectAsState()

    var selectedOption by remember { mutableStateOf(RewardOption.ODIN_AI) }
    var showCelebrationDialog by remember { mutableStateOf(false) }

    val lastRewardAdTime by prefs.lastRewardAdTime.collectAsState()
    var cooldownSeconds by remember { mutableStateOf(prefs.getRewardCooldownSeconds()) }

    LaunchedEffect(lastRewardAdTime) {
        while (true) {
            val remaining = prefs.getRewardCooldownSeconds()
            cooldownSeconds = remaining
            if (remaining <= 0L) break
            delay(1000L)
        }
    }

    if (showCelebrationDialog) {
        PunchCardUnlockedDialog(
            onDismiss = {
                showCelebrationDialog = false
                onDismiss()
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CardGiftcard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Reward Arcade",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Current Balance: ⚡ $aiCredits AI Queries • 🔄 $autoBackupCredits Backup Nights",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Pick your reward below and watch a quick ad to claim your credits instantly:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

                // Reward Options
                RewardOption.values().forEach { option ->
                    val isSelected = selectedOption == option
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedOption = option }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedOption = option }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = option.iconVector,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = option.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (option == RewardOption.AUTO_BACKUP) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Punch Card: $backupAdProgress of 3 Ads Watched",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = if (backupAdProgress >= 2) "🎯 Next ad unlocks 1 backup night!" else "⏳ ${3 - backupAdProgress} more ads to unlock 1 night",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // 3 Clear Punch Stamps
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                for (step in 1..3) {
                                                    val isStamped = backupAdProgress >= step
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isStamped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(34.dp)
                                                    ) {
                                                        Row(
                                                            modifier = Modifier.fillMaxSize(),
                                                            horizontalArrangement = Arrangement.Center,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isStamped) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                                                contentDescription = null,
                                                                tint = if (isStamped) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "Ad $step",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = if (isStamped) FontWeight.Bold else FontWeight.Normal,
                                                                color = if (isStamped) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val hasReviewedApp by prefs.hasReviewedApp.collectAsState()
                Spacer(modifier = Modifier.height(10.dp))

                // Rate & Review Bonus Card (+5 AI & +1 Backup Night)
                Surface(
                    color = if (hasReviewedApp) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !hasReviewedApp) {
                            try {
                                val uri = android.net.Uri.parse("market://details?id=${context.packageName}")
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                            } catch (e: Exception) {
                                val uri = android.net.Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                            }
                            val awarded = prefs.markAppReviewed()
                            if (awarded) {
                                Toast.makeText(context, "🌟 Thank you for reviewing! +5 AI & +1 Backup Night awarded!", Toast.LENGTH_LONG).show()
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = null,
                            tint = if (hasReviewedApp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (hasReviewedApp) "⭐ Review Bonus Claimed" else "⭐ Rate on Google Play (+5 AI & +1 Night)",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hasReviewedApp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Text(
                                text = if (hasReviewedApp) "Thank you for supporting Message Backup development!" else "Leave a quick 5-star review to claim +5 AI queries and +1 Auto-backup night instantly!",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasReviewedApp) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                val isCoolingDown = cooldownSeconds > 0L
                val buttonLabel = if (isCoolingDown) {
                    "⏳ Cooldown (${cooldownSeconds}s left)"
                } else {
                    when (selectedOption) {
                        RewardOption.ODIN_AI -> "Watch Ad (+2 AI Queries)"
                        RewardOption.AUTO_BACKUP -> {
                            when (backupAdProgress) {
                                0 -> "Watch Ad (Ad 1 of 3)"
                                1 -> "Watch Ad (Ad 2 of 3)"
                                else -> "Watch Ad (Ad 3 of 3 — Unlock Night!)"
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        if (isCoolingDown) {
                            Toast.makeText(context, "⏳ Please wait ${cooldownSeconds}s before watching another ad.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        adManager.showRewardedAd(
                            activity = activity,
                            isAdsRemoved = false,
                            onUserEarnedReward = {
                                prefs.recordRewardAdWatched()
                                when (selectedOption) {
                                    RewardOption.ODIN_AI -> {
                                        prefs.addAiCredits(2)
                                        Toast.makeText(context, "🌟 Claimed +2 O.D.I.N. AI Credits!", Toast.LENGTH_SHORT).show()
                                        onDismiss()
                                    }
                                    RewardOption.AUTO_BACKUP -> {
                                        val unlockedNight = prefs.recordBackupAdWatched()
                                        if (unlockedNight) {
                                            if (prefs.autoBackupEnabled.value) {
                                                com.messagebackup.app.worker.AutoBackupWorker.scheduleAutoBackup(context)
                                            }
                                            Toast.makeText(context, "🎉 3/3 Ads Watched! Claimed +1 Auto-Backup Night!", Toast.LENGTH_LONG).show()
                                            showCelebrationDialog = true
                                        } else {
                                            val current = prefs.backupAdProgress.value
                                            Toast.makeText(context, "🎟️ Stamp recorded! ($current/3 ads watched)", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                    },
                    enabled = !isCoolingDown,
                    colors = if (isCoolingDown) {
                        ButtonDefaults.buttonColors(
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = if (isCoolingDown) Icons.Default.HourglassTop else Icons.Default.VideoLibrary,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(buttonLabel)
                }
            }
        }
    }
}
