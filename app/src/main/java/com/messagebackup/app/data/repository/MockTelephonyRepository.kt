package com.messagebackup.app.data.repository

import android.net.Uri
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.MediaAttachment
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.MessageType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock implementation of TelephonyRepository for instant offline testing and unit tests.
 * Provides realistic SMS and MMS threads including image attachments.
 */
class MockTelephonyRepository : TelephonyRepository {

    // 1x1 pixel PNG byte data for mock attachments
    private val mockSampleImageBytes = byteArrayOf(
        0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x1A.toByte(), 0x0A.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0D.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
        0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1F.toByte(), 0x15.toByte(), 0xC4.toByte(),
        0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0A.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
        0x54.toByte(), 0x78.toByte(), 0x9C.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
        0x05.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0D.toByte(), 0x0A.toByte(), 0x2D.toByte(), 0xB4.toByte(), 0x00.toByte(),
        0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4E.toByte(), 0x44.toByte(), 0xAE.toByte(),
        0x42.toByte(), 0x60.toByte(), 0x82.toByte()
    )

    private val now = System.currentTimeMillis()

    private val sampleThreads = listOf(
        ConversationThread(
            threadId = 1L,
            address = "+1 (555) 019-2834",
            contactName = "Sarah Connor",
            snippet = "Check out this photo from the event!",
            timestamp = now - 1000 * 60 * 15, // 15 mins ago
            messageCount = 4,
            hasAttachment = true
        ),
        ConversationThread(
            threadId = 2L,
            address = "+1 (555) 742-9901",
            contactName = "John Doe (Project Lead)",
            snippet = "The release documentation is ready for review.",
            timestamp = now - 1000 * 60 * 60 * 3, // 3 hours ago
            messageCount = 6,
            hasAttachment = false
        ),
        ConversationThread(
            threadId = 3L,
            address = "+1 (555) 883-1244",
            contactName = null,
            snippet = "Your one-time verification code is 482910.",
            timestamp = now - 1000 * 60 * 60 * 24 * 2, // 2 days ago
            messageCount = 1,
            hasAttachment = false
        )
    )

    private val sampleMessages = mapOf(
        1L to listOf(
            MessageItem(
                messageId = 101L,
                threadId = 1L,
                type = MessageType.SMS,
                direction = MessageDirection.INCOMING,
                sender = "Sarah Connor",
                recipient = "Me",
                body = "Hey! Are you still at the convention?",
                timestamp = now - 1000 * 60 * 45
            ),
            MessageItem(
                messageId = 102L,
                threadId = 1L,
                type = MessageType.SMS,
                direction = MessageDirection.OUTGOING,
                sender = "Me",
                recipient = "Sarah Connor",
                body = "Yes, just wrapped up the keynote speech. Did you see the main demo?",
                timestamp = now - 1000 * 60 * 35
            ),
            MessageItem(
                messageId = 103L,
                threadId = 1L,
                type = MessageType.MMS,
                direction = MessageDirection.INCOMING,
                sender = "Sarah Connor",
                recipient = "Me",
                body = "Check out this photo from the event!",
                timestamp = now - 1000 * 60 * 15,
                attachments = listOf(
                    MediaAttachment(
                        partId = 901L,
                        contentType = "image/png",
                        uriString = "mock://attachment/901",
                        fileName = "convention_demo.png"
                    )
                )
            ),
            MessageItem(
                messageId = 104L,
                threadId = 1L,
                type = MessageType.SMS,
                direction = MessageDirection.OUTGOING,
                sender = "Me",
                recipient = "Sarah Connor",
                body = "Looks amazing! Let's sync up later tonight.",
                timestamp = now - 1000 * 60 * 5
            )
        ),
        2L to listOf(
            MessageItem(
                messageId = 201L,
                threadId = 2L,
                type = MessageType.SMS,
                direction = MessageDirection.INCOMING,
                sender = "John Doe (Project Lead)",
                recipient = "Me",
                body = "Good morning! Can you review the markdown export spec?",
                timestamp = now - 1000 * 60 * 60 * 5
            ),
            MessageItem(
                messageId = 202L,
                threadId = 2L,
                type = MessageType.SMS,
                direction = MessageDirection.OUTGOING,
                sender = "Me",
                recipient = "John Doe (Project Lead)",
                body = "On it right now. Formatting looks clean and compliant.",
                timestamp = now - 1000 * 60 * 60 * 4
            ),
            MessageItem(
                messageId = 203L,
                threadId = 2L,
                type = MessageType.SMS,
                direction = MessageDirection.INCOMING,
                sender = "John Doe (Project Lead)",
                recipient = "Me",
                body = "The release documentation is ready for review.",
                timestamp = now - 1000 * 60 * 60 * 3
            )
        ),
        3L to listOf(
            MessageItem(
                messageId = 301L,
                threadId = 3L,
                type = MessageType.SMS,
                direction = MessageDirection.INCOMING,
                sender = "+1 (555) 883-1244",
                recipient = "Me",
                body = "Your one-time verification code is 482910. Do not share this with anyone.",
                timestamp = now - 1000 * 60 * 60 * 24 * 2
            )
        )
    )

    override fun getConversationThreads(resolveContacts: Boolean): Flow<List<ConversationThread>> = flow {
        val list = if (resolveContacts) {
            sampleThreads
        } else {
            sampleThreads.map { it.copy(contactName = null) }
        }
        emit(list)
    }

    override suspend fun getMessagesForThread(
        threadId: Long,
        resolveContacts: Boolean
    ): List<MessageItem> {
        val list = sampleMessages[threadId] ?: emptyList()
        return if (!resolveContacts) {
            list.map {
                if (it.direction == MessageDirection.INCOMING) {
                    it.copy(sender = sampleThreads.find { t -> t.threadId == threadId }?.address ?: "Unknown")
                } else it
            }
        } else {
            list
        }
    }

    override suspend fun getAttachmentBytes(contentUri: Uri): ByteArray {
        return mockSampleImageBytes
    }

    override suspend fun insertSmsMessage(message: MessageItem, address: String): Boolean {
        return true
    }

    override fun isDefaultSmsApp(): Boolean {
        return true
    }
}
