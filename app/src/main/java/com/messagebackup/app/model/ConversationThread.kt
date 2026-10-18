package com.messagebackup.app.model

/**
 * Represents a single SMS or MMS conversation thread.
 *
 * @property threadId Unique Android telephony thread ID.
 * @property address Recipient/sender address or raw phone number.
 * @property contactName Resolved contact display name from address book, or null if unknown or permission denied.
 * @property snippet Preview of the most recent message in the thread.
 * @property timestamp Unix epoch timestamp in milliseconds of the last interaction.
 * @property messageCount Total number of messages in the thread.
 * @property hasAttachment True if any message in the thread contains an MMS attachment.
 */
data class ConversationThread(
    val threadId: Long,
    val address: String,
    val contactName: String?,
    val snippet: String,
    val timestamp: Long,
    val messageCount: Int,
    val hasAttachment: Boolean
) {
    /**
     * User-facing display title: prefer contact name if resolved, otherwise address.
     */
    val displayName: String
        get() = if (!contactName.isNullOrBlank()) contactName else address.ifBlank { "Unknown" }
}
