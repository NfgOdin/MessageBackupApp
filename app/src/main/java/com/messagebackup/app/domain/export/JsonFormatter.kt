package com.messagebackup.app.domain.export

import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.MessageType
import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes conversation threads and messages into structured JSON format
 * specifically designed for lossless backups, migration, and restoration.
 */
class JsonFormatter {

    fun formatConversation(
        thread: ConversationThread,
        messages: List<MessageItem>
    ): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("app", "MessageBackupApp")
        root.put("timestamp", System.currentTimeMillis())

        val threadObj = JSONObject().apply {
            put("threadId", thread.threadId)
            put("address", thread.address)
            put("contactName", thread.contactName ?: "")
            put("displayName", thread.displayName)
            put("messageCount", messages.size)
            put("snippet", thread.snippet)
            put("timestamp", thread.timestamp)
            put("hasAttachment", thread.hasAttachment)
        }
        root.put("thread", threadObj)

        val messagesArray = JSONArray()
        for (msg in messages) {
            val msgObj = JSONObject().apply {
                put("messageId", msg.messageId)
                put("threadId", msg.threadId)
                put("type", msg.type.name)
                put("direction", msg.direction.name)
                put("sender", msg.sender)
                put("recipient", msg.recipient)
                put("body", msg.body)
                put("timestamp", msg.timestamp)
                if (msg.attachments.isNotEmpty()) {
                    val attachmentsArray = JSONArray()
                    for (att in msg.attachments) {
                        val attObj = JSONObject().apply {
                            put("partId", att.partId)
                            put("contentType", att.contentType)
                            put("fileName", att.fileName)
                        }
                        attachmentsArray.put(attObj)
                    }
                    put("attachments", attachmentsArray)
                }
            }
            messagesArray.put(msgObj)
        }
        root.put("messages", messagesArray)

        return root.toString(2)
    }

    /**
     * Parses a previously exported JSON backup file into a list of MessageItems.
     */
    fun parseBackupJson(jsonString: String): Pair<ConversationThread?, List<MessageItem>> {
        val root = JSONObject(jsonString)
        val threadObj = root.optJSONObject("thread")
        val thread = if (threadObj != null) {
            val contact = threadObj.optString("contactName").ifBlank {
                threadObj.optString("displayName").ifBlank { null }
            }
            ConversationThread(
                threadId = threadObj.optLong("threadId"),
                address = threadObj.optString("address"),
                contactName = contact,
                snippet = threadObj.optString("snippet"),
                timestamp = threadObj.optLong("timestamp", threadObj.optLong("date", System.currentTimeMillis())),
                messageCount = threadObj.optInt("messageCount"),
                hasAttachment = threadObj.optBoolean("hasAttachment", false)
            )
        } else null

        val messages = mutableListOf<MessageItem>()
        val messagesArray = root.optJSONArray("messages") ?: JSONArray()
        for (i in 0 until messagesArray.length()) {
            val msgObj = messagesArray.getJSONObject(i)
            messages.add(
                MessageItem(
                    messageId = msgObj.optLong("messageId"),
                    threadId = msgObj.optLong("threadId"),
                    type = try {
                        MessageType.valueOf(msgObj.optString("type", MessageType.SMS.name))
                    } catch (e: Exception) {
                        MessageType.SMS
                    },
                    direction = try {
                        MessageDirection.valueOf(msgObj.optString("direction", MessageDirection.INCOMING.name))
                    } catch (e: Exception) {
                        MessageDirection.INCOMING
                    },
                    sender = msgObj.optString("sender"),
                    recipient = msgObj.optString("recipient"),
                    body = msgObj.optString("body"),
                    timestamp = msgObj.optLong("timestamp"),
                    attachments = emptyList()
                )
            )
        }
        return Pair(thread, messages)
    }
}
