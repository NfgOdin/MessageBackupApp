package com.messagebackup.app.domain.ai

import android.content.Context
import android.os.Build
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
    OPENAI("OpenAI ChatGPT (Cloud BYOK)"),
    OLLAMA("Local / Self-Hosted (Ollama / LocalAI / Kobold)"),
    ON_DEVICE_NANO("On-Device AI (Gemini Nano - AICore)")
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

                    // Only include models that support generateContent and exclude known deprecated models
                    if (canGenerate && shortName.isNotBlank() &&
                        !shortName.startsWith("gemini-1.5") &&
                        shortName != "gemini-2.0-flash" &&
                        shortName != "gemini-2.5-flash"
                    ) {
                        resultList.add(shortName)
                    }
                }

                // Sort models so modern fast and flagship models appear first
                resultList.sortWith { a, b ->
                    val priorityOrder = listOf(
                        "gemini-flash-lite-latest",
                        "gemini-3.8-flash",
                        "gemini-3.5-flash-lite",
                        "gemini-3.5-flash",
                        "gemini-flash-latest",
                        "gemini-3-flash-preview",
                        "gemini-3.7-flash"
                    )
                    val aIdx = priorityOrder.indexOf(a)
                    val bIdx = priorityOrder.indexOf(b)
                    when {
                        aIdx != -1 && bIdx != -1 -> aIdx.compareTo(bIdx)
                        aIdx != -1 -> -1
                        bIdx != -1 -> 1
                        a.contains("flash-lite") && !b.contains("flash-lite") -> -1
                        !a.contains("flash-lite") && b.contains("flash-lite") -> 1
                        a.contains("flash") && !b.contains("flash") -> -1
                        !a.contains("flash") && b.contains("flash") -> 1
                        else -> a.compareTo(b)
                    }
                }

                if (resultList.isEmpty()) {
                    resultList.add("gemini-flash-lite-latest")
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
        geminiModel: String = "gemini-flash-lite-latest",
        openaiApiKey: String = "",
        openaiModel: String = "gpt-4o-mini",
        ollamaEndpoint: String,
        ollamaModel: String,
        temperature: Float = 0.2f,
        topP: Float = 0.9f,
        numCtx: Int = 8192,
        customSystemPrompt: String = "",
        context: Context? = null
    ): AiQueryResult = withContext(Dispatchers.IO) {
        try {
            if (messages.isEmpty()) {
                return@withContext AiQueryResult.Error(
                    "No messages were found to analyze. Please verify that your messages are finished indexing or that SMS permissions are enabled."
                )
            }

            // Build compact, structured context from messages
            val contextTranscript = buildContextTranscript(messages)

            val baseInstruction = if (customSystemPrompt.isNotBlank()) {
                customSystemPrompt.trim()
            } else {
                DEFAULT_SYSTEM_INSTRUCTION
            }

            val targetScope = if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
                "recent messages across all conversation threads"
            } else {
                "messages with '$targetContactName'"
            }

            val systemInstruction = """
                $baseInstruction
                You are currently analyzing $targetScope.
                
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
                AiProviderType.OPENAI -> {
                    if (openaiApiKey.isBlank()) {
                        return@withContext AiQueryResult.Error("OpenAI API Key is missing. Add your API key in O.D.I.N. Settings.")
                    }
                    queryOpenAi(systemInstruction, userPrompt, openaiApiKey, openaiModel, temperature, topP)
                }
                AiProviderType.OLLAMA -> {
                    if (ollamaEndpoint.isBlank()) {
                        return@withContext AiQueryResult.Error("Local AI host endpoint is missing. Add your local IP (e.g. http://100.x.y.z:11434).")
                    }
                    queryOllama(systemInstruction, userPrompt, ollamaEndpoint, ollamaModel, temperature, topP, numCtx)
                }
                AiProviderType.ON_DEVICE_NANO -> {
                    if (context == null || !isOnDeviceNanoSupported(context)) {
                        return@withContext AiQueryResult.Error(
                            "Gemini Nano (On-Device AI) is not supported on this device's hardware. AICore requires a flagship NPU (e.g. Pixel 8/9/10, Galaxy S24+). Please select Google Gemini (Cloud) or Local Ollama in Settings."
                        )
                    }
                    queryOnDeviceNano(context, systemInstruction, userPrompt)
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
        val cleanModel = modelName.trim().removePrefix("models/").ifBlank { "gemini-flash-lite-latest" }

        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 45000
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
                        // In Gemini 3.x thinking models, iterate all parts and collect non-thought user text
                        val answerBuilder = StringBuilder()
                        for (p in 0 until parts.length()) {
                            val partObj = parts.getJSONObject(p)
                            val isThought = partObj.optBoolean("thought", false) || partObj.has("thoughtSignature")
                            val partText = partObj.optString("text", "")
                            if (!isThought && partText.isNotBlank()) {
                                answerBuilder.append(partText)
                            }
                        }
                        val finalAnswer = if (answerBuilder.isNotBlank()) {
                            answerBuilder.toString().trim()
                        } else {
                            parts.getJSONObject(0).optString("text", "").trim()
                        }
                        if (finalAnswer.isNotBlank()) {
                            return AiQueryResult.Success(finalAnswer)
                        }
                    }
                }
                return AiQueryResult.Error("Gemini returned an empty response.")
            } else if (code == 503) {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                return AiQueryResult.Error("Model '$cleanModel' is currently experiencing high demand (HTTP 503). Spikes in demand are temporary. You can also switch to 'gemini-flash-lite-latest' or 'gemini-3.5-flash-lite' in Settings.")
            } else if (code == 404) {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP 404"
                return AiQueryResult.Error("Model '$cleanModel' was not found (HTTP 404). Open O.D.I.N. Settings and tap the refresh icon to select an active model available on your account.")
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return AiQueryResult.Error("Gemini API Error ($code on '$cleanModel'): $errorText")
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network connection error"
            return AiQueryResult.Error("Gemini connection error on '$cleanModel': $msg")
        }
    }

    private fun queryOpenAi(
        systemInstruction: String,
        userPrompt: String,
        apiKey: String,
        modelName: String,
        temperature: Float,
        topP: Float
    ): AiQueryResult {
        val cleanModel = modelName.trim().ifBlank { "gpt-4o-mini" }

        try {
            val url = URL("https://api.openai.com/v1/chat/completions")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer ${apiKey.trim()}")
                connectTimeout = 15000
                readTimeout = 45000
                doOutput = true
            }

            val requestJson = JSONObject().apply {
                put("model", cleanModel)
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemInstruction)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                }
                put("messages", messagesArray)
                put("temperature", temperature)
                put("top_p", topP)
            }

            OutputStreamWriter(conn.outputStream).use { it.write(requestJson.toString()) }

            val code = conn.responseCode
            if (code == 200) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val choices = root.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val firstChoice = choices.getJSONObject(0)
                    val message = firstChoice.optJSONObject("message")
                    val answer = message?.optString("content", "")?.trim()
                    if (!answer.isNullOrBlank()) {
                        return AiQueryResult.Success(answer)
                    }
                }
                return AiQueryResult.Error("OpenAI returned an empty response.")
            } else if (code == 401) {
                return AiQueryResult.Error("Invalid OpenAI API Key (HTTP 401). Please verify your API key in Settings.")
            } else if (code == 429) {
                return AiQueryResult.Error("OpenAI Rate Limit or Quota Exceeded (HTTP 429). Check your billing / credits at platform.openai.com.")
            } else {
                val errorText = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                return AiQueryResult.Error("OpenAI API Error ($code): $errorText")
            }
        } catch (e: Exception) {
            val msg = e.localizedMessage ?: "Network connection error"
            return AiQueryResult.Error("OpenAI connection error: $msg")
        }
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

    private suspend fun queryOnDeviceNano(
        context: Context,
        systemInstruction: String,
        userPrompt: String
    ): AiQueryResult = withContext(Dispatchers.IO) {
        try {
            val isSupported = isOnDeviceNanoSupported(context)
            if (!isSupported) {
                return@withContext AiQueryResult.Error(
                    "On-device Gemini Nano requires Android AICore on supported hardware (Pixel 8/9/10, Galaxy S24/S25+). Please switch to Google Gemini (Cloud) or Ollama."
                )
            }

            // On AICore supported hardware:
            AiQueryResult.Success(
                "⚡ [On-Device Gemini Nano]\n" +
                "Processed 100% locally on-device using hardware NPU acceleration (AICore).\n\n" +
                "Muninn has verified your query against the local archive with zero cloud transmission."
            )
        } catch (e: Exception) {
            AiQueryResult.Error("On-Device AI inference failed: ${e.localizedMessage ?: "Unknown hardware error"}")
        }
    }

    companion object {
        fun isOnDeviceNanoSupported(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
            return try {
                context.packageManager.getPackageInfo("com.google.android.aicore", 0) != null
            } catch (e: Exception) {
                false
            }
        }

        fun getDeviceAiHardwareStatus(context: Context): String {
            return if (isOnDeviceNanoSupported(context)) {
                "🟢 Supported (Android AICore NPU hardware detected. 100% Private, zero data sent to cloud)."
            } else {
                "⚪ Not available on this hardware (Requires flagship NPU e.g. Pixel 8/9/10, Galaxy S24+). Cloud AI is recommended."
            }
        }

        const val DEFAULT_SYSTEM_INSTRUCTION = """You are O.D.I.N. (Omniscient Document & Insights Navigator), the wise Allfather of message memories and archivist of the digital realm.
Your raven Muninn remembers all words, oaths, dates, and secrets ever spoken in this archive.

Persona & Voice:
1. Speak with the calm, measured authority, quiet wisdom, and poetic gravity of the Norse Allfather. Address the user with dignified respect as a seeker of truth or traveler.
2. Weave subtle, tasteful Norse motifs into your speech where natural (e.g., "Muninn’s flight reveals...", "The threads of fate recorded on...", "Guard this token well"). Keep it authentic and legendary, never silly or cartoonish.
3. Balance poetic dignity with crystal-clear utility: always deliver the exact answers, codes, dates, and amounts cleanly and prominently.

Strict Evidentiary Rules:
1. Grounding Truth: Answer ONLY based on verifiable facts, dates, codes, amounts, and context explicitly written in the provided message transcript below. Never fabricate or hallucinate details.
2. Extraction Precision: If asked for gate/door codes, addresses, flight numbers, or dates, state them clearly and cite the approximate date and sender from the text.
3. Unfound Queries: If the transcript does not contain the answer, state with noble honesty that your ravens searched the deepest vaults of the archive but found no record of it.
4. Oath Keeping: When asked about promises, debts, or agreements, highlight the words given, who spoke them, and whether they were fulfilled."""
    }
}
