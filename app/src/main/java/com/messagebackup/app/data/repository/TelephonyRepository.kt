package com.messagebackup.app.data.repository

import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageItem
import kotlinx.coroutines.flow.Flow

/**
 * Interface defining operations for querying device conversations and messages.
 */
interface TelephonyRepository {

    /**
     * Retrieves all conversation threads on the device.
     *
     * @param resolveContacts If true, queries contacts database to resolve display names.
     * @return Flow emitting the list of threads sorted chronologically descending.
     */
    fun getConversationThreads(resolveContacts: Boolean): Flow<List<ConversationThread>>

    /**
     * Searches message bodies (both SMS and MMS text parts) across all conversations
     * and returns the set of conversation thread IDs that contain matching messages.
     */
    suspend fun searchThreadIdsByMessageBody(query: String): Set<Long>

    /**
     * Retrieves all messages (SMS + MMS) for a specific thread, sorted ascending by timestamp.
     *
     * @param threadId Unique thread ID.
     * @param resolveContacts If true, resolves address book names for senders.
     * @return List of MessageItem instances.
     */
    suspend fun getMessagesForThread(threadId: Long, resolveContacts: Boolean): List<MessageItem>

    /**
     * Retrieves the most recent messages across all conversation threads.
     * Useful for global grounding and copilot insights.
     */
    suspend fun getRecentMessagesAcrossConversations(maxMessages: Int = 350, resolveContacts: Boolean): List<MessageItem>

    /**
     * Reads the binary stream of an MMS attachment given its content URI.
     *
     * @param contentUri Content URI (e.g. content://mms/part/123)
     * @return ByteArray containing the media file contents, or null on error.
     */
    suspend fun getAttachmentBytes(contentUri: android.net.Uri): ByteArray?

    /**
     * Inserts an SMS message into the device telephony provider.
     * Note: Android requires the app to temporarily be the Default SMS App to write to telephony provider.
     */
    suspend fun insertSmsMessage(message: MessageItem, address: String): Boolean

    /**
     * Checks if this app is currently the default SMS app on the device.
     */
    fun isDefaultSmsApp(): Boolean

    /**
     * Retrieves all multimedia attachments across all MMS conversation threads.
     *
     * @param resolveContacts If true, resolves address book names for senders.
     * @param onProgress Optional callback receiving (processedCount, totalCount) for progress tracking.
     */
    suspend fun getAllMediaAttachments(
        resolveContacts: Boolean,
        onProgress: ((processed: Int, total: Int) -> Unit)? = null
    ): List<com.messagebackup.app.model.VaultMediaItem>
}
