package com.messagebackup.app.domain.export

import android.util.Xml
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.MessageType
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.io.StringWriter

/**
 * Standard SMS Backup & Restore compliant XML generator and parser.
 * Enables migration and cross-compatibility with standard Android SMS backup tools.
 */
class XmlFormatter {

    fun formatConversation(
        thread: ConversationThread,
        messages: List<MessageItem>
    ): String {
        val writer = StringWriter()
        val serializer = Xml.newSerializer()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", true)
        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true)

        serializer.startTag(null, "smses")
        serializer.attribute(null, "count", messages.size.toString())
        serializer.attribute(null, "backup_set", thread.displayName)
        serializer.attribute(null, "backup_date", System.currentTimeMillis().toString())

        for (msg in messages) {
            serializer.startTag(null, "sms")
            serializer.attribute(null, "protocol", "0")
            val address = if (msg.direction == MessageDirection.OUTGOING) msg.recipient else msg.sender
            serializer.attribute(null, "address", address.ifBlank { thread.address })
            serializer.attribute(null, "date", msg.timestamp.toString())
            serializer.attribute(null, "type", if (msg.direction == MessageDirection.INCOMING) "1" else "2")
            serializer.attribute(null, "subject", "null")
            serializer.attribute(null, "body", msg.body)
            serializer.attribute(null, "toa", "null")
            serializer.attribute(null, "sc_toa", "null")
            serializer.attribute(null, "service_center", "null")
            serializer.attribute(null, "read", "1")
            serializer.attribute(null, "status", "-1")
            serializer.attribute(null, "locked", "0")
            serializer.attribute(null, "contact_name", thread.displayName)
            serializer.endTag(null, "sms")
        }

        serializer.endTag(null, "smses")
        serializer.endDocument()
        return writer.toString()
    }

    /**
     * Parses standard SMS XML backup into MessageItems.
     */
    fun parseBackupXml(xmlString: String): List<MessageItem> {
        val messages = mutableListOf<MessageItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlString))

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name.equals("sms", ignoreCase = true)) {
                val address = parser.getAttributeValue(null, "address") ?: ""
                val body = parser.getAttributeValue(null, "body") ?: ""
                val dateStr = parser.getAttributeValue(null, "date") ?: "0"
                val typeStr = parser.getAttributeValue(null, "type") ?: "1"
                val contactName = parser.getAttributeValue(null, "contact_name") ?: address

                val timestamp = dateStr.toLongOrNull() ?: System.currentTimeMillis()
                val isIncoming = (typeStr == "1")

                messages.add(
                    MessageItem(
                        messageId = timestamp,
                        threadId = 0L,
                        type = MessageType.SMS,
                        direction = if (isIncoming) MessageDirection.INCOMING else MessageDirection.OUTGOING,
                        sender = if (isIncoming) address.ifBlank { contactName } else "Me",
                        recipient = if (isIncoming) "Me" else address.ifBlank { contactName },
                        body = body,
                        timestamp = timestamp,
                        attachments = emptyList()
                    )
                )
            }
            eventType = parser.next()
        }
        return messages
    }
}
