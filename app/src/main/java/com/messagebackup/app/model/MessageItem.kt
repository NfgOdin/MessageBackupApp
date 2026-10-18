package com.messagebackup.app.model

import android.net.Uri

/**
 * Indicates whether a message is an SMS (text only) or MMS (multimedia).
 */
enum class MessageType {
    SMS,
    MMS
}

/**
 * Direction of a message.
 */
enum class MessageDirection {
    INCOMING,
    OUTGOING
}

/**
 * Represents a multimedia attachment found in an MMS message.
 *
 * @property partId Internal MMS part ID.
 * @property contentType MIME type (e.g. image/jpeg, image/png, audio/amr).
 * @property uriString Content URI string referencing the part's binary stream (content://mms/part/<id>).
 * @property fileName Suggested file name for export (e.g. mms_part_123.jpg).
 */
data class MediaAttachment(
    val partId: Long,
    val contentType: String,
    val uriString: String,
    val fileName: String
) {
    val contentUri: Uri
        get() = Uri.parse(uriString)
}

/**
 * Represents a single parsed SMS or MMS message within a conversation thread.
 *
 * @property messageId Telephony ID of the message.
 * @property threadId Associated conversation thread ID.
 * @property type Whether SMS or MMS.
 * @property direction INCOMING or OUTGOING.
 * @property sender Display name or phone number of the sender.
 * @property recipient Display name or phone number of the recipient.
 * @property body Text content of the message.
 * @property timestamp Unix epoch timestamp in milliseconds.
 * @property attachments List of multimedia attachments (images/audio) associated with this message.
 */
data class MessageItem(
    val messageId: Long,
    val threadId: Long,
    val type: MessageType,
    val direction: MessageDirection,
    val sender: String,
    val recipient: String,
    val body: String,
    val timestamp: Long,
    val attachments: List<MediaAttachment> = emptyList()
)
