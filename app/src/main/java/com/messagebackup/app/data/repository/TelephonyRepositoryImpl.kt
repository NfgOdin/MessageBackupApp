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
    private val contactCache = mutableMapOf<String, String>()

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
                    val address = resolveAddressForThread(threadId, recipientIds)
                    val contactName = if (resolveContacts && address.isNotBlank()) {
                        resolveContactName(address)
                    } else null

                    // Check if thread contains MMS attachments
                    val hasAttachment = checkThreadHasMms(threadId)

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

    override suspend fun getMessagesForThread(
        threadId: Long,
        resolveContacts: Boolean
    ): List<MessageItem> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<MessageItem>()

        // 1. Fetch SMS messages for this thread
        fetchSmsMessages(threadId, resolveContacts, messages)

        // 2. Fetch MMS messages for this thread
        fetchMmsMessages(threadId, resolveContacts, messages)

        // Sort ascending by timestamp so export reads in chronological order
        messages.sortBy { it.timestamp }
        messages
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
        outList: MutableList<MessageItem>
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

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                smsUri,
                projection,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = it.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addressIdx = it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = it.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = it.getColumnIndexOrThrow(Telephony.Sms.TYPE)

                while (it.moveToNext()) {
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
        outList: MutableList<MessageItem>
    ) {
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
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} ASC"
            )

            cursor?.let {
                val idIdx = it.getColumnIndexOrThrow(Telephony.Mms._ID)
                val tIdIdx = it.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIdx = it.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val msgBoxIdx = it.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)

                while (it.moveToNext()) {
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
                            messageId = mmsId,
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

    private fun resolveAddressForThread(threadId: Long, recipientIds: String): String {
        // Query sms table first for this thread to find address
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

        // Fallback: Query canonical addresses table if recipientIds is present
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
        if (contactCache.containsKey(phoneNumber)) {
            return contactCache[phoneNumber]
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

        contactCache[phoneNumber] = phoneNumber
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

    companion object {
        private const val TAG = "TelephonyRepoImpl"
    }
}
