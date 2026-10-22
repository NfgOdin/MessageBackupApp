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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.clickable
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ButtonDefaults
import com.messagebackup.app.model.ExportFormat
import androidx.compose.material.icons.filled.PermMedia
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.res.painterResource
import com.messagebackup.app.R
import com.messagebackup.app.data.local.LocalMessageDatabase
import com.messagebackup.app.data.local.RavenDatabaseHelper
import com.messagebackup.app.data.local.StarredMemoryItem
import com.messagebackup.app.domain.mesh.RavenPeerStatus
import com.messagebackup.app.domain.mesh.RavenSendResult
import com.messagebackup.app.domain.mesh.RavenSocketTransport
import com.messagebackup.app.model.DateRangeFilter
import com.messagebackup.app.model.ExportOptions
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
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
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
    val aiCredits by prefs.aiCredits.collectAsState()
    val ansuzCredits by prefs.ansuzCredits.collectAsState()
    val autoBackupCredits by prefs.autoBackupCredits.collectAsState()
    val alwaysPromptExportOptions by prefs.alwaysPromptExportOptions.collectAsState()
    val isOnboardingCompleted by prefs.isOnboardingCompleted.collectAsState()
    val huginnComposerEnabled by prefs.huginnComposerEnabled.collectAsState()
    val ravenMeshEnabled by prefs.ravenMeshEnabled.collectAsState()
    val memoryCapsuleEnabled by prefs.memoryCapsuleEnabled.collectAsState()
    val aiEngine = remember { com.messagebackup.app.domain.ai.AiClientEngine() }
    val ravenDb = remember { RavenDatabaseHelper(context) }
    val localCacheDb = remember { LocalMessageDatabase.getInstance(context) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val socketTransport = remember { RavenSocketTransport.getInstance(context, scope) }
    var ravenUnreadCount by remember { mutableStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isMessageSelectionMode by rememberSaveable { mutableStateOf(false) }
    var showRewardArcade by rememberSaveable { mutableStateOf(false) }
    var showStarredMemoriesDialog by rememberSaveable { mutableStateOf(false) }
    var showSendExcerptToRavenDialog by rememberSaveable { mutableStateOf(false) }
    var memoryCapsuleDismissed by rememberSaveable { mutableStateOf(false) }
    val viewModelShowRewardArcade = uiState.showRewardArcade
    LaunchedEffect(viewModelShowRewardArcade) {
        if (viewModelShowRewardArcade) {
            showRewardArcade = true
            viewModel.setShowRewardArcade(false)
        }
    }
    var showRavenMeshDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(ravenMeshEnabled, showRavenMeshDialog) {
        socketTransport.setMeshEnabled(ravenMeshEnabled)
        if (ravenMeshEnabled) {
            socketTransport.startServer()
            ravenUnreadCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                ravenDb.getTotalUnreadCount()
            }
        } else {
            ravenUnreadCount = 0
        }
    }
    LaunchedEffect(ravenMeshEnabled) {
        if (ravenMeshEnabled) {
            socketTransport.incomingMessages.collect {
                ravenUnreadCount = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    ravenDb.getTotalUnreadCount()
                }
            }
        }
    }
    var showOnboarding by rememberSaveable(isOnboardingCompleted) { mutableStateOf(!isOnboardingCompleted) }
    var isAppSessionUnlocked by rememberSaveable { mutableStateOf(false) }
    var pendingProtectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val previewLazyListState = rememberLazyListState()

    var lastScrolledThreadId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(uiState.selectedThreadForPreview?.threadId) {
        if (uiState.selectedThreadForPreview == null) {
            lastScrolledThreadId = null
        }
    }
    LaunchedEffect(uiState.selectedThreadForPreview?.threadId, uiState.previewMessages.size) {
        val currentThreadId = uiState.selectedThreadForPreview?.threadId
        if (currentThreadId != null && currentThreadId != lastScrolledThreadId && uiState.previewMessages.isNotEmpty()) {
            lastScrolledThreadId = currentThreadId
            previewLazyListState.scrollToItem(uiState.previewMessages.size - 1)
        }
    }

    LaunchedEffect(previewLazyListState, uiState.hasMoreOlderMessages, uiState.isLoadingOlderMessages) {
        androidx.compose.runtime.snapshotFlow { previewLazyListState.firstVisibleItemIndex }
            .collect { firstIndex ->
                if (firstIndex == 0 && uiState.hasMoreOlderMessages && !uiState.isLoadingOlderMessages && !uiState.isPreviewLoading && uiState.threadSearchQuery.isBlank()) {
                    viewModel.loadOlderPreviewMessages()
                }
            }
    }

    // Scroll to latest message when keyboard pops up
    val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
    LaunchedEffect(imeBottom) {
        if (imeBottom > 0 && uiState.selectedThreadForPreview != null && uiState.previewMessages.isNotEmpty()) {
            previewLazyListState.animateScrollToItem(uiState.previewMessages.size - 1)
        }
    }

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
                title = "Unlock Muninn",
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
            onOpenRestoreDialog = {
                viewModel.setSettingsOpen(false)
                viewModel.setShowRestoreDialog(true)
            },
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
        val isMessagesLoading = if (uiState.selectedThreadForPreview != null) {
            uiState.isPreviewLoading
        } else {
            uiState.isCopilotLoadingMessages
        }
        com.messagebackup.app.ui.screens.MessageCopilotScreen(
            prefs = prefs,
            adManager = viewModel.adManager,
            isAdsRemoved = isAdsRemoved,
            threads = uiState.threads,
            currentMessages = targetMessages,
            targetContactName = targetName,
            isLoadingMessages = isMessagesLoading,
            onNavigateBack = {
                viewModel.adManager.showInterstitialAd(activity, isAdsRemoved) {
                    viewModel.setCopilotOpen(false)
                }
            },
            onOpenSettings = {
                viewModel.setCopilotOpen(false)
                viewModel.setSettingsOpen(true)
            },
            initialPrompt = uiState.copilotInitialPrompt,
            modifier = modifier
        )
        return
    }

    if (uiState.isVaultOpen) {
        com.messagebackup.app.ui.screens.MediaVaultScreen(
            mediaItems = uiState.vaultMediaItems,
            isLoading = uiState.isVaultLoading,
            progressPct = uiState.vaultProgressPct,
            progressMessage = uiState.vaultProgressMessage,
            adManager = viewModel.adManager,
            isAdsRemoved = isAdsRemoved,
            onRefresh = { viewModel.loadMediaVault(forceRefresh = true) },
            onJumpToThread = { threadId ->
                val targetThread = uiState.threads.find { it.threadId == threadId }
                if (targetThread != null) {
                    viewModel.setVaultOpen(false)
                    viewModel.openThreadPreview(targetThread)
                }
            },
            onNavigateBack = {
                viewModel.adManager.showInterstitialAd(activity, isAdsRemoved) {
                    viewModel.setVaultOpen(false)
                }
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

    // File picker launcher for JSON and XML message backups
    val restorePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.startRestoreFromUri(uri) { success, count, message ->
                scope.launch {
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = SnackbarDuration.Long
                    )
                }
            }
        }
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

    val executeExportWithOptions: (ExportOptions) -> Unit = { options ->
        val onExportAction = {
            viewModel.startExport(options) { result ->
                if (result.success && result.outputFile != null) {
                    if (options.outputType == OutputDestination.GOOGLE_DRIVE ||
                        options.outputType == OutputDestination.ONEDRIVE ||
                        options.outputType == OutputDestination.DROPBOX ||
                        options.outputType == OutputDestination.WEBDAV_NAS) {
                        viewModel.app.cloudStorageDispatcher.dispatchBackupToCloud(result.outputFile, options.outputType)
                        val destName = when (options.outputType) {
                            OutputDestination.GOOGLE_DRIVE -> "Google Drive"
                            OutputDestination.ONEDRIVE -> "Microsoft OneDrive"
                            OutputDestination.DROPBOX -> "Dropbox"
                            else -> "WebDAV / Local NAS"
                        }
                        scope.launch {
                            val snackResult = snackbarHostState.showSnackbar(
                                message = "Syncing to $destName: ${result.outputFile.name}",
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
                    com.messagebackup.app.ui.util.HapticHelper.performSuccess(context)
                    prefs.incrementExportSuccessCount()
                    if (prefs.getExportSuccessCount() == 2 && !prefs.hasReviewedApp.value) {
                        showRewardArcade = true
                    }
                } else {
                    com.messagebackup.app.ui.util.HapticHelper.performHeavy(context)
                    Toast.makeText(context, result.errorMessage ?: "Export error occurred", Toast.LENGTH_LONG).show()
                }
            }
        }

        // High-eCPM Rewarded Interstitial for premium Legal Forensic Mode & Desktop HTML Viewer
        if (options.legalEvidenceMode || options.format == ExportFormat.DESKTOP_VIEWER) {
            viewModel.adManager.showRewardedInterstitialOrFallback(
                activity = activity,
                isAdsRemoved = isAdsRemoved,
                onUserEarnedReward = onExportAction
            )
        } else {
            viewModel.adManager.showRewardedAd(
                activity = activity,
                isAdsRemoved = isAdsRemoved,
                onUserEarnedReward = onExportAction
            )
        }
    }

    val initiateExport: () -> Unit = {
        if (alwaysPromptExportOptions) {
            viewModel.setShowExportDialog(true)
        } else {
            // Directly export using the defaults configured in Settings
            val defaultOptions = ExportOptions(
                format = defaultFormat,
                includeMedia = true,
                maxMessagesPerFile = maxMessagesPerFile,
                theme = exportTheme,
                dateRange = DateRangeFilter.ALL_TIME,
                outputType = outputDestination
            )
            executeExportWithOptions(defaultOptions)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.selectedThreadForPreview != null) {
                // Thread Preview Top Bar - Buttons & Actions on top row
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.closeThreadPreview() }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.refreshCurrentThreadPreview() },
                            enabled = !uiState.isPreviewLoading
                        ) {
                            if (uiState.isPreviewLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Messages",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        if (uiState.selectedThreadForPreview?.hasAttachment == true) {
                            IconButton(onClick = { viewModel.setVaultOpen(true) }) {
                                Icon(
                                    imageVector = Icons.Default.PermMedia,
                                    contentDescription = "Media Gallery",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        IconButton(onClick = { runWithOdinLock { viewModel.setCopilotOpen(true) } }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_odin_allfather),
                                contentDescription = "O.D.I.N. Copilot",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
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
                        Column(
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Muninn",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            // At-a-glance Dual Credits Badge (or PRO indicator)
                            Surface(
                                color = if (isAdsRemoved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .clickable { showRewardArcade = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    if (isAdsRemoved) {
                                        Icon(
                                            imageVector = Icons.Default.Stars,
                                            contentDescription = "Pro",
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "PRO LIFETIME",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    } else {
                                        // ᛟ Othala Runes (ODIN AI)
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.messagebackup.app.R.drawable.ic_rune_othala),
                                            contentDescription = "Othala Runes",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "$aiCredits",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )

                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))

                                        // ᚫ Ansuz Runes (Huginn Thought & Dispatch)
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.messagebackup.app.R.drawable.ic_rune_ansuz),
                                            contentDescription = "Ansuz Runes",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "$ansuzCredits",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )

                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))

                                        // ᛃ Jera Runes (Nightly Auto-Backups) - ONLY WHOLE CREDITS!
                                        Icon(
                                            painter = androidx.compose.ui.res.painterResource(id = com.messagebackup.app.R.drawable.ic_rune_jera),
                                            contentDescription = "Jera Runes",
                                            modifier = Modifier.size(12.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "$autoBackupCredits",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { runWithOdinLock { viewModel.setCopilotOpen(true) } }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_odin_allfather),
                                contentDescription = "O.D.I.N. Copilot",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        if (ravenMeshEnabled) {
                            IconButton(onClick = { showRavenMeshDialog = true }) {
                                BadgedBox(
                                    badge = {
                                        if (ravenUnreadCount > 0) {
                                            Badge {
                                                Text(
                                                    text = if (ravenUnreadCount > 99) "99+" else "$ravenUnreadCount",
                                                    style = MaterialTheme.typography.labelSmall
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_flying_raven),
                                        contentDescription = "Raven Mesh Network",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
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
                        IconButton(onClick = { showMenu = true }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {

                            if (ravenMeshEnabled) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = "Raven Mesh Identity",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Decentralized E2EE Peer Network & Sigil",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Default.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showMenu = false
                                        showRavenMeshDialog = true
                                    }
                                )
                            }

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Pinned Vault Memories",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Starred messages, codes & O.D.I.N. answers",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    showStarredMemoriesDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Media Vault & Gallery",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Browse photos, videos & audio attachments",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.PermMedia, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setVaultOpen(true)
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Conversation Insights",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Analytics, top contacts & activity trends",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Insights, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
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
                                text = {
                                    Column {
                                        Text(
                                            text = "Arcade & Bazaar",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (isAdsRemoved) "PRO License Active" else "ᛟ $aiCredits • ᚫ $ansuzCredits • ᛃ $autoBackupCredits Runes",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    showRewardArcade = true
                                }
                            )

                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(
                                            text = "Settings",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "Backup, restore, O.D.I.N. AI & vault security",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setSettingsOpen(true)
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            ) {
                if (uiState.selectedThreadForPreview != null && huginnComposerEnabled) {
                    com.messagebackup.app.ui.components.HuginnMessageComposerBar(
                        thread = uiState.selectedThreadForPreview!!,
                        recentMessages = uiState.previewMessages,
                        prefs = prefs,
                        aiEngine = aiEngine,
                        isAdsRemoved = isAdsRemoved,
                        onRequireCredits = { showRewardArcade = true }
                    )
                }
                AdBannerView(isAdsRemoved = isAdsRemoved)
            }
        },
        floatingActionButton = {
            if (uiState.selectedThreadForPreview != null && imeBottom == 0) {
                val includedCount = (uiState.previewMessages.size - uiState.excludedMessageIds.size).coerceAtLeast(0)
                val totalMessages = uiState.filteredPreviewMessages.size
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (totalMessages > 8) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        previewLazyListState.animateScrollToItem(0)
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowUpward,
                                    contentDescription = "Scroll to Oldest (Top)"
                                )
                            }

                            SmallFloatingActionButton(
                                onClick = {
                                    scope.launch {
                                        if (totalMessages > 0) {
                                            previewLazyListState.animateScrollToItem(totalMessages - 1)
                                        }
                                    }
                                },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowDownward,
                                    contentDescription = "Scroll to Newest (Bottom)"
                                )
                            }
                        }
                    }

                    FloatingActionButton(
                        onClick = { initiateExport() },
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
                }
            } else if (uiState.isSelectionMode && uiState.selectedThreadIds.isNotEmpty()) {
                // Floating Batch Actions Pill
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 6.dp,
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${uiState.selectedThreadIds.size} Selected",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )

                        Button(
                            onClick = { initiateExport() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Export", style = MaterialTheme.typography.labelMedium)
                        }

                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
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
                        val thread = uiState.selectedThreadForPreview!!
                        val hasRealContactName = !thread.contactName.isNullOrBlank() && thread.contactName != thread.address
                        var showNumberDetails by remember(thread.threadId) { mutableStateOf(false) }
                        var isStatsExpanded by remember(thread.threadId) { mutableStateOf(false) }

                        // Compact Sleek Conversation Header with Interactive Stats Dropdown
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Contact Name / Number Area (Clickable to reveal number if name exists)
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                            .then(
                                                if (hasRealContactName) {
                                                    Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .clickable { showNumberDetails = !showNumberDetails }
                                                } else Modifier
                                            )
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = if (hasRealContactName) thread.contactName!! else thread.displayName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (hasRealContactName) {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = if (showNumberDetails) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                    contentDescription = "Toggle phone number",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }

                                        // Click-to-reveal phone number sub-line
                                        AnimatedVisibility(visible = showNumberDetails && hasRealContactName) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier
                                                    .padding(top = 2.dp)
                                                    .clickable {
                                                        clipboardManager.setText(AnnotatedString(thread.address))
                                                        Toast.makeText(context, "Copied ${thread.address}", Toast.LENGTH_SHORT).show()
                                                    }
                                            ) {
                                                Text(
                                                    text = thread.address,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ContentCopy,
                                                    contentDescription = "Copy number",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Right: Stats Dropdown Toggle Button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isStatsExpanded) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isStatsExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { isStatsExpanded = !isStatsExpanded }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "📊 Stats",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isStatsExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Icon(
                                                imageVector = if (isStatsExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                                contentDescription = null,
                                                tint = if (isStatsExpanded) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                // Expanded Stats Dropdown Content
                                AnimatedVisibility(visible = isStatsExpanded) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp)
                                    ) {
                                        if (messages.isNotEmpty()) {
                                            Text(
                                                text = "📅 $firstMsgDate – $lastMsgDate",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                        }

                                        val mediaCount = messages.sumOf { it.attachments.size }
                                        val statsLabel = if (mediaCount > 0) {
                                            "💬 ${messages.size} MSGS • $mediaCount MMS"
                                        } else {
                                            "💬 ${messages.size} MSGS"
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = statsLabel,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (uiState.isPreviewLoading) {
                                                Text(
                                                    text = "• Refreshing...",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // O.D.I.N. Quick Action & Summary Chips Bar
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            runWithOdinLock {
                                                viewModel.setCopilotOpen(
                                                    open = true,
                                                    initialPrompt = "Please give me a concise 3-bullet summary of this conversation with ${uiState.selectedThreadForPreview?.displayName ?: "this contact"}."
                                                )
                                            }
                                        },
                                        label = { Text("⚡ Summarize Thread", style = MaterialTheme.typography.labelSmall) },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_odin_allfather),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            runWithOdinLock {
                                                viewModel.setCopilotOpen(
                                                    open = true,
                                                    initialPrompt = "Extract any dates, schedules, deadlines, or upcoming plans mentioned in this conversation."
                                                )
                                            }
                                        },
                                        label = { Text("📅 Dates & Deadlines", style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            runWithOdinLock {
                                                viewModel.setCopilotOpen(
                                                    open = true,
                                                    initialPrompt = "Find any addresses, locations, or directions mentioned in this thread."
                                                )
                                            }
                                        },
                                        label = { Text("📍 Locations", style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            runWithOdinLock {
                                                viewModel.setCopilotOpen(
                                                    open = true,
                                                    initialPrompt = "Find any passcodes, door codes, lockbox combinations, or Wi-Fi passwords in this thread."
                                                )
                                            }
                                        },
                                        label = { Text("🔑 Codes & Passwords", style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
                                item {
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            runWithOdinLock {
                                                viewModel.setCopilotOpen(
                                                    open = true,
                                                    initialPrompt = "Draft a friendly, polite follow-up message responding to the latest context in this conversation."
                                                )
                                            }
                                        },
                                        label = { Text("✍️ Draft Follow-up", style = MaterialTheme.typography.labelSmall) },
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                }
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

                        // Message Selection & Vault Actions Bar
                        AnimatedVisibility(visible = isMessageSelectionMode) {
                            val includedMessages = uiState.filteredPreviewMessages.filter {
                                !uiState.excludedMessageIds.contains(it.messageId)
                            }
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${includedMessages.size} of ${uiState.filteredPreviewMessages.size} included",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = {
                                                val toStar = includedMessages.takeLast(15)
                                                if (toStar.isEmpty()) {
                                                    Toast.makeText(context, "Include at least 1 message to pin", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    scope.launch {
                                                        toStar.forEach { m ->
                                                            localCacheDb.addStarredMemory(
                                                                messageId = m.messageId,
                                                                threadId = m.threadId,
                                                                contactName = thread.displayName,
                                                                sender = if (m.direction == com.messagebackup.app.model.MessageDirection.OUTGOING) "Me" else thread.displayName,
                                                                body = m.body,
                                                                timestamp = m.timestamp,
                                                                sourceType = "SMS"
                                                            )
                                                        }
                                                        Toast.makeText(context, "⭐ Pinned ${toStar.size} message(s) to Vault Memories", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pin to Vault", style = MaterialTheme.typography.labelSmall)
                                        }
                                        if (ravenMeshEnabled) {
                                            Button(
                                                onClick = { showSendExcerptToRavenDialog = true },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                shape = RoundedCornerShape(16.dp)
                                            ) {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.ic_flying_raven),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Send via Raven", style = MaterialTheme.typography.labelSmall)
                                            }
                                        }
                                    }
                                }
                            }
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
                            LazyColumn(
                                state = previewLazyListState,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (uiState.hasMoreOlderMessages && uiState.threadSearchQuery.isBlank()) {
                                    item(key = "load_older_messages_header") {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (uiState.isLoadingOlderMessages) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                    Text(
                                                        text = "Loading older messages...",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            } else {
                                                val totalCount = uiState.selectedThreadForPreview?.messageCount ?: 0
                                                val currentCount = uiState.previewMessages.size
                                                val remaining = (totalCount - currentCount).coerceAtLeast(0)
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.loadOlderPreviewMessages() },
                                                        shape = RoundedCornerShape(20.dp),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowUpward,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Load older ($currentCount of $totalCount)",
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }

                                                    if (remaining > 0) {
                                                        TextButton(
                                                            onClick = { viewModel.loadAllPreviewMessages() },
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                                        ) {
                                                            Text(
                                                                text = "Load all ($remaining)",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
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
                                item {
                                    Spacer(modifier = Modifier.height(88.dp))
                                }
                            }
                        }
                    }
                }

                // Permission Required State
                !uiState.hasSmsPermission -> {
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
                    }
                }

                // Main Conversation List & Search
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Collapsible Search Bar (always visible while search is active, even if 0 results match!)
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

                        when {
                            uiState.isLoading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
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
                                        text = if (uiState.searchQuery.isNotBlank()) "No messages found matching \"${uiState.searchQuery}\"" else "No SMS/MMS messages found.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> {
                                val memoryCapsuleThread = remember(uiState.threads, memoryCapsuleEnabled, memoryCapsuleDismissed) {
                                    if (!memoryCapsuleEnabled || memoryCapsuleDismissed || uiState.threads.isEmpty()) {
                                        null
                                    } else {
                                        val nowZone = java.time.LocalDate.now()
                                        val exactDayMatch = uiState.threads.find { t ->
                                            val dt = Instant.ofEpochMilli(t.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
                                            dt.monthValue == nowZone.monthValue && dt.dayOfMonth == nowZone.dayOfMonth && dt.year < nowZone.year
                                        }
                                        exactDayMatch ?: uiState.threads
                                            .filter { it.snippet.isNotBlank() && it.messageCount >= 5 }
                                            .minByOrNull { it.timestamp }
                                    }
                                }

                                PullToRefreshBox(
                                    isRefreshing = uiState.isLoading,
                                    onRefresh = { viewModel.loadThreads(forceRefresh = true) },
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        if (memoryCapsuleThread != null && uiState.searchQuery.isBlank()) {
                                            item(key = "memory_capsule_card") {
                                                val capsuleDate = remember(memoryCapsuleThread.timestamp) {
                                                    DateTimeFormatter.ofPattern("MMM d, yyyy")
                                                        .withZone(ZoneId.systemDefault())
                                                        .format(Instant.ofEpochMilli(memoryCapsuleThread.timestamp))
                                                }
                                                Surface(
                                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                                    shape = RoundedCornerShape(14.dp),
                                                    border = androidx.compose.foundation.BorderStroke(
                                                        1.dp,
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                                    ),
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                        .clickable { viewModel.openThreadPreview(memoryCapsuleThread) }
                                                ) {
                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                Icon(
                                                                    painter = painterResource(id = R.drawable.ic_flying_raven),
                                                                    contentDescription = null,
                                                                    tint = MaterialTheme.colorScheme.primary,
                                                                    modifier = Modifier.size(16.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = "Muninn Memory Capsule • $capsuleDate",
                                                                    style = MaterialTheme.typography.labelMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.primary
                                                                )
                                                            }
                                                            IconButton(
                                                                onClick = { memoryCapsuleDismissed = true },
                                                                modifier = Modifier.size(22.dp)
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Close,
                                                                    contentDescription = "Dismiss Memory Capsule",
                                                                    modifier = Modifier.size(14.dp),
                                                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                                                )
                                                            }
                                                        }
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = memoryCapsuleThread.displayName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                                        )
                                                        Text(
                                                            text = "\"${memoryCapsuleThread.snippet}\"",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            maxLines = 2,
                                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        itemsIndexed(uiState.filteredThreads, key = { _, it -> it.threadId }) { index, thread ->
                                            val isSelected = uiState.selectedThreadIds.contains(thread.threadId)
                                            ConversationItem(
                                                thread = thread,
                                                isSelected = isSelected,
                                                isSelectionMode = uiState.isSelectionMode,
                                                onToggleSelect = { viewModel.toggleThreadSelection(thread.threadId) },
                                                onClick = { viewModel.openThreadPreview(thread) }
                                            )

                                            // Native In-Feed Video Ad inserted after conversation #3 for non-pro users
                                            if (!isAdsRemoved && (index == 3 || (index > 3 && (index - 3) % 12 == 0))) {
                                                com.messagebackup.app.ui.components.NativeFeedAdCard(
                                                    adManager = viewModel.adManager
                                                )
                                            }
                                        }

                                        item {
                                            Spacer(modifier = Modifier.height(96.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Export Progress Overlay
            if (uiState.isExporting) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(
                                progress = { uiState.exportProgressPct.toFloat() / 100f },
                                modifier = Modifier.size(76.dp),
                                strokeWidth = 6.dp,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Text(
                                text = "${uiState.exportProgressPct}%",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = uiState.exportProgressMessage ?: "Packaging conversations...",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LinearProgressIndicator(
                            progress = { uiState.exportProgressPct.toFloat() / 100f },
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
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
                executeExportWithOptions(options)
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
            progressPct = uiState.statsProgressPct,
            progressMessage = uiState.statsProgressMessage,
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
            onSearchPhrase = { query, onResult ->
                viewModel.searchPhraseFrequency(query, onResult)
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

    if (uiState.showRestoreDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isRestoring) {
                    viewModel.setShowRestoreDialog(false)
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "SMS Restore & Migration",
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Restoration requires a .json or .xml backup created by Muninn or standard SMS Backup & Restore.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    if (uiState.isRestoring) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = { uiState.restoreProgressPct.toFloat() / 100f },
                                    modifier = Modifier.size(64.dp),
                                    strokeWidth = 5.dp,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                                Text(
                                    text = "${uiState.restoreProgressPct}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = uiState.restoreProgressMessage ?: "Restoring messages...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            LinearProgressIndicator(
                                progress = { uiState.restoreProgressPct.toFloat() / 100f },
                                modifier = Modifier
                                    .fillMaxWidth(0.85f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }
                    } else {
                        Text(
                            text = "Select a backup file from your device, Google Drive, or local storage to restore your messages back into your phone's SMS database.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                if (!uiState.isRestoring) {
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.setShowRestoreDialog(false)
                                viewModel.setShowMigrationDialog(true)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("QR Direct Phone-to-Phone")
                        }

                        Button(
                            onClick = {
                                restorePickerLauncher.launch(
                                    arrayOf(
                                        "application/json",
                                        "text/xml",
                                        "application/xml",
                                        "text/plain",
                                        "*/*"
                                    )
                                )
                            }
                        ) {
                            Text("Select Backup File (.json / .xml)")
                        }
                    }
                }
            },
            dismissButton = {
                if (!uiState.isRestoring) {
                    TextButton(onClick = { viewModel.setShowRestoreDialog(false) }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    if (uiState.showMigrationDialog) {
        com.messagebackup.app.ui.dialogs.PhoneMigrationDialog(
            latestExportFile = uiState.lastExportResult?.outputFile,
            onPrepareExport = { onReady ->
                viewModel.prepareMigrationArchive(onReady)
            },
            onBackupReceivedForRestore = { receivedFile ->
                viewModel.setShowMigrationDialog(false)
                viewModel.startRestoreFromFile(
                    file = receivedFile,
                    onResult = { success: Boolean, count: Int, message: String ->
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                )
            },
            onDismiss = { viewModel.setShowMigrationDialog(false) }
        )
    }

    if (showRavenMeshDialog) {
        com.messagebackup.app.ui.dialogs.RavenIdentityDialog(
            prefs = prefs,
            adManager = viewModel.adManager,
            isAdsRemoved = isAdsRemoved,
            onDismiss = { showRavenMeshDialog = false }
        )
    }

    if (showStarredMemoriesDialog) {
        StarredMemoriesDialog(
            localCacheDb = localCacheDb,
            onJumpToThread = { threadId ->
                val targetThread = uiState.threads.find { it.threadId == threadId }
                if (targetThread != null) {
                    showStarredMemoriesDialog = false
                    viewModel.openThreadPreview(targetThread)
                }
            },
            onDismiss = { showStarredMemoriesDialog = false }
        )
    }

    if (showSendExcerptToRavenDialog && uiState.selectedThreadForPreview != null) {
        val thread = uiState.selectedThreadForPreview!!
        val includedMessages = uiState.filteredPreviewMessages.filter {
            !uiState.excludedMessageIds.contains(it.messageId)
        }.takeLast(15)
        SendExcerptToRavenDialog(
            ravenDb = ravenDb,
            socketTransport = socketTransport,
            contactName = thread.displayName,
            messagesToForward = includedMessages,
            onDismiss = { showSendExcerptToRavenDialog = false }
        )
    }

    if (showOnboarding) {
        com.messagebackup.app.ui.dialogs.OnboardingWalkthroughDialog(
            onComplete = {
                prefs.setOnboardingCompleted(true)
                showOnboarding = false
            }
        )
    }
}

@Composable
private fun StarredMemoriesDialog(
    localCacheDb: LocalMessageDatabase,
    onJumpToThread: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var starredList by remember { mutableStateOf<List<StarredMemoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a").withZone(ZoneId.systemDefault())
    }

    LaunchedEffect(Unit) {
        starredList = localCacheDb.getStarredMemories()
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "Pinned Vault Memories",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (starredList.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No Pinned Memories Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Pin important O.D.I.N. answers (using the '⭐ Pin' chip in O.D.I.N. Copilot) or use the Filter checklist inside any conversation to pin important messages, codes, or addresses here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(starredList, key = { it.id }) { item ->
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (item.sourceType == "ODIN") "⚡ O.D.I.N. • ${item.contactName}" else "💬 ${item.contactName}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(item.body))
                                                Toast.makeText(context, "Copied memory to clipboard", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copy",
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                scope.launch {
                                                    localCacheDb.removeStarredMemory(item.id)
                                                    starredList = localCacheDb.getStarredMemories()
                                                }
                                            },
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.DeleteOutline,
                                                contentDescription = "Remove",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = item.body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = dateFormatter.format(Instant.ofEpochMilli(item.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (item.threadId > 0L) {
                                        Text(
                                            text = "Open Thread →",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable { onJumpToThread(item.threadId) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SendExcerptToRavenDialog(
    ravenDb: RavenDatabaseHelper,
    socketTransport: RavenSocketTransport,
    contactName: String,
    messagesToForward: List<com.messagebackup.app.model.MessageItem>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val peers = remember {
        ravenDb.getAllPeers().filter { it.status == RavenPeerStatus.CONNECTED }
    }
    var isSending by remember { mutableStateOf(false) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("MMM d, h:mm a").withZone(ZoneId.systemDefault())
    }

    AlertDialog(
        onDismissRequest = { if (!isSending) onDismiss() },
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_flying_raven),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
        },
        title = {
            Text(
                text = "Send Vault Excerpt via Raven",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Forward ${messagesToForward.size} selected message(s) from $contactName over end-to-end encrypted Raven Mesh:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (peers.isEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No connected Raven friends yet. Pair with a friend in Raven Mesh first!",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(peers, key = { it.peerId }) { peer ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isSending) {
                                        isSending = true
                                        val excerptBuilder = StringBuilder()
                                        excerptBuilder.append("📜 [Shared Vault Excerpt: $contactName]\n")
                                        messagesToForward.forEach { m ->
                                            val who = if (m.direction == com.messagebackup.app.model.MessageDirection.OUTGOING) "Me" else contactName
                                            val dt = dateFormatter.format(Instant.ofEpochMilli(m.timestamp))
                                            excerptBuilder.append("• [$dt] $who: ${m.body}\n")
                                        }
                                        val payload = excerptBuilder.toString().trim()
                                        scope.launch {
                                            val res = socketTransport.sendMessageToPeer(peer, payload, messageType = "SMS_EXCERPT")
                                            isSending = false
                                            if (res is RavenSendResult.Success) {
                                                Toast.makeText(context, "🪶 Excerpt dispatched to ${peer.displayName}", Toast.LENGTH_SHORT).show()
                                                onDismiss()
                                            } else {
                                                Toast.makeText(context, "Could not reach ${peer.displayName}", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = peer.displayName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                        Text(
                                            text = peer.peerId,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) {
                Text("Cancel")
            }
        }
    )
}
