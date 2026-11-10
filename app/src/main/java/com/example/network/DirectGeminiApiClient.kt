package com.example.network

import android.util.Base64
import android.util.Log
import com.example.core.config.AlexConfig
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
import com.example.domain.model.MessageSender
import com.example.domain.model.TranscriptTurn
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "DirectGeminiApiClient"

class DirectGeminiLiveWebSocket(
    private val apiKey: String,
    private val settings: AlexSettings,
    private val onAudioChunkReceived: (ByteArray) -> Unit,
    private val onTextReceived: (String, Boolean) -> Unit,
    private val onInterrupted: () -> Unit,
    private val onSetupComplete: () -> Unit,
    private val onClosedOrFailed: (String?) -> Unit
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    val isConnected = AtomicBoolean(false)
    val isSetupDone = AtomicBoolean(false)

    fun connect() {
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key=${apiKey.trim()}"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected.set(true)
                sendInitialSetup(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleJsonMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: okio.ByteString) {
                handleJsonMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                isConnected.set(false)
                isSetupDone.set(false)
                webSocket.close(1000, null)
                onClosedOrFailed(reason.ifBlank { null })
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected.set(false)
                isSetupDone.set(false)
                onClosedOrFailed(t.localizedMessage)
            }
        })
    }

    private fun resolveBidiModelName(selectedLiveModel: String): String {
        val clean = selectedLiveModel.trim().removePrefix("models/")
        return when {
            clean.isBlank() -> "models/gemini-2.0-flash-exp"
            clean.contains("tts", ignoreCase = true) -> "models/gemini-2.0-flash-exp"
            else -> "models/$clean"
        }
    }

    private fun sendInitialSetup(ws: WebSocket) {
        val systemText = AlexConfig.buildSystemInstruction(
            userTitle = settings.userTitle,
            preferredLanguage = settings.preferredLanguage,
            voiceName = settings.selectedVoiceId
        )
        val prebuiltVoice = AlexConfig.toGeminiPrebuiltVoiceName(settings.selectedVoiceId)
        val targetBidiModel = resolveBidiModelName(settings.liveModelName)

        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", targetBidiModel)
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().put("AUDIO"))
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", prebuiltVoice)
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemText)
                    }))
                })
            })
        }
        ws.send(setupPayload.toString())
    }

    fun sendPcmAudioChunk(pcmBytes: ByteArray) {
        if (!isConnected.get() || !isSetupDone.get()) return
        val base64Audio = Base64.encodeToString(pcmBytes, Base64.NO_WRAP)
        val payload = JSONObject().apply {
            put("realtimeInput", JSONObject().apply {
                put("mediaChunks", JSONArray().put(JSONObject().apply {
                    put("mimeType", "audio/pcm;rate=16000")
                    put("data", base64Audio)
                }))
            })
        }
        webSocket?.send(payload.toString())
    }

    fun sendClientText(text: String) {
        if (!isConnected.get()) return
        val payload = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", text)
                    }))
                }))
                put("turnComplete", true)
            })
        }
        webSocket?.send(payload.toString())
    }

    private fun handleJsonMessage(rawJson: String) {
        try {
            val root = JSONObject(rawJson)
            if (root.has("setupComplete")) {
                isSetupDone.set(true)
                onSetupComplete()
                return
            }

            val serverContent = root.optJSONObject("serverContent") ?: return
            if (serverContent.optBoolean("interrupted", false)) {
                onInterrupted()
                return
            }

            val turnComplete = serverContent.optBoolean("turnComplete", false)
            val modelTurn = serverContent.optJSONObject("modelTurn")
            val parts = modelTurn?.optJSONArray("parts")
            val textBuilder = StringBuilder()

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text", "")
                    if (text.isNotEmpty()) {
                        textBuilder.append(text)
                    }
                    val inlineData = part.optJSONObject("inlineData")
                    if (inlineData != null) {
                        val mimeType = inlineData.optString("mimeType", "")
                        val b64 = inlineData.optString("data", "")
                        if (mimeType.startsWith("audio/pcm") && b64.isNotEmpty()) {
                            val pcmBytes = Base64.decode(b64, Base64.DEFAULT)
                            onAudioChunkReceived(pcmBytes)
                        }
                    }
                }
            }

            if (textBuilder.isNotEmpty() || turnComplete) {
                onTextReceived(textBuilder.toString(), turnComplete)
            }
        } catch (e: Exception) {
            Log.w(TAG, "WebSocket frame parse note: ${e.message}")
        }
    }

    fun close() {
        isConnected.set(false)
        isSetupDone.set(false)
        try {
            webSocket?.close(1000, "User ended call")
        } catch (_: Exception) {
        }
        webSocket = null
    }
}

class DirectGeminiApiClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(25, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // Caches models discovered from v1beta/models for a given API key
    private val discoveredModelsCache = ConcurrentHashMap<String, List<String>>()

    // Tracks models that returned 429 Quota Exceeded so we immediately route to working models
    private val quotaExhaustedModels: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    @Volatile
    var lastSucceededModel: String = AlexConfig.DEFAULT_CHAT_MODEL
        private set

    fun clearQuotaCache() {
        quotaExhaustedModels.clear()
    }

    /**
     * Builds an ordered list of all candidate Gemini models, starting with the user's selected
     * Live & Chat models, followed by high-quota Flash-Lite / Flash / 2.0 / 3.x models and any
     * dynamically discovered models supported by the user's API key.
     */
    private fun candidateModelsFor(
        primaryModel: String,
        secondaryModel: String = "",
        discoveredForKey: List<String> = emptyList(),
        requireAudioMultimodal: Boolean = false
    ): List<String> {
        val rawOrdered = buildList {
            if (primaryModel.isNotBlank() && !primaryModel.contains("tts", ignoreCase = true)) {
                add(primaryModel.removePrefix("models/"))
            }
            if (secondaryModel.isNotBlank() && !secondaryModel.contains("tts", ignoreCase = true)) {
                add(secondaryModel.removePrefix("models/"))
            }
            // High-quota & universal models across Gemini 3.x, 2.5, and 2.0 families
            add("gemini-2.5-flash-lite")
            add("gemini-3.5-flash-lite")
            add("gemini-2.0-flash-lite")
            add("gemini-flash-lite-latest")
            add("gemini-2.5-flash")
            add("gemini-2.0-flash")
            add("gemini-flash-latest")
            add("gemini-3.8-flash")
            add("gemini-3-flash-preview")
            add("gemini-2.5-pro")
            add("gemini-4-argon")
            if (!requireAudioMultimodal) {
                add("gemma-3-27b-it")
                add("gemma-3-12b-it")
            }
            // Append all dynamically discovered models for this API key (excluding TTS/embedding-only models)
            discoveredForKey.forEach { m ->
                val clean = m.removePrefix("models/")
                if (!clean.contains("tts", ignoreCase = true) &&
                    !clean.contains("embedding", ignoreCase = true) &&
                    !clean.contains("imagen", ignoreCase = true) &&
                    !clean.contains("veo", ignoreCase = true) &&
                    (!requireAudioMultimodal || clean.startsWith("gemini"))
                ) {
                    add(clean)
                }
            }
        }.distinct()

        // Place non-exhausted models first, and keep quota-exhausted models at the very end as last-resort
        val activeCandidates = rawOrdered.filterNot { quotaExhaustedModels.contains(it) }
        val exhaustedCandidates = rawOrdered.filter { quotaExhaustedModels.contains(it) }
        return activeCandidates + exhaustedCandidates
    }

    /**
     * Dynamically discovers supported Gemini models from the official `v1beta/models` endpoint.
     */
    suspend fun discoverSupportedModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext AlexConfig.AVAILABLE_CHAT_MODELS
        }
        discoveredModelsCache[cleanKey]?.let { cached ->
            if (cached.isNotEmpty()) return@withContext cached
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
        val request = Request.Builder().url(url).get().build()
        val result = runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful || body.isBlank()) {
                    return@use AlexConfig.AVAILABLE_CHAT_MODELS
                }
                val root = JSONObject(body)
                val modelsArr = root.optJSONArray("models") ?: return@use AlexConfig.AVAILABLE_CHAT_MODELS
                val discovered = mutableListOf<String>()
                for (i in 0 until modelsArr.length()) {
                    val m = modelsArr.optJSONObject(i) ?: continue
                    val name = m.optString("name", "").removePrefix("models/")
                    val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
                    if ((name.startsWith("gemini") || name.startsWith("gemma")) &&
                        methods.contains("generateContent")
                    ) {
                        discovered.add(name)
                    }
                }
                val merged = (AlexConfig.AVAILABLE_CHAT_MODELS + discovered).distinct()
                if (merged.isNotEmpty()) merged else AlexConfig.AVAILABLE_CHAT_MODELS
            }
        }.getOrDefault(AlexConfig.AVAILABLE_CHAT_MODELS)

        discoveredModelsCache[cleanKey] = result
        result
    }

    suspend fun generateChatReplyWithKey(
        apiKey: String,
        prompt: String,
        historyTurns: List<TranscriptTurn>,
        settings: AlexSettings
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val discovered = discoveredModelsCache[cleanKey] ?: runCatching {
            discoverSupportedModels(cleanKey)
        }.getOrDefault(emptyList())

        val detectedLanguage = if (settings.preferredLanguage == AlexLanguage.AUTO) {
            LanguageDetector.detectLanguage(prompt)
        } else {
            settings.preferredLanguage
        }

        val systemInstruction = AlexConfig.buildSystemInstruction(
            userTitle = settings.userTitle,
            preferredLanguage = detectedLanguage,
            voiceName = settings.selectedVoiceId
        )

        val contentsArray = JSONArray()
        historyTurns
            .takeLast(14)
            .filter { it.text.isNotBlank() && !it.isStreaming }
            .forEach { turn ->
                val role = if (turn.sender == MessageSender.USER) "user" else "model"
                contentsArray.put(
                    JSONObject().apply {
                        put("role", role)
                        put(
                            "parts",
                            JSONArray().put(JSONObject().apply { put("text", turn.text) })
                        )
                    }
                )
            }

        contentsArray.put(
            JSONObject().apply {
                put("role", "user")
                put("parts", JSONArray().put(JSONObject().apply { put("text", prompt) }))
            }
        )

        val requestJson = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
                }
            )
            put("contents", contentsArray)
            put(
                "generationConfig",
                JSONObject().apply {
                    put("temperature", 0.75)
                }
            )
        }

        // Also prepare a fallback payload with system instruction inlined for models (like Gemma) that don't accept system_instruction
        val inlinedContents = JSONArray().apply {
            put(
                JSONObject().apply {
                    put("role", "user")
                    put(
                        "parts",
                        JSONArray().put(
                            JSONObject().apply {
                                put("text", "$systemInstruction\n\nUser (${settings.userTitle}): $prompt")
                            }
                        )
                    )
                }
            )
        }
        val fallbackRequestJson = JSONObject().apply {
            put("contents", inlinedContents)
            put("generationConfig", JSONObject().apply { put("temperature", 0.75) })
        }

        val primaryModel = selectOptimalModelForPrompt(prompt, settings)
        val secondaryModel = settings.liveModelName
        val candidates = candidateModelsFor(
            primaryModel = primaryModel,
            secondaryModel = secondaryModel,
            discoveredForKey = discovered,
            requireAudioMultimodal = false
        )

        executeWithModelFallback(
            apiKey = cleanKey,
            candidates = candidates,
            requestBodyJson = requestJson,
            inlinedFallbackBodyJson = fallbackRequestJson
        )
    }

    /**
     * Fallback provider support for Groq / OpenAI-compatible chat completions when configured.
     */
    suspend fun generateGroqCompatibleReply(
        groqApiKey: String,
        prompt: String,
        historyTurns: List<TranscriptTurn>,
        settings: AlexSettings
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = groqApiKey.trim()
        val systemText = AlexConfig.buildSystemInstruction(
            userTitle = settings.userTitle,
            preferredLanguage = settings.preferredLanguage,
            voiceName = settings.selectedVoiceId
        )

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemText)
            })
            historyTurns.takeLast(10).forEach { turn ->
                put(JSONObject().apply {
                    put("role", if (turn.sender == MessageSender.USER) "user" else "assistant")
                    put("content", turn.text)
                })
            }
            put(JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            })
        }

        val payload = JSONObject().apply {
            put("model", "llama-3.3-70b-versatile")
            put("messages", messages)
            put("temperature", 0.7)
        }

        val req = Request.Builder()
            .url("https://api.groq.com/openai/v1/chat/completions")
            .addHeader("Authorization", "Bearer $cleanKey")
            .post(payload.toString().toRequestBody(jsonMediaType))
            .build()

        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (!resp.isSuccessful || body.isBlank()) {
                throw IllegalStateException("Groq fallback HTTP ${resp.code}")
            }
            val root = JSONObject(body)
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content", "")
                ?.trim()
                .orEmpty()
            if (content.isBlank()) throw IllegalStateException("Empty Groq response")
            lastSucceededModel = "groq/llama-3.3-70b"
            content
        }
    }

    suspend fun processLiveAudioTurnWithKey(
        apiKey: String,
        wavBytes: ByteArray,
        historyTurns: List<TranscriptTurn>,
        settings: AlexSettings
    ): LiveAudioTurnResult = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        val discovered = discoveredModelsCache[cleanKey] ?: runCatching {
            discoverSupportedModels(cleanKey)
        }.getOrDefault(emptyList())

        val base64Wav = Base64.encodeToString(wavBytes, Base64.NO_WRAP)

        val recentContext = historyTurns
            .takeLast(6)
            .joinToString("\n") { turn ->
                val speaker = if (turn.sender == MessageSender.USER) settings.userTitle else "ALEX"
                "$speaker: ${turn.text}"
            }

        val systemInstruction = AlexConfig.buildSystemInstruction(
            userTitle = settings.userTitle,
            preferredLanguage = settings.preferredLanguage,
            voiceName = settings.selectedVoiceId
        )

        val promptInstruction = buildString {
            append("Listen to this live voice audio from ${settings.userTitle}. ")
            append("Return ONLY a valid JSON object with keys: ")
            append("\"heardSpeech\" (boolean), \"userTranscript\" (string in original script: বাংলা, English, or हिन्दी), ")
            append("\"alexReply\" (string: ALEX's natural spoken response in the exact same language addressing user as ${settings.userTitle}), ")
            append("and \"languageCode\" (\"BN\", \"EN\", or \"HI\"). ")
            if (recentContext.isNotBlank()) {
                append("\nRecent context:\n$recentContext")
            }
        }

        val userParts = JSONArray().apply {
            put(
                JSONObject().apply {
                    put(
                        "inline_data",
                        JSONObject().apply {
                            put("mime_type", "audio/wav")
                            put("data", base64Wav)
                        }
                    )
                }
            )
            put(JSONObject().apply { put("text", promptInstruction) })
        }

        val requestJson = JSONObject().apply {
            put(
                "system_instruction",
                JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply { put("text", systemInstruction) }))
                }
            )
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", userParts)
                    }
                )
            )
            put(
                "generationConfig",
                JSONObject().apply {
                    put("responseMimeType", "application/json")
                    put("temperature", 0.7)
                }
            )
        }

        val fallbackNoMimeJson = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", userParts)
                    }
                )
            )
            put("generationConfig", JSONObject().apply { put("temperature", 0.7) })
        }

        // Prioritize settings.liveModelName first, then settings.chatModelName, then all multimodal models
        val candidates = candidateModelsFor(
            primaryModel = settings.liveModelName,
            secondaryModel = settings.chatModelName,
            discoveredForKey = discovered,
            requireAudioMultimodal = true
        )

        val rawResponse = executeWithModelFallback(
            apiKey = cleanKey,
            candidates = candidates,
            requestBodyJson = requestJson,
            inlinedFallbackBodyJson = fallbackNoMimeJson
        )

        parseLiveAudioTurnResponse(rawResponse, settings)
    }

    private fun parseLiveAudioTurnResponse(
        rawResponse: String,
        settings: AlexSettings
    ): LiveAudioTurnResult {
        val cleanJsonString = rawResponse
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        return try {
            val startBrace = cleanJsonString.indexOf('{')
            val endBrace = cleanJsonString.lastIndexOf('}')
            val jsonCandidate = if (startBrace >= 0 && endBrace > startBrace) {
                cleanJsonString.substring(startBrace, endBrace + 1)
            } else {
                cleanJsonString
            }
            val json = JSONObject(jsonCandidate)
            val heardSpeech = json.optBoolean("heardSpeech", true)
            val userTranscript = json.optString("userTranscript", "").trim()
            val alexReply = json.optString("alexReply", "").trim()
            val langCode = json.optString("languageCode", "EN")
            val detectedLang = when {
                userTranscript.isNotEmpty() -> LanguageDetector.detectLanguage(userTranscript)
                alexReply.isNotEmpty() -> LanguageDetector.detectLanguage(alexReply)
                else -> AlexLanguage.fromCode(langCode)
            }
            LiveAudioTurnResult(
                heardSpeech = heardSpeech && alexReply.isNotBlank(),
                userTranscript = userTranscript.ifBlank { "(Voice message)" },
                alexReply = alexReply,
                language = detectedLang
            )
        } catch (_: Exception) {
            // Graceful fallback if the model returned plain spoken text instead of JSON
            val lang = LanguageDetector.detectLanguage(cleanJsonString)
            LiveAudioTurnResult(
                heardSpeech = cleanJsonString.isNotBlank(),
                userTranscript = "(Voice input)",
                alexReply = cleanJsonString,
                language = if (settings.preferredLanguage == AlexLanguage.AUTO) lang else settings.preferredLanguage
            )
        }
    }

    private fun selectOptimalModelForPrompt(prompt: String, settings: AlexSettings): String {
        val baseModel = if (settings.chatModelName.contains("tts", ignoreCase = true)) {
            if (!settings.liveModelName.contains("tts", ignoreCase = true)) {
                settings.liveModelName
            } else {
                AlexConfig.FAST_CHAT_MODEL
            }
        } else {
            settings.chatModelName
        }

        if (!settings.autoModelSwitchingEnabled) return baseModel
        val isComplexReasoning = prompt.length > 220 ||
            prompt.contains("architecture", ignoreCase = true) ||
            prompt.contains("step by step", ignoreCase = true)
        return if (isComplexReasoning && !quotaExhaustedModels.contains(baseModel)) {
            baseModel
        } else {
            baseModel
        }
    }

    private fun executeWithModelFallback(
        apiKey: String,
        candidates: List<String>,
        requestBodyJson: JSONObject,
        inlinedFallbackBodyJson: JSONObject? = null
    ): String {
        var firstMeaningfulError: String? = null
        var lastError = "Unable to reach Gemini API."

        for (modelName in candidates) {
            val cleanModel = modelName.trim().removePrefix("models/")
            if (cleanModel.isBlank()) continue

            val useInlined = cleanModel.startsWith("gemma", ignoreCase = true) && inlinedFallbackBodyJson != null
            val activePayload = if (useInlined) inlinedFallbackBodyJson!! else requestBodyJson

            val url = "https://generativelanguage.googleapis.com/v1beta/models/$cleanModel:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(activePayload.toString().toRequestBody(jsonMediaType))
                .build()

            val attemptResult = runCatching {
                httpClient.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string().orEmpty()
                    if (response.isSuccessful && bodyStr.isNotBlank()) {
                        val extracted = extractTextFromGenerateContentResponse(bodyStr)
                        if (extracted.isNotEmpty()) {
                            lastSucceededModel = cleanModel
                            return extracted
                        }
                    }

                    val errorMsg = runCatching {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                    }.getOrNull() ?: "HTTP ${response.code}"

                    if (response.code == 400 && bodyStr.contains("API_KEY_INVALID", ignoreCase = true)) {
                        throw IllegalArgumentException("Invalid Gemini API Key. Please check the API key you entered.")
                    }

                    if (response.code == 429 || errorMsg.contains("quota", ignoreCase = true) || errorMsg.contains("rate", ignoreCase = true)) {
                        quotaExhaustedModels.add(cleanModel)
                    }

                    // If 400 due to system_instruction or responseMimeType not supported by this model, retry once with simplified body
                    if (response.code == 400 && inlinedFallbackBodyJson != null && !useInlined) {
                        val retryReq = Request.Builder()
                            .url(url)
                            .post(inlinedFallbackBodyJson.toString().toRequestBody(jsonMediaType))
                            .build()
                        httpClient.newCall(retryReq).execute().use { retryResp ->
                            val retryBody = retryResp.body?.string().orEmpty()
                            if (retryResp.isSuccessful && retryBody.isNotBlank()) {
                                val extractedRetry = extractTextFromGenerateContentResponse(retryBody)
                                if (extractedRetry.isNotEmpty()) {
                                    lastSucceededModel = cleanModel
                                    return extractedRetry
                                }
                            }
                        }
                    }

                    val formattedErr = "$cleanModel: $errorMsg"
                    if (firstMeaningfulError == null) {
                        firstMeaningfulError = formattedErr
                    }
                    lastError = formattedErr
                }
            }

            if (attemptResult.isFailure) {
                val ex = attemptResult.exceptionOrNull()
                if (ex is IllegalArgumentException) throw ex
                lastError = "$cleanModel: ${ex?.localizedMessage ?: "Network error"}"
            }
        }

        throw IllegalStateException(firstMeaningfulError ?: lastError)
    }

    private fun extractTextFromGenerateContentResponse(bodyStr: String): String {
        val root = JSONObject(bodyStr)
        val candidatesArr = root.optJSONArray("candidates") ?: return ""
        val firstCandidate = candidatesArr.optJSONObject(0) ?: return ""
        val content = firstCandidate.optJSONObject("content") ?: return ""
        val parts = content.optJSONArray("parts") ?: return ""
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val t = part.optString("text", "")
            if (t.isNotEmpty()) sb.append(t)
        }
        return sb.toString().trim()
    }
}
