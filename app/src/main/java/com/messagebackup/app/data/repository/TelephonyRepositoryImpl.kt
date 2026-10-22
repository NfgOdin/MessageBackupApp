package com.messagebackup.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Telephony
import android.util.Log
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MediaAttachment
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Production implementation of TelephonyRepository using Android Telephony Content Providers.
 * Safely accesses content://mms-sms/conversations, content://sms, content://mms, and ContactsContract.
 */
class TelephonyRepositoryImpl(
    private val context: Context
) : TelephonyRepository {

    private val contentResolver: ContentResolver = context.contentResolver
    private val contactCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun getConversationThreads(resolveContacts: Boolean): Flow<List<ConversationThread>> = flow {
        val threads = mutableListOf<ConversationThread>()

        // Query the canonical conversations view
        val conversationUri = Telephony.Threads.CONTENT_URI.buildUpon()
            .appendQueryParameter("simple", "true")
            .build()

        val projection = arrayOf(
            Telephony.Threads._ID,
            Telephony.Threads.SNIPPET,
            Telephony.Threads.MESSAGE_COUNT,
            Telephony.Threads.DATE,
            Telephony.Threads.RECIPIENT_IDS
        )

        // Pre-query threads that have MMS attachments in a single batch query to avoid N queries
        val mmsThreadIds = mutableSetOf<Long>()
        try {
            contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf("DISTINCT ${Telephony.Mms.THREAD_ID}"),
                null,
                null,
                null
            )?.use { mmsCursor ->
                val tidIdx = mmsCursor.getColumnIndex(Telephony.Mms.THREAD_ID)
                if (tidIdx != -1) {
                    while (mmsCursor.moveToNext()) {
                        mmsThreadIds.add(mmsCursor.getLong(tidIdx))
                    }
                }
            }
        } catch (_: Exception) {}

        // Pre-query canonical addresses table in a single batch query to avoid N+1 queries per thread
        val canonicalAddressMap = mutableMapOf<String, String>()
        try {
            contentResolver.query(
                Uri.parse("content://mms-sms/canonical-addresses"),
                arrayOf("_id", "address"),
                null,
                null,
                null
            )?.use { canonCursor ->
                val cIdIdx = canonCursor.getColumnIndex("_id")
                val cAddrIdx = canonCursor.getColumnIndex("address")
                if (cIdIdx != -1 && cAddrIdx != -1) {
                    while (canonCursor.moveToNext()) {
                        val idStr = canonCursor.getLong(cIdIdx).toString()
                        val addr = canonCursor.getString(cAddrIdx)
                        if (!addr.isNullOrBlank()) {
                            canonicalAddressMap[idStr] = addr
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                conversationUri,
                projection,
                null,
                null,
                "${Telephony.Threads.DATE} DESC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Threads._ID)
                val snippetIdx = it.getColumnIndex(Telephony.Threads.SNIPPET)
                val countIdx = it.getColumnIndex(Telephony.Threads.MESSAGE_COUNT)
                val dateIdx = it.getColumnIndex(Telephony.Threads.DATE)
                val recipientIdsIdx = it.getColumnIndex(Telephony.Threads.RECIPIENT_IDS)

                while (it.moveToNext()) {
                    val threadId = it.getLong(idIdx)
                    val snippet = if (snippetIdx != -1) it.getString(snippetIdx) ?: "" else ""
                    val messageCount = if (countIdx != -1) it.getInt(countIdx) else 0
                    val date = if (dateIdx != -1) it.getLong(dateIdx) else System.currentTimeMillis()
                    val recipientIds = if (recipientIdsIdx != -1) it.getString(recipientIdsIdx) ?: "" else ""

                    // Resolve phone number address from canonical recipient IDs
                    val address = resolveAddressForThread(threadId, recipientIds, canonicalAddressMap)
                    val contactName = if (resolveContacts && address.isNotBlank()) {
                        resolveContactName(address)
                    } else null

                    // Check if thread contains MMS attachments via pre-queried set
                    val hasAttachment = mmsThreadIds.contains(threadId)

                    threads.add(
                        ConversationThread(
                            threadId = threadId,
                            address = address,
                            contactName = contactName,
                            snippet = snippet,
                            timestamp = date,
                            messageCount = messageCount,
                            hasAttachment = hasAttachment
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying conversation threads", e)
        } finally {
            cursor?.close()
        }

        emit(threads)
    }.flowOn(Dispatchers.IO)

    override suspend fun searchThreadIdsByMessageBody(query: String): Set<Long> = withContext(Dispatchers.IO) {
        val matchingThreadIds = mutableSetOf<Long>()
        if (query.isBlank()) return@withContext matchingThreadIds

        try {
            // Search SMS body for query substring
            val smsProjection = arrayOf(Telephony.Sms.THREAD_ID)
            val smsSelection = "${Telephony.Sms.BODY} LIKE ?"
            val smsArgs = arrayOf("%$query%")
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                smsProjection,
                smsSelection,
                smsArgs,
                null
            )?.use { c ->
                val tidIdx = c.getColumnIndex(Telephony.Sms.THREAD_ID)
                if (tidIdx != -1) {
                    while (c.moveToNext()) {
                        matchingThreadIds.add(c.getLong(tidIdx))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching SMS by body", e)
        }

        try {
            // Also search MMS text parts
            val mmsPartUri = Uri.parse("content://mms/part")
            val partProjection = arrayOf("mid", "text")
            val partSelection = "(ct LIKE 'text/%' OR ct = 'application/txt' OR ct = 'text/plain') AND text LIKE ?"
            val partArgs = arrayOf("%$query%")
            val matchingMmsIds = mutableSetOf<Long>()

            contentResolver.query(
                mmsPartUri,
                partProjection,
                partSelection,
                partArgs,
                null
            )?.use { c ->
                var midIdx = c.getColumnIndex("mid")
                if (midIdx == -1) midIdx = c.getColumnIndex("m_id")
                if (midIdx != -1) {
                    while (c.moveToNext()) {
                        matchingMmsIds.add(c.getLong(midIdx))
                    }
                }
            }

            // Map MMS IDs back to thread IDs
            if (matchingMmsIds.isNotEmpty()) {
                val mmsIdsChunked = matchingMmsIds.chunked(100)
                for (chunk in mmsIdsChunked) {
                    val inClause = chunk.joinToString(",")
                    contentResolver.query(
                        Telephony.Mms.CONTENT_URI,
                        arrayOf(Telephony.Mms.THREAD_ID),
                        "${Telephony.Mms._ID} IN ($inClause)",
                        null,
                        null
                    )?.use { c ->
                        val tidIdx = c.getColumnIndex(Telephony.Mms.THREAD_ID)
                        if (tidIdx != -1) {
                            while (c.moveToNext()) {
                                matchingThreadIds.add(c.getLong(tidIdx))
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error searching MMS by body", e)
        }

        matchingThreadIds
    }

    override suspend fun getMessagesForThread(
        threadId: Long,
        resolveContacts: Boolean,
        limit: Int?,
        beforeTimestamp: Long?
    ): List<MessageItem> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<MessageItem>()

        // 1. Fetch SMS messages for this thread
        fetchSmsMessages(threadId, resolveContacts, messages, limit, beforeTimestamp)

        // 2. Fetch MMS messages for this thread
        fetchMmsMessages(threadId, resolveContacts, messages, limit, beforeTimestamp)

        // Sort ascending by timestamp so UI reads in chronological order
        messages.sortBy { it.timestamp }
        if (limit != null && messages.size > limit) {
            messages.takeLast(limit)
        } else {
            messages
        }
    }

    override suspend fun getRecentMessagesAcrossConversations(
        maxMessages: Int,
        resolveContacts: Boolean
    ): List<MessageItem> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<MessageItem>()
        val threadNameCache = mutableMapOf<Long, String>()

        // 1. Fetch recent SMS messages across all threads
        try {
            val projection = arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE
            )
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                var count = 0
                while (cursor.moveToNext() && count < maxMessages) {
                    count++
                    val msgId = cursor.getLong(idIdx)
                    val tId = cursor.getLong(threadIdIdx)
                    val rawAddress = cursor.getString(addressIdx) ?: ""
                    val body = cursor.getString(bodyIdx) ?: ""
                    val date = cursor.getLong(dateIdx)
                    val type = cursor.getInt(typeIdx)
                    val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)

                    val contactName = threadNameCache.getOrPut(tId) {
                        if (resolveContacts && rawAddress.isNotBlank()) {
                            resolveContactName(rawAddress) ?: rawAddress
                        } else rawAddress.ifBlank { "Contact" }
                    }

                    val sender = if (isIncoming) contactName else "Me (to $contactName)"
                    val recipient = if (isIncoming) "Me" else contactName

                    messages.add(
                        MessageItem(
                            messageId = msgId,
                            threadId = tId,
                            type = MessageType.SMS,
                            direction = if (isIncoming) MessageDirection.INCOMING else MessageDirection.OUTGOING,
                            sender = sender,
                            recipient = recipient,
                            body = body,
                            timestamp = date,
                            attachments = emptyList()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching global SMS for copilot", e)
        }

        // 2. Fetch recent MMS messages across all threads
        try {
            val projection = arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX
            )
            val mmsLimit = minOf(maxMessages / 4, 25).coerceAtLeast(10)
            contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Mms.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
                val threadIdIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val msgBoxIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)

                var count = 0
                while (cursor.moveToNext() && count < mmsLimit) {
                    count++
                    val mmsId = cursor.getLong(idIdx)
                    val tId = cursor.getLong(threadIdIdx)
                    val rawDate = cursor.getLong(dateIdx)
                    val date = if (rawDate < 100000000000L) rawDate * 1000L else rawDate
                    val msgBox = cursor.getInt(msgBoxIdx)
                    val isIncoming = (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX)

                    val (textBody, attachments) = parseMmsParts(mmsId)
                    if (textBody.isBlank() && attachments.isEmpty()) continue

                    val address = getMmsAddress(mmsId, isSender = isIncoming)
                    val contactName = threadNameCache.getOrPut(tId) {
                        if (resolveContacts && address.isNotBlank()) {
                            resolveContactName(address) ?: address
                        } else address.ifBlank { "Contact" }
                    }

                    val sender = if (isIncoming) contactName else "Me (to $contactName)"
                    val recipient = if (isIncoming) "Me" else contactName

                    messages.add(
                        MessageItem(
                            messageId = mmsId + MMS_ID_OFFSET,
                            threadId = tId,
                            type = MessageType.MMS,
                            direction = if (isIncoming) MessageDirection.INCOMING else MessageDirection.OUTGOING,
                            sender = sender,
                            recipient = recipient,
                            body = textBody,
                            timestamp = date,
                            attachments = attachments
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching global MMS for copilot", e)
        }

        // Sort chronologically ascending for copilot context
        messages.sortBy { it.timestamp }
        if (messages.size > maxMessages) {
            messages.takeLast(maxMessages)
        } else {
            messages
        }
    }

    override suspend fun getAttachmentBytes(contentUri: Uri): ByteArray? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(contentUri)?.use { inputStream: InputStream ->
                val buffer = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                var bytesRead: Int
                while (inputStream.read(chunk).also { bytesRead = it } != -1) {
                    buffer.write(chunk, 0, bytesRead)
                }
                buffer.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read attachment stream for $contentUri", e)
            null
        }
    }

    private fun fetchSmsMessages(
        threadId: Long,
        resolveContacts: Boolean,
        outList: MutableList<MessageItem>,
        limit: Int? = null,
        beforeTimestamp: Long? = null
    ) {
        val smsUri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )

        val (selection, selectionArgs) = if (beforeTimestamp != null) {
            Pair("${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.DATE} < ?", arrayOf(threadId.toString(), beforeTimestamp.toString()))
        } else {
            Pair("${Telephony.Sms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        }

        val sortOrder = if (limit != null) {
            "${Telephony.Sms.DATE} DESC"
        } else {
            "${Telephony.Sms.DATE} ASC"
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                smsUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                var count = 0
                while (it.moveToNext() && (limit == null || count < limit)) {
                    count++
                    val msgId = it.getLong(idIdx)
                    val tId = it.getLong(threadIdIdx)
                    val rawAddress = it.getString(addressIdx) ?: "Unknown"
                    val body = it.getString(bodyIdx) ?: ""
                    val date = it.getLong(dateIdx)
                    val type = it.getInt(typeIdx)

                    val isIncoming = (type == Telephony.Sms.MESSAGE_TYPE_INBOX)
                    val direction = if (isIncoming) MessageDirection.INCOMING else MessageDirection.OUTGOING

                    val resolvedSender = if (isIncoming) {
                        if (resolveContacts) resolveContactName(rawAddress) ?: rawAddress else rawAddress
                    } else "Me"

                    val resolvedRecipient = if (!isIncoming) {
                        if (resolveContacts) resolveContactName(rawAddress) ?: rawAddress else rawAddress
                    } else "Me"

                    outList.add(
                        MessageItem(
                            messageId = msgId,
                            threadId = tId,
                            type = MessageType.SMS,
                            direction = direction,
                            sender = resolvedSender,
                            recipient = resolvedRecipient,
                            body = body,
                            timestamp = date,
                            attachments = emptyList()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching SMS for thread $threadId", e)
        } finally {
            cursor?.close()
        }
    }

    private fun fetchMmsMessages(
        threadId: Long,
        resolveContacts: Boolean,
        outList: MutableList<MessageItem>,
        limit: Int? = null,
        beforeTimestamp: Long? = null
    ) {
        val mmsUri = Telephony.Mms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX
        )

        // Telephony.Mms.DATE is stored in seconds rather than milliseconds
        val (selection, selectionArgs) = if (beforeTimestamp != null) {
            val beforeSec = beforeTimestamp / 1000L
            Pair("${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.DATE} < ?", arrayOf(threadId.toString(), beforeSec.toString()))
        } else {
            Pair("${Telephony.Mms.THREAD_ID} = ?", arrayOf(threadId.toString()))
        }

        val sortOrder = if (limit != null) {
            "${Telephony.Mms.DATE} DESC"
        } else {
            "${Telephony.Mms.DATE} ASC"
        }

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                mmsUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Mms._ID)
                val tIdIdx = it.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val msgBoxIdx = it.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)

                var count = 0
                while (it.moveToNext() && (limit == null || count < limit)) {
                    count++
                    val mmsId = it.getLong(idIdx)
                    val tId = it.getLong(tIdIdx)
                    // Telephony.Mms.DATE is stored in seconds rather than milliseconds
                    val date = it.getLong(dateIdx) * 1000L
                    val msgBox = it.getInt(msgBoxIdx)

                    val isIncoming = (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX)
                    val direction = if (isIncoming) MessageDirection.INCOMING else MessageDirection.OUTGOING

                    // Extract text body and media parts from MMS parts table
                    val (textBody, attachments) = parseMmsParts(mmsId)

                    val senderAddress = getMmsAddress(mmsId, isSender = true)
                    val resolvedSender = if (isIncoming) {
                        if (resolveContacts && senderAddress.isNotBlank()) {
                            resolveContactName(senderAddress) ?: senderAddress
                        } else senderAddress.ifBlank { "Unknown" }
                    } else "Me"

                    val resolvedRecipient = if (!isIncoming) {
                        val recipientAddress = getMmsAddress(mmsId, isSender = false)
                        if (resolveContacts && recipientAddress.isNotBlank()) {
                            resolveContactName(recipientAddress) ?: recipientAddress
                        } else recipientAddress.ifBlank { "Unknown" }
                    } else "Me"

                    outList.add(
                        MessageItem(
                            messageId = mmsId + MMS_ID_OFFSET,
                            threadId = tId,
                            type = MessageType.MMS,
                            direction = direction,
                            sender = resolvedSender,
                            recipient = resolvedRecipient,
                            body = textBody,
                            timestamp = date,
                            attachments = attachments
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching MMS for thread $threadId", e)
        } finally {
            cursor?.close()
        }
    }

    private fun parseMmsParts(mmsId: Long): Pair<String, List<MediaAttachment>> {
        val partUri = Uri.parse("content://mms/$mmsId/part")
        val projection = arrayOf("_id", "ct", "text", "_data", "name", "cl")
        var cursor: Cursor? = null
        val bodyBuilder = StringBuilder()
        val attachments = mutableListOf<MediaAttachment>()

        try {
            cursor = contentResolver.query(partUri, projection, null, null, null)
            cursor?.let {
                val idIdx = it.getColumnIndexOrThrow("_id")
                val ctIdx = it.getColumnIndex("ct")
                val textIdx = it.getColumnIndex("text")
                val dataIdx = it.getColumnIndex("_data")
                val nameIdx = it.getColumnIndex("name")
                val clIdx = it.getColumnIndex("cl")

                while (it.moveToNext()) {
                    val partId = it.getLong(idIdx)
                    val contentType = if (ctIdx != -1) it.getString(ctIdx) ?: "" else ""
                    val text = if (textIdx != -1) it.getString(textIdx) else null
                    val data = if (dataIdx != -1) it.getString(dataIdx) else null
                    val name = if (nameIdx != -1) it.getString(nameIdx) else null
                    val cl = if (clIdx != -1) it.getString(clIdx) else null

                    // Text part
                    if (contentType.equals("text/plain", ignoreCase = true)) {
                        if (text != null) {
                            bodyBuilder.append(text)
                        } else if (data != null) {
                            // If text is stored in file, read the part stream
                            val partContentUri = Uri.parse("content://mms/part/$partId")
                            val textFromStream = readTextFromUri(partContentUri)
                            if (textFromStream.isNotBlank()) {
                                bodyBuilder.append(textFromStream)
                            }
                        }
                    } else if (contentType.startsWith("image/") || contentType.startsWith("audio/") || contentType.startsWith("video/")) {
                        // Media attachment: always prefix with partId to guarantee uniqueness in zip entries
                        val extension = getExtensionForMime(contentType)
                        val rawName = name ?: cl
                        val fileName = if (rawName != null && rawName.isNotBlank()) {
                            "${partId}_$rawName"
                        } else {
                            "mms_part_${partId}.$extension"
                        }

                        attachments.add(
                            MediaAttachment(
                                partId = partId,
                                contentType = contentType,
                                uriString = "content://mms/part/$partId",
                                fileName = fileName
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing parts for MMS $mmsId", e)
        } finally {
            cursor?.close()
        }

        return Pair(bodyBuilder.toString(), attachments)
    }

    private fun readTextFromUri(uri: Uri): String {
        return try {
            contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun getMmsAddress(mmsId: Long, isSender: Boolean): String {
        val uri = Uri.parse("content://mms/$mmsId/addr")
        // Type 137 is FROM, 151 is TO
        val targetType = if (isSender) 137 else 151
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                uri,
                arrayOf("address", "type"),
                "type = ?",
                arrayOf(targetType.toString()),
                null
            )
            if (cursor != null && cursor.moveToFirst()) {
                val addrIdx = cursor.getColumnIndex("address")
                if (addrIdx != -1) {
                    val addr = cursor.getString(addrIdx) ?: ""
                    // Filter out insert-address-token placeholders
                    if (!addr.contains("insert-address-token", ignoreCase = true)) {
                        return addr
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting MMS address for $mmsId", e)
        } finally {
            cursor?.close()
        }
        return ""
    }

    private fun checkThreadHasMms(threadId: Long): Boolean {
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                null
            )
            return (cursor != null && cursor.count > 0)
        } catch (e: Exception) {
            return false
        } finally {
            cursor?.close()
        }
    }

    private fun resolveAddressForThread(
        threadId: Long,
        recipientIds: String,
        canonicalAddressMap: Map<String, String> = emptyMap()
    ): String {
        // 1. Fast O(1) lookup from preloaded canonical-addresses map
        if (recipientIds.isNotBlank() && canonicalAddressMap.isNotEmpty()) {
            val ids = recipientIds.split(" ").filter { it.isNotBlank() }
            for (id in ids) {
                val cachedAddr = canonicalAddressMap[id]
                if (!cachedAddr.isNullOrBlank()) return cachedAddr
            }
        }

        // 2. Fallback: Query sms table for this thread to find address
        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.ADDRESS),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC"
            )
            if (cursor != null && cursor.moveToFirst()) {
                val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                if (addressIdx != -1) {
                    val addr = cursor.getString(addressIdx)
                    if (!addr.isNullOrBlank()) return addr
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up address for thread $threadId", e)
        } finally {
            cursor?.close()
        }

        // 3. Fallback: Query canonical addresses table per ID if recipientIds is present
        if (recipientIds.isNotBlank()) {
            val ids = recipientIds.split(" ").filter { it.isNotBlank() }
            for (id in ids) {
                var c: Cursor? = null
                try {
                    val uri = Uri.parse("content://mms-sms/canonical-address/$id")
                    c = contentResolver.query(uri, null, null, null, null)
                    if (c != null && c.moveToFirst()) {
                        val addr = c.getString(0)
                        if (!addr.isNullOrBlank()) return addr
                    }
                } catch (e: Exception) {
                    // Ignore and try next
                } finally {
                    c?.close()
                }
            }
        }

        return "Thread #$threadId"
    }

    private fun resolveContactName(phoneNumber: String): String? {
        if (phoneNumber.isBlank()) return null
        val cached = contactCache[phoneNumber]
        if (cached != null) {
            return if (cached == NO_CONTACT_SENTINEL) null else cached
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(uri, projection, null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                if (nameIdx != -1) {
                    val name = cursor.getString(nameIdx)
                    if (!name.isNullOrBlank()) {
                        contactCache[phoneNumber] = name
                        return name
                    }
                }
            }
        } catch (e: SecurityException) {
            // Permission denied or revoked
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving contact name for $phoneNumber", e)
        } finally {
            cursor?.close()
        }

        contactCache[phoneNumber] = NO_CONTACT_SENTINEL
        return null
    }

    private fun getExtensionForMime(mimeType: String): String {
        return when (mimeType.lowercase()) {
            "image/jpeg", "image/jpg" -> "jpg"
            "image/png" -> "png"
            "image/gif" -> "gif"
            "image/webp" -> "webp"
            "audio/amr" -> "amr"
            "audio/aac" -> "aac"
            "audio/mp3", "audio/mpeg" -> "mp3"
            "video/mp4" -> "mp4"
            else -> "bin"
        }
    }

    override suspend fun insertSmsMessage(message: MessageItem, address: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = android.content.ContentValues().apply {
                val isOutgoing = (message.direction == MessageDirection.OUTGOING)
                val targetAddress = if (address.isNotBlank()) address else if (isOutgoing) message.recipient else message.sender
                put(Telephony.Sms.ADDRESS, targetAddress)
                put(Telephony.Sms.BODY, message.body)
                put(Telephony.Sms.DATE, message.timestamp)
                put(Telephony.Sms.DATE_SENT, message.timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, if (isOutgoing) Telephony.Sms.MESSAGE_TYPE_SENT else Telephony.Sms.MESSAGE_TYPE_INBOX)
            }
            val uri = contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
            uri != null
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting restored SMS into telephony provider", e)
            false
        }
    }

    override fun isDefaultSmsApp(): Boolean {
        return try {
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
            defaultPackage == context.packageName
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getAllMediaAttachments(
        resolveContacts: Boolean,
        onProgress: ((processed: Int, total: Int) -> Unit)?
    ): List<com.messagebackup.app.model.VaultMediaItem> = withContext(Dispatchers.IO) {
        val vaultItems = mutableListOf<com.messagebackup.app.model.VaultMediaItem>()
        // 1. Query all MMS messages
        val mmsUri = Telephony.Mms.CONTENT_URI
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                mmsUri,
                projection,
                null,
                null,
                "${Telephony.Mms.DATE} DESC"
            )

            cursor?.let {
                val totalCount = it.count
                val idIdx = it.getColumnIndexOrThrow(Telephony.Mms._ID)
                val tIdIdx = it.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val msgBoxIdx = it.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)

                var processedCount = 0
                while (it.moveToNext()) {
                    processedCount++
                    onProgress?.invoke(processedCount, totalCount)

                    val mmsId = it.getLong(idIdx)
                    val threadId = it.getLong(tIdIdx)
                    val date = it.getLong(dateIdx) * 1000L
                    val msgBox = it.getInt(msgBoxIdx)
                    val isIncoming = (msgBox == Telephony.Mms.MESSAGE_BOX_INBOX)

                    val (_, attachments) = parseMmsParts(mmsId)
                    if (attachments.isNotEmpty()) {
                        val senderAddress = getMmsAddress(mmsId, isSender = true)
                        val recipientAddress = if (!isIncoming) getMmsAddress(mmsId, isSender = false) else ""
                        val relevantAddress = if (isIncoming) senderAddress else recipientAddress

                        val contactName = if (resolveContacts && relevantAddress.isNotBlank()) {
                            resolveContactName(relevantAddress) ?: relevantAddress
                        } else if (relevantAddress.isNotBlank()) {
                            relevantAddress
                        } else {
                            if (isIncoming) "Unknown" else "Me"
                        }

                        for (att in attachments) {
                            vaultItems.add(
                                com.messagebackup.app.model.VaultMediaItem(
                                    partId = att.partId,
                                    messageId = mmsId,
                                    threadId = threadId,
                                    contactName = contactName,
                                    contactAddress = relevantAddress,
                                    timestamp = date,
                                    contentType = att.contentType,
                                    fileName = att.fileName,
                                    uriString = att.uriString
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving media vault attachments", e)
        } finally {
            cursor?.close()
        }
        vaultItems
    }

    companion object {
        private const val TAG = "TelephonyRepoImpl"
        private const val NO_CONTACT_SENTINEL = "\u0000"
        private const val MMS_ID_OFFSET = 1_000_000_000_000L
    }
}
