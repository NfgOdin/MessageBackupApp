package com.messagebackup.app.domain.export

import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates human-readable plain text (.txt) exports of conversation threads.
 */
class PlainTextFormatter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun formatConversation(
        thread: ConversationThread,
        messages: List<MessageItem>
    ): String {
        val sb = StringBuilder()
        sb.append("=====================================================\n")
        sb.append("CONVERSATION BACKUP: ${thread.displayName}\n")
        sb.append("Address: ${thread.address}\n")
        if (!thread.contactName.isNullOrBlank()) {
            sb.append("Contact Name: ${thread.contactName}\n")
        }
        sb.append("Total Messages: ${messages.size}\n")
        sb.append("Exported Date: ${dateFormatter.format(Instant.now())}\n")
        sb.append("=====================================================\n\n")

        for (msg in messages) {
            val time = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
            val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender
            sb.append("[$time] $sender:\n")
            if (msg.body.isNotBlank()) {
                sb.append("  ${msg.body}\n")
            }
            for (att in msg.attachments) {
                sb.append("  [Attachment: ${att.fileName} (${att.contentType})]\n")
            }
            sb.append("\n")
        }

        return sb.toString()
    }
}
