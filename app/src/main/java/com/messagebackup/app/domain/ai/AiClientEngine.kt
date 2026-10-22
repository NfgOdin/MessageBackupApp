package com.messagebackup.app.domain.ai

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
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
import java.util.regex.Pattern

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
                    queryOnDeviceNano(context, baseInstruction, userPrompt, messages, targetContactName)
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
        context: Context?,
        baseInstruction: String,
        userPrompt: String,
        messages: List<MessageItem>,
        targetContactName: String
    ): AiQueryResult = withContext(Dispatchers.IO) {
        // 1. Pre-extract high-signal forensic matches locally from the full message archive
        val forensicReport = buildLocalForensicReport(userPrompt, messages, targetContactName)

        // 2. Build a compact context window tailored for Gemini Nano's 4,000-token NPU limit (~9,500 chars max)
        val nanoContext = buildNanoContextTranscript(userPrompt, messages, maxChars = 8500)

        // 3. Attempt real hardware NPU inference via Google ML Kit GenAI Prompt API (AICore / Gemini Nano)
        if (context != null && isOnDeviceNanoSupported(context)) {
            var generativeModel: com.google.mlkit.genai.prompt.GenerativeModel? = null
            try {
                generativeModel = Generation.getClient()
                val status = generativeModel.checkStatus()

                if (status == FeatureStatus.DOWNLOADABLE) {
                    // Trigger background model download if AICore has not yet cached weights
                    try {
                        generativeModel.download().collect { downloadStatus ->
                            if (downloadStatus is DownloadStatus.DownloadCompleted) {
                                // Ready
                            }
                        }
                    } catch (_: Exception) {
                        // Continue to attempt inference or fallback
                    }
                }

                if (status == FeatureStatus.AVAILABLE || status == FeatureStatus.DOWNLOADABLE) {
                    val targetScope = if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
                        "all conversations"
                    } else {
                        "messages with '$targetContactName'"
                    }

                    val nanoPrompt = buildString {
                        appendLine(baseInstruction)
                        appendLine()
                        appendLine("You are analyzing $targetScope on-device.")
                        if (forensicReport.hasMatches) {
                            appendLine("=== PRE-EXTRACTED FORENSIC MATCHES ===")
                            appendLine(forensicReport.rawSummaryForPrompt)
                            appendLine("=== END PRE-EXTRACTED MATCHES ===")
                        }
                        appendLine("=== RECENT & RELEVANT MESSAGE TRANSCRIPT ===")
                        appendLine(nanoContext)
                        appendLine("=== END TRANSCRIPT ===")
                        appendLine()
                        appendLine("User Question: $userPrompt")
                        appendLine("Answer clearly and concisely using ONLY the facts, dates, and messages above:")
                    }

                    val response = generativeModel.generateContent(nanoPrompt)
                    val generatedText = response.candidates.firstOrNull()?.text?.trim()
                    if (!generatedText.isNullOrBlank()) {
                        return@withContext AiQueryResult.Success(generatedText)
                    }
                }
            } catch (_: Throwable) {
                // If AICore throws a quota/token/binding exception, seamlessly use the Local On-Device Forensic Engine below
            } finally {
                try {
                    generativeModel?.close()
                } catch (_: Throwable) {}
            }
        }

        // 4. Deterministic On-Device Forensic Intelligence Engine (100% offline, instant, works even if AICore is downloading or unavailable)
        AiQueryResult.Success(forensicReport.formattedUserResponse)
    }

    private data class LocalForensicReport(
        val hasMatches: Boolean,
        val rawSummaryForPrompt: String,
        val formattedUserResponse: String
    )

    private fun buildNanoContextTranscript(
        userPrompt: String,
        messages: List<MessageItem>,
        maxChars: Int = 8500
    ): String {
        val queryWords = userPrompt.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }

        // Prioritize messages matching query keywords or patterns, plus the most recent 60 messages
        val scored = messages.map { msg ->
            val lower = msg.body.lowercase()
            var score = 0
            for (w in queryWords) {
                if (lower.contains(w)) score += 3
            }
            if (ADDRESS_PATTERN.matcher(msg.body).find()) score += 2
            if (CODE_PATTERN.matcher(msg.body).find()) score += 2
            if (FLIGHT_PATTERN.matcher(msg.body).find()) score += 2
            if (DATE_TIME_PATTERN.matcher(msg.body).find()) score += 1
            Pair(msg, score)
        }

        val topRelevant = scored.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MessageItem, Int>> { it.second }.thenByDescending { it.first.timestamp })
            .take(50)
            .map { it.first }

        val recent = messages.takeLast(60)
        val combined = (topRelevant + recent)
            .distinctBy { "${it.timestamp}_${it.body.hashCode()}" }
            .sortedBy { it.timestamp }

        val sb = StringBuilder()
        // Build from newest to oldest so we never drop the most recent messages if truncated, then reverse lines
        val lines = mutableListOf<String>()
        var currentChars = 0
        for (msg in combined.asReversed()) {
            val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
            val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender
            val cleanBody = msg.body.replace("\n", " ").trim()
            val line = "[$dateStr] $sender: $cleanBody\n"
            if (currentChars + line.length > maxChars) break
            lines.add(line)
            currentChars += line.length
        }
        for (line in lines.asReversed()) {
            sb.append(line)
        }
        return sb.toString()
    }

    private fun buildLocalForensicReport(
        userPrompt: String,
        messages: List<MessageItem>,
        targetContactName: String
    ): LocalForensicReport {
        val lowerPrompt = userPrompt.lowercase()
        val contactLabel = if (targetContactName.isBlank() || targetContactName == "All Conversations") {
            "your archive"
        } else {
            targetContactName
        }

        val isAddressQuery = lowerPrompt.contains("address") || lowerPrompt.contains("direction") ||
            lowerPrompt.contains("where") || lowerPrompt.contains("location") || lowerPrompt.contains("street") ||
            lowerPrompt.contains("live") || lowerPrompt.contains("house") || lowerPrompt.contains("apt")

        val isCodeQuery = lowerPrompt.contains("code") || lowerPrompt.contains("gate") ||
            lowerPrompt.contains("door") || lowerPrompt.contains("wifi") || lowerPrompt.contains("wi-fi") ||
            lowerPrompt.contains("password") || lowerPrompt.contains("pin") || lowerPrompt.contains("lock") ||
            lowerPrompt.contains("alarm") || lowerPrompt.contains("key")

        val isFlightQuery = lowerPrompt.contains("flight") || lowerPrompt.contains("hotel") ||
            lowerPrompt.contains("reservation") || lowerPrompt.contains("booking") ||
            lowerPrompt.contains("airline") || lowerPrompt.contains("airport") || lowerPrompt.contains("confirmation")

        val isDateCommitmentQuery = lowerPrompt.contains("date") || lowerPrompt.contains("deadline") ||
            lowerPrompt.contains("commitment") || lowerPrompt.contains("promise") ||
            lowerPrompt.contains("schedule") || lowerPrompt.contains("appointment") ||
            lowerPrompt.contains("meet") || lowerPrompt.contains("when")

        val isSummaryQuery = lowerPrompt.contains("summarize") || lowerPrompt.contains("summary") ||
            lowerPrompt.contains("bullet") || lowerPrompt.contains("recap") || lowerPrompt.contains("overview")

        val isDraftReplyQuery = lowerPrompt.contains("draft") || lowerPrompt.contains("reply") ||
            lowerPrompt.contains("follow-up") || lowerPrompt.contains("follow up") || lowerPrompt.contains("respond")

        val matchedEntries = mutableListOf<String>()

        if (isAddressQuery) {
            for (msg in messages.asReversed()) {
                val body = msg.body.trim()
                if (body.length < 6) continue
                val hasAddressMatch = ADDRESS_PATTERN.matcher(body).find() ||
                    MAPS_URL_PATTERN.matcher(body).find() ||
                    DIRECTION_KEYWORDS_PATTERN.matcher(body).find()
                if (hasAddressMatch) {
                    val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                    matchedEntries.add("• **[$dateStr] $sender:** \"$body\"")
                    if (matchedEntries.size >= 12) break
                }
            }
        } else if (isCodeQuery) {
            for (msg in messages.asReversed()) {
                val body = msg.body.trim()
                if (body.isEmpty()) continue
                val hasCodeMatch = CODE_CONTEXT_PATTERN.matcher(body).find() ||
                    (CODE_KEYWORD_ONLY_PATTERN.matcher(body).find() && DIGIT_PIN_PATTERN.matcher(body).find()) ||
                    HASH_STAR_CODE_PATTERN.matcher(body).find()
                if (hasCodeMatch) {
                    val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                    matchedEntries.add("• **[$dateStr] $sender:** \"$body\"")
                    if (matchedEntries.size >= 12) break
                }
            }
        } else if (isFlightQuery) {
            for (msg in messages.asReversed()) {
                val body = msg.body.trim()
                if (body.length < 5) continue
                if (FLIGHT_PATTERN.matcher(body).find()) {
                    val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                    matchedEntries.add("• **[$dateStr] $sender:** \"$body\"")
                    if (matchedEntries.size >= 12) break
                }
            }
        } else if (isDateCommitmentQuery) {
            val radar = CommitmentRadarEngine(this)
            val commitments = radar.scanLocalCommitments(messages, contactLabel)
            for (c in commitments.take(8)) {
                val dateStr = dateFormatter.format(Instant.ofEpochMilli(c.timestamp))
                val who = if (c.isMadeByMe) "Me" else c.contactName
                val dueTag = if (!c.dueDate.isNullOrBlank()) " *(Due: ${c.dueDate})*" else ""
                matchedEntries.add("• **[$dateStr] $who:** \"${c.rawSnippet}\"$dueTag")
            }
            if (matchedEntries.size < 8) {
                for (msg in messages.asReversed()) {
                    val body = msg.body.trim()
                    if (DATE_TIME_PATTERN.matcher(body).find()) {
                        val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                        val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                        val entry = "• **[$dateStr] $sender:** \"$body\""
                        if (!matchedEntries.contains(entry)) {
                            matchedEntries.add(entry)
                            if (matchedEntries.size >= 12) break
                        }
                    }
                }
            }
        } else if (isSummaryQuery) {
            val totalCount = messages.size
            val incomingCount = messages.count { it.direction == MessageDirection.INCOMING }
            val outgoingCount = totalCount - incomingCount
            val recentSlice = messages.takeLast(30)
            val firstDate = messages.firstOrNull()?.let { dateFormatter.format(Instant.ofEpochMilli(it.timestamp)) } ?: "N/A"
            val lastDate = messages.lastOrNull()?.let { dateFormatter.format(Instant.ofEpochMilli(it.timestamp)) } ?: "N/A"

            val highlights = recentSlice.filter { it.body.trim().length > 15 }
                .takeLast(5)
                .map { msg ->
                    val d = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val s = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                    "  - [$d] **$s**: \"${msg.body.trim().take(120)}\""
                }

            val summaryText = buildString {
                appendLine("⚡ **O.D.I.N. On-Device Thread Summary ($contactLabel):**")
                appendLine("• **Archive Span:** $totalCount messages ($incomingCount received, $outgoingCount sent) from **$firstDate** to **$lastDate**.")
                val radarCount = CommitmentRadarEngine(this@AiClientEngine).scanLocalCommitments(messages, contactLabel).size
                val addressHits = messages.count { ADDRESS_PATTERN.matcher(it.body).find() }
                val codeHits = messages.count { CODE_CONTEXT_PATTERN.matcher(it.body).find() }
                appendLine("• **Detected Key Signals:** $radarCount commitments/tasks, $addressHits location/address mentions, and $codeHits code/PIN references.")
                if (highlights.isNotEmpty()) {
                    appendLine("• **Latest Key Exchanges:**")
                    highlights.forEach { appendLine(it) }
                }
            }.trim()

            return LocalForensicReport(
                hasMatches = true,
                rawSummaryForPrompt = summaryText,
                formattedUserResponse = summaryText
            )
        } else if (isDraftReplyQuery) {
            val lastIncoming = messages.lastOrNull { it.direction == MessageDirection.INCOMING && it.body.isNotBlank() }
            val lastMsgPreview = lastIncoming?.body?.trim()?.take(100) ?: "your last message"
            val replyDraft = buildString {
                appendLine("⚡ **O.D.I.N. On-Device Suggested Replies for $contactLabel:**")
                if (lastIncoming != null) {
                    val d = dateFormatter.format(Instant.ofEpochMilli(lastIncoming.timestamp))
                    appendLine("*Replying to [$d]: \"$lastMsgPreview\"*\n")
                }
                appendLine("**Option 1 (Warm & Polite):**")
                appendLine("\"Hey! Just following up on your message—thanks for reaching out. Let me know what time works best for you!\"\n")
                appendLine("**Option 2 (Quick & Direct):**")
                appendLine("\"Got your message! Everything looks good on my end—I'll keep you posted shortly.\"")
            }.trim()

            return LocalForensicReport(
                hasMatches = true,
                rawSummaryForPrompt = replyDraft,
                formattedUserResponse = replyDraft
            )
        }

        // General keyword search if no specific intent matched or if intent returned 0 matches
        if (matchedEntries.isEmpty()) {
            val keywords = userPrompt.lowercase()
                .replace(Regex("[^a-z0-9 ]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 3 && it !in STOP_WORDS }

            if (keywords.isNotEmpty()) {
                val scored = messages.mapNotNull { msg ->
                    val lower = msg.body.lowercase()
                    val hitCount = keywords.count { k -> lower.contains(k) }
                    if (hitCount > 0) Pair(msg, hitCount) else null
                }.sortedWith(compareByDescending<Pair<MessageItem, Int>> { it.second }.thenByDescending { it.first.timestamp })

                for ((msg, _) in scored.take(10)) {
                    val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                    matchedEntries.add("• **[$dateStr] $sender:** \"${msg.body.trim()}\"")
                }
            }
        }

        val categoryTitle = when {
            isAddressQuery -> "Addresses & Directions"
            isCodeQuery -> "Gate, Door, Wi-Fi & Security Codes"
            isFlightQuery -> "Flights, Bookings & Reservations"
            isDateCommitmentQuery -> "Dates, Deadlines & Commitments"
            else -> "Matching Archive Records"
        }

        return if (matchedEntries.isNotEmpty()) {
            val joined = matchedEntries.joinToString("\n")
            val formatted = buildString {
                appendLine("⚡ **O.D.I.N. On-Device Scan — $categoryTitle ($contactLabel):**")
                appendLine("Muninn searched ${messages.size} messages locally and found ${matchedEntries.size} matching record(s):\n")
                appendLine(joined)
            }.trim()
            LocalForensicReport(
                hasMatches = true,
                rawSummaryForPrompt = joined,
                formattedUserResponse = formatted
            )
        } else {
            val recentFallback = messages.takeLast(5).reversed().joinToString("\n") { msg ->
                val d = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                val s = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                "• **[$d] $s:** \"${msg.body.trim().take(120)}\""
            }
            val noMatchText = buildString {
                appendLine("⚡ **O.D.I.N. On-Device Scan — $categoryTitle ($contactLabel):**")
                appendLine("Muninn searched all **${messages.size} messages** with $contactLabel on-device, but found no explicit **$categoryTitle** recorded in this thread.")
                if (recentFallback.isNotEmpty()) {
                    appendLine("\n**Most Recent Messages in Thread:**")
                    appendLine(recentFallback)
                }
            }.trim()
            LocalForensicReport(
                hasMatches = false,
                rawSummaryForPrompt = "No exact regex matches found for $categoryTitle.",
                formattedUserResponse = noMatchText
            )
        }
    }

    companion object {
        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "that", "this", "from", "have", "what", "when",
            "where", "which", "who", "whom", "whose", "why", "how", "are", "was", "were",
            "been", "being", "can", "could", "should", "would", "will", "shall", "may",
            "might", "must", "find", "list", "show", "tell", "give", "about", "into",
            "your", "their", "our", "any", "all", "some", "messages", "message", "thread"
        )

        private val ADDRESS_PATTERN = Pattern.compile(
            "(?i)\\b\\d{1,6}\\s+[A-Za-z0-9.\\s]{2,35}\\b(st|street|ave|avenue|rd|road|blvd|boulevard|dr|drive|ln|lane|ct|court|way|pl|place|pkwy|parkway|cir|circle|hwy|highway|apt|suite|ste|unit)\\b"
        )

        private val MAPS_URL_PATTERN = Pattern.compile(
            "(?i)(maps\\.app\\.goo\\.gl|google\\.com/maps|maps\\.google\\.com|waze\\.com/ul|maps\\.apple\\.com)"
        )

        private val DIRECTION_KEYWORDS_PATTERN = Pattern.compile(
            "(?i)\\b(address is|located at|my address|our address|turn left|turn right|exit\\s+\\d+|park in the|parking|gate is on|building\\s+[a-z0-9]+)\\b"
        )

        private val CODE_CONTEXT_PATTERN = Pattern.compile(
            "(?i)\\b(code|gate|door|wifi|wi-fi|password|passcode|pin|lockbox|keypad|alarm|entry|access|network)\\b[^.\\n]{0,35}?([#*]\\d{3,8}[#*]?|\\b\\d{4,8}\\b|[A-Za-z0-9!@#\$%^&*_-]{6,24})"
        )

        private val CODE_KEYWORD_ONLY_PATTERN = Pattern.compile(
            "(?i)\\b(code|gate|door|wifi|wi-fi|password|passcode|pin|lockbox|keypad|alarm|entry)\\b"
        )

        private val DIGIT_PIN_PATTERN = Pattern.compile("\\b\\d{4,8}\\b")

        private val HASH_STAR_CODE_PATTERN = Pattern.compile("(?:[#*]\\d{3,8}[#*]?|\\d{3,8}[#*])")

        private val CODE_PATTERN = CODE_CONTEXT_PATTERN

        private val FLIGHT_PATTERN = Pattern.compile(
            "(?i)\\b(flight|airline|airport|boarding|terminal|gate\\s+[a-z]?\\d{1,3}|confirmation|reservation|booking|hotel|check-in|check in|airbnb|vrbo|delta|united|american airlines|southwest|jetblue|alaska air|[A-Z]{2}\\s?\\d{3,4})\\b"
        )

        private val DATE_TIME_PATTERN = Pattern.compile(
            "(?i)\\b(monday|tuesday|wednesday|thursday|friday|saturday|sunday|tomorrow|tonight|this weekend|next week|january|february|march|april|may|june|july|august|september|october|november|december|\\d{1,2}/\\d{1,2}(/\\d{2,4})?|\\d{1,2}:\\d{2}\\s?(am|pm)?)\\b"
        )

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
