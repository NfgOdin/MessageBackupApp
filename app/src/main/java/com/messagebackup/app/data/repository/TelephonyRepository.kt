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
     * Retrieves all messages (SMS + MMS) for a specific thread, sorted ascending by timestamp.
     *
     * @param threadId Unique thread ID.
     * @param resolveContacts If true, resolves address book names for senders.
     * @return List of MessageItem instances.
     */
    suspend fun getMessagesForThread(threadId: Long, resolveContacts: Boolean): List<MessageItem>

    /**
     * Reads the binary stream of an MMS attachment given its content URI.
     *
     * @param contentUri Content URI (e.g. content://mms/part/123)
     * @return ByteArray containing the media file contents, or null on error.
     */
    suspend fun getAttachmentBytes(contentUri: android.net.Uri): ByteArray?
}
