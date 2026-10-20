package com.messagebackup.app.domain.export

import android.content.Context
import com.messagebackup.app.model.ConversationStats
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exports conversation insights and statistics into a standalone, styled HTML report
 * that visually matches the in-app Conversation Insights modal.
 */
class StatsReportFormatter(private val context: Context) {

    private val dateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
        .withZone(ZoneId.systemDefault())

    fun generateHtmlReport(stats: ConversationStats, targetTitle: String = "All Conversations"): File {
        val exportsDir = File(context.cacheDir, "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }

        val timestamp = System.currentTimeMillis()
        val reportFile = File(exportsDir, "Conversation_Insights_${timestamp}.html")

        val oldestStr = stats.oldestTimestamp?.let { dateFormatter.format(Instant.ofEpochMilli(it)) } ?: "N/A"
        val newestStr = stats.newestTimestamp?.let { dateFormatter.format(Instant.ofEpochMilli(it)) } ?: "N/A"
        val exportedDateStr = dateFormatter.format(Instant.now())

        val totalSentRecv = stats.totalSent + stats.totalReceived
        val sentPct = if (totalSentRecv > 0) (stats.totalSent.toFloat() / totalSentRecv.toFloat() * 100).toInt() else 50
        val recvPct = 100 - sentPct

        val busiestHourDisplay = stats.busiestHour?.let { hour ->
            val amPm = if (hour >= 12) "PM" else "AM"
            val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
            "$displayHour:00 $amPm - $displayHour:59 $amPm"
        } ?: "N/A"

        val topContactsHtml = StringBuilder()
        stats.topContacts.forEachIndexed { index, contact ->
            topContactsHtml.append("""
                <div class="contact-row">
                    <div class="contact-meta">
                        <span class="contact-name">${index + 1}. ${escapeHtml(contact.displayName)}</span>
                        <span class="contact-count">${contact.count} msgs (${contact.percentage.toInt()}%)</span>
                    </div>
                    <div class="progress-bar">
                        <div class="progress-fill" style="width: ${contact.percentage.coerceIn(0f, 100f)}%;"></div>
                    </div>
                </div>
            """.trimIndent())
        }

        val htmlContent = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Conversation Insights — $targetTitle</title>
                <style>
                    :root {
                        --bg-color: #0f172a;
                        --card-bg: #1e293b;
                        --primary: #3b82f6;
                        --primary-glow: rgba(59, 130, 246, 0.2);
                        --secondary: #10b981;
                        --text: #f8fafc;
                        --text-muted: #94a3b8;
                        --border: #334155;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg-color);
                        color: var(--text);
                        margin: 0;
                        padding: 24px 16px;
                    }
                    .container {
                        max-width: 680px;
                        margin: 0 auto;
                    }
                    .header {
                        text-align: center;
                        margin-bottom: 28px;
                    }
                    .header h1 {
                        font-size: 1.8rem;
                        margin: 0 0 8px 0;
                        background: linear-gradient(135deg, #60a5fa, #38bdf8);
                        -webkit-background-clip: text;
                        -webkit-text-fill-color: transparent;
                    }
                    .header p {
                        font-size: 0.9rem;
                        color: var(--text-muted);
                        margin: 0;
                    }
                    .badge {
                        display: inline-block;
                        padding: 4px 12px;
                        background: var(--primary-glow);
                        color: #93c5fd;
                        border-radius: 9999px;
                        font-size: 0.8rem;
                        font-weight: 600;
                        margin-bottom: 12px;
                    }
                    .grid-2 {
                        display: grid;
                        grid-template-columns: 1fr 1fr;
                        gap: 12px;
                        margin-bottom: 16px;
                    }
                    .card {
                        background-color: var(--card-bg);
                        border: 1px solid var(--border);
                        border-radius: 16px;
                        padding: 16px;
                    }
                    .card-label {
                        font-size: 0.8rem;
                        color: var(--text-muted);
                        margin-bottom: 6px;
                    }
                    .card-value {
                        font-size: 1.35rem;
                        font-weight: 700;
                        color: #ffffff;
                    }
                    .section-title {
                        font-size: 1.1rem;
                        font-weight: 600;
                        margin: 20px 0 10px 0;
                    }
                    .ratio-labels {
                        display: flex;
                        justify-content: space-between;
                        font-size: 0.85rem;
                        margin-bottom: 6px;
                    }
                    .ratio-bar {
                        height: 12px;
                        border-radius: 6px;
                        background: #064e3b;
                        overflow: hidden;
                        display: flex;
                    }
                    .ratio-sent {
                        background: var(--primary);
                        height: 100%;
                    }
                    .ratio-recv {
                        background: var(--secondary);
                        height: 100%;
                    }
                    .pattern-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 8px 0;
                        border-bottom: 1px solid var(--border);
                        font-size: 0.9rem;
                    }
                    .pattern-row:last-child {
                        border-bottom: none;
                    }
                    .pattern-label {
                        color: var(--text-muted);
                    }
                    .contact-row {
                        margin-bottom: 10px;
                    }
                    .contact-meta {
                        display: flex;
                        justify-content: space-between;
                        font-size: 0.85rem;
                        margin-bottom: 4px;
                    }
                    .progress-bar {
                        height: 8px;
                        background-color: #334155;
                        border-radius: 4px;
                        overflow: hidden;
                    }
                    .progress-fill {
                        height: 100%;
                        background: linear-gradient(90deg, #3b82f6, #60a5fa);
                        border-radius: 4px;
                    }
                    .footer {
                        text-align: center;
                        margin-top: 32px;
                        font-size: 0.75rem;
                        color: var(--text-muted);
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <span class="badge">📊 Muninn</span>
                        <h1>Conversation Insights</h1>
                        <p>Archive: $targetTitle • Exported $exportedDateStr</p>
                    </div>

                    <div class="grid-2">
                        <div class="card">
                            <div class="card-label">Total Messages</div>
                            <div class="card-value">${String.format("%,d", stats.totalMessages)}</div>
                        </div>
                        <div class="card">
                            <div class="card-label">Total Conversations</div>
                            <div class="card-value">${String.format("%,d", stats.totalThreads)}</div>
                        </div>
                    </div>

                    <div class="grid-2">
                        <div class="card">
                            <div class="card-label">SMS / MMS Breakdown</div>
                            <div class="card-value">${stats.totalSms} / ${stats.totalMms}</div>
                        </div>
                        <div class="card">
                            <div class="card-label">Media Attachments</div>
                            <div class="card-value">${String.format("%,d", stats.totalMediaAttachments)}</div>
                        </div>
                    </div>

                    <div class="card" style="margin-bottom: 16px;">
                        <div class="section-title" style="margin-top: 0;">Sent vs. Received Ratio</div>
                        <div class="ratio-labels">
                            <span style="color: #60a5fa;">Sent: ${stats.totalSent} ($sentPct%)</span>
                            <span style="color: #34d399;">Received: ${stats.totalReceived} ($recvPct%)</span>
                        </div>
                        <div class="ratio-bar">
                            <div class="ratio-sent" style="width: $sentPct%;"></div>
                            <div class="ratio-recv" style="width: $recvPct%;"></div>
                        </div>
                    </div>

                    <div class="card" style="margin-bottom: 16px;">
                        <div class="section-title" style="margin-top: 0;">Activity Patterns</div>
                        <div class="pattern-row">
                            <span class="pattern-label">Peak Chat Time</span>
                            <span>$busiestHourDisplay</span>
                        </div>
                        <div class="pattern-row">
                            <span class="pattern-label">Most Active Day</span>
                            <span>${stats.busiestDayOfWeek ?: "N/A"}</span>
                        </div>
                        <div class="pattern-row">
                            <span class="pattern-label">Archive Span</span>
                            <span>$oldestStr — $newestStr</span>
                        </div>
                    </div>

                    ${if (stats.topContacts.isNotEmpty()) {
                        """
                        <div class="card">
                            <div class="section-title" style="margin-top: 0;">Top Active Contacts</div>
                            $topContactsHtml
                        </div>
                        """
                    } else ""}

                    <div class="footer">
                        Generated by Muninn • by NfgOdin
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()

        reportFile.writeText(htmlContent, Charsets.UTF_8)
        return reportFile
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
