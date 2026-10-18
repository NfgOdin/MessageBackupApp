package com.messagebackup.app.ui.main

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.messagebackup.app.model.OutputDestination
import com.messagebackup.app.ui.components.AdBannerView
import com.messagebackup.app.ui.components.ConversationItem
import com.messagebackup.app.ui.components.ExportConfigDialog
import com.messagebackup.app.ui.components.MessageBubble
import com.messagebackup.app.ui.components.SmsPermissionDisclosureDialog
import com.messagebackup.app.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Primary UI Screen hosting conversation list, search bar, selection mode,
 * preview overlay with conversation stats, and export triggers with quick-open.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as Activity
    val uiState by viewModel.uiState.collectAsState()
    val isAdsRemoved by viewModel.isAdsRemoved.collectAsState()
    val prefs = viewModel.prefs
    val defaultFormat by prefs.defaultFormat.collectAsState()
    val maxMessagesPerFile by prefs.maxMessagesPerFile.collectAsState()
    val exportTheme by prefs.exportTheme.collectAsState()
    val outputDestination by prefs.outputDestination.collectAsState()
    val safFolderDisplay by prefs.safDirectoryDisplay.collectAsState()
    val appLockEnabled by prefs.appLockEnabled.collectAsState()
    val odinLockEnabled by prefs.odinLockEnabled.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isMessageSelectionMode by remember { mutableStateOf(false) }
    var showRewardArcade by remember { mutableStateOf(false) }
    var isAppSessionUnlocked by remember { mutableStateOf(false) }
    var pendingProtectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val runWithOdinLock: (() -> Unit) -> Unit = { action ->
        if (odinLockEnabled) {
            pendingProtectedAction = action
        } else {
            action()
        }
    }

    // App Startup Privacy Lock
    if (appLockEnabled && !isAppSessionUnlocked) {
        Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            com.messagebackup.app.ui.dialogs.PrivacyUnlockDialog(
                title = "Unlock Message Backup",
                subtitle = "Enter your Privacy PIN or use Fingerprint / Device Lock to access your messages.",
                prefs = prefs,
                onUnlocked = { isAppSessionUnlocked = true },
                onCancel = { activity.finish() }
            )
        }
        return
    }

    // O.D.I.N. & Viewer Privacy Lock Prompt
    pendingProtectedAction?.let { action ->
        com.messagebackup.app.ui.dialogs.PrivacyUnlockDialog(
            title = "Unlock Protected Vault",
            subtitle = "Verify your identity to access O.D.I.N. Copilot or the Document Viewer.",
            prefs = prefs,
            onUnlocked = {
                pendingProtectedAction = null
                action()
            },
            onCancel = { pendingProtectedAction = null }
        )
    }

    // In-App Document Viewer Mode
    uiState.viewerFile?.let { file ->
        com.messagebackup.app.ui.viewer.DocumentViewerScreen(
            zipFile = file,
            onNavigateBack = {
                viewModel.adManager.showInterstitialAd(activity, isAdsRemoved) {
                    viewModel.setViewerFile(null)
                }
            },
            onShare = {
                val uri: Uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Save or Share Exported Messages"))
            },
            modifier = modifier
        )
        return
    }

    if (uiState.isSettingsOpen) {
        SettingsScreen(
            onNavigateBack = { viewModel.setSettingsOpen(false) },
            modifier = modifier
        )
        return
    }

    if (uiState.isCopilotOpen) {
        val targetName = uiState.selectedThreadForPreview?.displayName ?: "All Conversations"
        val targetMessages = if (uiState.selectedThreadForPreview != null) {
            uiState.previewMessages
        } else {
            uiState.globalCopilotMessages
        }
        com.messagebackup.app.ui.screens.MessageCopilotScreen(
            prefs = prefs,
            adManager = viewModel.adManager,
            isAdsRemoved = isAdsRemoved,
            threads = uiState.threads,
            currentMessages = targetMessages,
            targetContactName = targetName,
            onNavigateBack = { viewModel.setCopilotOpen(false) },
            onOpenSettings = {
                viewModel.setCopilotOpen(false)
                viewModel.setSettingsOpen(true)
            },
            modifier = modifier
        )
        return
    }

    // System Back Button Navigation Handling
    androidx.activity.compose.BackHandler(
        enabled = uiState.selectedThreadForPreview != null || uiState.isSelectionMode || uiState.isSearchActive
    ) {
        when {
            uiState.selectedThreadForPreview != null && isMessageSelectionMode -> {
                isMessageSelectionMode = false
            }
            uiState.selectedThreadForPreview != null && uiState.isThreadSearchActive -> {
                viewModel.setThreadSearchActive(false)
            }
            uiState.selectedThreadForPreview != null -> {
                isMessageSelectionMode = false
                viewModel.closeThreadPreview()
            }
            uiState.isSelectionMode -> {
                viewModel.clearSelection()
            }
            uiState.isSearchActive -> {
                viewModel.setSearchActive(false)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val smsGranted = permissions[android.Manifest.permission.READ_SMS] ?: false
        val contactsGranted = permissions[android.Manifest.permission.READ_CONTACTS] ?: false
        viewModel.onPermissionsChecked(hasSms = smsGranted, hasContacts = contactsGranted)
    }

    LaunchedEffect(Unit) {
        val hasSms = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_SMS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        val hasContacts = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        viewModel.onPermissionsChecked(hasSms = hasSms, hasContacts = hasContacts)
        viewModel.adManager.initializeUmpAndAds(activity)
    }

    val triggerShareSheet: (File) -> Unit = { file ->
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Save or Share Exported Messages"))
    }

    val triggerOpenFile: (File) -> Unit = { file ->
        viewModel.adManager.showRewardedAd(
            activity = activity,
            isAdsRemoved = isAdsRemoved,
            onUserEarnedReward = {
                runWithOdinLock {
                    viewModel.setViewerFile(file)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.selectedThreadForPreview != null) {
                // Thread Preview Top Bar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.selectedThreadForPreview!!.displayName,
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${uiState.previewMessages.size} messages",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeThreadPreview() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { runWithOdinLock { viewModel.setCopilotOpen(true) } }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "O.D.I.N. Copilot",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            viewModel.loadStatistics()
                            viewModel.adManager.showRewardedAd(
                                activity = activity,
                                isAdsRemoved = isAdsRemoved,
                                onUserEarnedReward = {
                                    viewModel.setShowStatsDialog(true)
                                }
                            )
                        }) {
                            Icon(
                                imageVector = Icons.Default.Insights,
                                contentDescription = "Thread Analytics",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.setThreadSearchActive(!uiState.isThreadSearchActive) }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search thread",
                                tint = if (uiState.isThreadSearchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { isMessageSelectionMode = !isMessageSelectionMode }) {
                            Icon(
                                imageVector = Icons.Default.Checklist,
                                contentDescription = "Filter Messages",
                                tint = if (isMessageSelectionMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else if (uiState.isSelectionMode) {
                // Multi-select Action Bar
                TopAppBar(
                    title = { Text(text = "${uiState.selectedThreadIds.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAllThreads() }) {
                            Icon(imageVector = Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            } else {
                // Standard Top Bar
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "Message Backup")
                            if (uiState.isMockMode) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = "MOCK DATA",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { runWithOdinLock { viewModel.setCopilotOpen(true) } }) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = "O.D.I.N. Copilot",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { viewModel.setSearchActive(!uiState.isSearchActive) }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search")
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(imageVector = Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            ThreadSortOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName) },
                                    trailingIcon = {
                                        if (uiState.sortOption == option) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected")
                                        }
                                    },
                                    onClick = {
                                        viewModel.setSortOption(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.loadThreads() }) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        IconButton(onClick = { showMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("O.D.I.N. Message Copilot")
                                        Text(
                                            "Omniscient Document & Insights Navigator",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    runWithOdinLock { viewModel.setCopilotOpen(true) }
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Conversation Insights") },
                                leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.loadStatistics()
                                    viewModel.adManager.showRewardedAd(
                                        activity = activity,
                                        isAdsRemoved = isAdsRemoved,
                                        onUserEarnedReward = {
                                            viewModel.setShowStatsDialog(true)
                                        }
                                    )
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Reward Arcade (Free Credits)") },
                                leadingIcon = { Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    showRewardArcade = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setSettingsOpen(true)
                                }
                            )

                            if (com.messagebackup.app.BuildConfig.DEBUG || uiState.isMockMode) {
                                DropdownMenuItem(
                                    text = { Text(if (uiState.isMockMode) "Switch to Real Device SMS" else "Switch to Mock Data (Testing)") },
                                    leadingIcon = { Icon(Icons.Default.BugReport, contentDescription = null) },
                                    onClick = {
                                        viewModel.toggleMockMode()
                                        showMenu = false
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            AdBannerView(isAdsRemoved = isAdsRemoved)
        },
        floatingActionButton = {
            if (uiState.selectedThreadForPreview != null) {
                val includedCount = (uiState.previewMessages.size - uiState.excludedMessageIds.size).coerceAtLeast(0)
                FloatingActionButton(
                    onClick = { viewModel.setShowExportDialog(true) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Export")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (uiState.excludedMessageIds.isNotEmpty())
                                "Export ($includedCount msgs)"
                            else
                                "Export Thread"
                        )
                    }
                }
            } else if (uiState.isSelectionMode && uiState.selectedThreadIds.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.setShowExportDialog(true) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = "Export")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = "Export (${uiState.selectedThreadIds.size})")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // Thread Preview Mode
                uiState.selectedThreadForPreview != null -> {
                    val messages = uiState.previewMessages
                    val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
                        .withZone(ZoneId.systemDefault())

                    val firstMsgDate = if (messages.isNotEmpty()) dateFormatter.format(Instant.ofEpochMilli(messages.first().timestamp)) else "N/A"
                    val lastMsgDate = if (messages.isNotEmpty()) dateFormatter.format(Instant.ofEpochMilli(messages.last().timestamp)) else "N/A"
                    val totalMediaCount = messages.sumOf { it.attachments.size }

                    Column(modifier = Modifier.fillMaxSize()) {
                        // Conversation Metadata Stats Banner
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📅 $firstMsgDate - $lastMsgDate",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(
                                    text = "💬 ${messages.size} msgs • 📷 $totalMediaCount media",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }

                        // Collapsible In-Thread Search Bar
                        AnimatedVisibility(visible = uiState.isThreadSearchActive) {
                            OutlinedTextField(
                                value = uiState.threadSearchQuery,
                                onValueChange = { viewModel.setThreadSearchQuery(it) },
                                placeholder = { Text("Search this conversation...") },
                                singleLine = true,
                                trailingIcon = {
                                    if (uiState.threadSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.setThreadSearchQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        if (uiState.isPreviewLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else if (uiState.filteredPreviewMessages.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (uiState.threadSearchQuery.isNotBlank()) "No messages match \"${uiState.threadSearchQuery}\"" else "No messages found.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(uiState.filteredPreviewMessages, key = { it.messageId }) { message ->
                                    val isExcluded = uiState.excludedMessageIds.contains(message.messageId)
                                    MessageBubble(
                                        message = message,
                                        isExcluded = isExcluded,
                                        showExclusionToggle = isMessageSelectionMode,
                                        onToggleExclude = {
                                            viewModel.toggleMessageExclusion(message.messageId)
                                            if (!isAdsRemoved) {
                                                viewModel.adManager.loadRewardedAd(activity)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Permission Required State
                !uiState.hasSmsPermission && !uiState.isMockMode -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(text = "Permission Required", style = MaterialTheme.typography.titleLarge)
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "To browse and export your message history, the app requires read access to your device SMS/MMS storage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = { viewModel.setShowPermissionDisclosure(true) }) {
                            Text(text = "Grant SMS Access")
                        }
                        if (com.messagebackup.app.BuildConfig.DEBUG) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { viewModel.toggleMockMode() }) {
                                Text(text = "Try With Mock Data (Instant Test)")
                            }
                        }
                    }
                }

                // Loading State
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                // Empty State
                uiState.filteredThreads.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No Matching Conversations" else "No Messages Found",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "Try searching for a different contact name or number." else "No SMS/MMS messages found.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Main Conversation List
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Collapsible Search Bar
                        AnimatedVisibility(visible = uiState.isSearchActive) {
                            OutlinedTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                placeholder = { Text("Search name, number, or message text...") },
                                singleLine = true,
                                trailingIcon = {
                                    if (uiState.searchQuery.isNotBlank()) {
                                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                            Icon(Icons.Default.Close, contentDescription = "Clear")
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }

                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(uiState.filteredThreads, key = { it.threadId }) { thread ->
                                val isSelected = uiState.selectedThreadIds.contains(thread.threadId)
                                ConversationItem(
                                    thread = thread,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    onToggleSelect = { viewModel.toggleThreadSelection(thread.threadId) },
                                    onClick = { viewModel.openThreadPreview(thread) }
                                )
                            }

                            item {
                                Spacer(modifier = Modifier.height(96.dp))
                            }
                        }
                    }
                }
            }

            // Export Progress Overlay
            if (uiState.isExporting) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = uiState.exportProgressMessage ?: "Exporting conversations...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }

    // Permission Disclosure Modal
    if (uiState.showPermissionDisclosure) {
        SmsPermissionDisclosureDialog(
            onAccept = {
                viewModel.setShowPermissionDisclosure(false)
                permissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.READ_SMS,
                        android.Manifest.permission.READ_CONTACTS
                    )
                )
            },
            onDismiss = { viewModel.setShowPermissionDisclosure(false) }
        )
    }

    // Export Options Modal
    if (uiState.showExportDialog) {
        val exportThreadCount = if (uiState.selectedThreadForPreview != null) 1 else uiState.selectedThreadIds.size
        ExportConfigDialog(
            selectedCount = exportThreadCount,
            isAdsRemoved = isAdsRemoved,
            initialFormat = defaultFormat,
            initialMaxPerFile = maxMessagesPerFile,
            initialTheme = exportTheme,
            initialDestination = outputDestination,
            onConfirmExport = { options ->
                viewModel.setShowExportDialog(false)
                viewModel.adManager.showRewardedAd(
                    activity = activity,
                    isAdsRemoved = isAdsRemoved,
                    onUserEarnedReward = {
                        viewModel.startExport(options) { result ->
                            if (result.success && result.outputFile != null) {
                                if (options.outputType == OutputDestination.GOOGLE_DRIVE) {
                                    viewModel.app.googleDriveSyncManager.uploadFileToDrive(result.outputFile)
                                    scope.launch {
                                        val snackResult = snackbarHostState.showSnackbar(
                                            message = "Syncing to Google Drive: ${result.outputFile.name}",
                                            actionLabel = "View File",
                                            duration = SnackbarDuration.Long
                                        )
                                        if (snackResult == SnackbarResult.ActionPerformed) {
                                            triggerOpenFile(result.outputFile)
                                        }
                                    }
                                } else if (options.outputType == OutputDestination.PERSISTENT_DIRECTORY) {
                                    scope.launch {
                                        val snackResult = snackbarHostState.showSnackbar(
                                            message = "Saved to $safFolderDisplay",
                                            actionLabel = "Open",
                                            duration = SnackbarDuration.Long
                                        )
                                        if (snackResult == SnackbarResult.ActionPerformed) {
                                            triggerOpenFile(result.outputFile)
                                        }
                                    }
                                } else {
                                    triggerShareSheet(result.outputFile)
                                    scope.launch {
                                        val snackResult = snackbarHostState.showSnackbar(
                                            message = "Export ready: ${result.outputFile.name}",
                                            actionLabel = "Open File",
                                            duration = SnackbarDuration.Long
                                        )
                                        if (snackResult == SnackbarResult.ActionPerformed) {
                                            triggerOpenFile(result.outputFile)
                                        }
                                    }
                                }
                            } else {
                                Toast.makeText(context, result.errorMessage ?: "Export error occurred", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            },
            onDismiss = { viewModel.setShowExportDialog(false) }
        )
    }

    // Conversation Insights / Statistics Modal
    if (uiState.showStatsDialog) {
        val statsFormatter = remember { com.messagebackup.app.domain.export.StatsReportFormatter(context) }
        com.messagebackup.app.ui.components.ConversationStatsDialog(
            stats = uiState.stats,
            isLoading = uiState.isStatsLoading,
            onRecalculate = { viewModel.loadStatistics(forceRefresh = true) },
            onExportReport = {
                uiState.stats?.let { statsData ->
                    viewModel.adManager.showRewardedAd(
                        activity = activity,
                        isAdsRemoved = isAdsRemoved,
                        onUserEarnedReward = {
                            val targetTitle = uiState.selectedThreadForPreview?.displayName ?: "All Conversations"
                            val reportFile = statsFormatter.generateHtmlReport(statsData, targetTitle)
                            val uri: Uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                reportFile
                            )
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/html"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "$targetTitle - Conversation Insights Report")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share Insights Report"))
                        }
                    )
                } ?: run {
                    Toast.makeText(context, "Insights still loading, please wait...", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                viewModel.setShowStatsDialog(false)
                viewModel.adManager.showInterstitialAd(
                    activity = activity,
                    isAdsRemoved = isAdsRemoved,
                    onComplete = {}
                )
            }
        )
    }

    if (showRewardArcade) {
        com.messagebackup.app.ui.dialogs.CreditArcadeDialog(
            prefs = prefs,
            adManager = viewModel.adManager,
            onDismiss = { showRewardArcade = false }
        )
    }
}
