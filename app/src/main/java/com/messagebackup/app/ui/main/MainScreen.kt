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
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
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

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var isMessageSelectionMode by remember { mutableStateOf(false) }

    if (uiState.isSettingsOpen) {
        SettingsScreen(
            onNavigateBack = { viewModel.setSettingsOpen(false) },
            modifier = modifier
        )
        return
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
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/zip")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(viewIntent, "Open Archive"))
        } catch (e: Exception) {
            triggerShareSheet(file)
        }
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
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    viewModel.setSettingsOpen(true)
                                }
                            )

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
                )
            }
        },
        bottomBar = {
            AdBannerView(isAdsRemoved = isAdsRemoved)
        },
        floatingActionButton = {
            if (uiState.isSelectionMode && uiState.selectedThreadIds.isNotEmpty() && uiState.selectedThreadForPreview == null) {
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

                        if (uiState.isPreviewLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(uiState.previewMessages, key = { it.messageId }) { message ->
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
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { viewModel.toggleMockMode() }) {
                            Text(text = "Try With Mock Data (Instant Test)")
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
                        android.Manifest.permission.RECEIVE_MMS,
                        android.Manifest.permission.READ_CONTACTS
                    )
                )
            },
            onDismiss = { viewModel.setShowPermissionDisclosure(false) }
        )
    }

    // Export Options Modal
    if (uiState.showExportDialog) {
        ExportConfigDialog(
            selectedCount = uiState.selectedThreadIds.size,
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
                                if (options.outputType == OutputDestination.PERSISTENT_DIRECTORY) {
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
}
