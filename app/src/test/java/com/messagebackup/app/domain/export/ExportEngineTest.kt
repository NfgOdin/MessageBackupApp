package com.messagebackup.app.domain.export

import com.messagebackup.app.data.repository.MockTelephonyRepository
import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.DateRangeFilter
import com.messagebackup.app.model.ExportFormat
import com.messagebackup.app.model.ExportOptions
import com.messagebackup.app.model.ExportTheme
import com.messagebackup.app.model.MediaAttachment
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import com.messagebackup.app.model.MessageType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for MarkdownFormatter, HtmlFormatter with pagination, and MockTelephonyRepository.
 */
class ExportEngineTest {

    private val markdownFormatter = MarkdownFormatter()
    private val htmlFormatter = HtmlFormatter()
    private val plainTextFormatter = PlainTextFormatter()
    private val mockRepo = MockTelephonyRepository()

    @Test
    fun mockTelephonyRepository_providesSampleThreads() = runTest {
        val threads = mockRepo.getConversationThreads(resolveContacts = true).first()
        assertTrue("Expected at least one mock conversation thread", threads.isNotEmpty())
        val firstThread = threads.first()
        assertNotNull(firstThread.displayName)
        assertTrue(firstThread.messageCount > 0)
    }

    @Test
    fun markdownFormatter_generatesHeadersAndMessages() {
        val thread = ConversationThread(
            threadId = 10L,
            address = "+1 (555) 123-4567",
            contactName = "Alice Wonderland",
            snippet = "Hello world",
            timestamp = 1700000000000L,
            messageCount = 2,
            hasAttachment = false
        )

        val messages = listOf(
            MessageItem(
                messageId = 1L,
                threadId = 10L,
                type = MessageType.SMS,
                direction = MessageDirection.INCOMING,
                sender = "Alice Wonderland",
                recipient = "Me",
                body = "Hello there!",
                timestamp = 1700000000000L
            ),
            MessageItem(
                messageId = 2L,
                threadId = 10L,
                type = MessageType.SMS,
                direction = MessageDirection.OUTGOING,
                sender = "Me",
                recipient = "Alice Wonderland",
                body = "Hi Alice, nice to meet you.",
                timestamp = 1700000060000L
            )
        )

        val output = markdownFormatter.formatConversationPart(
            thread = thread,
            messages = messages,
            currentPart = 1,
            totalParts = 1
        )

        assertTrue(output.contains("# Conversation with Alice Wonderland"))
        assertTrue(output.contains("+1 (555) 123-4567"))
        assertTrue(output.contains("Hello there!"))
        assertTrue(output.contains("Hi Alice, nice to meet you."))
        assertTrue(output.contains("**Alice Wonderland**"))
        assertTrue(output.contains("**Me**"))
    }

    @Test
    fun markdownFormatter_paginationLinksIncluded() {
        val thread = ConversationThread(
            threadId = 10L,
            address = "+1 (555) 123-4567",
            contactName = "Alice",
            snippet = "Test",
            timestamp = 1700000000000L,
            messageCount = 2,
            hasAttachment = false
        )

        val messages = listOf(
            MessageItem(
                messageId = 1L,
                threadId = 10L,
                type = MessageType.SMS,
                direction = MessageDirection.INCOMING,
                sender = "Alice",
                recipient = "Me",
                body = "Part 1 msg",
                timestamp = 1700000000000L
            )
        )

        val output = markdownFormatter.formatConversationPart(
            thread = thread,
            messages = messages,
            currentPart = 1,
            totalParts = 2,
            prevFileName = null,
            nextFileName = "conversation_part_02.md"
        )

        assertTrue(output.contains("Part 1 of 2"))
        assertTrue(output.contains("[Next Part →](conversation_part_02.md)"))
    }

    @Test
    fun htmlFormatter_generatesChatBubblesAndNavButtons() {
        val thread = ConversationThread(
            threadId = 12L,
            address = "+1 (555) 000-1111",
            contactName = "Carol Danvers",
            snippet = "Testing HTML format",
            timestamp = 1700000000000L,
            messageCount = 1,
            hasAttachment = false
        )

        val messages = listOf(
            MessageItem(
                messageId = 4L,
                threadId = 12L,
                type = MessageType.SMS,
                direction = MessageDirection.INCOMING,
                sender = "Carol Danvers",
                recipient = "Me",
                body = "Checking web layout <safe>",
                timestamp = 1700000000000L
            )
        )

        val output = htmlFormatter.formatConversationPart(
            thread = thread,
            messages = messages,
            currentPart = 1,
            totalParts = 2,
            prevFileName = null,
            nextFileName = "conversation_part_02.html",
            theme = ExportTheme.DARK
        )

        assertTrue(output.contains("<!DOCTYPE html>"))
        assertTrue(output.contains("Carol Danvers - Part 1 of 2"))
        assertTrue(output.contains("Part 1 of 2"))
        assertTrue(output.contains("conversation_part_02.html"))
        assertTrue(output.contains("&lt;safe&gt;"))
        assertFalse(output.contains("<safe>"))
    }
}
