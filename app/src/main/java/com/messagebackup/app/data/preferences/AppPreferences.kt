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
        private const val KEY_AUTO_BACKUP_FREQUENCY_DAYS = "auto_backup_frequency_days"
        private const val KEY_AUTO_BACKUP_REQUIRES_WIFI = "auto_backup_requires_wifi"
        private const val KEY_LAST_AUTO_BACKUP_TIMESTAMP = "last_auto_backup_timestamp"
        private const val KEY_AUTO_BACKUP_SELECTED_THREAD_IDS = "auto_backup_selected_thread_ids"
        private const val KEY_AUTO_BACKUP_ALL_THREADS = "auto_backup_all_threads"
        private const val KEY_AI_PROVIDER = "ai_provider"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_GEMINI_MODEL = "gemini_model"
        private const val KEY_OPENAI_API_KEY = "openai_api_key"
        private const val KEY_OPENAI_MODEL = "openai_model"
        private const val KEY_OLLAMA_ENDPOINT = "ollama_endpoint"
        private const val KEY_OLLAMA_MODEL = "ollama_model"
        private const val KEY_ODIN_TEMPERATURE = "odin_temperature"
        private const val KEY_ODIN_TOP_P = "odin_top_p"
        private const val KEY_ODIN_NUM_CTX = "odin_num_ctx"
        private const val KEY_ODIN_CUSTOM_SYSTEM_PROMPT = "odin_custom_system_prompt"
        private const val KEY_HUGINN_COMPOSER_ENABLED = "huginn_composer_enabled"
        private const val KEY_ANSUZ_CREDITS = "ansuz_credits"
        private const val KEY_HUGINN_LEASE_EXPIRY_MS = "huginn_lease_expiry_ms"
        private const val KEY_HUGINN_AD_PROGRESS = "huginn_ad_progress"
        private const val KEY_HUGINN_PERMANENT_UNLOCKED = "huginn_permanent_unlocked"
        private const val KEY_ODIN_PERMANENT_UNLOCKED = "odin_permanent_unlocked"
        private const val KEY_ANIMATIONS_ENABLED = "animations_enabled"
        private const val KEY_CONFIRM_RUNE_SPEND = "confirm_rune_spend"
        private const val KEY_RAVEN_TRANSPORT_MODE = "raven_transport_mode"

        // Privacy PIN & Biometric Lock Preferences
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_ODIN_LOCK_ENABLED = "odin_lock_enabled"
        private const val KEY_PRIVACY_PIN_HASH = "privacy_pin_hash"
        private const val KEY_BACKUP_AD_PROGRESS = "backup_ad_progress"
        private const val KEY_LAST_REWARD_AD_TIME = "last_reward_ad_time"
        private const val KEY_CACHED_STATS = "cached_conversation_stats"
        private const val KEY_ALWAYS_PROMPT_EXPORT_OPTIONS = "always_prompt_export_options"
        private const val KEY_HAS_REVIEWED_APP = "has_reviewed_app"
        private const val KEY_EXPORT_SUCCESS_COUNT = "export_success_count"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_INITIAL_MESSAGE_BATCH_SIZE = "initial_message_batch_size"
        private const val KEY_SCROLL_MESSAGE_BATCH_SIZE = "scroll_message_batch_size"
        private const val KEY_FAST_PAGINATION_ENABLED = "fast_pagination_enabled"

        // Google Account & Authentication
        private const val KEY_GOOGLE_ACCOUNT_EMAIL = "google_account_email"
        private const val KEY_GOOGLE_ACCOUNT_NAME = "google_account_name"
        private const val KEY_GOOGLE_ACCOUNT_PHOTO_URL = "google_account_photo_url"
        private const val KEY_GOOGLE_ID_TOKEN = "google_id_token"

        // Raven Mesh & Walkie-Talkie Master Settings
        private const val KEY_RAVEN_MESH_ENABLED = "raven_mesh_enabled"
        private const val KEY_RAVEN_AUDIO_LOUDSPEAKER = "raven_audio_loudspeaker"
        private const val KEY_RAVEN_PTT_AUTO_PLAY = "raven_ptt_auto_play"
        private const val KEY_RAVEN_SEND_READ_RECEIPTS = "raven_send_read_receipts"
        private const val KEY_RAVEN_RADIO_CHANNEL = "raven_radio_channel"
        private const val KEY_RAVEN_RADIO_SQUELCH_MUTE = "raven_radio_squelch_mute"
        private const val KEY_RAVEN_NOTIFICATION_SOUND = "raven_notification_sound"
        private const val KEY_LAST_RELAY_POLL_TS = "last_relay_poll_ts"
        private const val KEY_PROCESSED_RELAY_IDS = "processed_relay_ids"

        // O.D.I.N. Persistent History & Memory Capsule Settings
        private const val KEY_ODIN_SAVE_HISTORY = "odin_save_history"
        private const val KEY_MEMORY_CAPSULE_ENABLED = "memory_capsule_enabled"
    }

    private val _googleDriveAccount = MutableStateFlow(prefs.getString(KEY_GOOGLE_DRIVE_ACCOUNT, null))
    val googleDriveAccount: StateFlow<String?> = _googleDriveAccount.asStateFlow()

    private val _lastRewardAdTime = MutableStateFlow(prefs.getLong(KEY_LAST_REWARD_AD_TIME, 0L))
    val lastRewardAdTime: StateFlow<Long> = _lastRewardAdTime.asStateFlow()

    // Default 3 welcome AI credits for new users
    private val _aiCredits = MutableStateFlow(prefs.getInt(KEY_AI_CREDITS, 3))
    val aiCredits: StateFlow<Int> = _aiCredits.asStateFlow()

    // Default 5 welcome Ansuz Runes for Huginn voice & thought
    private val _ansuzCredits = MutableStateFlow(prefs.getInt(KEY_ANSUZ_CREDITS, 5))
    val ansuzCredits: StateFlow<Int> = _ansuzCredits.asStateFlow()

    // Default 3 welcome Auto-Backup credits for new users
    private val _autoBackupCredits = MutableStateFlow(prefs.getInt(KEY_AUTO_BACKUP_CREDITS, 3))
    val autoBackupCredits: StateFlow<Int> = _autoBackupCredits.asStateFlow()

    // Punch-card progress towards next Auto-Backup credit (0, 1, or 2 out of 3)
    private val _backupAdProgress = MutableStateFlow(prefs.getInt(KEY_BACKUP_AD_PROGRESS, 0))
    val backupAdProgress: StateFlow<Int> = _backupAdProgress.asStateFlow()

    // Huginn 72-hour lease expiry timestamp (supports infinite stacking)
    private val _huginnLeaseExpiryMs = MutableStateFlow(prefs.getLong(KEY_HUGINN_LEASE_EXPIRY_MS, 0L))
    val huginnLeaseExpiryMs: StateFlow<Long> = _huginnLeaseExpiryMs.asStateFlow()

    // Huginn 5-ad punch card progress (0 to 5)
    private val _huginnAdProgress = MutableStateFlow(prefs.getInt(KEY_HUGINN_AD_PROGRESS, 0))
    val huginnAdProgress: StateFlow<Int> = _huginnAdProgress.asStateFlow()

    // Standalone $0.99 permanent Huginn unlock
    private val _isHuginnPermanentUnlocked = MutableStateFlow(prefs.getBoolean(KEY_HUGINN_PERMANENT_UNLOCKED, false))
    val isHuginnPermanentUnlocked: StateFlow<Boolean> = _isHuginnPermanentUnlocked.asStateFlow()

    // Standalone $0.99 permanent O.D.I.N. unlock (Eye of Mímir)
    private val _isOdinPermanentUnlocked = MutableStateFlow(prefs.getBoolean(KEY_ODIN_PERMANENT_UNLOCKED, false))
    val isOdinPermanentUnlocked: StateFlow<Boolean> = _isOdinPermanentUnlocked.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false))
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _autoBackupFrequencyDays = MutableStateFlow(prefs.getInt(KEY_AUTO_BACKUP_FREQUENCY_DAYS, 1))
    val autoBackupFrequencyDays: StateFlow<Int> = _autoBackupFrequencyDays.asStateFlow()

    private val _autoBackupRequiresWifi = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP_REQUIRES_WIFI, true))
    val autoBackupRequiresWifi: StateFlow<Boolean> = _autoBackupRequiresWifi.asStateFlow()

    private val _lastAutoBackupTimestamp = MutableStateFlow(prefs.getLong(KEY_LAST_AUTO_BACKUP_TIMESTAMP, 0L))
    val lastAutoBackupTimestamp: StateFlow<Long> = _lastAutoBackupTimestamp.asStateFlow()

    private val _autoBackupAllThreads = MutableStateFlow(prefs.getBoolean(KEY_AUTO_BACKUP_ALL_THREADS, true))
    val autoBackupAllThreads: StateFlow<Boolean> = _autoBackupAllThreads.asStateFlow()

    private val _autoBackupSelectedThreadIds = MutableStateFlow(
        prefs.getStringSet(KEY_AUTO_BACKUP_SELECTED_THREAD_IDS, emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()
    )
    val autoBackupSelectedThreadIds: StateFlow<Set<Long>> = _autoBackupSelectedThreadIds.asStateFlow()

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

    private val _geminiModel = MutableStateFlow(prefs.getString(KEY_GEMINI_MODEL, "gemini-flash-lite-latest") ?: "gemini-flash-lite-latest")
    val geminiModel: StateFlow<String> = _geminiModel.asStateFlow()

    private val _openaiApiKey = MutableStateFlow(prefs.getString(KEY_OPENAI_API_KEY, "") ?: "")
    val openaiApiKey: StateFlow<String> = _openaiApiKey.asStateFlow()

    private val _openaiModel = MutableStateFlow(prefs.getString(KEY_OPENAI_MODEL, "gpt-4o-mini") ?: "gpt-4o-mini")
    val openaiModel: StateFlow<String> = _openaiModel.asStateFlow()

    private val _ollamaEndpoint = MutableStateFlow(prefs.getString(KEY_OLLAMA_ENDPOINT, "http://100.0.0.1:11434") ?: "http://100.0.0.1:11434")
    val ollamaEndpoint: StateFlow<String> = _ollamaEndpoint.asStateFlow()

    private val _ollamaModel = MutableStateFlow(prefs.getString(KEY_OLLAMA_MODEL, "qwen2.5:14b") ?: "qwen2.5:14b")
    val ollamaModel: StateFlow<String> = _ollamaModel.asStateFlow()

    private val _odinTemperature = MutableStateFlow(prefs.getFloat(KEY_ODIN_TEMPERATURE, 0.2f))
    val odinTemperature: StateFlow<Float> = _odinTemperature.asStateFlow()

    private val _odinTopP = MutableStateFlow(prefs.getFloat(KEY_ODIN_TOP_P, 0.9f))
    val odinTopP: StateFlow<Float> = _odinTopP.asStateFlow()

    private val _odinNumCtx = MutableStateFlow(prefs.getInt(KEY_ODIN_NUM_CTX, 8192))
    val odinNumCtx: StateFlow<Int> = _odinNumCtx.asStateFlow()

    private val _odinCustomSystemPrompt = MutableStateFlow(prefs.getString(KEY_ODIN_CUSTOM_SYSTEM_PROMPT, "") ?: "")
    val odinCustomSystemPrompt: StateFlow<String> = _odinCustomSystemPrompt.asStateFlow()

    private val _huginnComposerEnabled = MutableStateFlow(prefs.getBoolean(KEY_HUGINN_COMPOSER_ENABLED, true))
    val huginnComposerEnabled: StateFlow<Boolean> = _huginnComposerEnabled.asStateFlow()

    private val _animationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ANIMATIONS_ENABLED, true))
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    private val _confirmRuneSpend = MutableStateFlow(prefs.getBoolean(KEY_CONFIRM_RUNE_SPEND, true))
    val confirmRuneSpend: StateFlow<Boolean> = _confirmRuneSpend.asStateFlow()

    private val _ravenTransportMode = MutableStateFlow(
        prefs.getString(KEY_RAVEN_TRANSPORT_MODE, com.messagebackup.app.domain.mesh.RavenTransportMode.AUTO.name)
            ?.let { runCatching { com.messagebackup.app.domain.mesh.RavenTransportMode.valueOf(it) }.getOrNull() }
            ?: com.messagebackup.app.domain.mesh.RavenTransportMode.AUTO
    )
    val ravenTransportMode: StateFlow<com.messagebackup.app.domain.mesh.RavenTransportMode> = _ravenTransportMode.asStateFlow()

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

    private val _alwaysPromptExportOptions = MutableStateFlow(prefs.getBoolean(KEY_ALWAYS_PROMPT_EXPORT_OPTIONS, true))
    val alwaysPromptExportOptions: StateFlow<Boolean> = _alwaysPromptExportOptions.asStateFlow()

    private val _hasReviewedApp = MutableStateFlow(prefs.getBoolean(KEY_HAS_REVIEWED_APP, false))
    val hasReviewedApp: StateFlow<Boolean> = _hasReviewedApp.asStateFlow()

    private val _isOnboardingCompleted = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDING_COMPLETED, false))
    val isOnboardingCompleted: StateFlow<Boolean> = _isOnboardingCompleted.asStateFlow()

    private val _initialMessageBatchSize = MutableStateFlow(prefs.getInt(KEY_INITIAL_MESSAGE_BATCH_SIZE, 50).coerceIn(10, 75))
    val initialMessageBatchSize: StateFlow<Int> = _initialMessageBatchSize.asStateFlow()

    private val _scrollMessageBatchSize = MutableStateFlow(prefs.getInt(KEY_SCROLL_MESSAGE_BATCH_SIZE, 50).coerceIn(10, 75))
    val scrollMessageBatchSize: StateFlow<Int> = _scrollMessageBatchSize.asStateFlow()

    private val _fastPaginationEnabled = MutableStateFlow(prefs.getBoolean(KEY_FAST_PAGINATION_ENABLED, true))
    val fastPaginationEnabled: StateFlow<Boolean> = _fastPaginationEnabled.asStateFlow()

    private val _googleAccountEmail = MutableStateFlow(prefs.getString(KEY_GOOGLE_ACCOUNT_EMAIL, "") ?: "")
    val googleAccountEmail: StateFlow<String> = _googleAccountEmail.asStateFlow()

    private val _googleAccountName = MutableStateFlow(prefs.getString(KEY_GOOGLE_ACCOUNT_NAME, "") ?: "")
    val googleAccountName: StateFlow<String> = _googleAccountName.asStateFlow()

    private val _googleAccountPhotoUrl = MutableStateFlow(prefs.getString(KEY_GOOGLE_ACCOUNT_PHOTO_URL, "") ?: "")
    val googleAccountPhotoUrl: StateFlow<String> = _googleAccountPhotoUrl.asStateFlow()

    private val _googleIdToken = MutableStateFlow(prefs.getString(KEY_GOOGLE_ID_TOKEN, "") ?: "")
    val googleIdToken: StateFlow<String> = _googleIdToken.asStateFlow()

    private val _ravenMeshEnabled = MutableStateFlow(prefs.getBoolean(KEY_RAVEN_MESH_ENABLED, true))
    val ravenMeshEnabled: StateFlow<Boolean> = _ravenMeshEnabled.asStateFlow()

    // Default is true (Loudspeaker) per user preference
    private val _ravenAudioLoudspeaker = MutableStateFlow(prefs.getBoolean(KEY_RAVEN_AUDIO_LOUDSPEAKER, true))
    val ravenAudioLoudspeaker: StateFlow<Boolean> = _ravenAudioLoudspeaker.asStateFlow()

    // Default is true (Walkie-Talkie live auto-play mode; false = Silent / play-button only)
    private val _ravenPttAutoPlay = MutableStateFlow(prefs.getBoolean(KEY_RAVEN_PTT_AUTO_PLAY, true))
    val ravenPttAutoPlay: StateFlow<Boolean> = _ravenPttAutoPlay.asStateFlow()

    // Default is true (Send seen/read reports to peers)
    private val _ravenSendReadReceipts = MutableStateFlow(prefs.getBoolean(KEY_RAVEN_SEND_READ_RECEIPTS, true))
    val ravenSendReadReceipts: StateFlow<Boolean> = _ravenSendReadReceipts.asStateFlow()

    // Tactical Walkie-Talkie Channel (Default: Channel 1 Bifrost)
    private val _ravenRadioChannel = MutableStateFlow(prefs.getInt(KEY_RAVEN_RADIO_CHANNEL, 1))
    val ravenRadioChannel: StateFlow<Int> = _ravenRadioChannel.asStateFlow()

    // Radio Squelch Mute (true = complete radio silence, false = normal monitoring)
    private val _ravenRadioSquelchMute = MutableStateFlow(prefs.getBoolean(KEY_RAVEN_RADIO_SQUELCH_MUTE, false))
    val ravenRadioSquelchMute: StateFlow<Boolean> = _ravenRadioSquelchMute.asStateFlow()

    // Notification Sound & Heads-up Tones for The Unkindness
    private val _ravenNotificationSound = MutableStateFlow(prefs.getBoolean(KEY_RAVEN_NOTIFICATION_SOUND, true))
    val ravenNotificationSound: StateFlow<Boolean> = _ravenNotificationSound.asStateFlow()

    private val _odinSaveHistory = MutableStateFlow(prefs.getBoolean(KEY_ODIN_SAVE_HISTORY, true))
    val odinSaveHistory: StateFlow<Boolean> = _odinSaveHistory.asStateFlow()

    private val _memoryCapsuleEnabled = MutableStateFlow(prefs.getBoolean(KEY_MEMORY_CAPSULE_ENABLED, true))
    val memoryCapsuleEnabled: StateFlow<Boolean> = _memoryCapsuleEnabled.asStateFlow()

    fun setRavenMeshEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RAVEN_MESH_ENABLED, enabled).apply()
        _ravenMeshEnabled.value = enabled
    }

    fun setRavenAudioLoudspeaker(loudspeaker: Boolean) {
        prefs.edit().putBoolean(KEY_RAVEN_AUDIO_LOUDSPEAKER, loudspeaker).apply()
        _ravenAudioLoudspeaker.value = loudspeaker
    }

    fun setRavenPttAutoPlay(autoPlay: Boolean) {
        prefs.edit().putBoolean(KEY_RAVEN_PTT_AUTO_PLAY, autoPlay).apply()
        _ravenPttAutoPlay.value = autoPlay
    }

    fun setRavenSendReadReceipts(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RAVEN_SEND_READ_RECEIPTS, enabled).apply()
        _ravenSendReadReceipts.value = enabled
    }

    fun setRavenRadioChannel(channel: Int) {
        val clamped = channel.coerceIn(1, 16)
        prefs.edit().putInt(KEY_RAVEN_RADIO_CHANNEL, clamped).apply()
        _ravenRadioChannel.value = clamped
    }

    fun setRavenRadioSquelchMute(muted: Boolean) {
        prefs.edit().putBoolean(KEY_RAVEN_RADIO_SQUELCH_MUTE, muted).apply()
        _ravenRadioSquelchMute.value = muted
    }

    fun setRavenNotificationSound(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_RAVEN_NOTIFICATION_SOUND, enabled).apply()
        _ravenNotificationSound.value = enabled
    }

    fun setOdinSaveHistory(save: Boolean) {
        prefs.edit().putBoolean(KEY_ODIN_SAVE_HISTORY, save).apply()
        _odinSaveHistory.value = save
    }

    fun setMemoryCapsuleEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MEMORY_CAPSULE_ENABLED, enabled).apply()
        _memoryCapsuleEnabled.value = enabled
    }

    fun getLastRelayPollTimestampSeconds(): Long {
        val saved = prefs.getLong(KEY_LAST_RELAY_POLL_TS, 0L)
        return if (saved > 0L) saved else (System.currentTimeMillis() / 1000L) - 300L
    }

    fun setLastRelayPollTimestampSeconds(tsSec: Long) {
        val current = prefs.getLong(KEY_LAST_RELAY_POLL_TS, 0L)
        if (tsSec > current) {
            prefs.edit().putLong(KEY_LAST_RELAY_POLL_TS, tsSec).apply()
        }
    }

    fun getProcessedRelayIds(): Set<String> {
        return prefs.getStringSet(KEY_PROCESSED_RELAY_IDS, emptySet()) ?: emptySet()
    }

    fun addProcessedRelayId(id: String) {
        val current = (prefs.getStringSet(KEY_PROCESSED_RELAY_IDS, emptySet()) ?: emptySet()).toMutableSet()
        if (current.size > 400) {
            val trimmed = current.toList().takeLast(250).toMutableSet()
            trimmed.add(id)
            prefs.edit().putStringSet(KEY_PROCESSED_RELAY_IDS, trimmed).apply()
        } else {
            current.add(id)
            prefs.edit().putStringSet(KEY_PROCESSED_RELAY_IDS, current).apply()
        }
    }

    fun setFastPaginationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FAST_PAGINATION_ENABLED, enabled).apply()
        _fastPaginationEnabled.value = enabled
    }

    fun setInitialMessageBatchSize(size: Int) {
        val clamped = size.coerceIn(10, 75)
        prefs.edit().putInt(KEY_INITIAL_MESSAGE_BATCH_SIZE, clamped).apply()
        _initialMessageBatchSize.value = clamped
    }

    fun setScrollMessageBatchSize(size: Int) {
        val clamped = size.coerceIn(10, 75)
        prefs.edit().putInt(KEY_SCROLL_MESSAGE_BATCH_SIZE, clamped).apply()
        _scrollMessageBatchSize.value = clamped
    }

    fun setOnboardingCompleted(completed: Boolean = true) {
        prefs.edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
        _isOnboardingCompleted.value = completed
    }

    fun getExportSuccessCount(): Int = prefs.getInt(KEY_EXPORT_SUCCESS_COUNT, 0)

    fun incrementExportSuccessCount(): Int {
        val next = getExportSuccessCount() + 1
        prefs.edit().putInt(KEY_EXPORT_SUCCESS_COUNT, next).apply()
        return next
    }

    fun markAppReviewed(): Boolean {
        if (!_hasReviewedApp.value) {
            prefs.edit().putBoolean(KEY_HAS_REVIEWED_APP, true).apply()
            _hasReviewedApp.value = true
            // Grant +5 O.D.I.N. AI credits & +1 Auto-backup credit as a review thank-you
            addAiCredits(5)
            addAutoBackupCredits(1)
            return true
        }
        return false
    }

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

    fun setAnsuzCredits(credits: Int) {
        val safe = credits.coerceAtLeast(0)
        prefs.edit().putInt(KEY_ANSUZ_CREDITS, safe).apply()
        _ansuzCredits.value = safe
    }

    fun addAnsuzCredits(amount: Int) {
        setAnsuzCredits(_ansuzCredits.value + amount)
    }

    fun consumeAnsuzCredit(): Boolean {
        if (_ansuzCredits.value > 0) {
            setAnsuzCredits(_ansuzCredits.value - 1)
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

    /**
     * Records timestamp when a rewarded ad is completed to enforce cooldown.
     */
    fun recordRewardAdWatched() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_REWARD_AD_TIME, now).apply()
        _lastRewardAdTime.value = now
    }

    /**
     * Returns remaining cooldown in seconds (60 seconds cooldown), or 0 if ready.
     */
    fun getRewardCooldownSeconds(): Long {
        val lastTime = _lastRewardAdTime.value
        val diff = (System.currentTimeMillis() - lastTime) / 1000L
        val remaining = 60L - diff
        return if (remaining > 0L) remaining else 0L
    }

    fun isHuginnActive(isAdsRemoved: Boolean = false): Boolean {
        if (isAdsRemoved || _isHuginnPermanentUnlocked.value) return true
        return System.currentTimeMillis() < _huginnLeaseExpiryMs.value
    }

    fun getHuginnRemainingTimeMs(): Long {
        val remaining = _huginnLeaseExpiryMs.value - System.currentTimeMillis()
        return if (remaining > 0L) remaining else 0L
    }

    /**
     * Stacking lease mechanism: Adds 3 days (72 hours) to the Huginn lease.
     * If already active, the new 72 hours are appended directly on top of the existing balance.
     * Returns the new expiration timestamp in milliseconds.
     */
    fun addHuginnLeaseDays(days: Int = 3): Long {
        val durationMillis = days * 24L * 60L * 60L * 1000L
        val currentExpiry = _huginnLeaseExpiryMs.value
        val now = System.currentTimeMillis()
        val baseTime = if (currentExpiry > now) currentExpiry else now
        val newExpiry = baseTime + durationMillis
        prefs.edit().putLong(KEY_HUGINN_LEASE_EXPIRY_MS, newExpiry).apply()
        _huginnLeaseExpiryMs.value = newExpiry
        return newExpiry
    }

    /**
     * Records a watched ad for Huginn punch card (5 ads = +3 days lease stacked).
     * @return true if 5th punch unlocked/stacked 3 days, false if punch was recorded (1..4 of 5).
     */
    fun recordHuginnAdWatched(): Boolean {
        val nextProgress = _huginnAdProgress.value + 1
        return if (nextProgress >= 5) {
            prefs.edit().putInt(KEY_HUGINN_AD_PROGRESS, 0).apply()
            _huginnAdProgress.value = 0
            addHuginnLeaseDays(3)
            true
        } else {
            prefs.edit().putInt(KEY_HUGINN_AD_PROGRESS, nextProgress).apply()
            _huginnAdProgress.value = nextProgress
            false
        }
    }

    fun setHuginnPermanentUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_HUGINN_PERMANENT_UNLOCKED, unlocked).apply()
        _isHuginnPermanentUnlocked.value = unlocked
    }

    fun setOdinPermanentUnlocked(unlocked: Boolean) {
        prefs.edit().putBoolean(KEY_ODIN_PERMANENT_UNLOCKED, unlocked).apply()
        _isOdinPermanentUnlocked.value = unlocked
    }

    fun isOdinActive(isAdsRemoved: Boolean = false): Boolean {
        if (isAdsRemoved || _isOdinPermanentUnlocked.value) return true
        return _aiCredits.value > 0
    }

    /**
     * Increments punch-card progress towards next Auto-Backup credit (3 ads = 1 credit).
     * @return true if 3rd ad was reached and +1 credit was awarded, false if progress incremented (1/3 or 2/3).
     */
    fun recordBackupAdWatched(): Boolean {
        val nextProgress = _backupAdProgress.value + 1
        return if (nextProgress >= 3) {
            prefs.edit().putInt(KEY_BACKUP_AD_PROGRESS, 0).apply()
            _backupAdProgress.value = 0
            addAutoBackupCredits(1)
            true
        } else {
            prefs.edit().putInt(KEY_BACKUP_AD_PROGRESS, nextProgress).apply()
            _backupAdProgress.value = nextProgress
            false
        }
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

    fun setAutoBackupFrequencyDays(days: Int) {
        val safe = days.coerceIn(1, 30)
        prefs.edit().putInt(KEY_AUTO_BACKUP_FREQUENCY_DAYS, safe).apply()
        _autoBackupFrequencyDays.value = safe
    }

    fun setAutoBackupRequiresWifi(requires: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_REQUIRES_WIFI, requires).apply()
        _autoBackupRequiresWifi.value = requires
    }

    fun setAutoBackupAllThreads(all: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ALL_THREADS, all).apply()
        _autoBackupAllThreads.value = all
    }

    fun setAutoBackupSelectedThreadIds(threadIds: Set<Long>) {
        val stringSet = threadIds.map { it.toString() }.toSet()
        prefs.edit().putStringSet(KEY_AUTO_BACKUP_SELECTED_THREAD_IDS, stringSet).apply()
        _autoBackupSelectedThreadIds.value = threadIds
    }

    fun recordAutoBackupCompleted() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_AUTO_BACKUP_TIMESTAMP, now).apply()
        _lastAutoBackupTimestamp.value = now
    }

    fun setAiProvider(provider: String) {
        prefs.edit().putString(KEY_AI_PROVIDER, provider).apply()
        _aiProvider.value = provider
    }

    fun setGeminiApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key.trim()).apply()
        _geminiApiKey.value = key.trim()
    }

    fun setGeminiModel(model: String) {
        val clean = model.trim().removePrefix("models/").ifBlank { "gemini-flash-lite-latest" }
        prefs.edit().putString(KEY_GEMINI_MODEL, clean).apply()
        _geminiModel.value = clean
    }

    fun setOpenaiApiKey(key: String) {
        prefs.edit().putString(KEY_OPENAI_API_KEY, key.trim()).apply()
        _openaiApiKey.value = key.trim()
    }

    fun setOpenaiModel(model: String) {
        val clean = model.trim().ifBlank { "gpt-4o-mini" }
        prefs.edit().putString(KEY_OPENAI_MODEL, clean).apply()
        _openaiModel.value = clean
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

    fun setOdinTemperature(temp: Float) {
        val clamped = temp.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_ODIN_TEMPERATURE, clamped).apply()
        _odinTemperature.value = clamped
    }

    fun setOdinTopP(topP: Float) {
        val clamped = topP.coerceIn(0.0f, 1.0f)
        prefs.edit().putFloat(KEY_ODIN_TOP_P, clamped).apply()
        _odinTopP.value = clamped
    }

    fun setOdinNumCtx(numCtx: Int) {
        val clamped = numCtx.coerceIn(2048, 32768)
        prefs.edit().putInt(KEY_ODIN_NUM_CTX, clamped).apply()
        _odinNumCtx.value = clamped
    }

    fun setOdinCustomSystemPrompt(prompt: String) {
        prefs.edit().putString(KEY_ODIN_CUSTOM_SYSTEM_PROMPT, prompt.trim()).apply()
        _odinCustomSystemPrompt.value = prompt.trim()
    }

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS_ENABLED, enabled).apply()
        _animationsEnabled.value = enabled
    }

    fun setConfirmRuneSpend(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONFIRM_RUNE_SPEND, enabled).apply()
        _confirmRuneSpend.value = enabled
    }

    fun setRavenTransportMode(mode: com.messagebackup.app.domain.mesh.RavenTransportMode) {
        prefs.edit().putString(KEY_RAVEN_TRANSPORT_MODE, mode.name).apply()
        _ravenTransportMode.value = mode
    }

    fun resetOdinSystemPrompt() {
        prefs.edit().remove(KEY_ODIN_CUSTOM_SYSTEM_PROMPT).apply()
        _odinCustomSystemPrompt.value = ""
    }

    fun setHuginnComposerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HUGINN_COMPOSER_ENABLED, enabled).apply()
        _huginnComposerEnabled.value = enabled
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

    fun setAlwaysPromptExportOptions(alwaysPrompt: Boolean) {
        prefs.edit().putBoolean(KEY_ALWAYS_PROMPT_EXPORT_OPTIONS, alwaysPrompt).apply()
        _alwaysPromptExportOptions.value = alwaysPrompt
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

    fun saveCachedStats(stats: com.messagebackup.app.model.ConversationStats) {
        val json = org.json.JSONObject().apply {
            put("totalThreads", stats.totalThreads)
            put("totalMessages", stats.totalMessages)
            put("totalSms", stats.totalSms)
            put("totalMms", stats.totalMms)
            put("totalSent", stats.totalSent)
            put("totalReceived", stats.totalReceived)
            put("totalMedia", stats.totalMediaAttachments)
            put("busiestHour", stats.busiestHour ?: -1)
            put("busiestDay", stats.busiestDayOfWeek ?: "")

            val contactsArray = org.json.JSONArray()
            for (c in stats.topContacts.take(3)) {
                val cObj = org.json.JSONObject().apply {
                    put("name", c.displayName)
                    put("count", c.count)
                }
                contactsArray.put(cObj)
            }
            put("topContacts", contactsArray)
        }
        prefs.edit().putString(KEY_CACHED_STATS, json.toString()).apply()
    }

    fun getCachedStats(): com.messagebackup.app.model.ConversationStats? {
        val raw = prefs.getString(KEY_CACHED_STATS, null) ?: return null
        return try {
            val json = org.json.JSONObject(raw)
            val topContacts = mutableListOf<com.messagebackup.app.model.ContactMessageCount>()
            val cArray = json.optJSONArray("topContacts")
            if (cArray != null) {
                for (i in 0 until cArray.length()) {
                    val obj = cArray.getJSONObject(i)
                    topContacts.add(
                        com.messagebackup.app.model.ContactMessageCount(
                            displayName = obj.optString("name", "Unknown"),
                            address = "",
                            count = obj.optInt("count", 0),
                            percentage = 0f
                        )
                    )
                }
            }
            val hour = json.optInt("busiestHour", -1)
            val day = json.optString("busiestDay", "").ifBlank { null }
            com.messagebackup.app.model.ConversationStats(
                totalThreads = json.optInt("totalThreads", 0),
                totalMessages = json.optLong("totalMessages", 0L),
                totalSms = json.optLong("totalSms", 0L),
                totalMms = json.optLong("totalMms", 0L),
                totalSent = json.optLong("totalSent", 0L),
                totalReceived = json.optLong("totalReceived", 0L),
                totalMediaAttachments = json.optLong("totalMedia", 0L),
                topContacts = topContacts,
                busiestHour = if (hour != -1) hour else null,
                busiestDayOfWeek = day
            )
        } catch (e: Exception) {
            null
        }
    }

    fun setGoogleAccount(email: String, displayName: String, photoUrl: String, idToken: String) {
        prefs.edit()
            .putString(KEY_GOOGLE_ACCOUNT_EMAIL, email)
            .putString(KEY_GOOGLE_ACCOUNT_NAME, displayName)
            .putString(KEY_GOOGLE_ACCOUNT_PHOTO_URL, photoUrl)
            .putString(KEY_GOOGLE_ID_TOKEN, idToken)
            .apply()
        _googleAccountEmail.value = email
        _googleAccountName.value = displayName
        _googleAccountPhotoUrl.value = photoUrl
        _googleIdToken.value = idToken
    }

    fun clearGoogleAccount() {
        prefs.edit()
            .remove(KEY_GOOGLE_ACCOUNT_EMAIL)
            .remove(KEY_GOOGLE_ACCOUNT_NAME)
            .remove(KEY_GOOGLE_ACCOUNT_PHOTO_URL)
            .remove(KEY_GOOGLE_ID_TOKEN)
            .apply()
        _googleAccountEmail.value = ""
        _googleAccountName.value = ""
        _googleAccountPhotoUrl.value = ""
        _googleIdToken.value = ""
    }

    private fun hashPinSha256(pin: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(("OdinVaultPinSalt:" + pin).toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
