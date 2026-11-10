package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.util.Log
import com.example.core.config.AlexConfig
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "GeminiBrainService"

data class DiagnosticTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val modelTested: String,
    val responseSnippet: String,
    val errorDetail: String? = null
)

class GeminiBrainService(private val context: Context) {

    // Default generative model lazily initialized per Firebase AI Logic best practices
    private val defaultGenerativeModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AlexConfig.DEFAULT_CHAT_MODEL,
            systemInstruction = content {
                text(AlexConfig.buildSystemInstruction())
            }
        )
    }

    fun isNetworkConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun buildAssistantTools(): List<Tool> {
        val dateTimeTool = FunctionDeclaration(
            name = "getCurrentDateTime",
            description = "Returns the current local time, date, and day of the week for Boss.",
            parameters = emptyMap()
        )
        val deviceTelemetryTool = FunctionDeclaration(
            name = "getDeviceTelemetry",
            description = "Returns battery percentage and network connection status of the Android phone.",
            parameters = emptyMap()
        )
        val languageHelpTool = FunctionDeclaration(
            name = "getLanguageCapabilities",
            description = "Returns ALEX AI's supported languages (Bangla, English, Hindi) and voice profile info.",
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
                "[Tool Result: getDeviceTelemetry] batteryPercentage=$batteryPct%, networkOnline=$online"
            }
            "getLanguageCapabilities" -> {
                "[Tool Result: getLanguageCapabilities] supportedLanguages=Bangla (বাংলা), English, Hindi (हिन्दी)"
            }
            else -> {
                "[Tool Result: ${call.name}] executed successfully"
            }
        }
    }

    /**
     * Connects to the official Gemini Live API using Firebase AI Logic (`Firebase.ai.liveModel`)
     * for real-time full-duplex audio input and natural female voice audio output.
     */
    @OptIn(PublicPreviewAPI::class)
    suspend fun connectLiveSession(settings: AlexSettings): LiveSession {
        if (!isNetworkConnected()) {
            throw IllegalStateException("No internet connection detected. Please check Wi-Fi or mobile data.")
        }

        val systemInstructionContent: Content = content {
            text(
                AlexConfig.buildSystemInstruction(
                    userTitle = settings.userTitle,
                    preferredLanguage = settings.preferredLanguage,
                    voiceName = settings.selectedVoiceId
                )
            )
        }

        val liveConfig = liveGenerationConfig {
            responseModality = ResponseModality.AUDIO
            speechConfig = SpeechConfig(Voice(settings.selectedVoiceId))
            inputAudioTranscription = AudioTranscriptionConfig()
            outputAudioTranscription = AudioTranscriptionConfig()
            temperature = 0.8f
        }

        val liveModel: LiveGenerativeModel = Firebase.ai.liveModel(
            modelName = settings.liveModelName,
            generationConfig = liveConfig,
            tools = buildAssistantTools(),
            systemInstruction = systemInstructionContent
        )

        return withTimeout(30_000L) {
            liveModel.connect()
        }
    }

    /**
     * Streams a contextual, multi-turn conversational response from Gemini (`gemini-3.8-flash` by default)
     * with a realistic 60-second timeout window and transparent error handling.
     */
    fun streamChatResponse(
        prompt: String,
        historyTurns: List<TranscriptTurn>,
        settings: AlexSettings
    ): Flow<String> = flow {
        if (!isNetworkConnected()) {
            throw IllegalStateException("No internet connection. Please reconnect and try again.")
        }

        val detectedLanguage = if (settings.preferredLanguage == AlexLanguage.AUTO) {
            LanguageDetector.detectLanguage(prompt)
        } else {
            settings.preferredLanguage
        }

        val model = if (
            settings.chatModelName == AlexConfig.DEFAULT_CHAT_MODEL &&
            settings.userTitle == AlexConfig.DEFAULT_USER_TITLE &&
            settings.preferredLanguage == AlexLanguage.AUTO
        ) {
            defaultGenerativeModel
        } else {
            Firebase.ai.generativeModel(
                modelName = settings.chatModelName,
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

        val chatHistory = historyTurns
            .takeLast(16)
            .filter { it.text.isNotBlank() && !it.isStreaming }
            .map { turn ->
                val role = if (turn.sender == MessageSender.USER) "user" else "model"
                content(role) { text(turn.text) }
            }

        val chat = model.startChat(history = chatHistory)
        val accumulated = StringBuilder()

        withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
            chat.sendMessageStream(prompt).collect { chunk ->
                val chunkText = chunk.text.orEmpty()
                if (chunkText.isNotEmpty()) {
                    accumulated.append(chunkText)
                    emit(accumulated.toString())
                }
            }
        }

        if (accumulated.isBlank()) {
            throw IllegalStateException("ALEX received an empty response from Gemini.")
        }
    }

    /**
     * Runs a live diagnostic verification call against Gemini via Firebase AI Logic + App Check.
     */
    suspend fun runConnectionDiagnostic(settings: AlexSettings): DiagnosticTestResult {
        val startMs = System.currentTimeMillis()
        if (!isNetworkConnected()) {
            return DiagnosticTestResult(
                success = false,
                latencyMs = 0L,
                modelTested = settings.chatModelName,
                responseSnippet = "",
                errorDetail = "Device is offline. Please enable internet access."
            )
        }

        return try {
            val response = withTimeout(AlexConfig.GENERATION_TIMEOUT_MS) {
                defaultGenerativeModel.generateContent(
                    "Reply in one short sentence as ALEX greeting ${settings.userTitle} and confirming systems are online."
                )
            }
            val elapsed = System.currentTimeMillis() - startMs
            val text = response.text?.trim().orEmpty()
            if (text.isNotEmpty()) {
                DiagnosticTestResult(
                    success = true,
                    latencyMs = elapsed,
                    modelTested = settings.chatModelName,
                    responseSnippet = text
                )
            } else {
                DiagnosticTestResult(
                    success = false,
                    latencyMs = elapsed,
                    modelTested = settings.chatModelName,
                    responseSnippet = "",
                    errorDetail = "Model returned an empty diagnostic response."
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Diagnostic timed out", e)
            DiagnosticTestResult(
                success = false,
                latencyMs = System.currentTimeMillis() - startMs,
                modelTested = settings.chatModelName,
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
                modelTested = settings.chatModelName,
                responseSnippet = "",
                errorDetail = e.localizedMessage ?: "Gemini diagnostic call failed."
            )
        }
    }
}
