package com.messagebackup.app.ui.settings

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.billing.BillingManager
import com.messagebackup.app.model.ExportFormat
import com.messagebackup.app.model.OutputDestination
import java.io.File

/**
 * Settings Screen with Pro purchase, Tip Jar donations, Cache Cleaner,
 * SAF folder picker, pagination default, and contact resolution toggle.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.activity.compose.BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val activity = context as Activity
    val app = MessageBackupApp.instance
    val prefs = app.appPreferences
    val billingManager = app.billingManager

    val resolveContacts by prefs.resolveContacts.collectAsState()
    val defaultFormat by prefs.defaultFormat.collectAsState()
    val maxMessagesPerFile by prefs.maxMessagesPerFile.collectAsState()
    val outputDestination by prefs.outputDestination.collectAsState()
    val safFolderDisplay by prefs.safDirectoryDisplay.collectAsState()
    val appTheme by prefs.appTheme.collectAsState()
    val alwaysPromptExportOptions by prefs.alwaysPromptExportOptions.collectAsState()
    val isAdsRemoved by billingManager.isAdsRemoved.collectAsState()
    val productPrice by billingManager.productPrice.collectAsState()
    val isAutoBackupUnlocked by billingManager.isAutoBackupUnlocked.collectAsState()
    val autoBackupPrice by billingManager.autoBackupPrice.collectAsState()

    // O.D.I.N. AI, Auto-Backup, and Privacy Lock States
    val aiCredits by prefs.aiCredits.collectAsState()
    val autoBackupCredits by prefs.autoBackupCredits.collectAsState()
    val autoBackupEnabled by prefs.autoBackupEnabled.collectAsState()
    val appLockEnabled by prefs.appLockEnabled.collectAsState()
    val odinLockEnabled by prefs.odinLockEnabled.collectAsState()
    val privacyPinHash by prefs.privacyPinHash.collectAsState()
    val aiProvider by prefs.aiProvider.collectAsState()
    val geminiApiKey by prefs.geminiApiKey.collectAsState()
    val geminiModel by prefs.geminiModel.collectAsState()
    val ollamaEndpoint by prefs.ollamaEndpoint.collectAsState()
    val ollamaModel by prefs.ollamaModel.collectAsState()
    val odinTemperature by prefs.odinTemperature.collectAsState()
    val odinTopP by prefs.odinTopP.collectAsState()
    val odinNumCtx by prefs.odinNumCtx.collectAsState()
    val customSystemPrompt by prefs.odinCustomSystemPrompt.collectAsState()
    var isAdvancedOdinExpanded by remember { mutableStateOf(false) }
    var promptEditText by remember(customSystemPrompt) { mutableStateOf(customSystemPrompt) }

    var showOllamaGuide by remember { mutableStateOf(false) }
    var showRewardArcade by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var showDriveUnlockDialog by remember { mutableStateOf(false) }
    var devTapCount by remember { mutableStateOf(0) }
    var showDevKeyDialog by remember { mutableStateOf(false) }
    var devKeyInput by remember { mutableStateOf("") }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    // Export cache size computation (including subdirectories like unzipped viewer files)
    val exportsDir = File(context.cacheDir, "exports")
    var cacheSizeMb by remember {
        val bytes = if (exportsDir.exists()) {
            exportsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } else 0L
        mutableStateOf(String.format("%.2f MB", bytes.toDouble() / (1024 * 1024)))
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri: Uri? ->
        if (treeUri != null) {
            try {
                val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
                val folderName = treeUri.lastPathSegment?.substringAfterLast(':') ?: "Selected Folder"
                prefs.setSafDirectory(treeUri, folderName)
                prefs.setOutputDestination(OutputDestination.PERSISTENT_DIRECTORY)
            } catch (e: Exception) {
                // Fallback
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings & Preferences") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 1. Pro / Ad-Free Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdsRemoved) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isAdsRemoved) {
                        billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS)
                    }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isAdsRemoved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isAdsRemoved) Icons.Default.Stars else Icons.Default.ShoppingCart,
                                    contentDescription = "Billing",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAdsRemoved) "🌟 Pro Lifetime License Active" else "⚡ Upgrade to Pro ($productPrice)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (isAdsRemoved) "All premium features and ad-free access permanently unlocked." else "One-time purchase • No subscriptions • Lifetime access",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isAdsRemoved) "UNLOCKED FEATURES:" else "WHAT'S INCLUDED IN PRO:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    val proFeatures = listOf(
                        "🚫 100% Ad-Free Experience" to "Removes all banners, interstitials, and video ads across the entire app.",
                        "☁️ Direct Google Drive Cloud Sync" to "Seamlessly save and sync all message backups directly to your Google Drive.",
                        "⚡ Unlimited O.D.I.N. AI Queries" to "Ask unlimited AI questions about your messages without credits or watching ads.",
                        "🔄 Unlimited Automated Nightly Backups" to "Scheduled hands-free backups run automatically every night with zero ad requirements.",
                        "⚡ Instant One-Click Message Exports" to "Export to HTML, PDF, CSV, JSON, TXT, or Encrypted ZIP without watching rewarded ads.",
                        "📊 Unlimited Conversation Insights" to "Generate full conversational statistics and share HTML reports without ad gates."
                    )

                    proFeatures.forEach { (title, subtitle) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .size(16.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (!isAdsRemoved) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Get Pro ($productPrice)")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    billingManager.restorePurchases { restored ->
                                        android.widget.Toast.makeText(
                                            context,
                                            if (restored) "Pro License restored successfully!" else "No previous purchase found for this Google account.",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            ) {
                                Text(
                                    text = "Restore",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Developer Tip Jar (Consumable In-App Purchases)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Tip Jar",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Tip Jar / Support the Developer",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Enjoying the app? Your tips help fund future updates, new export formats, and ongoing development!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { billingManager.launchBillingFlow(activity, BillingManager.TIP_SMALL) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "☕ $0.99")
                        }
                        OutlinedButton(
                            onClick = { billingManager.launchBillingFlow(activity, BillingManager.TIP_MEDIUM) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🍕 $2.99")
                        }
                        OutlinedButton(
                            onClick = { billingManager.launchBillingFlow(activity, BillingManager.TIP_LARGE) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "🚀 $4.99")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 3. Export Destination (SAF Folder vs Share Sheet)
            Text(
                text = "Default Save Location",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { prefs.setOutputDestination(OutputDestination.SHARE_SHEET) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (outputDestination == OutputDestination.SHARE_SHEET),
                    onClick = { prefs.setOutputDestination(OutputDestination.SHARE_SHEET) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Android Share Sheet (Default)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Opens Drive, Gmail, WhatsApp, or local file manager",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { folderPickerLauncher.launch(null) }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (outputDestination == OutputDestination.PERSISTENT_DIRECTORY),
                    onClick = { folderPickerLauncher.launch(null) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Save to Custom Folder (SD / Device)", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Folder: $safFolderDisplay (Tap to select)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Icon(imageVector = Icons.Default.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary)
            }

            // Google Drive Cloud Destination
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.GOOGLE_DRIVE)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (outputDestination == OutputDestination.GOOGLE_DRIVE),
                    onClick = {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.GOOGLE_DRIVE)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Google Drive (Cloud Sync)", style = MaterialTheme.typography.bodyMedium)
                        if (!isAutoBackupUnlocked) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "PRO / AUTO",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isAutoBackupUnlocked) "Direct cloud upload to your Google Drive account" else "Unlock with Auto-Backup ($autoBackupPrice) or Pro ($productPrice)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAutoBackupUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Drive", tint = MaterialTheme.colorScheme.primary)
            }

            // Microsoft OneDrive
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.ONEDRIVE)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (outputDestination == OutputDestination.ONEDRIVE),
                    onClick = {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.ONEDRIVE)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Microsoft OneDrive", style = MaterialTheme.typography.bodyMedium)
                        if (!isAutoBackupUnlocked) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "PRO / AUTO",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isAutoBackupUnlocked) "Direct cloud upload to your OneDrive storage" else "Unlock with Auto-Backup ($autoBackupPrice) or Pro ($productPrice)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAutoBackupUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "OneDrive", tint = MaterialTheme.colorScheme.primary)
            }

            // Dropbox
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.DROPBOX)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (outputDestination == OutputDestination.DROPBOX),
                    onClick = {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.DROPBOX)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "Dropbox", style = MaterialTheme.typography.bodyMedium)
                        if (!isAutoBackupUnlocked) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "PRO / AUTO",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isAutoBackupUnlocked) "Direct cloud upload to your Dropbox account" else "Unlock with Auto-Backup ($autoBackupPrice) or Pro ($productPrice)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAutoBackupUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(imageVector = Icons.Default.CloudUpload, contentDescription = "Dropbox", tint = MaterialTheme.colorScheme.primary)
            }

            // WebDAV / Local NAS / Nextcloud
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.WEBDAV_NAS)
                        }
                    }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (outputDestination == OutputDestination.WEBDAV_NAS),
                    onClick = {
                        if (!isAutoBackupUnlocked) {
                            showDriveUnlockDialog = true
                        } else {
                            prefs.setOutputDestination(OutputDestination.WEBDAV_NAS)
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "WebDAV / Local NAS / Nextcloud", style = MaterialTheme.typography.bodyMedium)
                        if (!isAutoBackupUnlocked) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.padding(start = 6.dp)
                            ) {
                                Text(
                                    text = "PRO / AUTO",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = if (isAutoBackupUnlocked) "Upload to private home server, Synology, or Nextcloud" else "Unlock with Auto-Backup ($autoBackupPrice) or Pro ($productPrice)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isAutoBackupUnlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(imageVector = Icons.Default.Lan, contentDescription = "WebDAV", tint = MaterialTheme.colorScheme.primary)
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 4. Global Pagination Default
            Text(
                text = "Global Pagination Default",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Default setting for splitting conversations into sequential files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            val paginationOptions = listOf(0 to "No Split (Single File - Default)", 50 to "50 messages per file", 100 to "100 messages per file", 250 to "250 messages per file")
            paginationOptions.forEach { (limit, label) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { prefs.setMaxMessagesPerFile(limit) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (maxMessagesPerFile == limit),
                        onClick = { prefs.setMaxMessagesPerFile(limit) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = label, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 5. Default Format
            Text(
                text = "Default Export Format",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ExportFormat.values().forEach { format ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { prefs.setDefaultFormat(format) }
                        .padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (defaultFormat == format),
                        onClick = { prefs.setDefaultFormat(format) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = format.displayName, style = MaterialTheme.typography.bodyMedium)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 6. Export Dialog Configuration Prompt Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Export Options Popup",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (alwaysPromptExportOptions)
                            "Shows the customization popup before each export (format, theme, date range, destination)."
                        else
                            "Always use default settings set here and start export immediately without showing the popup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = alwaysPromptExportOptions,
                    onCheckedChange = { prefs.setAlwaysPromptExportOptions(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 7. Automated Scheduled Backups
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAutoBackupUnlocked || autoBackupCredits > 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Schedule",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "Automatic Scheduled Backups",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Switch(
                            checked = autoBackupEnabled && (isAutoBackupUnlocked || autoBackupCredits > 0),
                            onCheckedChange = { enabled ->
                                if (enabled && !isAutoBackupUnlocked && autoBackupCredits <= 0) {
                                    Toast.makeText(context, "Claim free Auto-Backup credits in Reward Arcade or unlock Pro first!", Toast.LENGTH_SHORT).show()
                                    showRewardArcade = true
                                } else {
                                    prefs.setAutoBackupEnabled(enabled)
                                    if (enabled) {
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                        com.messagebackup.app.worker.AutoBackupWorker.scheduleAutoBackup(context)
                                        Toast.makeText(context, "Nightly Auto-Backup enabled!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        com.messagebackup.app.worker.AutoBackupWorker.cancelAutoBackup(context)
                                        Toast.makeText(context, "Auto-Backup disabled.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAutoBackupUnlocked)
                            "100% Optional: Toggle on to automatically export conversations nightly while charging to your chosen destination."
                        else
                            "Automatically archive SMS/MMS messages nightly in the background. Use free Reward Arcade credits, buy standalone ($autoBackupPrice), or get Pro ($productPrice)!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!isAutoBackupUnlocked) {
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
                                    text = "Current Credit: $autoBackupCredits Night(s)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = "Claim Free Nights >",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable { showRewardArcade = true }
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_AUTO_BACKUP) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "Unlimited ($autoBackupPrice)")
                            }
                            OutlinedButton(
                                onClick = { billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(text = "Get Pro ($productPrice)")
                            }
                        }
                    } else {
                        val destinationText = when (outputDestination) {
                            OutputDestination.GOOGLE_DRIVE -> "Google Drive"
                            OutputDestination.ONEDRIVE -> "Microsoft OneDrive"
                            OutputDestination.DROPBOX -> "Dropbox"
                            OutputDestination.WEBDAV_NAS -> "WebDAV / Local NAS"
                            OutputDestination.PERSISTENT_DIRECTORY -> safFolderDisplay
                            OutputDestination.SHARE_SHEET -> "Share Sheet"
                        }
                        Text(
                            text = if (autoBackupEnabled) "✓ Active: Nightly backup to $destinationText" else "⏸ Paused: Toggle switch above to enable nightly backup to $destinationText",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (autoBackupEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 7. O.D.I.N. (Omniscient Document & Insights Navigator) AI Configuration
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                text = "O.D.I.N. AI Intelligence",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (isAdsRemoved) "PRO: UNLIMITED" else "$aiCredits CREDITS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Choose your AI intelligence provider to ask natural questions about your archived messages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Provider Radio Selection
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { prefs.setAiProvider("GEMINI") },
                            colors = if (aiProvider == "GEMINI") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Google Gemini")
                        }

                        OutlinedButton(
                            onClick = { prefs.setAiProvider("OLLAMA") },
                            colors = if (aiProvider == "OLLAMA") ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Local Ollama")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (aiProvider == "GEMINI") {
                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = { prefs.setGeminiApiKey(it) },
                            label = { Text("Gemini API Key (BYOK)") },
                            placeholder = { Text("Paste free Google AI Studio key") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { prefs.setGeminiModel(it) },
                            label = { Text("Gemini Model Name (★ gemini-2.0-flash)") },
                            placeholder = { Text("gemini-2.0-flash") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Get a free API key at aistudio.google.com. Defaults to fast Gemini 2.0 Flash.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    } else {
                        OutlinedTextField(
                            value = ollamaEndpoint,
                            onValueChange = { prefs.setOllamaEndpoint(it) },
                            label = { Text("Ollama Host / Tailscale URL") },
                            placeholder = { Text("http://100.x.y.z:11434") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = ollamaModel,
                            onValueChange = { prefs.setOllamaModel(it) },
                            label = { Text("Model Name (★ qwen2.5:14b Recommended)") },
                            placeholder = { Text("qwen2.5:14b") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "★ Recommended: qwen2.5:14b for highest accuracy. Lightweight fallback: llama3.2 (3B).",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { showOllamaGuide = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lan,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Ollama & Tailscale Setup Guide")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Collapsible Advanced Model Tuning & System Prompt Section
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAdvancedOdinExpanded = !isAdvancedOdinExpanded }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Advanced Tuning & System Prompt",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isAdvancedOdinExpanded) "Tap to collapse" else "Temperature, Context Window & Prompt rules",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Icon(
                                imageVector = if (isAdvancedOdinExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isAdvancedOdinExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                        ) {
                            // 1. Temperature Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Temperature (Creativity vs. Precision):",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = String.format("%.2f", odinTemperature),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (odinTemperature <= 0.25f) "0.0 - 0.25: Strict factual recall (Best for message searches)" else if (odinTemperature <= 0.7f) "0.3 - 0.7: Balanced tone & natural summaries" else "0.8 - 1.0: Creative responses (May hallucinate facts)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = odinTemperature,
                                onValueChange = { prefs.setOdinTemperature(it) },
                                valueRange = 0.0f..1.0f,
                                steps = 19,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // 2. Context Window (num_ctx) for Ollama
                            if (aiProvider == "OLLAMA") {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Context Window (num_ctx):",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$odinNumCtx tokens",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text(
                                    text = "Controls how many text messages O.D.I.N. can read at once. 8k is ideal for Qwen 2.5 14B.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf(2048, 4096, 8192, 16384).forEach { size ->
                                        OutlinedButton(
                                            onClick = { prefs.setOdinNumCtx(size) },
                                            colors = if (odinNumCtx == size) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text(
                                                text = "${size / 1024}k",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (odinNumCtx == size) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // 3. Custom System Prompt & Persona
                            Text(
                                text = "System Prompt & Persona Rules:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Edit O.D.I.N.'s base instructions, tone, and formatting rules. Leave blank to use the default archivist prompt.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            // Quick Prompt Preset Chips
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val concise = "You are O.D.I.N., an ultra-concise assistant. Answer factually in 1-2 bullet points. Never include conversational filler."
                                        promptEditText = concise
                                        prefs.setOdinCustomSystemPrompt(concise)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⚡ Concise", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Button(
                                    onClick = {
                                        val forensic = "You are O.D.I.N. Forensic Archivist. Quote relevant text messages verbatim with timestamps. Highlight discrepancies and confirm sender IDs."
                                        promptEditText = forensic
                                        prefs.setOdinCustomSystemPrompt(forensic)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("⚖️ Forensic", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            OutlinedTextField(
                                value = promptEditText,
                                onValueChange = {
                                    promptEditText = it
                                    prefs.setOdinCustomSystemPrompt(it)
                                },
                                placeholder = {
                                    Text(
                                        text = com.messagebackup.app.domain.ai.AiClientEngine.DEFAULT_SYSTEM_INSTRUCTION,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                },
                                minLines = 4,
                                maxLines = 8,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                TextButton(
                                    onClick = {
                                        prefs.resetOdinSystemPrompt()
                                        promptEditText = ""
                                        Toast.makeText(context, "System Prompt reset to default!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Reset to Default")
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 8. Privacy PIN & Biometric Lock
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔒 Privacy PIN & Biometric Lock",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Protect your message archive and O.D.I.N. Copilot with your phone's Fingerprint / Face Unlock or a custom SHA-256 Privacy PIN.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Lock App on Startup", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Require PIN or Biometrics when opening the app",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { enabled ->
                                prefs.setAppLockEnabled(enabled)
                                if (enabled && privacyPinHash.isBlank()) {
                                    showSetPinDialog = true
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Lock O.D.I.N. & Document Viewer", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Require PIN or Biometrics before opening AI or viewing archives",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = odinLockEnabled,
                            onCheckedChange = { enabled ->
                                prefs.setOdinLockEnabled(enabled)
                                if (enabled && privacyPinHash.isBlank()) {
                                    showSetPinDialog = true
                                }
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = { showSetPinDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (privacyPinHash.isNotBlank()) "Change Custom Privacy PIN" else "Set Custom Privacy PIN")
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 9. Contact Resolution
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Show Contact Names", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Resolve phone numbers to your address book names",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = resolveContacts,
                    onCheckedChange = { prefs.setResolveContacts(it) }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 10. App Appearance Theme
            Text(
                text = "App Appearance Theme",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                com.messagebackup.app.model.ExportTheme.values().forEach { themeOption ->
                    val isSelected = appTheme == themeOption
                    OutlinedButton(
                        onClick = { prefs.setAppTheme(themeOption) },
                        colors = if (isSelected) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = themeOption.displayName.split(" ").first(),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 11. Storage / Export Cache Cleaner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Export Storage Cache", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Temporary archives: $cacheSizeMb",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        exportsDir.listFiles()?.forEach { it.deleteRecursively() }
                        cacheSizeMb = "0.00 MB"
                        Toast.makeText(context, "Temporary export cache cleared.", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Cache", modifier = Modifier.padding(end = 4.dp))
                    Text(text = "Clear")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 12. About & Legal
            Text(
                text = "About & Legal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Message Backup & Export v1.0.0",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable {
                    devTapCount++
                    if (devTapCount >= 7) {
                        devTapCount = 0
                        showDevKeyDialog = true
                    } else if (devTapCount >= 4) {
                        Toast.makeText(context, "${7 - devTapCount} taps away from Developer Mode", Toast.LENGTH_SHORT).show()
                    }
                }
            )
            Text(
                text = "Private on-device SMS/MMS archive utility.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    val privacyIntent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://NfgOdin.github.io/MessageBackupApp/")
                    )
                    try {
                        context.startActivity(privacyIntent)
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "View Privacy Policy")
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    prefs.setOnboardingCompleted(false)
                    Toast.makeText(context, "Onboarding walkthrough will appear on main screen.", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "Replay App Tour & Feature Walkthrough")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (showSetPinDialog) {
        com.messagebackup.app.ui.dialogs.SetPrivacyPinDialog(
            prefs = prefs,
            onPinSaved = { showSetPinDialog = false },
            onDismiss = { showSetPinDialog = false }
        )
    }

    if (showOllamaGuide) {
        com.messagebackup.app.ui.dialogs.OllamaSetupGuideDialog(
            currentEndpoint = ollamaEndpoint,
            currentModel = ollamaModel,
            onSaveEndpoint = { endpoint, model ->
                prefs.setOllamaEndpoint(endpoint)
                prefs.setOllamaModel(model)
                prefs.setAiProvider("LOCAL_OLLAMA")
            },
            onDismiss = { showOllamaGuide = false }
        )
    }

    if (showRewardArcade) {
        com.messagebackup.app.ui.dialogs.CreditArcadeDialog(
            prefs = prefs,
            adManager = app.adManager,
            onDismiss = { showRewardArcade = false }
        )
    }

    if (showDevKeyDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDevKeyDialog = false },
            title = { Text("Developer License Activation") },
            text = {
                Column {
                    Text(
                        text = "Enter your secret developer master key to unlock lifetime Pro features with zero payment.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = devKeyInput,
                        onValueChange = { devKeyInput = it },
                        label = { Text("Master Secret Key") },
                        placeholder = { Text("Paste developer key") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isAdsRemoved) {
                        OutlinedButton(
                            onClick = {
                                billingManager.resetDeveloperPro()
                                Toast.makeText(context, "↩️ License reset: Switched back to Free Tier!", Toast.LENGTH_LONG).show()
                                showDevKeyDialog = false
                            },
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Revoke Pro (Test Free)")
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val success = billingManager.unlockDeveloperPro(devKeyInput)
                                if (success) {
                                    Toast.makeText(context, "🌟 Developer Pro License Activated!", Toast.LENGTH_LONG).show()
                                    showDevKeyDialog = false
                                    devKeyInput = ""
                                } else {
                                    Toast.makeText(context, "Invalid Developer Key", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Activate")
                        }
                        OutlinedButton(
                            onClick = { showDevKeyDialog = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
            },
            dismissButton = null
        )
    }

    // Google Drive Unlock Option Modal
    if (showDriveUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showDriveUnlockDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Google Drive",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Unlock Google Drive Cloud Sync",
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column {
                    Text(
                        text = "Direct Google Drive Cloud Sync allows you to automatically upload and sync all your conversation backups securely to your personal Google Drive account.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Option A: Pro (Best Value)
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDriveUnlockDialog = false
                                billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS)
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🌟 Upgrade to Pro (Best Value)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = productPrice ?: "$4.99",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Includes Google Drive Cloud Sync, 100% Ad-Free, unlimited AI, and unlimited nightly backups forever.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Option B: Auto-Backup Standalone
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                showDriveUnlockDialog = false
                                billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_AUTO_BACKUP)
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🔄 Auto-Backup Standalone",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = autoBackupPrice ?: "$1.99",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Unlocks Google Drive cloud destination and unlimited nightly scheduled backups.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDriveUnlockDialog = false }) {
                    Text("Maybe Later")
                }
            }
        )
    }
}
