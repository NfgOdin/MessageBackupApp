package com.messagebackup.app.domain.export

import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates RFC 4180-compliant CSV files ideal for Excel, legal discovery,
 * spreadsheets, and automated accounting workflows.
 */
class CsvFormatter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    fun formatConversation(
        thread: ConversationThread,
        messages: List<MessageItem>
    ): String {
        val sb = StringBuilder()

        // CSV Header
        sb.append("Message ID,Thread ID,Timestamp,Direction,Sender,Recipient,Body,Attachment Count,Attachments\n")

        for (msg in messages) {
            val formattedTime = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
            val directionStr = if (msg.direction == MessageDirection.OUTGOING) "Outgoing" else "Incoming"
            val attachmentsStr = msg.attachments.joinToString("; ") { "${it.fileName} (${it.contentType})" }

            sb.append("${msg.messageId},")
            sb.append("${thread.threadId},")
            sb.append("\"$formattedTime\",")
            sb.append("\"$directionStr\",")
            sb.append("\"${escapeCsv(msg.sender)}\",")
            sb.append("\"${escapeCsv(msg.recipient)}\",")
            sb.append("\"${escapeCsv(msg.body)}\",")
            sb.append("${msg.attachments.size},")
            sb.append("\"${escapeCsv(attachmentsStr)}\"\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(text: String): String {
        return text.replace("\"", "\"\"")
    }
}
