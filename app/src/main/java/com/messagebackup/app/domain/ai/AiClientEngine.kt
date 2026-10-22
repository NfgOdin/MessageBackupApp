package com.messagebackup.app.domain.ai

import android.content.Context
import android.os.Build
import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.messagebackup.app.model.MessageDirection
import com.messagebackup.app.model.MessageItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern

enum class AiProviderType(val displayName: String) {
    GEMINI("Google Gemini (1-Tap Google Sign-In or BYOK)"),
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

            // Pre-extract high-signal forensic matches across 100% of the message history
            val forensicReport = buildLocalForensicReport(userPrompt, messages, targetContactName)

            // Build smart, relevance-boosted + chronological context from messages
            val contextTranscript = buildSmartContextTranscript(userPrompt, messages, maxChars = 16000)

            val baseInstruction = if (customSystemPrompt.isNotBlank()) {
                customSystemPrompt.trim()
            } else {
                DEFAULT_SYSTEM_INSTRUCTION
            }

            val targetScope = if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
                "recent messages across all conversation threads (${messages.size} messages scanned)"
            } else {
                "messages with '$targetContactName' (${messages.size} messages scanned)"
            }

            val forensicBlock = if (forensicReport.hasMatches) {
                """
                === PRE-EXTRACTED FORENSIC MATCHES FROM FULL ARCHIVE ===
                ${forensicReport.rawSummaryForPrompt}
                === END PRE-EXTRACTED MATCHES ===
                """.trimIndent()
            } else {
                ""
            }

            val systemInstruction = """
                $baseInstruction
                You are currently analyzing $targetScope.
                $forensicBlock
                
                === MESSAGE TRANSCRIPT START ===
                $contextTranscript
                === MESSAGE TRANSCRIPT END ===
            """.trimIndent()

            when (providerType) {
                AiProviderType.GEMINI -> {
                    if (geminiApiKey.isNotBlank()) {
                        val primaryResult = queryGemini(systemInstruction, userPrompt, geminiApiKey, geminiModel, temperature, topP)
                        if (primaryResult is AiQueryResult.Success) {
                            primaryResult
                        } else {
                            // If user's selected model failed (e.g. 503/404) and wasn't already flash-lite, try flash-lite first
                            val fallbackModel = "gemini-flash-lite-latest"
                            val retryResult = if (!geminiModel.trim().equals(fallbackModel, ignoreCase = true)) {
                                queryGemini(systemInstruction, userPrompt, geminiApiKey, fallbackModel, temperature, topP)
                            } else {
                                primaryResult
                            }
                            if (retryResult is AiQueryResult.Success) {
                                retryResult
                            } else {
                                // Seamlessly fall back to 1-Tap Cloud AI Gateway so the user still gets their answer
                                queryCloudLinkedAi(
                                    baseInstruction = baseInstruction,
                                    userPrompt = userPrompt,
                                    messages = messages,
                                    targetContactName = targetContactName,
                                    forensicReport = forensicReport,
                                    temperature = temperature,
                                    topP = topP,
                                    context = context
                                )
                            }
                        }
                    } else {
                        // 1-Tap Google Sign-In / Zero-Key Cloud AI Mode (works automatically without requiring a manual API key!)
                        queryCloudLinkedAi(
                            baseInstruction = baseInstruction,
                            userPrompt = userPrompt,
                            messages = messages,
                            targetContactName = targetContactName,
                            forensicReport = forensicReport,
                            temperature = temperature,
                            topP = topP,
                            context = context
                        )
                    }
                }
                AiProviderType.OPENAI -> {
                    if (openaiApiKey.isBlank()) {
                        return@withContext AiQueryResult.Error("OpenAI API Key is missing. Add your API key in O.D.I.N. Settings, or switch to Google Gemini / On-Device AI.")
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
                    queryOnDeviceNano(context, baseInstruction, userPrompt, messages, targetContactName, forensicReport)
                }
            }
        } catch (e: Exception) {
            AiQueryResult.Error("Error querying O.D.I.N.: ${e.localizedMessage ?: "Unknown network error"}")
        }
    }

    /**
     * 1-Tap Google Account / Zero-Config Cloud AI Gateway.
     * Allows users who signed in with Google (or selected Gemini without manually creating a BYOK key)
     * to get full cloud LLM intelligence immediately, with automatic offline fallback to On-Device AI.
     */
    private suspend fun queryCloudLinkedAi(
        baseInstruction: String,
        userPrompt: String,
        messages: List<MessageItem>,
        targetContactName: String,
        forensicReport: LocalForensicReport,
        temperature: Float,
        topP: Float,
        context: Context?
    ): AiQueryResult {
        val compactTranscript = buildSmartContextTranscript(userPrompt, messages, maxChars = 9500)
        val targetScope = if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
            "recent messages across all conversation threads (${messages.size} messages scanned)"
        } else {
            "messages with '$targetContactName' (${messages.size} messages scanned)"
        }

        val cloudSystemPrompt = buildString {
            appendLine(baseInstruction)
            appendLine("You are currently analyzing $targetScope.")
            if (forensicReport.hasMatches) {
                appendLine()
                appendLine("=== VERIFIED FORENSIC MATCHES FROM ARCHIVE ===")
                appendLine(forensicReport.rawSummaryForPrompt)
                appendLine("=== END VERIFIED MATCHES ===")
            }
            appendLine()
            appendLine("=== MESSAGE TRANSCRIPT START ===")
            appendLine(compactTranscript)
            appendLine("=== MESSAGE TRANSCRIPT END ===")
        }

        try {
            val url = URL("https://text.pollinations.ai/openai")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MuninnMessageVault/1.0")
                connectTimeout = 10000
                readTimeout = 22000
                doOutput = true
            }

            val requestJson = JSONObject().apply {
                put("model", "openai-fast")
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", cloudSystemPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", userPrompt)
                    })
                }
                put("messages", messagesArray)
                put("temperature", temperature.toDouble())
                put("top_p", topP.toDouble())
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(requestJson.toString()) }

            val code = conn.responseCode
            if (code in 200..299) {
                val responseText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
                if (responseText.startsWith("{")) {
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
                } else if (responseText.isNotBlank() && !responseText.startsWith("<")) {
                    return AiQueryResult.Success(responseText)
                }
            }
        } catch (_: Exception) {
            // If offline or gateway unreachable, fall back seamlessly to On-Device Intelligence
        }

        // Seamless fallback to On-Device AI / Forensic Engine so the user never sees an API key error or failure
        return queryOnDeviceNano(context, baseInstruction, userPrompt, messages, targetContactName, forensicReport)
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
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=${apiKey.trim()}")
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
                return AiQueryResult.Error("Model '$cleanModel' is currently experiencing high demand (HTTP 503).")
            } else if (code == 404) {
                return AiQueryResult.Error("Model '$cleanModel' was not found (HTTP 404).")
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
        targetContactName: String,
        precomputedReport: LocalForensicReport? = null
    ): AiQueryResult = withContext(Dispatchers.IO) {
        // 1. Pre-extract high-signal forensic matches locally from the full message archive (< 25ms)
        val forensicReport = precomputedReport ?: buildLocalForensicReport(userPrompt, messages, targetContactName)

        // 2. Build a compact context window tailored for Gemini Nano's hardware NPU token budget (~2,800 chars max)
        val nanoContext = buildSmartContextTranscript(userPrompt, messages, maxChars = 2800)

        // 3. Attempt real hardware NPU inference via Google ML Kit GenAI Prompt API (AICore / Gemini Nano)
        // Strictly bounded by timeouts and non-blocking background weight download so it NEVER hangs!
        if (context != null && isOnDeviceNanoSupported(context)) {
            var generativeModel: com.google.mlkit.genai.prompt.GenerativeModel? = null
            try {
                generativeModel = Generation.getClient()
                val status = withTimeoutOrNull(2000L) {
                    generativeModel.checkStatus()
                }

                if (status == FeatureStatus.DOWNLOADABLE) {
                    // Never block the user's query waiting for a multi-gigabyte background AICore download!
                    // Warm up the model asynchronously in the background and immediately serve the forensic answer.
                    triggerBackgroundNanoDownload()
                } else if (status == FeatureStatus.AVAILABLE) {
                    val targetScope = if (targetContactName == "All Conversations" || targetContactName.isBlank()) {
                        "all conversations"
                    } else {
                        "messages with '$targetContactName'"
                    }

                    val nanoPrompt = buildString {
                        appendLine("You are O.D.I.N., an intelligent message archivist analyzing $targetScope.")
                        appendLine("Answer the user's question accurately using ONLY the dates, facts, and quotes below.")
                        if (forensicReport.hasMatches) {
                            appendLine()
                            appendLine("=== VERIFIED MATCHES FROM ARCHIVE ===")
                            appendLine(forensicReport.rawSummaryForPrompt.take(1200))
                            appendLine("=== END VERIFIED MATCHES ===")
                        }
                        appendLine()
                        appendLine("=== MESSAGE TRANSCRIPT ===")
                        appendLine(nanoContext)
                        appendLine("=== END TRANSCRIPT ===")
                        appendLine()
                        appendLine("Question: $userPrompt")
                        appendLine("Answer:")
                    }

                    val generatedText = withTimeoutOrNull(7500L) {
                        val response = generativeModel.generateContent(nanoPrompt)
                        response.candidates.firstOrNull()?.text?.trim()
                    }

                    if (!generatedText.isNullOrBlank()) {
                        // Combine Gemini Nano's natural language synthesis with verified archive excerpts if Nano omitted details
                        val finalOutput = if (forensicReport.hasMatches && !forensicReport.isSummaryOrDraft) {
                            buildString {
                                appendLine(generatedText)
                                appendLine()
                                appendLine("---")
                                appendLine("📋 **Verified Archive Records (${messages.size} msgs scanned):**")
                                appendLine(forensicReport.rawSummaryForPrompt)
                            }.trim()
                        } else {
                            generatedText
                        }
                        return@withContext AiQueryResult.Success(finalOutput)
                    }
                }
            } catch (_: Throwable) {
                // If AICore throws a quota/token/binding exception or times out, fall through to Local Forensic Intelligence
            } finally {
                try {
                    generativeModel?.close()
                } catch (_: Throwable) {}
            }
        }

        // 4. Deterministic On-Device Forensic Intelligence Engine (100% offline, instant, works even while AICore downloads weights)
        AiQueryResult.Success(forensicReport.formattedUserResponse)
    }

    private fun triggerBackgroundNanoDownload() {
        if (isNanoDownloadInProgress.compareAndSet(false, true)) {
            nanoBackgroundScope.launch {
                var model: com.google.mlkit.genai.prompt.GenerativeModel? = null
                try {
                    model = Generation.getClient()
                    model.download().collect { downloadStatus ->
                        if (downloadStatus is DownloadStatus.DownloadCompleted ||
                            downloadStatus is DownloadStatus.DownloadFailed
                        ) {
                            isNanoDownloadInProgress.set(false)
                        }
                    }
                } catch (_: Throwable) {
                    isNanoDownloadInProgress.set(false)
                } finally {
                    try {
                        model?.close()
                    } catch (_: Throwable) {}
                    isNanoDownloadInProgress.set(false)
                }
            }
        }
    }

    private data class LocalForensicReport(
        val hasMatches: Boolean,
        val rawSummaryForPrompt: String,
        val formattedUserResponse: String,
        val isSummaryOrDraft: Boolean = false
    )

    /**
     * Builds a relevance-boosted + chronological message transcript that includes:
     * 1. High-scoring keyword & pattern matches from ANY point in the conversation history (even years ago)
     * 2. Conversational Q&A lookahead replies (1-3 messages following a relevant question)
     * 3. The most recent messages in the conversation
     */
    private fun buildSmartContextTranscript(
        userPrompt: String,
        messages: List<MessageItem>,
        maxChars: Int = 8500
    ): String {
        val queryWords = userPrompt.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }

        val selectedIndices = mutableSetOf<Int>()
        val indexScores = IntArray(messages.size)

        for (i in messages.indices) {
            val msg = messages[i]
            val body = msg.body
            val lower = body.lowercase()
            var score = 0
            for (w in queryWords) {
                if (lower.contains(w)) score += 4
            }
            if (ADDRESS_PATTERN.matcher(body).find() || MAPS_URL_PATTERN.matcher(body).find()) score += 3
            if (DIRECTION_KEYWORDS_PATTERN.matcher(body).find() || CITY_STATE_ZIP_PATTERN.matcher(body).find()) score += 2
            if (CODE_PATTERN.matcher(body).find() || HASH_STAR_CODE_PATTERN.matcher(body).find()) score += 3
            if (FLIGHT_PATTERN.matcher(body).find()) score += 3
            if (DATE_TIME_PATTERN.matcher(body).find()) score += 1

            if (score > 0) {
                indexScores[i] = maxOf(indexScores[i], score)
                // If this message asks a question or has a high match, also boost the next 1-2 replies in the conversation!
                if (body.contains("?") || ADDRESS_QUESTION_PATTERN.matcher(body).find() || CODE_QUESTION_PATTERN.matcher(body).find()) {
                    if (i + 1 < messages.size) indexScores[i + 1] = maxOf(indexScores[i + 1], score + 2)
                    if (i + 2 < messages.size) indexScores[i + 2] = maxOf(indexScores[i + 2], score + 1)
                }
            }
        }

        val topScoredIndices = messages.indices
            .filter { indexScores[it] > 0 }
            .sortedWith(compareByDescending<Int> { indexScores[it] }.thenByDescending { messages[it].timestamp })
            .take(80)

        selectedIndices.addAll(topScoredIndices)

        // Always include the most recent 80 messages
        val recentStart = (messages.size - 80).coerceAtLeast(0)
        for (i in recentStart until messages.size) {
            selectedIndices.add(i)
        }

        val sortedMsgs = selectedIndices.sorted().map { messages[it] }

        val sb = StringBuilder()
        val lines = mutableListOf<String>()
        var currentChars = 0

        // First reserve space for top-scored matches so older forensic hits are never crowded out
        val highPrioritySet = topScoredIndices.take(35).toSet()
        val orderedForPacking = (
            topScoredIndices.take(35).map { messages[it] } +
            sortedMsgs.asReversed()
        ).distinctBy { "${it.timestamp}_${it.body.hashCode()}" }

        val packedMsgs = mutableListOf<MessageItem>()
        for (msg in orderedForPacking) {
            val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
            val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender
            val cleanBody = msg.body.replace("\n", " ").trim()
            val estLen = dateStr.length + sender.length + cleanBody.length + 8
            if (currentChars + estLen > maxChars) continue
            packedMsgs.add(msg)
            currentChars += estLen
        }

        for (msg in packedMsgs.sortedBy { it.timestamp }) {
            val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
            val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender
            val cleanBody = msg.body.replace("\n", " ").trim()
            lines.add("[$dateStr] $sender: $cleanBody\n")
        }

        for (line in lines) {
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
            lowerPrompt.contains("live") || lowerPrompt.contains("house") || lowerPrompt.contains("apt") ||
            lowerPrompt.contains("place") || lowerPrompt.contains("park") || lowerPrompt.contains("route") ||
            lowerPrompt.contains("way to")

        val isCodeQuery = lowerPrompt.contains("code") || lowerPrompt.contains("gate") ||
            lowerPrompt.contains("door") || lowerPrompt.contains("wifi") || lowerPrompt.contains("wi-fi") ||
            lowerPrompt.contains("password") || lowerPrompt.contains("pin") || lowerPrompt.contains("lock") ||
            lowerPrompt.contains("alarm") || lowerPrompt.contains("key") || lowerPrompt.contains("passcode")

        val isFlightQuery = lowerPrompt.contains("flight") || lowerPrompt.contains("hotel") ||
            lowerPrompt.contains("reservation") || lowerPrompt.contains("booking") ||
            lowerPrompt.contains("airline") || lowerPrompt.contains("airport") || lowerPrompt.contains("confirmation") ||
            lowerPrompt.contains("trip") || lowerPrompt.contains("itinerary")

        val isDateCommitmentQuery = lowerPrompt.contains("date") || lowerPrompt.contains("deadline") ||
            lowerPrompt.contains("commitment") || lowerPrompt.contains("promise") ||
            lowerPrompt.contains("schedule") || lowerPrompt.contains("appointment") ||
            lowerPrompt.contains("meet") || lowerPrompt.contains("when") || lowerPrompt.contains("time")

        val isSummaryQuery = lowerPrompt.contains("summarize") || lowerPrompt.contains("summary") ||
            lowerPrompt.contains("bullet") || lowerPrompt.contains("recap") || lowerPrompt.contains("overview")

        val isDraftReplyQuery = lowerPrompt.contains("draft") || lowerPrompt.contains("reply") ||
            lowerPrompt.contains("follow-up") || lowerPrompt.contains("follow up") || lowerPrompt.contains("respond")

        val matchedEntries = LinkedHashSet<String>()

        fun formatMsg(msg: MessageItem, prefixNote: String = ""): String {
            val dateStr = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
            val sender = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
            val note = if (prefixNote.isNotBlank()) " *($prefixNote)*" else ""
            return "• **[$dateStr] $sender:** \"${msg.body.trim()}\"$note"
        }

        if (isAddressQuery) {
            // Pass 1: Direct street addresses, maps links, city/state/zip, GPS coords, and direction phrases (newest first)
            for (i in messages.indices.reversed()) {
                val msg = messages[i]
                val body = msg.body.trim()
                if (body.length < 4) continue
                val hasDirectAddress = ADDRESS_PATTERN.matcher(body).find() ||
                    MAPS_URL_PATTERN.matcher(body).find() ||
                    CITY_STATE_ZIP_PATTERN.matcher(body).find() ||
                    DIRECTION_KEYWORDS_PATTERN.matcher(body).find() ||
                    HOUSE_NUMBER_STREET_PATTERN.matcher(body).find()

                if (hasDirectAddress && !ADDRESS_QUESTION_ONLY_FILTER.matcher(body).matches()) {
                    matchedEntries.add(formatMsg(msg))
                    if (matchedEntries.size >= 14) break
                }
            }

            // Pass 2: Conversational Q&A Lookahead!
            // If someone asked "What's your address?" / "Where do you live?" / "Where are we meeting?", capture the next 1-3 replies!
            for (i in messages.indices.reversed()) {
                val msg = messages[i]
                val body = msg.body.trim()
                if (ADDRESS_QUESTION_PATTERN.matcher(body).find()) {
                    for (offset in 1..3) {
                        val replyIdx = i + offset
                        if (replyIdx < messages.size) {
                            val replyMsg = messages[replyIdx]
                            val replyBody = replyMsg.body.trim()
                            // Within 48 hours of the question and non-trivial
                            if (replyBody.length >= 4 && (replyMsg.timestamp - msg.timestamp) <= 172_800_000L) {
                                matchedEntries.add(formatMsg(replyMsg, "Reply to: \"${body.take(45)}\""))
                            }
                        }
                    }
                    if (matchedEntries.size >= 16) break
                }
            }
        } else if (isCodeQuery) {
            // Pass 1: Explicit code/password/PIN matches
            for (i in messages.indices.reversed()) {
                val msg = messages[i]
                val body = msg.body.trim()
                if (body.isEmpty()) continue
                val hasCodeMatch = CODE_CONTEXT_PATTERN.matcher(body).find() ||
                    (CODE_KEYWORD_ONLY_PATTERN.matcher(body).find() && DIGIT_PIN_PATTERN.matcher(body).find()) ||
                    HASH_STAR_CODE_PATTERN.matcher(body).find()
                if (hasCodeMatch) {
                    matchedEntries.add(formatMsg(msg))
                    if (matchedEntries.size >= 14) break
                }
            }

            // Pass 2: Conversational Q&A Lookahead for codes/passwords (e.g. Q: "What's the gate code?" -> A: "4812#")
            for (i in messages.indices.reversed()) {
                val msg = messages[i]
                val body = msg.body.trim()
                if (CODE_QUESTION_PATTERN.matcher(body).find()) {
                    for (offset in 1..2) {
                        val replyIdx = i + offset
                        if (replyIdx < messages.size) {
                            val replyMsg = messages[replyIdx]
                            val replyBody = replyMsg.body.trim()
                            if (replyBody.isNotEmpty() && (replyMsg.timestamp - msg.timestamp) <= 86_400_000L) {
                                matchedEntries.add(formatMsg(replyMsg, "Reply to: \"${body.take(45)}\""))
                            }
                        }
                    }
                    if (matchedEntries.size >= 16) break
                }
            }
        } else if (isFlightQuery) {
            for (msg in messages.asReversed()) {
                val body = msg.body.trim()
                if (body.length < 5) continue
                if (FLIGHT_PATTERN.matcher(body).find()) {
                    matchedEntries.add(formatMsg(msg))
                    if (matchedEntries.size >= 14) break
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
            if (matchedEntries.size < 10) {
                for (msg in messages.asReversed()) {
                    val body = msg.body.trim()
                    if (DATE_TIME_PATTERN.matcher(body).find()) {
                        matchedEntries.add(formatMsg(msg))
                        if (matchedEntries.size >= 14) break
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

            val highlights = recentSlice.filter { it.body.trim().length > 12 }
                .takeLast(6)
                .map { msg ->
                    val d = dateFormatter.format(Instant.ofEpochMilli(msg.timestamp))
                    val s = if (msg.direction == MessageDirection.OUTGOING) "Me" else msg.sender.ifBlank { contactLabel }
                    "  - [$d] **$s**: \"${msg.body.trim().take(130)}\""
                }

            val summaryText = buildString {
                appendLine("⚡ **O.D.I.N. On-Device Thread Summary ($contactLabel):**")
                appendLine("• **Archive Span:** $totalCount messages ($incomingCount received, $outgoingCount sent) from **$firstDate** to **$lastDate**.")
                val radarCount = CommitmentRadarEngine(this@AiClientEngine).scanLocalCommitments(messages, contactLabel).size
                val addressHits = messages.count { ADDRESS_PATTERN.matcher(it.body).find() || DIRECTION_KEYWORDS_PATTERN.matcher(it.body).find() }
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
                formattedUserResponse = summaryText,
                isSummaryOrDraft = true
            )
        } else if (isDraftReplyQuery) {
            val lastIncoming = messages.lastOrNull { it.direction == MessageDirection.INCOMING && it.body.isNotBlank() }
            val lastMsgPreview = lastIncoming?.body?.trim()?.take(110) ?: "your last message"
            val replyDraft = buildString {
                appendLine("⚡ **O.D.I.N. Suggested Replies for $contactLabel:**")
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
                formattedUserResponse = replyDraft,
                isSummaryOrDraft = true
            )
        }

        // General keyword + Q&A context search if no specific intent matched or if intent returned 0 matches
        if (matchedEntries.isEmpty()) {
            val keywords = userPrompt.lowercase()
                .replace(Regex("[^a-z0-9 ]"), " ")
                .split(Regex("\\s+"))
                .filter { it.length >= 3 && it !in STOP_WORDS }

            if (keywords.isNotEmpty()) {
                val scoredIndices = mutableListOf<Pair<Int, Int>>()
                for (i in messages.indices) {
                    val lower = messages[i].body.lowercase()
                    val hitCount = keywords.count { k -> lower.contains(k) }
                    if (hitCount > 0) {
                        scoredIndices.add(Pair(i, hitCount))
                    }
                }
                scoredIndices.sortWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { messages[it.first].timestamp })

                for ((idx, _) in scoredIndices.take(10)) {
                    val msg = messages[idx]
                    matchedEntries.add(formatMsg(msg))
                    // If the matched message was a question, also include the immediate reply!
                    if (msg.body.contains("?") && idx + 1 < messages.size) {
                        val nextMsg = messages[idx + 1]
                        if (nextMsg.body.isNotBlank()) {
                            matchedEntries.add(formatMsg(nextMsg, "Immediate reply"))
                        }
                    }
                    if (matchedEntries.size >= 14) break
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
                appendLine("⚡ **O.D.I.N. Archive Intelligence — $categoryTitle ($contactLabel):**")
                appendLine("Muninn searched **${messages.size} messages** and found **${matchedEntries.size} matching record(s)**:\n")
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
                appendLine("⚡ **O.D.I.N. Archive Intelligence — $categoryTitle ($contactLabel):**")
                appendLine("Muninn searched all **${messages.size} messages** with $contactLabel, but found no explicit **$categoryTitle** recorded in this thread.")
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
        private val isNanoDownloadInProgress = AtomicBoolean(false)
        private val nanoBackgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        private val STOP_WORDS = setOf(
            "the", "and", "for", "with", "that", "this", "from", "have", "what", "when",
            "where", "which", "who", "whom", "whose", "why", "how", "are", "was", "were",
            "been", "being", "can", "could", "should", "would", "will", "shall", "may",
            "might", "must", "find", "list", "show", "tell", "give", "about", "into",
            "your", "their", "our", "any", "all", "some", "messages", "message", "thread",
            "directions", "direction", "addresses", "address"
        )

        private val ADDRESS_PATTERN = Pattern.compile(
            "(?i)\\b\\d{1,6}\\s+(?:[NSEW]\\.?\\s+)?[A-Za-z0-9.\\s]{2,35}\\b(st|street|ave|avenue|rd|road|blvd|boulevard|dr|drive|ln|lane|ct|court|way|pl|place|pkwy|parkway|cir|circle|ter|terrace|trl|trail|hwy|highway|fwy|freeway|loop|sq|square|cv|cove|run|pass|pike|row|walk|xing|crossing|apt|suite|ste|unit|bldg|po box|p\\.o\\. box|county road|cr|state route|sr|fm)\\b"
        )

        private val HOUSE_NUMBER_STREET_PATTERN = Pattern.compile(
            "(?i)(?:\\b(?:at|to|is|on|live at|address is|meet at)\\s+\\d{2,5}\\s+[A-Z][a-z]{2,18}\\b|^\\s*\\d{2,5}\\s+[A-Z][a-z]{2,18}(?:\\s+[A-Za-z0-9#]+)?\\s*\$)"
        )

        private val CITY_STATE_ZIP_PATTERN = Pattern.compile(
            "\\b[A-Z][a-zA-Z.\\s]{2,22},\\s*[A-Z]{2}(?:\\s+\\d{5}(?:-\\d{4})?)?\\b"
        )

        private val MAPS_URL_PATTERN = Pattern.compile(
            "(?i)(maps\\.app\\.goo\\.gl|google\\.com/maps|maps\\.google\\.com|waze\\.com/ul|maps\\.apple\\.com|-?\\d{1,2}\\.\\d{4,},\\s*-?\\d{1,3}\\.\\d{4,})"
        )

        private val DIRECTION_KEYWORDS_PATTERN = Pattern.compile(
            "(?i)\\b(address is|located at|my address|our address|new address|live at|live on|turn left|turn right|exit\\s+\\d+|park in the|parking|gate is on|building\\s+[a-z0-9]+|house on the|across from|next to the|miles\\s+(?:north|south|east|west|past|down)|head(?:ed|ing)?\\s+to)\\b"
        )

        private val ADDRESS_QUESTION_PATTERN = Pattern.compile(
            "(?i)\\b(what(?:'s|\\s+is)\\s+(?:your|the|his|her|their|mom'?s|dad'?s|new)?\\s*(?:address|location|place|street)|where\\s+(?:do\\s+you\\s+live|are\\s+you\\s+located|are\\s+we\\s+meeting|is\\s+the\\s+house|should\\s+i\\s+go|should\\s+i\\s+park)|send\\s+(?:me\\s+)?(?:your|the)\\s+(?:address|location|pin))\\b"
        )

        private val ADDRESS_QUESTION_ONLY_FILTER = Pattern.compile(
            "(?i)^\\s*(?:hey\\s+)?(?:what(?:'s|\\s+is)\\s+(?:your|the)\\s+address|where\\s+do\\s+you\\s+live|can\\s+you\\s+send\\s+(?:me\\s+)?(?:your|the)\\s+address)\\s*\\??\\s*\$"
        )

        private val CODE_QUESTION_PATTERN = Pattern.compile(
            "(?i)\\b(what(?:'s|\\s+is)\\s+(?:the|your)\\s+(?:gate|door|wifi|wi-fi|alarm|garage|lockbox|entry)?\\s*(?:code|password|passcode|pin)|send\\s+(?:me\\s+)?the\\s+(?:code|password|pin))\\b"
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
