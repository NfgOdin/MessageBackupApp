package com.messagebackup.app.ui.main

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.messagebackup.app.MessageBackupApp
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.ExportOptions
import com.messagebackup.app.model.ExportResult
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.OutputDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThreadSortOption(val displayName: String) {
    RECENT("Most Recent"),
    MESSAGE_COUNT("Most Messages"),
    NAME("Contact Name (A-Z)")
}

/**
 * UI State for the Main Screen.
 */
data class MainUiState(
    val isLoading: Boolean = false,
    val threads: List<ConversationThread> = emptyList(),
    val filteredThreads: List<ConversationThread> = emptyList(),
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val threadSearchQuery: String = "",
    val isThreadSearchActive: Boolean = false,
    val sortOption: ThreadSortOption = ThreadSortOption.RECENT,
    val selectedThreadIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val hasSmsPermission: Boolean = false,
    val hasContactsPermission: Boolean = false,
    val showPermissionDisclosure: Boolean = false,
    val showExportDialog: Boolean = false,
    val isExporting: Boolean = false,
    val exportProgressMessage: String? = null,
    val lastExportResult: ExportResult? = null,
    val selectedThreadForPreview: ConversationThread? = null,
    val previewMessages: List<MessageItem> = emptyList(),
    val filteredPreviewMessages: List<MessageItem> = emptyList(),
    val excludedMessageIds: Set<Long> = emptySet(),
    val isPreviewLoading: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val viewerFile: java.io.File? = null,
    val showStatsDialog: Boolean = false,
    val isStatsLoading: Boolean = false,
    val statsProgressPct: Int = 0,
    val statsProgressMessage: String = "",
    val stats: com.messagebackup.app.model.ConversationStats? = null,
    val statsTargetThreadId: Long? = null,
    val isCopilotOpen: Boolean = false,
    val copilotInitialPrompt: String? = null,
    val globalCopilotMessages: List<MessageItem> = emptyList(),
    val showRestoreDialog: Boolean = false,
    val isRestoring: Boolean = false,
    val restoreProgressMessage: String? = null,
    val restoredCount: Int = 0,
    val showReviewPromptDialog: Boolean = false,
    val isVaultOpen: Boolean = false,
    val isVaultLoading: Boolean = false,
    val vaultMediaItems: List<com.messagebackup.app.model.VaultMediaItem> = emptyList()
)

/**
 * ViewModel managing conversation querying, live search filtering, message exclusion,
 * preview, and export workflows.
 */
class MainViewModel : ViewModel() {

    val app = MessageBackupApp.instance
    val prefs = app.appPreferences
    val billingManager = app.billingManager
    val adManager = app.adManager

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val isAdsRemoved: StateFlow<Boolean> = billingManager.isAdsRemoved
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            prefs.resolveContacts.collect {
                if (_uiState.value.hasSmsPermission) {
                    loadThreads()
                }
            }
        }
    }

    fun onPermissionsChecked(hasSms: Boolean, hasContacts: Boolean) {
        _uiState.value = _uiState.value.copy(
            hasSmsPermission = hasSms,
            hasContactsPermission = hasContacts
        )
        if (hasSms) {
            loadThreads()
        }
    }

    fun setShowPermissionDisclosure(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPermissionDisclosure = show)
    }

    fun setSettingsOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsOpen = open)
    }

    fun setCopilotOpen(open: Boolean, initialPrompt: String? = null) {
        _uiState.value = _uiState.value.copy(
            isCopilotOpen = open,
            copilotInitialPrompt = initialPrompt
        )
        if (open && _uiState.value.selectedThreadForPreview == null && _uiState.value.globalCopilotMessages.isEmpty()) {
            loadGlobalCopilotContext()
        }
    }

    private fun loadGlobalCopilotContext() {
        viewModelScope.launch {
            val sampleThreads = _uiState.value.threads.take(15)
            val resolve = prefs.resolveContacts.value && _uiState.value.hasContactsPermission
            val combined = mutableListOf<MessageItem>()
            for (thread in sampleThreads) {
                val msgs = app.currentTelephonyRepository.getMessagesForThread(
                    threadId = thread.threadId,
                    resolveContacts = resolve
                ).takeLast(15).map { msg ->
                    if (msg.direction == com.messagebackup.app.model.MessageDirection.OUTGOING) {
                        msg.copy(sender = "Me (to ${thread.displayName})")
                    } else {
                        msg.copy(sender = thread.displayName)
                    }
                }
                combined.addAll(msgs)
            }
            combined.sortBy { it.timestamp }
            _uiState.value = _uiState.value.copy(globalCopilotMessages = combined)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        applySearchFilter(query, _uiState.value.threads)
    }

    fun setSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isSearchActive = active,
            searchQuery = if (!active) "" else _uiState.value.searchQuery
        )
        if (!active) {
            _uiState.value = _uiState.value.copy(filteredThreads = _uiState.value.threads)
        }
    }

    fun setSortOption(sort: ThreadSortOption) {
        _uiState.value = _uiState.value.copy(sortOption = sort)
        applySearchFilter(_uiState.value.searchQuery, _uiState.value.threads)
    }

    private fun applySearchFilter(query: String, allThreads: List<ConversationThread>) {
        val baseList = if (query.isBlank()) {
            allThreads
        } else {
            val q = query.trim().lowercase()
            allThreads.filter {
                it.displayName.lowercase().contains(q) ||
                        it.address.lowercase().contains(q) ||
                        it.snippet.lowercase().contains(q)
            }
        }

        val sortedList = when (_uiState.value.sortOption) {
            ThreadSortOption.RECENT -> baseList.sortedByDescending { it.timestamp }
            ThreadSortOption.MESSAGE_COUNT -> baseList.sortedByDescending { it.messageCount }
            ThreadSortOption.NAME -> baseList.sortedBy { it.displayName.lowercase() }
        }

        _uiState.value = _uiState.value.copy(filteredThreads = sortedList)
    }

    fun loadThreads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            app.currentTelephonyRepository.getConversationThreads(
                resolveContacts = prefs.resolveContacts.value && _uiState.value.hasContactsPermission
            ).collect { list ->
                _uiState.value = _uiState.value.copy(
                    threads = list,
                    isLoading = false
                )
                applySearchFilter(_uiState.value.searchQuery, list)
            }
        }
    }

    fun toggleThreadSelection(threadId: Long) {
        val currentSelection = _uiState.value.selectedThreadIds.toMutableSet()
        if (currentSelection.contains(threadId)) {
            currentSelection.remove(threadId)
        } else {
            currentSelection.add(threadId)
        }

        _uiState.value = _uiState.value.copy(
            selectedThreadIds = currentSelection,
            isSelectionMode = currentSelection.isNotEmpty()
        )
    }

    fun selectAllThreads() {
        val allIds = _uiState.value.filteredThreads.map { it.threadId }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedThreadIds = allIds,
            isSelectionMode = true
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedThreadIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun setShowExportDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showExportDialog = show)
    }

    fun openThreadPreview(thread: ConversationThread) {
        _uiState.value = _uiState.value.copy(
            selectedThreadForPreview = thread,
            isPreviewLoading = true,
            previewMessages = emptyList(),
            filteredPreviewMessages = emptyList(),
            threadSearchQuery = "",
            isThreadSearchActive = false,
            excludedMessageIds = emptySet()
        )

        viewModelScope.launch {
            val messages = app.currentTelephonyRepository.getMessagesForThread(
                threadId = thread.threadId,
                resolveContacts = prefs.resolveContacts.value && _uiState.value.hasContactsPermission
            )
            _uiState.value = _uiState.value.copy(
                previewMessages = messages,
                filteredPreviewMessages = messages,
                isPreviewLoading = false
            )
        }
    }

    fun setThreadSearchQuery(query: String) {
        val q = query.trim().lowercase()
        val allMessages = _uiState.value.previewMessages
        val filtered = if (q.isBlank()) {
            allMessages
        } else {
            allMessages.filter { msg ->
                msg.body.lowercase().contains(q) ||
                msg.sender.lowercase().contains(q) ||
                msg.recipient.lowercase().contains(q)
            }
        }
        _uiState.value = _uiState.value.copy(
            threadSearchQuery = query,
            filteredPreviewMessages = filtered
        )
    }

    fun setThreadSearchActive(active: Boolean) {
        _uiState.value = _uiState.value.copy(
            isThreadSearchActive = active,
            threadSearchQuery = if (!active) "" else _uiState.value.threadSearchQuery,
            filteredPreviewMessages = if (!active) _uiState.value.previewMessages else _uiState.value.filteredPreviewMessages
        )
    }

    fun setViewerFile(file: java.io.File?) {
        _uiState.value = _uiState.value.copy(viewerFile = file)
    }

    fun toggleMessageExclusion(messageId: Long) {
        val currentExcluded = _uiState.value.excludedMessageIds.toMutableSet()
        if (currentExcluded.contains(messageId)) {
            currentExcluded.remove(messageId)
        } else {
            currentExcluded.add(messageId)
        }
        _uiState.value = _uiState.value.copy(excludedMessageIds = currentExcluded)
    }

    fun closeThreadPreview() {
        _uiState.value = _uiState.value.copy(
            selectedThreadForPreview = null,
            previewMessages = emptyList(),
            filteredPreviewMessages = emptyList(),
            threadSearchQuery = "",
            isThreadSearchActive = false,
            excludedMessageIds = emptySet()
        )
    }

    fun startExport(options: ExportOptions, onComplete: (ExportResult) -> Unit) {
        val previewThread = _uiState.value.selectedThreadForPreview
        val selectedThreads = if (previewThread != null) {
            listOf(previewThread)
        } else {
            val selectedIds = _uiState.value.selectedThreadIds
            _uiState.value.threads.filter { selectedIds.contains(it.threadId) }
        }

        if (selectedThreads.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            isExporting = true,
            showExportDialog = false,
            exportProgressMessage = "Packaging ${selectedThreads.size} conversation${if (selectedThreads.size > 1) "s" else ""} into ${options.format.displayName}..."
        )

        viewModelScope.launch {
            val safUri = prefs.safDirectoryUri.value?.let { Uri.parse(it) }
            val destType = if (prefs.outputDestination.value == OutputDestination.PERSISTENT_DIRECTORY && safUri != null) {
                OutputDestination.PERSISTENT_DIRECTORY
            } else {
                options.outputType
            }
            val effectiveOptions = options.copy(
                outputType = destType,
                excludedMessageIds = _uiState.value.excludedMessageIds
            )

            val result = app.exportEngine.exportThreads(
                threads = selectedThreads,
                options = effectiveOptions,
                resolveContacts = prefs.resolveContacts.value && _uiState.value.hasContactsPermission,
                customSafTreeUri = safUri
            )

            _uiState.value = _uiState.value.copy(
                isExporting = false,
                exportProgressMessage = null,
                lastExportResult = result
            )

            onComplete(result)
        }
    }

    fun setShowStatsDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showStatsDialog = show)
        if (show) {
            loadStatistics(forceRefresh = false)
        }
    }

    fun loadStatistics(forceRefresh: Boolean = false) {
        val targetThread = _uiState.value.selectedThreadForPreview
        val targetThreadId = targetThread?.threadId
        if (!forceRefresh && _uiState.value.stats != null && _uiState.value.statsTargetThreadId == targetThreadId) {
            // Already cached for this exact scope (global vs specific thread), don't recompute
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isStatsLoading = true,
                statsProgressPct = 5,
                statsProgressMessage = "Indexing conversations..."
            )
            val threads = if (targetThread != null) listOf(targetThread) else _uiState.value.threads
            val totalThreads = threads.size
            val totalMessages = threads.sumOf { it.messageCount.toLong() }

            // Compute top contacts
            val topContacts = threads.sortedByDescending { it.messageCount }
                .take(5)
                .map { t ->
                    val pct = if (totalMessages > 0) (t.messageCount.toFloat() / totalMessages.toFloat()) * 100f else 0f
                    com.messagebackup.app.model.ContactMessageCount(
                        displayName = t.displayName,
                        address = t.address,
                        count = t.messageCount,
                        percentage = pct
                    )
                }

            _uiState.value = _uiState.value.copy(
                statsProgressPct = 15,
                statsProgressMessage = "Analyzing top contacts & message ratios..."
            )

            // Sample detailed message metrics across top threads
            var totalSent = 0L
            var totalReceived = 0L
            var totalSms = 0L
            var totalMms = 0L
            var totalMedia = 0L
            val hourCounts = IntArray(24)
            val dayOfWeekCounts = mutableMapOf<java.time.DayOfWeek, Int>()
            var oldestTime: Long? = null
            var newestTime: Long? = null

            // Inspect messages across selected thread or top 15 active threads
            val sampleThreads = if (targetThread != null) {
                listOf(targetThread)
            } else {
                threads.take(15)
            }

            for ((index, thread) in sampleThreads.withIndex()) {
                val pct = 15 + ((index.toFloat() / sampleThreads.size.toFloat()) * 80f).toInt()
                _uiState.value = _uiState.value.copy(
                    statsProgressPct = pct,
                    statsProgressMessage = "Scanning \"${thread.displayName}\" (${index + 1}/${sampleThreads.size})..."
                )
                val messages = app.currentTelephonyRepository.getMessagesForThread(
                    threadId = thread.threadId,
                    resolveContacts = false
                )
                for (msg in messages) {
                    if (msg.direction == com.messagebackup.app.model.MessageDirection.OUTGOING) totalSent++ else totalReceived++
                    if (msg.type == com.messagebackup.app.model.MessageType.SMS) totalSms++ else totalMms++
                    totalMedia += msg.attachments.size

                    val zdt = java.time.Instant.ofEpochMilli(msg.timestamp).atZone(java.time.ZoneId.systemDefault())
                    hourCounts[zdt.hour]++
                    dayOfWeekCounts[zdt.dayOfWeek] = (dayOfWeekCounts[zdt.dayOfWeek] ?: 0) + 1

                    if (oldestTime == null || msg.timestamp < oldestTime) oldestTime = msg.timestamp
                    if (newestTime == null || msg.timestamp > newestTime) newestTime = msg.timestamp
                }
            }

            _uiState.value = _uiState.value.copy(
                statsProgressPct = 98,
                statsProgressMessage = "Finalizing activity patterns & insights..."
            )

            val busiestHour = hourCounts.indices.maxByOrNull { hourCounts[it] }
            val busiestDay = dayOfWeekCounts.maxByOrNull { it.value }?.key?.name?.lowercase()?.replaceFirstChar { it.uppercase() }

            val stats = com.messagebackup.app.model.ConversationStats(
                totalThreads = totalThreads,
                totalMessages = totalMessages,
                totalSms = totalSms,
                totalMms = totalMms,
                totalSent = totalSent,
                totalReceived = totalReceived,
                totalMediaAttachments = totalMedia,
                oldestTimestamp = oldestTime,
                newestTimestamp = newestTime,
                topContacts = topContacts,
                busiestHour = busiestHour,
                busiestDayOfWeek = busiestDay
            )

            if (targetThreadId == null) {
                prefs.saveCachedStats(stats)
                com.messagebackup.app.widget.FullInsightsWidgetProvider.updateAllWidgets(app)
            }

            _uiState.value = _uiState.value.copy(
                stats = stats,
                statsTargetThreadId = targetThreadId,
                isStatsLoading = false,
                statsProgressPct = 100,
                statsProgressMessage = "Complete!"
            )
        }
    }

    fun setShowRestoreDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showRestoreDialog = show)
    }

    fun setShowReviewPromptDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showReviewPromptDialog = show)
    }

    fun startRestoreFromUri(uri: Uri, onResult: (Boolean, Int, String) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isRestoring = true,
                restoreProgressMessage = "Reading backup file..."
            )

            try {
                val inputStream = app.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    _uiState.value = _uiState.value.copy(isRestoring = false, restoreProgressMessage = null)
                    onResult(false, 0, "Could not open backup file.")
                    return@launch
                }

                val content = inputStream.bufferedReader().use { it.readText() }
                val messages = mutableListOf<MessageItem>()
                var fallbackAddress = ""

                if (content.trimStart().startsWith("{")) {
                    val (thread, parsedMsgs) = com.messagebackup.app.domain.export.JsonFormatter().parseBackupJson(content)
                    messages.addAll(parsedMsgs)
                    fallbackAddress = thread?.address ?: ""
                } else if (content.trimStart().startsWith("<")) {
                    val parsedMsgs = com.messagebackup.app.domain.export.XmlFormatter().parseBackupXml(content)
                    messages.addAll(parsedMsgs)
                } else {
                    _uiState.value = _uiState.value.copy(isRestoring = false, restoreProgressMessage = null)
                    onResult(false, 0, "Unsupported backup format. Please select a valid .json or .xml backup.")
                    return@launch
                }

                if (messages.isEmpty()) {
                    _uiState.value = _uiState.value.copy(isRestoring = false, restoreProgressMessage = null)
                    onResult(false, 0, "No messages found in the selected backup file.")
                    return@launch
                }

                var successCount = 0
                val total = messages.size
                for ((index, msg) in messages.withIndex()) {
                    _uiState.value = _uiState.value.copy(
                        restoreProgressMessage = "Restoring message ${index + 1} of $total..."
                    )
                    val inserted = app.currentTelephonyRepository.insertSmsMessage(msg, fallbackAddress)
                    if (inserted) successCount++
                }

                _uiState.value = _uiState.value.copy(
                    isRestoring = false,
                    restoreProgressMessage = null,
                    restoredCount = successCount
                )

                loadThreads()
                onResult(true, successCount, "Successfully restored $successCount messages to your phone!")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isRestoring = false, restoreProgressMessage = null)
                onResult(false, 0, "Restore failed: ${e.message}")
            }
        }
    }

    fun setVaultOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isVaultOpen = open)
        if (open && _uiState.value.vaultMediaItems.isEmpty()) {
            loadMediaVault()
        }
    }

    fun loadMediaVault(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.vaultMediaItems.isNotEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isVaultLoading = true)
            val resolve = prefs.resolveContacts.value && _uiState.value.hasContactsPermission
            val items = app.currentTelephonyRepository.getAllMediaAttachments(resolve)
            _uiState.value = _uiState.value.copy(
                isVaultLoading = false,
                vaultMediaItems = items
            )
        }
    }
}
