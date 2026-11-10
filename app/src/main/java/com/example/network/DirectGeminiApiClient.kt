package com.example.network

import android.util.Base64
import android.util.Log
import com.example.core.config.AlexConfig
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
import com.example.domain.model.MessageSender
import com.example.domain.model.TranscriptTurn
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

    private fun sendInitialSetup(ws: WebSocket) {
        val systemText = AlexConfig.buildSystemInstruction(
            userTitle = settings.userTitle,
            preferredLanguage = settings.preferredLanguage,
            voiceName = settings.selectedVoiceId
        )
        val prebuiltVoice = AlexConfig.toGeminiPrebuiltVoiceName(settings.selectedVoiceId)

        val setupPayload = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/gemini-2.0-flash-exp")
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
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun candidateModelsFor(preferred: String): List<String> {
        return listOf(
            preferred,
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-2.0-flash",
            "gemini-1.5-flash",
            "gemini-3.8-flash"
        ).distinct()
    }

    /**
     * Dynamically discovers supported Gemini models from the official `v1beta/models` endpoint.
     */
    suspend fun discoverSupportedModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            return@withContext AlexConfig.AVAILABLE_CHAT_MODELS
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models?key=$cleanKey"
        val request = Request.Builder().url(url).get().build()
        runCatching {
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
                    if (name.startsWith("gemini") && methods.contains("generateContent")) {
                        discovered.add(name)
                    }
                }
                if (discovered.isNotEmpty()) discovered.distinct() else AlexConfig.AVAILABLE_CHAT_MODELS
            }
        }.getOrDefault(AlexConfig.AVAILABLE_CHAT_MODELS)
    }

    suspend fun generateChatReplyWithKey(
        apiKey: String,
        prompt: String,
        historyTurns: List<TranscriptTurn>,
        settings: AlexSettings
    ): String = withContext(Dispatchers.IO) {
        val cleanKey = apiKey.trim()
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

        val targetModel = selectOptimalModelForPrompt(prompt, settings)
        executeWithModelFallback(cleanKey, targetModel, requestJson)
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

        val rawResponse = executeWithModelFallback(cleanKey, settings.chatModelName, requestJson)
        val cleanJsonString = rawResponse
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        val json = JSONObject(cleanJsonString)
        val heardSpeech = json.optBoolean("heardSpeech", false)
        val userTranscript = json.optString("userTranscript", "").trim()
        val alexReply = json.optString("alexReply", "").trim()
        val langCode = json.optString("languageCode", "EN")
        val detectedLang = if (userTranscript.isNotEmpty()) {
            LanguageDetector.detectLanguage(userTranscript)
        } else {
            AlexLanguage.fromCode(langCode)
        }

        LiveAudioTurnResult(
            heardSpeech = heardSpeech && userTranscript.isNotBlank() && alexReply.isNotBlank(),
            userTranscript = userTranscript,
            alexReply = alexReply,
            language = detectedLang
        )
    }

    private fun selectOptimalModelForPrompt(prompt: String, settings: AlexSettings): String {
        if (!settings.autoModelSwitchingEnabled) return settings.chatModelName
        val isComplexReasoning = prompt.length > 180 ||
            prompt.contains("analyze", ignoreCase = true) ||
            prompt.contains("architecture", ignoreCase = true) ||
            prompt.contains("step by step", ignoreCase = true) ||
            prompt.contains("code", ignoreCase = true)
        return if (isComplexReasoning) {
            "gemini-2.5-pro"
        } else {
            settings.chatModelName
        }
    }

    private fun executeWithModelFallback(
        apiKey: String,
        preferredModel: String,
        requestBodyJson: JSONObject
    ): String {
        val candidates = candidateModelsFor(preferredModel)
        var lastError = "Unable to reach Gemini API."

        for (modelName in candidates) {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody(jsonMediaType))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string().orEmpty()
                if (response.isSuccessful && bodyStr.isNotBlank()) {
                    val root = JSONObject(bodyStr)
                    val candidatesArr = root.optJSONArray("candidates")
                    val firstCandidate = candidatesArr?.optJSONObject(0)
                    val content = firstCandidate?.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    val text = parts?.optJSONObject(0)?.optString("text", "")?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        return text
                    }
                } else {
                    val errorMsg = runCatching {
                        JSONObject(bodyStr).optJSONObject("error")?.optString("message")
                    }.getOrNull() ?: "HTTP ${response.code}"

                    if (response.code == 400 && bodyStr.contains("API_KEY_INVALID", ignoreCase = true)) {
                        throw IllegalArgumentException("Invalid Gemini API Key. Please check the API key you entered.")
                    }
                    lastError = "$modelName: $errorMsg"
                }
            }
        }

        throw IllegalStateException(lastError)
    }
}
