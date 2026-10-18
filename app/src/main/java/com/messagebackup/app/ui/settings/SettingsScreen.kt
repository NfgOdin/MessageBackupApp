package com.messagebackup.app.ui.settings

import android.app.Activity
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ShoppingCart
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
    val isAdsRemoved by billingManager.isAdsRemoved.collectAsState()
    val productPrice by billingManager.productPrice.collectAsState()

    // Export cache size computation
    val exportsDir = File(context.cacheDir, "exports")
    var cacheSizeMb by remember {
        val bytes = exportsDir.listFiles()?.sumOf { it.length() } ?: 0L
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
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !isAdsRemoved) {
                        billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Billing",
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isAdsRemoved) "Pro License Active" else "Upgrade to Pro ($productPrice)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isAdsRemoved) "Ad-free experience & automated scheduled backups unlocked." else "One-time purchase: removes all ads & unlocks automatic nightly/weekly backups.",
                            style = MaterialTheme.typography.bodySmall
                        )
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

            // 6. Automated Scheduled Backups (Pro Feature)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdsRemoved) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isAdsRemoved)
                            "Nightly automatic export scheduled at 2:00 AM to your designated folder."
                        else
                            "Automatically archive all new SMS/MMS messages nightly or weekly in the background. Included with the $4.99 Pro upgrade.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (!isAdsRemoved) {
                        Button(
                            onClick = { billingManager.launchBillingFlow(activity, BillingManager.PRODUCT_REMOVE_ADS) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Unlock Auto Backups ($productPrice)")
                        }
                    } else {
                        Text(
                            text = "✓ Enabled: Backs up to $safFolderDisplay",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 7. Contact Resolution
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

            // 7. Storage / Export Cache Cleaner
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
                        exportsDir.listFiles()?.forEach { it.delete() }
                        cacheSizeMb = "0.00 MB"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = "Clear Cache", modifier = Modifier.padding(end = 4.dp))
                    Text(text = "Clear")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // 8. About & Legal
            Text(
                text = "About & Legal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Message Backup & Export v1.0.0", style = MaterialTheme.typography.bodyMedium)
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
                        android.net.Uri.parse("https://odinj2010.github.io/MessageBackupApp/")
                    )
                    try {
                        context.startActivity(privacyIntent)
                    } catch (e: Exception) {}
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "View Privacy Policy")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
