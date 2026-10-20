package com.messagebackup.app.ui.dialogs

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.R
import com.messagebackup.app.ads.AdManager
import com.messagebackup.app.billing.BillingManager
import com.messagebackup.app.data.preferences.AppPreferences
import com.messagebackup.app.worker.HuginnLeaseExpiryWorker
import kotlinx.coroutines.delay

enum class RewardOption(val amountLabel: String, val subtitle: String, val iconResId: Int) {
    ODIN_AI("+2 ᛟ", "Watch 1 short ad to receive 2 Othala Runes for O.D.I.N. AI forensic queries", R.drawable.ic_rune_othala),
    HUGINN_VOICE("+5 ᚫ", "Watch 1 short ad to receive 5 Ansuz Runes for Huginn Live Dispatch & Thought", R.drawable.ic_rune_ansuz),
    HUGINN_LEASE("3-Day Lease", "Punch 5 ads to unlock 72 hours of Huginn Live Dispatch (Stacks infinitely!)", R.drawable.ic_rune_ansuz),
    AUTO_BACKUP("+1 ᛃ", "Watch 3 short ads to receive 1 Jera Rune for automated nightly backup", R.drawable.ic_rune_jera)
}

/**
 * Interactive Reward Arcade & Runic Bazaar.
 * Features:
 * 1. 100% Free Ad-Reward Punch Cards:
 *    - +2 ᛟ Othala (1 ad)
 *    - +5 ᚫ Ansuz (1 ad)
 *    - 3-Day Huginn Lease (5-ad punch card with infinite stacking!)
 *    - +1 ᛃ Jera Night (3-ad punch card)
 * 2. The Runic Bazaar (In-App Store):
 *    - $0.99 Consumable Packs (100 Ansuz, 100 Othala, 10 Jera)
 *    - $0.99 Permanent Unlocks (Huginn Forever, Jera Auto-Backup Forever)
 *    - $4.99 Lifetime Pro Master Unlock (The ultimate no-brainer value)
 */
@Composable
fun CreditArcadeDialog(
    prefs: AppPreferences,
    adManager: AdManager,
    billingManager: BillingManager? = null,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as Activity
    val billing = billingManager ?: MessageBackupApp.instance.billingManager

    val aiCredits by prefs.aiCredits.collectAsState()
    val ansuzCredits by prefs.ansuzCredits.collectAsState()
    val autoBackupCredits by prefs.autoBackupCredits.collectAsState()
    val backupAdProgress by prefs.backupAdProgress.collectAsState()
    val huginnAdProgress by prefs.huginnAdProgress.collectAsState()
    val huginnLeaseExpiryMs by prefs.huginnLeaseExpiryMs.collectAsState()
    val isHuginnPermanentUnlocked by prefs.isHuginnPermanentUnlocked.collectAsState()
    val isAdsRemoved by billing.isAdsRemoved.collectAsState()
    val isHuginnUnlockedByBilling by billing.isHuginnUnlocked.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0 = Earn Free (Ads), 1 = Runic Bazaar (Store)
    var selectedOption by remember { mutableStateOf(RewardOption.HUGINN_LEASE) }

    var showCelebrationDialog by remember { mutableStateOf(false) }
    var celebrationTitle by remember { mutableStateOf("") }
    var celebrationDescription by remember { mutableStateOf("") }
    var celebrationSteps by remember { mutableStateOf(3) }

    val lastRewardAdTime by prefs.lastRewardAdTime.collectAsState()
    var cooldownSeconds by remember { mutableStateOf(prefs.getRewardCooldownSeconds()) }

    val now = System.currentTimeMillis()
    val remainingMs = huginnLeaseExpiryMs - now
    val remainingHours = if (remainingMs > 0) remainingMs / (1000L * 60L * 60L) else 0L
    val isHuginnActive = isAdsRemoved || isHuginnPermanentUnlocked || isHuginnUnlockedByBilling || remainingMs > 0

    LaunchedEffect(lastRewardAdTime) {
        while (true) {
            cooldownSeconds = prefs.getRewardCooldownSeconds()
            if (cooldownSeconds <= 0L) break
            delay(1000L)
        }
    }

    if (showCelebrationDialog) {
        PunchCardUnlockedDialog(
            title = celebrationTitle,
            description = celebrationDescription,
            totalSteps = celebrationSteps,
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
            tonalElevation = 8.dp,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
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
                            text = "Arcade & Bazaar",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Balance strip
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = "Balances:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))

                    // ᛟ Othala
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rune_othala),
                        contentDescription = "Othala",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$aiCredits",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // ᚫ Ansuz
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rune_ansuz),
                        contentDescription = "Ansuz",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$ansuzCredits",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // ᛃ Jera
                    Icon(
                        painter = painterResource(id = R.drawable.ic_rune_jera),
                        contentDescription = "Jera",
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$autoBackupCredits",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Tab Switcher: Free Ads vs. Runic Bazaar Store
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    contentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.VideoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Earn Free (Ads)", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.ShoppingBag, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Runic Bazaar", fontWeight = FontWeight.Bold)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedTab == 0) {
                    // ==========================================
                    // TAB 0: 100% FREE AD-REWARD PUNCH CARDS
                    // ==========================================

                    val isCoolingDown = cooldownSeconds > 0L

                    // SECTION 1: SACRED RUNES (Instant & Banked Runes)
                    Text(
                        text = "ᚱ Sacred Runes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tap a Rune to inspect and earn without spending a cent:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val runeOptions = listOf(RewardOption.HUGINN_VOICE, RewardOption.ODIN_AI, RewardOption.AUTO_BACKUP)
                    runeOptions.forEach { option ->
                        val isSelected = selectedOption == option
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable { selectedOption = option }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedOption = option },
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(id = option.iconResId),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = when (option) {
                                                    RewardOption.HUGINN_VOICE -> "Ansuz Rune"
                                                    RewardOption.ODIN_AI -> "Othala Rune"
                                                    RewardOption.AUTO_BACKUP -> "Jera Rune"
                                                    else -> option.name
                                                },
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Text(
                                                    text = option.amountLabel,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = option.subtitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                // If this option is JERA and selected, reveal the 3-punch card!
                                if (isSelected && option == RewardOption.AUTO_BACKUP) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                text = "Jera Punch Card: $backupAdProgress of 3 Ads Watched",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (backupAdProgress >= 2) "🔥 Next ad unlocks +1 Jera Auto-Backup Night!" else "Watch ${3 - backupAdProgress} more ads to bank 1 Jera Nightly Backup credit",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
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

                                // Contextual Watch Ad button immediately underneath selected item!
                                if (isSelected) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    val adBtnLabel = if (isCoolingDown) {
                                        "⏳ Cooldown (${cooldownSeconds}s left)"
                                    } else {
                                        when (option) {
                                            RewardOption.HUGINN_VOICE -> "Watch Ad (+5 ᚫ Ansuz)"
                                            RewardOption.ODIN_AI -> "Watch Ad (+2 ᛟ Othala)"
                                            RewardOption.AUTO_BACKUP -> {
                                                when (backupAdProgress) {
                                                    0 -> "Watch Ad (Ad 1 of 3)"
                                                    1 -> "Watch Ad (Ad 2 of 3)"
                                                    else -> "Watch Ad (Ad 3 of 3 — Unlock ᛃ Night!)"
                                                }
                                            }
                                            else -> "Watch Ad"
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
                                                    when (option) {
                                                        RewardOption.ODIN_AI -> {
                                                            prefs.addAiCredits(2)
                                                            Toast.makeText(context, "🌟 Claimed +2 ᛟ Othala Runes!", Toast.LENGTH_SHORT).show()
                                                            onDismiss()
                                                        }
                                                        RewardOption.HUGINN_VOICE -> {
                                                            prefs.addAnsuzCredits(5)
                                                            Toast.makeText(context, "🪶 Claimed +5 ᚫ Ansuz Runes!", Toast.LENGTH_SHORT).show()
                                                            onDismiss()
                                                        }
                                                        RewardOption.AUTO_BACKUP -> {
                                                            val unlockedNight = prefs.recordBackupAdWatched()
                                                            if (unlockedNight) {
                                                                if (prefs.autoBackupEnabled.value) {
                                                                    com.messagebackup.app.worker.AutoBackupWorker.scheduleAutoBackup(context)
                                                                }
                                                                celebrationTitle = "+1 Auto-Backup Night Unlocked!"
                                                                celebrationDescription = "All 3 ads watched! Your automated overnight backup credit is safely banked."
                                                                celebrationSteps = 3
                                                                showCelebrationDialog = true
                                                            } else {
                                                                val current = prefs.backupAdProgress.value
                                                                Toast.makeText(context, "🎟️ Stamp recorded! ($current/3 ads watched)", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                        else -> {}
                                                    }
                                                }
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isCoolingDown) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCoolingDown) Icons.Default.HourglassTop else Icons.Default.VideoLibrary,
                                            contentDescription = null,
                                            modifier = Modifier.padding(end = 8.dp)
                                        )
                                        Text(adBtnLabel)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION 2: DIVINE LEASES
                    Text(
                        text = "⏳ Divine Leases",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Stackable timed access passes for continuous powers:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Huginn Lease Card
                    val isLeaseSelected = selectedOption == RewardOption.HUGINN_LEASE
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLeaseSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isLeaseSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selectedOption = RewardOption.HUGINN_LEASE }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isLeaseSelected,
                                    onClick = { selectedOption = RewardOption.HUGINN_LEASE },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = RewardOption.HUGINN_LEASE.iconResId),
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Huginn Live Dispatch",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "3-Day Lease",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = RewardOption.HUGINN_LEASE.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // 5-Punch Huginn Lease Visual Card (Visible when selected)
                            if (isLeaseSelected) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Punch Card: $huginnAdProgress of 5 Ads",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                if (isHuginnActive) {
                                                    val days = remainingHours / 24
                                                    val hrs = remainingHours % 24
                                                    val timeStr = if (days > 0) "${days}d ${hrs}h" else "${hrs}h"
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "⏱️ $timeStr active (Stackable)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (huginnAdProgress >= 4) "🔥 Next ad unlocks/stacks +3 DAYS of Huginn!" else "Watch ${5 - huginnAdProgress} more ads to stack +3 days of Huginn Live Dispatch",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // 5 Clear Punch Stamps
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                for (step in 1..5) {
                                                    val isStamped = huginnAdProgress >= step
                                                    Surface(
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (isStamped) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(32.dp)
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
                                                                modifier = Modifier.size(14.dp)
                                                            )
                                                            Spacer(modifier = Modifier.width(2.dp))
                                                            Text(
                                                                text = "$step",
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

                                Spacer(modifier = Modifier.height(10.dp))

                                val leaseAdLabel = if (isCoolingDown) {
                                    "⏳ Cooldown (${cooldownSeconds}s left)"
                                } else {
                                    when (huginnAdProgress) {
                                        0 -> "Watch Ad (Ad 1 of 5)"
                                        1 -> "Watch Ad (Ad 2 of 5)"
                                        2 -> "Watch Ad (Ad 3 of 5)"
                                        3 -> "Watch Ad (Ad 4 of 5)"
                                        else -> "Watch Ad (Ad 5 of 5 — Stack +3 Days!)"
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
                                                val completed = prefs.recordHuginnAdWatched()
                                                if (completed) {
                                                    HuginnLeaseExpiryWorker.scheduleExpiryAlerts(context, prefs.huginnLeaseExpiryMs.value)
                                                    celebrationTitle = "🪶 +3 Days Huginn Stacked!"
                                                    celebrationDescription = "5/5 punch card complete! Huginn Live Dispatch is now active for another 72 hours (or stacked on your balance)."
                                                    celebrationSteps = 5
                                                    showCelebrationDialog = true
                                                } else {
                                                    val curr = prefs.huginnAdProgress.value
                                                    Toast.makeText(context, "🎟️ Huginn stamp recorded! ($curr/5 ads watched)", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isCoolingDown) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isCoolingDown) Icons.Default.HourglassTop else Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(leaseAdLabel)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // SECTION 3: COUNCIL OF THE GODS (Google Play Review)
                    Text(
                        text = "⭐ Council of the Gods",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Honour the app with your feedback for an instant permanent blessing:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val hasReviewedApp by prefs.hasReviewedApp.collectAsState()
                    Spacer(modifier = Modifier.height(8.dp))

                    // Rate & Review Bonus Card (+5 AI & +1 Backup Night)
                    Surface(
                        color = if (hasReviewedApp) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(14.dp),
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
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stars,
                                contentDescription = null,
                                tint = if (hasReviewedApp) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(26.dp)
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
                                    text = if (hasReviewedApp) "Thank you for supporting Muninn development!" else "Leave a quick 5-star review to claim +5 AI queries and +1 Auto-backup night instantly!",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (hasReviewedApp) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                } else {
                    // ==========================================
                    // TAB 1: THE RUNIC BAZAAR (IN-APP STORE)
                    // ==========================================

                    // Master Pro Card ($4.99) - Unlocks Everything
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                billing.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS)
                            }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.WorkspacePremium,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Throne of Hliðskjálf",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            text = "👑 Muninn Lifetime Pro • Everything Unlocked Forever",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        billing.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = billing.proProductPrice.value ?: "$4.99",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "• Unlimited Huginn Live Dispatch & Thought\n• Unlimited Jera Scheduled Auto-Backups\n• Unlimited O.D.I.N. Forensic Queries\n• 100% Zero Ads across the entire app forever",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Or unlock individual powers forever for $0.99:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // $0.99 AN EYE FOR MÍMIR (O.D.I.N. Permanent AI Intelligence Unlock)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                billing.launchBillingFlow(activity, BillingManager.PRODUCT_UNLOCK_ODIN_EYE)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_rune_othala),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("👁️ An Eye for Mímir", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("Sacrifice an eye for infinite knowledge", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { billing.launchBillingFlow(activity, BillingManager.PRODUCT_UNLOCK_ODIN_EYE) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(billing.odinEyePrice.value ?: "$0.99")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "“Will you sacrifice an eye at the Well for the Allfather’s wisdom?”\nUnlocks O.D.I.N. Forensic AI Intelligence forever with unlimited queries and zero rune consumption.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // $0.99 HUGINN'S FLIGHT (Huginn Live Dispatch Unlock)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                billing.launchBillingFlow(activity, BillingManager.PRODUCT_UNLOCK_HUGINN)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_rune_ansuz),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("🪶 Huginn’s Flight", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("Unleash the flight of thought across the realms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { billing.launchBillingFlow(activity, BillingManager.PRODUCT_UNLOCK_HUGINN) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(billing.huginnPrice.value ?: "$0.99")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "“Huginn and Muninn fly each dawn across the earth...”\nUnlocks the Huginn Live Composer Bar forever with direct dispatch, thought counsel, and zero ads.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // $0.99 DRAUPNIR'S RING (Jera Nightly Auto-Backup Forever)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                billing.launchBillingFlow(activity, BillingManager.PRODUCT_AUTO_BACKUP)
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_rune_jera),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text("💍 Draupnir’s Ring", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text("Endless nightly duplication into the vault", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                                OutlinedButton(
                                    onClick = { billing.launchBillingFlow(activity, BillingManager.PRODUCT_AUTO_BACKUP) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(billing.autoBackupPrice.value ?: "$0.99")
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "“Every ninth night, golden rings drop from it of equal weight...”\nUnlocks automated scheduled backups forever with uninterrupted overnight memory archiving.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Consumable Rune Packs ($0.99 each):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Consumable packs with graphical shield badges and x100 indicators:
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 100 Ansuz Pack ($0.99)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { billing.launchBillingFlow(activity, BillingManager.PRODUCT_PACK_ANSUZ_100) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_rune_ansuz),
                                            contentDescription = "Ansuz",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "x100 ᚫ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$0.99",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 100 Othala Pack ($0.99)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { billing.launchBillingFlow(activity, BillingManager.PRODUCT_PACK_OTHALA_100) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_rune_othala),
                                            contentDescription = "Othala",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "x100 ᛟ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$0.99",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // 10 Jera Pack ($0.99)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { billing.launchBillingFlow(activity, BillingManager.PRODUCT_PACK_JERA_10) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_rune_jera),
                                            contentDescription = "Jera",
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "x10 ᛃ",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "$0.99",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
