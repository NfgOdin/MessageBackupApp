package com.messagebackup.app.domain.ai

import com.messagebackup.app.model.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AiProviderType(val displayName: String) {
    GEMINI("Google Gemini (Cloud BYOK)"),
    OLLAMA("Ollama (Tailscale / Local PC)")
}

data class OdinChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isError: Boolean = false
) {
    enum class MessageSender {
        USER,
        ODIN
    }
}

sealed class AiQueryResult {
    data class Success(val responseText: String) : AiQueryResult()
    data class Error(val errorMessage: String) : AiQueryResult()
}

/**
 * Core engine for O.D.I.N. (Omniscient Document & Insights Navigator).
 * Coordinates prompting, context formatting, and query dispatching to Gemini or Ollama.
 */
class AiClientEngine {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    /**
     * Tests connectivity to an Ollama instance (e.g. over Tailscale or local Wi-Fi)
     * and returns the list of installed models available on the host machine.
     */
    suspend fun testOllamaConnection(endpoint: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val cleanEndpoint = endpoint.trim().trimEnd('/')
            val url = URL("$cleanEndpoint/api/tags")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 8000
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val modelsArray = json.optJSONArray("models") ?: JSONArray()
                val modelNames = mutableListOf<String>()
                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    modelNames.add(m.optString("name", "unknown"))
                }
                if (modelNames.isEmpty()) {
                    modelNames.add("llama3.2")
                }
                Result.success(modelNames)
            } else {
                Result.failure(Exception("Ollama responded with HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Connection failed: ${e.localizedMessage ?: "Check Tailscale / IP"}"))
        }
    }

    /**
     * Ask O.D.I.N. a question given a list of target messages as the grounding context.
     */
    suspend fun queryOdin(
        userPrompt: String,
        messages: List<MessageItem>,
        targetContactName: String,
        providerType: AiProviderType,
        geminiApiKey: String,
        ollamaEndpoint: String,
        ollamaModel: String
    ): AiQueryResult = withContext(Dispatchers.IO) {
        try {
            // Build compact, structured context from messages
            val contextTranscript = buildContextTranscript(messages)

            val systemInstruction = """
                You are O.D.I.N. (Omniscient Document & Insights Navigator), an intelligent personal message archivist.
                You are answering questions about the user's SMS/MMS messages with '$targetContactName'.
                
                Rules:
                1. Only answer based on facts, dates, codes, and context found in the provided message transcript below.
                2. If the user asks for gate/door codes, addresses, flight numbers, or dates, state them clearly and mention the approximate date of the text.
                3. If the transcript does not contain the answer, say honestly that you couldn't find that in the archived messages.
                4. Keep answers friendly, concise, and helpful.
                
                === MESSAGE TRANSCRIPT START ===
                $contextTranscript
                === MESSAGE TRANSCRIPT END ===
            """.trimIndent()

            when (providerType) {
                AiProviderType.GEMINI -> {
                    if (geminiApiKey.isBlank()) {
                        return@withContext AiQueryResult.Error("Google Gemini API Key is missing. Add your free key in O.D.I.N. Settings.")
                    }
                    queryGemini(systemInstruction, userPrompt, geminiApiKey)
                }
                AiProviderType.OLLAMA -> {
                    if (ollamaEndpoint.isBlank()) {
                        return@withContext AiQueryResult.Error("Ollama host endpoint is missing. Add your Tailscale or local IP (e.g. http://100.x.y.z:11434).")
                    }
                    queryOllama(systemInstruction, userPrompt, ollamaEndpoint, ollamaModel)
                }
            }
        } catch (e: Exception) {
            AiQueryResult.Error("Error querying O.D.I.N.: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }

    private fun buildContextTranscript(messages: List<MessageItem>, maxMessages: Int = 400): String {
        // Take the most relevant or recent messages within safe context window
        val subset = if (messages.size > maxMessages) messages.takeLast(maxMessages) else messages
        val sb = StringBuilder()
        for (msg in subset) {
            val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
            val sender = if (msg.direction == com.messagebackup.app.model.MessageDirection.OUTGOING) "Me" else msg.sender
            sb.append("[$dateStr] $sender: ${msg.body}")
            if (msg.attachments.isNotEmpty()) {
                sb.append(" [Attached ${msg.attachments.size} media file(s)]")
            }
            sb.append("\n")
        }
        return sb.toString()
    }

    private fun queryGemini(systemInstruction: String, userPrompt: String, apiKey: String): AiQueryResult {
        // Using Gemini 2.0 Flash (or 1.5 Flash fallback)
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
        }

        val requestJson = JSONObject().apply {
            val contentsArray = JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", "$systemInstruction\n\nUser Question: $userPrompt"))
                    })
                })
            }
            put("contents", contentsArray)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(requestJson.toString()) }

        val code = conn.responseCode
        if (code == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val candidates = root.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val first = candidates.getJSONObject(0)
                val content = first.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val text = parts.getJSONObject(0).optString("text", "")
                    return AiQueryResult.Success(text.trim())
                }
            }
            return AiQueryResult.Error("Gemini returned an empty response.")
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            return AiQueryResult.Error("Gemini API Error ($code): $errorText")
        }
    }

    private fun queryOllama(systemInstruction: String, userPrompt: String, endpoint: String, model: String): AiQueryResult {
        val cleanEndpoint = endpoint.trim().trimEnd('/')
        val url = URL("$cleanEndpoint/api/generate")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Content-Type", "application/json")
            connectTimeout = 10000
            readTimeout = 60000
            doOutput = true
        }

        val fullPrompt = "$systemInstruction\n\nUser Question: $userPrompt\n\nO.D.I.N. Answer:"
        val requestJson = JSONObject().apply {
            put("model", model.ifBlank { "llama3.2" })
            put("prompt", fullPrompt)
            put("stream", false)
        }

        OutputStreamWriter(conn.outputStream).use { it.write(requestJson.toString()) }

        val code = conn.responseCode
        if (code == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(responseText)
            val answer = root.optString("response", "")
            if (answer.isNotBlank()) {
                return AiQueryResult.Success(answer.trim())
            }
            return AiQueryResult.Error("Ollama returned an empty response.")
        } else {
            val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            return AiQueryResult.Error("Ollama Error ($code): $errorText")
        }
    }
}
