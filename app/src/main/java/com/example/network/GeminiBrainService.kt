package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import com.example.core.config.AlexConfig
import com.example.core.config.NativeSystemBridge
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AiProviderMode
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
import com.example.domain.model.ApiUsageMetrics
import com.example.domain.model.MessageSender
import com.example.domain.model.TranscriptTurn
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.LiveGenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.AudioTranscriptionConfig
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FunctionCallPart
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.liveGenerationConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiBrainService"

data class DiagnosticTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val modelTested: String,
    val responseSnippet: String,
    val errorDetail: String? = null
)

data class LiveAudioTurnResult(
    val heardSpeech: Boolean,
    val userTranscript: String,
    val alexReply: String,
    val language: AlexLanguage
)

class GeminiBrainService(private val context: Context) {

    private val directApiClient = DirectGeminiApiClient()

    private val _usageMetrics = MutableStateFlow(
        ApiUsageMetrics(
            discoveredModels = AlexConfig.AVAILABLE_CHAT_MODELS
        )
    )
    val usageMetrics: StateFlow<ApiUsageMetrics> = _usageMetrics.asStateFlow()

    // Default generative model lazily initialized per Firebase AI Logic best practices
    private val defaultGenerativeModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AlexConfig.DEFAULT_CHAT_MODEL,
            systemInstruction = content {
                text(AlexConfig.buildSystemInstruction())
            }
        )
    }

    private val liveAudioTurnSchema = Schema.obj(
        properties = mapOf(
            "heardSpeech" to Schema.boolean(
                description = "True if intelligible human speech in Bangla, English, or Hindi was heard in the audio clip; false if only silence or background noise."
            ),
            "userTranscript" to Schema.string(
                description = "Accurate transcript of what the user said in its original script (বাংলা for Bangla, English for English, हिन्दी for Hindi)."
            ),
            "alexReply" to Schema.string(
                description = "ALEX's natural, warm, human-like spoken response in the exact same language as the user, addressing the user as Boss."
            ),
            "languageCode" to Schema.enumeration(
                values = listOf("BN", "EN", "HI"),
                description = "Detected language code: BN for Bangla, EN for English, HI for Hindi."
            )
        )
    )

    fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun clearQuotaCache() {
        directApiClient.clearQuotaCache()
    }

    suspend fun refreshDiscoveredModels(apiKey: String) {
        val models = directApiClient.discoverSupportedModels(apiKey)
        _usageMetrics.value = _usageMetrics.value.copy(discoveredModels = models)
    }

    private fun firebaseCandidateModels(settings: AlexSettings, preferReasoning: Boolean = false): List<String> {
        val liveClean = AlexConfig.sanitizeModelName(settings.liveModelName, AlexConfig.DEFAULT_LIVE_MODEL)
        val chatClean = AlexConfig.sanitizeModelName(settings.chatModelName, AlexConfig.DEFAULT_CHAT_MODEL)
        val rawList = buildList {
            if (preferReasoning) {
                add(AlexConfig.REASONING_MODEL)
            }
            if (!liveClean.contains("-tts", ignoreCase = true)) {
                add(liveClean)
            }
            if (!chatClean.contains("-tts", ignoreCase = true)) {
                add(chatClean)
            }
            add("gemini-3.5-flash-lite")
            add("gemini-2.5-flash-lite")
            add("gemini-2.5-flash")
            add("gemini-2.0-flash-lite")
            add("gemini-2.0-flash")
            add("gemini-2.5-pro")
            add("gemini-3.8-flash")
            add("gemini-4-argon")
        }
        return rawList.distinct()
    }

    private fun recordMetric(latencyMs: Long, tokensDelta: Int, providerLabel: String, usedFallback: Boolean = false) {
        val current = _usageMetrics.value
        val newTotal = current.totalRequests + 1
        val newSuccess = current.successfulRequests + 1
        val newAvg = if (current.averageLatencyMs == 0L) {
            latencyMs
        } else {
            ((current.averageLatencyMs * current.successfulRequests) + latencyMs) / newSuccess
        }
        _usageMetrics.value = current.copy(
            totalRequests = newTotal,
            successfulRequests = newSuccess,
            fallbackTriggers = current.fallbackTriggers + (if (usedFallback) 1 else 0),
            estimatedTokensUsed = current.estimatedTokensUsed + tokensDelta.coerceAtLeast(12),
            lastLatencyMs = latencyMs,
            averageLatencyMs = newAvg,
            activeProviderLabel = providerLabel
        )
    }

    private fun buildAssistantTools(): List<Tool> {
        val dateTimeTool = FunctionDeclaration(
            name = "getCurrentDateTime",
            description = "Returns the current local time, date, and day of the week for Boss.",
            parameters = emptyMap()
        )
        val deviceTelemetryTool = FunctionDeclaration(
            name = "getDeviceTelemetry",
            description = "Returns battery percentage, network connection status, and native hardware telemetry of the Android phone.",
            parameters = emptyMap()
        )
        val languageHelpTool = FunctionDeclaration(
            name = "getLanguageCapabilities",
            description = "Returns ALEX AI's supported languages (Bangla, English, Hindi) and 10 human voice profiles.",
            parameters = mapOf(
                "query" to Schema.string("Optional query about language or voice support", nullable = true)
            ),
            optionalParameters = listOf("query")
        )
        return listOf(
            Tool.functionDeclarations(
                listOf(dateTimeTool, deviceTelemetryTool, languageHelpTool)
            )
        )
    }

    fun executeToolCall(call: FunctionCallPart): String {
        return when (call.name) {
            "getCurrentDateTime" -> {
                val now = SimpleDateFormat("EEEE, MMM d, yyyy 'at' hh:mm:ss a z", Locale.getDefault())
                    .format(Date())
                "[Tool Result: getCurrentDateTime] currentDateTime=$now, status=success"
            }
            "getDeviceTelemetry" -> {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val batteryPct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
                val online = isNetworkConnected()
                val hwSnapshot = NativeSystemBridge.captureHardwareSnapshot(context).toSummaryLine()
                "[Tool Result: getDeviceTelemetry] batteryPercentage=$batteryPct%, networkOnline=$online, hardware=$hwSnapshot"
            }
            "getLanguageCapabilities" -> {
                "[Tool Result: getLanguageCapabilities] supportedLanguages=Bangla (বাংলা), English, Hindi (हिन्दी), voices=5 Female + 5 Male"
            }
            else -> {
                "[Tool Result: ${call.name}] executed successfully"
            }
        }
    }

    @OptIn(PublicPreviewAPI::class)
    suspend fun tryConnectLiveWebSocketSession(settings: AlexSettings): LiveSession? {
        if (!isNetworkConnected()) {
            throw IllegalStateException("No internet connection detected. Please check Wi-Fi or mobile data.")
        }
        if (settings.customApiKey.isNotBlank()) {
            return null
        }

        val sanitizedLiveModel = AlexConfig.sanitizeModelName(
            settings.liveModelName,
            AlexConfig.DEFAULT_LIVE_MODEL
        )

        val systemInstructionContent: Content = content {
            text(
                AlexConfig.buildSystemInstruction(
                    userTitle = settings.userTitle,
                    preferredLanguage = settings.preferredLanguage,
                    voiceName = settings.selectedVoiceId
                )
            )
        }

        val prebuiltVoice = AlexConfig.toGeminiPrebuiltVoiceName(settings.selectedVoiceId)
        val liveConfig = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            speechConfig = SpeechConfig(Voice(prebuiltVoice))
            inputAudioTranscription = AudioTranscriptionConfig()
            outputAudioTranscription = AudioTranscriptionConfig()
            temperature = 0.8f
        }

        return try {
            val liveModel: LiveGenerativeModel = Firebase.ai.liveModel(
                modelName = sanitizedLiveModel,
                generationConfig = liveConfig,
                tools = buildAssistantTools(),
                systemInstruction = systemInstructionContent
            )
            withTimeout(6_000L) {
                liveModel.connect()
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                Log.i(TAG, "Live WebSocket handshake timed out; using Real-Time VAD Multimodal Audio Duplex.")
                null
            } else {
                throw e
            }
        } catch (e: Exception) {
            Log.i(TAG, "Live WebSocket unavailable (${e.javaClass.simpleName}); using Real-Time VAD Multimodal Audio Duplex.")
            null
        }
    }

    suspend fun processLiveAudioTurn(
        wavBytes: ByteArray,
        historyTurns: List<TranscriptTurn>,
        settings: AlexSettings
    ): LiveAudioTurnResult {
        if (!isNetworkConnected()) {
            throw IllegalStateException("No internet connection. Please reconnect and try again.")
        }

        val startMs = System.currentTimeMillis()
        if (settings.customApiKey.isNotBlank()) {
            try {
                val res = directApiClient.processLiveAudioTurnWithKey(
                    apiKey = settings.customApiKey,
                    wavBytes = wavBytes,
                    historyTurns = historyTurns,
                    settings = settings
                )
                val elapsed = System.currentTimeMillis() - startMs
                val modelUsed = directApiClient.lastSucceededModel.ifBlank { settings.liveModelName }
                recordMetric(
                    latencyMs = elapsed,
                    tokensDelta = (res.userTranscript.length + res.alexReply.length) / 3,
                    providerLabel = "Gemini Direct ($modelUsed)"
                )
                return res
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Custom API key live audio failed (${e.message}), falling back to Firebase AI Logic multi-model pool")
            }
        }

        val recentContext = historyTurns
            .takeLast(8)
            .joinToString("\n") { turn ->
                val speaker = if (turn.sender == MessageSender.USER) settings.userTitle else "ALEX"
                "$speaker: ${turn.text}"
            }

        var lastException: Exception? = null
        val candidates = firebaseCandidateModels(settings)
        for ((index, candidateModel) in candidates.withIndex()) {
            try {
                val multimodalAudioModel = Firebase.ai.generativeModel(
                    modelName = candidateModel,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = liveAudioTurnSchema
                        temperature = 0.75f
                    },
                    systemInstruction = content {
                        text(
                            AlexConfig.buildSystemInstruction(
                                userTitle = settings.userTitle,
                                preferredLanguage = settings.preferredLanguage,
                                voiceName = settings.selectedVoiceId
                            )
                        )
                    }
                )

                val response = withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
                    multimodalAudioModel.generateContent(
                        content {
                            inlineData(wavBytes, AlexConfig.AUDIO_WAV_MIME_TYPE)
                            text(
                                buildString {
                                    append("Listen to this live voice audio from ${settings.userTitle}. ")
                                    append("If intelligible speech is present in Bangla (বাংলা), English, or Hindi (हिन्दी), ")
                                    append("transcribe it accurately and provide ALEX's natural spoken response in the same language. ")
                                    if (recentContext.isNotBlank()) {
                                        append("\nRecent conversation context:\n$recentContext")
                                    }
                                }
                            )
                        }
                    )
                }

                val rawJson = response.text ?: throw IllegalStateException("Empty audio understanding response.")
                val json = JSONObject(rawJson)
                val heardSpeech = json.optBoolean("heardSpeech", false)
                val userTranscript = json.optString("userTranscript", "").trim()
                val alexReply = json.optString("alexReply", "").trim()
                val langCode = json.optString("languageCode", "EN")
                val detectedLang = if (userTranscript.isNotEmpty()) {
                    LanguageDetector.detectLanguage(userTranscript)
                } else {
                    AlexLanguage.fromCode(langCode)
                }

                val elapsed = System.currentTimeMillis() - startMs
                recordMetric(
                    latencyMs = elapsed,
                    tokensDelta = (userTranscript.length + alexReply.length) / 3,
                    providerLabel = "Firebase AI ($candidateModel)",
                    usedFallback = index > 0
                )

                return LiveAudioTurnResult(
                    heardSpeech = heardSpeech && userTranscript.isNotBlank() && alexReply.isNotBlank(),
                    userTranscript = userTranscript,
                    alexReply = alexReply,
                    language = detectedLang
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "Firebase AI audio turn failed on $candidateModel: ${e.message}")
            }
        }

        throw lastException ?: IllegalStateException("All Gemini voice models failed to process audio.")
    }

    fun streamChatResponse(
        prompt: String,
        historyTurns: List<TranscriptTurn>,
        settings: AlexSettings
    ): Flow<String> = flow {
        if (!isNetworkConnected()) {
            throw IllegalStateException("No internet connection. Please reconnect and try again.")
        }

        val startMs = System.currentTimeMillis()

        // 1. Explicit Groq Provider Mode
        if (settings.aiProviderMode == AiProviderMode.GROQ_FALLBACK && settings.groqApiKey.isNotBlank()) {
            val groqReply = withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
                directApiClient.generateGroqCompatibleReply(
                    groqApiKey = settings.groqApiKey,
                    prompt = prompt,
                    historyTurns = historyTurns,
                    settings = settings
                )
            }
            recordMetric(
                latencyMs = System.currentTimeMillis() - startMs,
                tokensDelta = (prompt.length + groqReply.length) / 4,
                providerLabel = "Groq Llama-3.3-70B"
            )
            emit(groqReply)
            return@flow
        }

        // 2. Direct Gemini API Key Mode (or Hybrid with Custom Key)
        if (settings.customApiKey.isNotBlank() && settings.aiProviderMode != AiProviderMode.FIREBASE_AI) {
            try {
                val reply = withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
                    directApiClient.generateChatReplyWithKey(
                        apiKey = settings.customApiKey,
                        prompt = prompt,
                        historyTurns = historyTurns,
                        settings = settings
                    )
                }
                if (reply.isBlank()) {
                    throw IllegalStateException("ALEX received an empty response from Gemini.")
                }
                val modelUsed = directApiClient.lastSucceededModel.ifBlank { settings.chatModelName }
                recordMetric(
                    latencyMs = System.currentTimeMillis() - startMs,
                    tokensDelta = (prompt.length + reply.length) / 4,
                    providerLabel = "Gemini Direct ($modelUsed)"
                )
                emit(reply)
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Direct API key chat failed (${e.message}), attempting fallback providers")
                // Automatic fallback to Groq if configured
                if (settings.groqApiKey.isNotBlank()) {
                    try {
                        val groqFallback = directApiClient.generateGroqCompatibleReply(
                            groqApiKey = settings.groqApiKey,
                            prompt = prompt,
                            historyTurns = historyTurns,
                            settings = settings
                        )
                        recordMetric(
                            latencyMs = System.currentTimeMillis() - startMs,
                            tokensDelta = (prompt.length + groqFallback.length) / 4,
                            providerLabel = "Groq Fallback",
                            usedFallback = true
                        )
                        emit(groqFallback)
                        return@flow
                    } catch (groqErr: Exception) {
                        Log.w(TAG, "Groq fallback failed: ${groqErr.message}")
                    }
                }
                // Fall through to Firebase AI multi-model pool so quota exhaustion on custom key never blocks the user
            }
        }

        // 3. Firebase AI Logic Mode (with multi-model automatic fallback)
        val detectedLanguage = if (settings.preferredLanguage == AlexLanguage.AUTO) {
            LanguageDetector.detectLanguage(prompt)
        } else {
            settings.preferredLanguage
        }

        val preferReasoning = settings.autoModelSwitchingEnabled && prompt.length > 180
        val candidates = firebaseCandidateModels(settings, preferReasoning = preferReasoning)

        val chatHistory = historyTurns
            .takeLast(16)
            .filter { it.text.isNotBlank() && !it.isStreaming }
            .map { turn ->
                val role = if (turn.sender == MessageSender.USER) "user" else "model"
                content(role) { text(turn.text) }
            }

        var lastError: Exception? = null
        for ((index, targetModelName) in candidates.withIndex()) {
            val accumulated = StringBuilder()
            try {
                val model = if (
                    targetModelName == AlexConfig.DEFAULT_CHAT_MODEL &&
                    settings.userTitle == AlexConfig.DEFAULT_USER_TITLE &&
                    settings.preferredLanguage == AlexLanguage.AUTO
                ) {
                    defaultGenerativeModel
                } else {
                    Firebase.ai.generativeModel(
                        modelName = targetModelName,
                        generationConfig = generationConfig {
                            temperature = 0.75f
                        },
                        systemInstruction = content {
                            text(
                                AlexConfig.buildSystemInstruction(
                                    userTitle = settings.userTitle,
                                    preferredLanguage = detectedLanguage,
                                    voiceName = settings.selectedVoiceId
                                )
                            )
                        }
                    )
                }

                val chat = model.startChat(history = chatHistory)

                withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
                    chat.sendMessageStream(prompt).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            emit(accumulated.toString())
                        }
                    }
                }

                val finalStr = accumulated.toString()
                if (finalStr.isBlank()) {
                    throw IllegalStateException("ALEX received an empty response from $targetModelName.")
                }

                recordMetric(
                    latencyMs = System.currentTimeMillis() - startMs,
                    tokensDelta = (prompt.length + finalStr.length) / 4,
                    providerLabel = "Firebase AI ($targetModelName)",
                    usedFallback = index > 0
                )
                return@flow
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Firebase AI chat failed on $targetModelName: ${e.message}")
                if (accumulated.isNotEmpty()) {
                    return@flow
                }
            }
        }

        throw lastError ?: IllegalStateException("All Gemini models failed to respond.")
    }

    suspend fun runConnectionDiagnostic(settings: AlexSettings): DiagnosticTestResult {
        val startMs = System.currentTimeMillis()
        val requestedModel = settings.liveModelName.ifBlank { settings.chatModelName }
        if (!isNetworkConnected()) {
            return DiagnosticTestResult(
                success = false,
                latencyMs = 0L,
                modelTested = requestedModel,
                responseSnippet = "",
                errorDetail = "Device is offline. Please enable internet access."
            )
        }

        val diagnosticPrompt = "Reply in one short sentence as ALEX greeting ${settings.userTitle} and confirming systems are online."

        return try {
            if (settings.customApiKey.isNotBlank()) {
                refreshDiscoveredModels(settings.customApiKey)
                try {
                    val text = withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
                        directApiClient.generateChatReplyWithKey(
                            apiKey = settings.customApiKey,
                            prompt = diagnosticPrompt,
                            historyTurns = emptyList(),
                            settings = settings
                        ).trim()
                    }
                    val elapsed = System.currentTimeMillis() - startMs
                    val actualModel = directApiClient.lastSucceededModel.ifBlank { requestedModel }
                    val modelLabel = if (actualModel != requestedModel && requestedModel.isNotBlank()) {
                        "$actualModel (Auto-Selected for $requestedModel • Custom API Key)"
                    } else {
                        "$actualModel (Custom API Key)"
                    }
                    if (text.isNotEmpty()) {
                        recordMetric(
                            latencyMs = elapsed,
                            tokensDelta = text.length / 3,
                            providerLabel = "Gemini Direct ($actualModel)",
                            usedFallback = actualModel != requestedModel
                        )
                        return DiagnosticTestResult(
                            success = true,
                            latencyMs = elapsed,
                            modelTested = modelLabel,
                            responseSnippet = text
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val msg = e.localizedMessage ?: ""
                    // If the API key itself is invalid (e.g. 400 API_KEY_INVALID), surface that clearly.
                    // If it's a Free Tier quota error (429), automatically fall back to Firebase AI Logic!
                    if (msg.contains("API_KEY_INVALID", ignoreCase = true) ||
                        msg.contains("API key not valid", ignoreCase = true)
                    ) {
                        throw e
                    }
                    Log.w(TAG, "Custom API key hit quota/limit during diagnostic; falling back to Firebase AI pool: $msg")
                }
            }

            // Firebase AI Logic multi-model pool
            var lastFirebaseErr: Exception? = null
            for (candidateModel in firebaseCandidateModels(settings)) {
                try {
                    val model = Firebase.ai.generativeModel(
                        modelName = candidateModel,
                        systemInstruction = content {
                            text(AlexConfig.buildSystemInstruction(userTitle = settings.userTitle))
                        }
                    )
                    val response = withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
                        model.generateContent(diagnosticPrompt)
                    }
                    val text = response.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        val elapsed = System.currentTimeMillis() - startMs
                        recordMetric(
                            latencyMs = elapsed,
                            tokensDelta = text.length / 3,
                            providerLabel = "Firebase AI ($candidateModel)"
                        )
                        return DiagnosticTestResult(
                            success = true,
                            latencyMs = elapsed,
                            modelTested = candidateModel,
                            responseSnippet = text
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastFirebaseErr = e
                    Log.w(TAG, "Firebase diagnostic failed on $candidateModel: ${e.message}")
                }
            }

            DiagnosticTestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - startMs,
                modelTested = requestedModel,
                responseSnippet = "",
                errorDetail = lastFirebaseErr?.localizedMessage ?: "Model returned an empty diagnostic response."
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Diagnostic timed out", e)
            DiagnosticTestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - startMs,
                modelTested = requestedModel,
                responseSnippet = "",
                errorDetail = "Diagnostic timed out after 60 seconds."
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Diagnostic test failed", e)
            DiagnosticTestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - startMs,
                modelTested = requestedModel,
                responseSnippet = "",
                errorDetail = e.localizedMessage ?: "Gemini diagnostic call failed."
            )
        }
    }
}
