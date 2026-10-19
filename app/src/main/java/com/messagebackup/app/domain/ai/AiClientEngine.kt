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
     * Queries the Google Gemini models API to discover all available models that support generateContent.
     */
    suspend fun fetchGeminiModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("Gemini API key is required to list available models."))
        }
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models?key=${apiKey.trim()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 12000
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                val modelsArray = json.optJSONArray("models") ?: JSONArray()
                val resultList = mutableListOf<String>()

                for (i in 0 until modelsArray.length()) {
                    val m = modelsArray.getJSONObject(i)
                    val rawName = m.optString("name", "")
                    val shortName = rawName.removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")

                    var canGenerate = false
                    if (methods != null) {
                        for (j in 0 until methods.length()) {
                            if (methods.optString(j) == "generateContent") {
                                canGenerate = true
                                break
                            }
                        }
                    }

                    // Only include models that support generateContent
                    if (canGenerate && shortName.isNotBlank()) {
                        resultList.add(shortName)
                    }
                }

                // Sort models so 2.0 / 2.5 / modern flash models appear first
                resultList.sortWith { a, b ->
                    when {
                        a == "gemini-2.0-flash" -> -1
                        b == "gemini-2.0-flash" -> 1
                        a.contains("2.0-flash") && !b.contains("2.0-flash") -> -1
                        !a.contains("2.0-flash") && b.contains("2.0-flash") -> 1
                        a.contains("flash") && !b.contains("flash") -> -1
                        !a.contains("flash") && b.contains("flash") -> 1
                        else -> a.compareTo(b)
                    }
                }

                if (resultList.isEmpty()) {
                    resultList.add("gemini-2.0-flash")
                }
                Result.success(resultList)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(Exception("Failed to fetch models ($responseCode): $err"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Network error fetching Gemini models: ${e.localizedMessage ?: "Unknown error"}"))
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
        geminiModel: String = "gemini-2.0-flash",
        ollamaEndpoint: String,
        ollamaModel: String,
        temperature: Float = 0.2f,
        topP: Float = 0.9f,
        numCtx: Int = 8192,
        customSystemPrompt: String = ""
    ): AiQueryResult = withContext(Dispatchers.IO) {
        try {
            // Build compact, structured context from messages
            val contextTranscript = buildContextTranscript(messages)

            val baseInstruction = if (customSystemPrompt.isNotBlank()) {
                customSystemPrompt.trim()
            } else {
                DEFAULT_SYSTEM_INSTRUCTION
            }

            val systemInstruction = """
                $baseInstruction
                You are currently analyzing messages with '$targetContactName'.
                
                === MESSAGE TRANSCRIPT START ===
                $contextTranscript
                === MESSAGE TRANSCRIPT END ===
            """.trimIndent()

            when (providerType) {
                AiProviderType.GEMINI -> {
                    if (geminiApiKey.isBlank()) {
                        return@withContext AiQueryResult.Error("Google Gemini API Key is missing. Add your free key in O.D.I.N. Settings.")
                    }
                    queryGemini(systemInstruction, userPrompt, geminiApiKey, geminiModel, temperature, topP)
                }
                AiProviderType.OLLAMA -> {
                    if (ollamaEndpoint.isBlank()) {
                        return@withContext AiQueryResult.Error("Ollama host endpoint is missing. Add your Tailscale or local IP (e.g. http://100.x.y.z:11434).")
                    }
                    queryOllama(systemInstruction, userPrompt, ollamaEndpoint, ollamaModel, temperature, topP, numCtx)
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

    private fun queryGemini(
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        topP: Float
    ): AiQueryResult {
        val cleanModel = modelName.trim().removePrefix("models/").ifBlank { "gemini-2.0-flash" }
        // Attempt user-configured model first, with modern 2.0 / 2.5 active fallbacks (1.5-flash is retired)
        val candidateModels = listOf(cleanModel, "gemini-2.0-flash", "gemini-2.5-flash").distinct()

        var lastError = "Unable to reach Gemini API"

        for (model in candidateModels) {
            try {
                val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 15000
                    readTimeout = 30000
                    doOutput = true
                }

                val requestJson = JSONObject().apply {
                    put("system_instruction", JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().put("text", systemInstruction))
                        })
                    })
                    val contentsArray = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().apply {
                                put(JSONObject().put("text", userPrompt))
                            })
                        })
                    }
                    put("contents", contentsArray)
                    put("generationConfig", JSONObject().apply {
                        put("temperature", temperature)
                        put("topP", topP)
                    })
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
                } else if (code == 404) {
                    val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP 404"
                    lastError = "Model '$model' (HTTP 404): $errorText"
                    continue
                } else {
                    val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                    return AiQueryResult.Error("Gemini API Error ($code on '$model'): $errorText")
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "Network connection error"
            }
        }

        return AiQueryResult.Error("Gemini Error: $lastError")
    }

    private fun queryOllama(
        systemInstruction: String,
        userPrompt: String,
        endpoint: String,
        model: String,
        temperature: Float,
        topP: Float,
        numCtx: Int
    ): AiQueryResult {
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
            put("model", model.ifBlank { "qwen2.5:14b" })
            put("prompt", fullPrompt)
            put("stream", false)
            put("options", JSONObject().apply {
                put("temperature", temperature)
                put("top_p", topP)
                put("num_ctx", numCtx)
            })
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

    companion object {
        const val DEFAULT_SYSTEM_INSTRUCTION = """You are O.D.I.N. (Omniscient Document & Insights Navigator), an intelligent personal message archivist.
Rules:
1. Only answer based on facts, dates, codes, and context found in the provided message transcript below.
2. If the user asks for gate/door codes, addresses, flight numbers, or dates, state them clearly and mention the approximate date of the text.
3. If the transcript does not contain the answer, say honestly that you couldn't find that in the archived messages.
4. Keep answers friendly, concise, and helpful."""
    }
}
