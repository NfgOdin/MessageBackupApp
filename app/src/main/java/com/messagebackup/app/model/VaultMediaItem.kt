package com.messagebackup.app.model

import android.net.Uri

/**
 * High-level classification of MMS multimedia attachments.
 */
enum class MediaCategory(val label: String) {
    ALL("All"),
    IMAGE("Photos"),
    VIDEO("Videos"),
    AUDIO("Audio & Voice"),
    OTHER("Other")
}

/**
 * Model representing an attachment item in the Media Vault across all conversations.
 *
 * @property partId Unique MMS part ID
 * @property messageId Originating MMS message ID
 * @property threadId Associated conversation thread ID
 * @property contactName Sender or conversation partner name
 * @property contactAddress Phone number or recipient address
 * @property timestamp Message timestamp in milliseconds
 * @property contentType MIME type string
 * @property fileName Display filename
 * @property uriString Content URI string
 */
data class VaultMediaItem(
    val partId: Long,
    val messageId: Long,
    val threadId: Long,
    val contactName: String,
    val contactAddress: String,
    val timestamp: Long,
    val contentType: String,
    val fileName: String,
    val uriString: String
) {
    val contentUri: Uri
        get() = Uri.parse(uriString)

    val category: MediaCategory
        get() = when {
            contentType.startsWith("image/", ignoreCase = true) -> MediaCategory.IMAGE
            contentType.startsWith("video/", ignoreCase = true) -> MediaCategory.VIDEO
            contentType.startsWith("audio/", ignoreCase = true) -> MediaCategory.AUDIO
            else -> MediaCategory.OTHER
        }
}
