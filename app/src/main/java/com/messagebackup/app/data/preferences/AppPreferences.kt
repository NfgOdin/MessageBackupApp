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
        private const val KEY_GOOGLE_DRIVE_ACCOUNT = "google_drive_account"

        // O.D.I.N. AI & Auto-Backup Preferences
        private const val KEY_AI_CREDITS = "ai_credits"
        private const val KEY_AUTO_BACKUP_CREDITS = "auto_backup_credits"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_OLLAMA_ENDPOINT = "ollama_endpoint"
        private const val KEY_OLLAMA_MODEL = "ollama_model"

        // Privacy PIN & Biometric Lock Preferences
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_ODIN_LOCK_ENABLED = "odin_lock_enabled"
        private const val KEY_PRIVACY_PIN_HASH = "privacy_pin_hash"
    }

    private val _googleDriveAccount = MutableStateFlow(prefs.getString(KEY_GOOGLE_DRIVE_ACCOUNT, null))
    val googleDriveAccount: StateFlow<String?> = _googleDriveAccount.asStateFlow()

    // Default 3 welcome AI credits for new users
    private val _aiCredits = MutableStateFlow(prefs.getInt(KEY_AI_CREDITS, 3))
    val aiCredits: StateFlow<Int> = _aiCredits.asStateFlow()

    // Default 3 welcome Auto-Backup credits for new users
    private val _autoBackupCredits = MutableStateFlow(prefs.getInt(KEY_AUTO_BACKUP_CREDITS, 3))
    val autoBackupCredits: StateFlow<Int> = _autoBackupCredits.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _appLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_APP_LOCK_ENABLED, false))
    val appLockEnabled: StateFlow<Boolean> = _appLockEnabled.asStateFlow()

    private val _odinLockEnabled = MutableStateFlow(prefs.getBoolean(KEY_ODIN_LOCK_ENABLED, false))
    val odinLockEnabled: StateFlow<Boolean> = _odinLockEnabled.asStateFlow()

    private val _privacyPinHash = MutableStateFlow(prefs.getString(KEY_PRIVACY_PIN_HASH, "") ?: "")
    val privacyPinHash: StateFlow<String> = _privacyPinHash.asStateFlow()

    private val _aiProvider = MutableStateFlow(prefs.getString(KEY_AI_PROVIDER, "GEMINI") ?: "GEMINI")
    val aiProvider: StateFlow<String> = _aiProvider.asStateFlow()

    private val _geminiApiKey = MutableStateFlow(prefs.getString(KEY_GEMINI_API_KEY, "") ?: "")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _ollamaEndpoint = MutableStateFlow(prefs.getString(KEY_OLLAMA_ENDPOINT, "http://100.0.0.1:11434") ?: "http://100.0.0.1:11434")
    val ollamaEndpoint: StateFlow<String> = _ollamaEndpoint.asStateFlow()

    private val _ollamaModel = MutableStateFlow(prefs.getString(KEY_OLLAMA_MODEL, "llama3.2") ?: "llama3.2")
    val ollamaModel: StateFlow<String> = _ollamaModel.asStateFlow()

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

    fun setAiCredits(credits: Int) {
        val safeCredits = credits.coerceAtLeast(0)
        prefs.edit().putInt(KEY_AI_CREDITS, safeCredits).apply()
        _aiCredits.value = safeCredits
    }

    fun addAiCredits(amount: Int) {
        val newCredits = (_aiCredits.value + amount).coerceAtLeast(0)
        setAiCredits(newCredits)
    }

    fun consumeAiCredit(): Boolean {
        if (_aiCredits.value > 0) {
            setAiCredits(_aiCredits.value - 1)
            return true
        }
        return false
    }

    fun setAutoBackupCredits(credits: Int) {
        val safeCredits = credits.coerceAtLeast(0)
        prefs.edit().putInt(KEY_AUTO_BACKUP_CREDITS, safeCredits).apply()
        _autoBackupCredits.value = safeCredits
    }

    fun addAutoBackupCredits(amount: Int) {
        val newCredits = (_autoBackupCredits.value + amount).coerceAtLeast(0)
        setAutoBackupCredits(newCredits)
    }

    fun consumeAutoBackupCredit(): Boolean {
        if (_autoBackupCredits.value > 0) {
            setAutoBackupCredits(_autoBackupCredits.value - 1)
            return true
        }
        return false
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enabled).apply()
        _autoBackupEnabled.value = enabled
    }

    fun setAiProvider(provider: String) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider).apply()
        _aiProvider.value = provider
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
        _geminiApiKey.value = key.trim()
    }

    fun setOllamaEndpoint(endpoint: String) {
        val clean = endpoint.trim().trimEnd('/')
        prefs.edit().putString(KEY_OLLAMA_ENDPOINT, clean).apply()
        _ollamaEndpoint.value = clean
    }

    fun setOllamaModel(model: String) {
        prefs.edit().putString(KEY_OLLAMA_MODEL, model.trim()).apply()
        _ollamaModel.value = model.trim()
    }

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

    fun setGoogleDriveAccount(email: String?) {
        prefs.edit().putString(KEY_GOOGLE_DRIVE_ACCOUNT, email).apply()
        _googleDriveAccount.value = email
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        _appLockEnabled.value = enabled
    }

    fun setOdinLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ODIN_LOCK_ENABLED, enabled).apply()
        _odinLockEnabled.value = enabled
    }

    fun setPrivacyPin(rawPin: String) {
        val hash = hashPinSha256(rawPin.trim())
        prefs.edit().putString(KEY_PRIVACY_PIN_HASH, hash).apply()
        _privacyPinHash.value = hash
    }

    fun verifyPrivacyPin(enteredPin: String): Boolean {
        val stored = _privacyPinHash.value
        if (stored.isBlank()) return false
        return hashPinSha256(enteredPin.trim()).equals(stored, ignoreCase = true)
    }

    fun hasPrivacyPin(): Boolean = _privacyPinHash.value.isNotBlank()

    private fun hashPinSha256(pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("OdinVaultPinSalt:" + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
