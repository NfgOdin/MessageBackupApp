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
    val isMockMode: Boolean = false,
    val isSettingsOpen: Boolean = false,
    val viewerFile: java.io.File? = null
)

/**
 * ViewModel managing conversation querying, live search filtering, message exclusion,
 * preview, and export workflows.
 */
class MainViewModel : ViewModel() {

    private val app = MessageBackupApp.instance
    val prefs = app.appPreferences
    val billingManager = app.billingManager
    val adManager = app.adManager

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val isAdsRemoved: StateFlow<Boolean> = billingManager.isAdsRemoved
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        _uiState.value = _uiState.value.copy(isMockMode = app.useMockData)

        viewModelScope.launch {
            prefs.resolveContacts.collect {
                if (_uiState.value.hasSmsPermission || _uiState.value.isMockMode) {
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
        if (hasSms || _uiState.value.isMockMode) {
            loadThreads()
        }
    }

    fun setShowPermissionDisclosure(show: Boolean) {
        _uiState.value = _uiState.value.copy(showPermissionDisclosure = show)
    }

    fun setSettingsOpen(open: Boolean) {
        _uiState.value = _uiState.value.copy(isSettingsOpen = open)
    }

    fun toggleMockMode() {
        val newMode = !app.useMockData
        app.useMockData = newMode
        app.updateExportEngineRepository(app.currentTelephonyRepository)
        _uiState.value = _uiState.value.copy(isMockMode = newMode)
        loadThreads()
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
        val selectedIds = _uiState.value.selectedThreadIds
        val selectedThreads = _uiState.value.threads.filter { selectedIds.contains(it.threadId) }

        if (selectedThreads.isEmpty()) return

        _uiState.value = _uiState.value.copy(
            isExporting = true,
            showExportDialog = false,
            exportProgressMessage = "Packaging ${selectedThreads.size} conversations into ${options.format.displayName}..."
        )

        viewModelScope.launch {
            val safUri = prefs.safDirectoryUri.value?.let { Uri.parse(it) }
            val effectiveOptions = if (prefs.outputDestination.value == OutputDestination.PERSISTENT_DIRECTORY && safUri != null) {
                options.copy(outputType = OutputDestination.PERSISTENT_DIRECTORY)
            } else {
                options
            }

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
}
