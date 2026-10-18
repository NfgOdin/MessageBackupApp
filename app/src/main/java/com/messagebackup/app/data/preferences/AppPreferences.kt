package com.messagebackup.app.data.preferences

import android.content.Context
import android.net.Uri
import com.messagebackup.app.model.DateRangeFilter
import com.messagebackup.app.model.ExportFormat
import com.messagebackup.app.model.ExportTheme
import com.messagebackup.app.model.OutputDestination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persisted user preferences for app configuration, default export settings,
 * pagination limits, theme, and Storage Access Framework directory URI.
 */
class AppPreferences(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "message_backup_app_prefs"
        private const val KEY_RESOLVE_CONTACTS = "resolve_contacts"
        private const val KEY_DEFAULT_FORMAT = "default_format"
        private const val KEY_MAX_MESSAGES_PER_FILE = "max_messages_per_file"
        private const val KEY_EXPORT_THEME = "export_theme"
        private const val KEY_DEFAULT_DESTINATION = "default_destination"
        private const val KEY_SAF_DIRECTORY_URI = "saf_directory_uri"
        private const val KEY_SAF_DIRECTORY_DISPLAY = "saf_directory_display"
        private const val KEY_APP_THEME = "app_theme"
    }

    private val _resolveContacts = MutableStateFlow(prefs.getBoolean(KEY_RESOLVE_CONTACTS, true))
    val resolveContacts: StateFlow<Boolean> = _resolveContacts.asStateFlow()

    private val _defaultFormat = MutableStateFlow(
        ExportFormat.valueOf(prefs.getString(KEY_DEFAULT_FORMAT, ExportFormat.HTML.name) ?: ExportFormat.HTML.name)
    )
    val defaultFormat: StateFlow<ExportFormat> = _defaultFormat.asStateFlow()

    private val _maxMessagesPerFile = MutableStateFlow(prefs.getInt(KEY_MAX_MESSAGES_PER_FILE, 0))
    val maxMessagesPerFile: StateFlow<Int> = _maxMessagesPerFile.asStateFlow()

    private val _exportTheme = MutableStateFlow(
        ExportTheme.valueOf(prefs.getString(KEY_EXPORT_THEME, ExportTheme.LIGHT.name) ?: ExportTheme.LIGHT.name)
    )
    val exportTheme: StateFlow<ExportTheme> = _exportTheme.asStateFlow()

    private val _outputDestination = MutableStateFlow(
        OutputDestination.valueOf(prefs.getString(KEY_DEFAULT_DESTINATION, OutputDestination.SHARE_SHEET.name) ?: OutputDestination.SHARE_SHEET.name)
    )
    val outputDestination: StateFlow<OutputDestination> = _outputDestination.asStateFlow()

    private val _safDirectoryUri = MutableStateFlow(prefs.getString(KEY_SAF_DIRECTORY_URI, null))
    val safDirectoryUri: StateFlow<String?> = _safDirectoryUri.asStateFlow()

    private val _safDirectoryDisplay = MutableStateFlow(prefs.getString(KEY_SAF_DIRECTORY_DISPLAY, "Not Selected") ?: "Not Selected")
    val safDirectoryDisplay: StateFlow<String> = _safDirectoryDisplay.asStateFlow()

    private val _appTheme = MutableStateFlow(
        ExportTheme.valueOf(prefs.getString(KEY_APP_THEME, ExportTheme.SYSTEM.name) ?: ExportTheme.SYSTEM.name)
    )
    val appTheme: StateFlow<ExportTheme> = _appTheme.asStateFlow()

    fun setResolveContacts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RESOLVE_CONTACTS, enabled).apply()
        _resolveContacts.value = enabled
    }

    fun setDefaultFormat(format: ExportFormat) {
        prefs.edit().putString(KEY_DEFAULT_FORMAT, format.name).apply()
        _defaultFormat.value = format
    }

    fun setMaxMessagesPerFile(max: Int) {
        prefs.edit().putInt(KEY_MAX_MESSAGES_PER_FILE, max).apply()
        _maxMessagesPerFile.value = max
    }

    fun setExportTheme(theme: ExportTheme) {
        prefs.edit().putString(KEY_EXPORT_THEME, theme.name).apply()
        _exportTheme.value = theme
    }

    fun setOutputDestination(destination: OutputDestination) {
        prefs.edit().putString(KEY_DEFAULT_DESTINATION, destination.name).apply()
        _outputDestination.value = destination
    }

    fun setSafDirectory(uri: Uri?, displayName: String) {
        prefs.edit()
            .putString(KEY_SAF_DIRECTORY_URI, uri?.toString())
            .putString(KEY_SAF_DIRECTORY_DISPLAY, displayName)
            .apply()
        _safDirectoryUri.value = uri?.toString()
        _safDirectoryDisplay.value = displayName
    }

    fun setAppTheme(theme: ExportTheme) {
        prefs.edit().putString(KEY_APP_THEME, theme.name).apply()
        _appTheme.value = theme
    }
}
