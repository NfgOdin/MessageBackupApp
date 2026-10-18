package com.messagebackup.app.domain.export

import com.messagebackup.app.model.ConversationThread
import com.messagebackup.app.model.ExportTheme
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Generates standalone, mobile-responsive HTML pages with modern chat bubbles,
 * theme support (Light, Dark, System), inline media, and pagination navigation controls
 * (Next / Previous buttons) when conversations are split across multiple files.
 */
class HtmlFormatter {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Formats a paginated segment of a conversation into modern HTML.
     *
     * @param thread The conversation thread metadata.
     * @param messages Chronological list of SMS and MMS messages for this part.
     * @param currentPart Index of the current part (1-based, 1 is oldest).
     * @param totalParts Total number of parts produced for this conversation.
     * @param prevFileName Relative file name of the previous part (or null if first).
     * @param nextFileName Relative file name of the next part (or null if last).
     * @param theme Light, Dark, or System color styling.
     * @param mediaSubdir Relative folder path where media attachments are stored.
     * @return Complete HTML document string.
     */
    fun formatConversationPart(
        thread: ConversationThread,
        messages: List<MessageItem>,
        currentPart: Int = 1,
        totalParts: Int = 1,
        prevFileName: String? = null,
        nextFileName: String? = null,
        theme: ExportTheme = ExportTheme.LIGHT,
        mediaSubdir: String = "media/"
    ): String {
        val sb = StringBuilder()
        val title = escapeHtml(thread.displayName)

        val isDark = (theme == ExportTheme.DARK)
        val isSystem = (theme == ExportTheme.SYSTEM)

        val cssVariables = when {
            isDark -> """
                :root {
                    --bg-color: #18191a;
                    --container-bg: #242526;
                    --bubble-in: #3a3b3c;
                    --bubble-out: #0084ff;
                    --text-in: #e4e6eb;
                    --text-out: #ffffff;
                    --meta-text: #b0b3b8;
                    --header-title: #ffffff;
                    --nav-btn-bg: #3a3b3c;
                    --nav-btn-text: #e4e6eb;
                    --nav-btn-border: #4e4f50;
                    --shadow: 0 1px 3px rgba(0,0,0,0.5);
                }
            """.trimIndent()
            isSystem -> """
                :root {
                    --bg-color: #f0f2f5;
                    --container-bg: #ffffff;
                    --bubble-in: #e4e6eb;
                    --bubble-out: #0084ff;
                    --text-in: #050505;
                    --text-out: #ffffff;
                    --meta-text: #65676b;
                    --header-title: #1c1e21;
                    --nav-btn-bg: #e4e6eb;
                    --nav-btn-text: #050505;
                    --nav-btn-border: #ccd0d5;
                    --shadow: 0 1px 2px rgba(0,0,0,0.1);
                }
                @media (prefers-color-scheme: dark) {
                    :root {
                        --bg-color: #18191a;
                        --container-bg: #242526;
                        --bubble-in: #3a3b3c;
                        --bubble-out: #0084ff;
                        --text-in: #e4e6eb;
                        --text-out: #ffffff;
                        --meta-text: #b0b3b8;
                        --header-title: #ffffff;
                        --nav-btn-bg: #3a3b3c;
                        --nav-btn-text: #e4e6eb;
                        --nav-btn-border: #4e4f50;
                        --shadow: 0 1px 3px rgba(0,0,0,0.5);
                    }
                }
            """.trimIndent()
            else -> """
                :root {
                    --bg-color: #f0f2f5;
                    --container-bg: #ffffff;
                    --bubble-in: #f0f2f5;
                    --bubble-out: #0084ff;
                    --text-in: #050505;
                    --text-out: #ffffff;
                    --meta-text: #65676b;
                    --header-title: #1c1e21;
                    --nav-btn-bg: #f0f2f5;
                    --nav-btn-text: #050505;
                    --nav-btn-border: #ccd0d5;
                    --shadow: 0 1px 2px rgba(0,0,0,0.1);
                }
            """.trimIndent()
        }

        sb.append("""
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>$title - Part $currentPart of $totalParts</title>
                <style>
                    $cssVariables
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg-color);
                        margin: 0;
                        padding: 16px;
                        display: flex;
                        justify-content: center;
                    }
                    .container {
                        max-width: 720px;
                        width: 100%;
                    }
                    .header {
                        background: var(--container-bg);
                        padding: 16px 20px;
                        border-radius: 12px;
                        box-shadow: var(--shadow);
                        margin-bottom: 20px;
                    }
                    .header h1 {
                        margin: 0 0 8px 0;
                        font-size: 1.4rem;
                        color: var(--header-title);
                    }
                    .header p {
                        margin: 4px 0;
                        font-size: 0.9rem;
                        color: var(--meta-text);
                    }
                    .part-badge {
                        display: inline-block;
                        background: var(--bubble-out);
                        color: #ffffff;
                        font-size: 0.8rem;
                        font-weight: 600;
                        padding: 2px 8px;
                        border-radius: 12px;
                        margin-bottom: 8px;
                    }
                    .nav-bar {
                        display: flex;
                        justify-content: space-between;
                        align-items: center;
                        margin: 16px 0;
                        padding: 10px 16px;
                        background: var(--container-bg);
                        border-radius: 12px;
                        box-shadow: var(--shadow);
                    }
                    .nav-btn {
                        display: inline-flex;
                        align-items: center;
                        padding: 8px 14px;
                        background: var(--nav-btn-bg);
                        color: var(--nav-btn-text);
                        text-decoration: none;
                        border-radius: 8px;
                        font-size: 0.85rem;
                        font-weight: 600;
                        border: 1px solid var(--nav-btn-border);
                        transition: opacity 0.15s;
                    }
                    .nav-btn:hover {
                        opacity: 0.85;
                    }
                    .nav-btn.disabled {
                        opacity: 0.4;
                        pointer-events: none;
                    }
                    .nav-label {
                        font-size: 0.85rem;
                        font-weight: 600;
                        color: var(--meta-text);
                    }
                    .chat-thread {
                        display: flex;
                        flex-direction: column;
                        gap: 12px;
                    }
                    .bubble-row {
                        display: flex;
                        flex-direction: column;
                        max-width: 80%;
                    }
                    .bubble-row.in {
                        align-self: flex-start;
                    }
                    .bubble-row.out {
                        align-self: flex-end;
                    }
                    .bubble-sender {
                        font-size: 0.75rem;
                        font-weight: 600;
                        margin-bottom: 2px;
                        color: var(--meta-text);
                        padding: 0 4px;
                    }
                    .bubble {
                        padding: 10px 14px;
                        border-radius: 18px;
                        box-shadow: var(--shadow);
                        word-wrap: break-word;
                    }
                    .bubble.in {
                        background-color: var(--bubble-in);
                        color: var(--text-in);
                        border-bottom-left-radius: 4px;
                    }
                    .bubble.out {
                        background-color: var(--bubble-out);
                        color: var(--text-out);
                        border-bottom-right-radius: 4px;
                    }
                    .bubble-text {
                        font-size: 0.95rem;
                        line-height: 1.4;
                        white-space: pre-wrap;
                    }
                    .bubble-time {
                        font-size: 0.7rem;
                        margin-top: 4px;
                        text-align: right;
                        opacity: 0.75;
                    }
                    .bubble-media {
                        margin-top: 8px;
                    }
                    .bubble-media img {
                        max-width: 100%;
                        max-height: 380px;
                        border-radius: 10px;
                        display: block;
                    }
                </style>
            </head>
            <body>
            <div class="container">
                <div class="header">
                    ${if (totalParts > 1) "<span class=\"part-badge\">Part $currentPart of $totalParts</span>" else ""}
                    <h1>$title</h1>
                    <p><strong>Address:</strong> ${escapeHtml(thread.address)}</p>
                    <p><strong>Messages in this part:</strong> ${messages.size}</p>
                    <p><strong>Exported On:</strong> ${dateFormatter.format(Instant.now())}</p>
                </div>
        """.trimIndent())

        // Top Navigation Bar (if paginated)
        if (totalParts > 1) {
            sb.append(renderNavBar(currentPart, totalParts, prevFileName, nextFileName))
        }

        sb.append("<div class=\"chat-thread\">\n")

        for (msg in messages) {
            val isOut = (msg.direction == MessageDirection.OUTGOING)
            val rowClass = if (isOut) "out" else "in"
            val senderName = if (isOut) "Me" else escapeHtml(msg.sender)
            val timeStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))

            sb.append("""
                    <div class="bubble-row $rowClass">
                        <span class="bubble-sender">$senderName</span>
                        <div class="bubble $rowClass">
            """.trimIndent())

            if (msg.body.isNotBlank()) {
                sb.append("<div class=\"bubble-text\">${escapeHtml(msg.body)}</div>")
            }

            for (attachment in msg.attachments) {
                val relPath = "$mediaSubdir${attachment.fileName}"
                if (attachment.contentType.startsWith("image/")) {
                    sb.append("""
                        <div class="bubble-media">
                            <a href="$relPath" target="_blank"><img src="$relPath" alt="${escapeHtml(attachment.fileName)}" /></a>
                        </div>
                    """.trimIndent())
                } else {
                    sb.append("""
                        <div class="bubble-media">
                            <a href="$relPath" style="color: inherit; text-decoration: underline;" target="_blank">Attachment: ${escapeHtml(attachment.fileName)}</a>
                        </div>
                    """.trimIndent())
                }
            }

            sb.append("""
                            <div class="bubble-time">$timeStr</div>
                        </div>
                    </div>
            """.trimIndent())
        }

        sb.append("</div>\n")

        // Bottom Navigation Bar (if paginated)
        if (totalParts > 1) {
            sb.append(renderNavBar(currentPart, totalParts, prevFileName, nextFileName))
        }

        sb.append("""
            </div>
            </body>
            </html>
        """.trimIndent())

        return sb.toString()
    }

    private fun renderNavBar(
        currentPart: Int,
        totalParts: Int,
        prevFileName: String?,
        nextFileName: String?
    ): String {
        val prevBtn = if (prevFileName != null) {
            "<a class=\"nav-btn\" href=\"$prevFileName\">&#8592; Previous (Part ${currentPart - 1})</a>"
        } else {
            "<span class=\"nav-btn disabled\">&#8592; Previous</span>"
        }

        val nextBtn = if (nextFileName != null) {
            "<a class=\"nav-btn\" href=\"$nextFileName\">Next (Part ${currentPart + 1}) &#8594;</a>"
        } else {
            "<span class=\"nav-btn disabled\">Next &#8594;</span>"
        }

        return """
            <div class="nav-bar">
                $prevBtn
                <span class="nav-label">Part $currentPart of $totalParts</span>
                $nextBtn
            </div>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
