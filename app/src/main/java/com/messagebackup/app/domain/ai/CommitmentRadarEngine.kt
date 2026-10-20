package com.messagebackup.app.domain.ai

import com.messagebackup.app.model.CommitmentCategory
import com.messagebackup.app.model.CommitmentStatus
import com.messagebackup.app.model.DetectedCommitment
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/**
 * Intelligent engine that analyzes message histories to detect promises, obligations,
 * scheduled meetings, and financial commitments.
 *
 * Uses a hybrid approach:
 * 1. Fast local deterministic regex heuristic scanner for instant 0 ms results offline.
 * 2. O.D.I.N. LLM parser (Gemini / Ollama) for deep semantic comprehension when configured.
 */
class CommitmentRadarEngine(private val aiEngine: AiClientEngine = AiClientEngine()) {

    // Fast heuristic patterns
    private val promisePatterns = listOf(
        Pattern.compile("(?i)\\b(I('ll| will| shall| promise to| swear I'll))\\s+([a-zA-Z0-9 ,'-]{4,60})"),
        Pattern.compile("(?i)\\b(let('s| us) meet|meet you|see you)\\s+([a-zA-Z0-9 ,'-]{4,40})"),
        Pattern.compile("(?i)\\b(I owe you|will venmo|will zelle|will pay|send you (\\$|the money))\\b"),
        Pattern.compile("(?i)\\b(remind me to|don't forget to|make sure you)\\s+([a-zA-Z0-9 ,'-]{4,50})"),
        Pattern.compile("(?i)\\b(call you (back|later|tomorrow)|text you (back|later|tomorrow))\\b"),
        Pattern.compile("(?i)\\b(I can drop off|pick up|deliver)\\s+([a-zA-Z0-9 ,'-]{4,40})")
    )

    /**
     * Fast local scanner that runs instantly across message archives with zero network usage.
     */
    fun scanLocalCommitments(messages: List<MessageItem>, targetContactName: String): List<DetectedCommitment> {
        val results = mutableListOf<DetectedCommitment>()
        val seenSummaries = mutableSetOf<String>()

        for (msg in messages) {
            val text = msg.body.trim()
            if (text.length < 5) continue

            val isMadeByMe = msg.direction == MessageDirection.OUTGOING
            val person = if (isMadeByMe) "Me" else msg.sender.ifBlank { targetContactName }

            for (pattern in promisePatterns) {
                val matcher = pattern.matcher(text)
                if (matcher.find()) {
                    val snippet = matcher.group()
                    val lowerSnippet = snippet.lowercase()

                    val category = when {
                        lowerSnippet.contains("venmo") || lowerSnippet.contains("zelle") || lowerSnippet.contains("pay") || lowerSnippet.contains("$") -> CommitmentCategory.FINANCIAL
                        lowerSnippet.contains("meet") || lowerSnippet.contains("see you") -> CommitmentCategory.MEETING
                        lowerSnippet.contains("promise") || lowerSnippet.contains("swear") -> CommitmentCategory.PROMISE
                        else -> CommitmentCategory.TASK
                    }

                    // Extract approximate time word if present
                    val due = extractTimeWord(text)
                    val summary = cleanSummary(snippet, isMadeByMe, targetContactName)

                    if (seenSummaries.add(summary.lowercase())) {
                        results.add(
                            DetectedCommitment(
                                messageId = msg.messageId,
                                threadId = msg.threadId,
                                contactName = if (isMadeByMe) targetContactName else person,
                                summary = summary,
                                isMadeByMe = isMadeByMe,
                                rawSnippet = text,
                                timestamp = msg.timestamp,
                                dueDate = due,
                                category = category,
                                status = CommitmentStatus.PENDING
                            )
                        )
                    }
                    break
                }
            }
        }

        return results.sortedByDescending { it.timestamp }
    }

    private fun extractTimeWord(text: String): String? {
        val lower = text.lowercase()
        return when {
            lower.contains("tomorrow") -> "Tomorrow"
            lower.contains("tonight") -> "Tonight"
            lower.contains("later today") -> "Later Today"
            lower.contains("next week") -> "Next Week"
            lower.contains("this weekend") -> "This Weekend"
            lower.contains("monday") -> "Monday"
            lower.contains("tuesday") -> "Tuesday"
            lower.contains("wednesday") -> "Wednesday"
            lower.contains("thursday") -> "Thursday"
            lower.contains("friday") -> "Friday"
            lower.contains("saturday") -> "Saturday"
            lower.contains("sunday") -> "Sunday"
            else -> null
        }
    }

    private fun cleanSummary(snippet: String, isMadeByMe: Boolean, contact: String): String {
        var s = snippet.trim()
        if (s.length > 50) s = s.substring(0, 47) + "..."
        return if (isMadeByMe) "Promised: \"$s\"" else "$contact: \"$s\""
    }
}
