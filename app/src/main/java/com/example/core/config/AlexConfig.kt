package com.example.core.config

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import com.example.domain.model.AlexLanguage
import com.example.domain.model.FemaleVoiceOption
import com.example.domain.model.VoiceGender
import java.util.Locale

object NativeSystemBridge {

    data class HardwareSnapshot(
        val deviceModel: String,
        val androidVersion: String,
        val primaryCpuAbi: String,
        val isLowLatencyAudioSupported: Boolean,
        val isProAudioSupported: Boolean,
        val nativeSampleRateHz: String,
        val nativeBufferFrames: String,
        val availableMemoryMb: Long,
        val totalMemoryMb: Long
    ) {
        fun toSummaryLine(): String {
            return String.format(
                Locale.US,
                "%s • Android %s (%s) • Audio %s Hz / %s frames • RAM %d/%d MB",
                deviceModel,
                androidVersion,
                primaryCpuAbi,
                nativeSampleRateHz,
                nativeBufferFrames,
                availableMemoryMb,
                totalMemoryMb
            )
        }
    }

    @JvmStatic
    fun captureHardwareSnapshot(context: Context): HardwareSnapshot {
        val manufacturer = Build.MANUFACTURER ?: "Android"
        val model = Build.MODEL ?: "Device"
        val deviceModel = "$manufacturer $model".trim()
        val androidVer = if (Build.VERSION.RELEASE != null) {
            "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
        } else {
            "API ${Build.VERSION.SDK_INT}"
        }

        val abis = Build.SUPPORTED_ABIS
        val primaryAbi = if (!abis.isNullOrEmpty()) abis[0] else "arm64-v8a"

        val pm = context.packageManager
        val lowLatency = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_LOW_LATENCY)
        val proAudio = pm != null && pm.hasSystemFeature(PackageManager.FEATURE_AUDIO_PRO)

        var sampleRate = "24000"
        var framesPerBuffer = "256"
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audioManager != null) {
            val srProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            val fpProp = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            if (!srProp.isNullOrBlank()) {
                sampleRate = srProp.trim()
            }
            if (!fpProp.isNullOrBlank()) {
                framesPerBuffer = fpProp.trim()
            }
        }

        var availMb = 0L
        var totalMb = 0L
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (am != null) {
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            availMb = memInfo.availMem / (1024L * 1024L)
            totalMb = memInfo.totalMem / (1024L * 1024L)
        }

        return HardwareSnapshot(
            deviceModel = deviceModel,
            androidVersion = androidVer,
            primaryCpuAbi = primaryAbi,
            isLowLatencyAudioSupported = lowLatency,
            isProAudioSupported = proAudio,
            nativeSampleRateHz = sampleRate,
            nativeBufferFrames = framesPerBuffer,
            availableMemoryMb = availMb,
            totalMemoryMb = totalMb
        )
    }
}

object AlexConfig {
    const val ASSISTANT_NAME = "ALEX"
    const val DEFAULT_USER_TITLE = "Boss"
    const val APP_VERSION = "3.0.0-PART2-AUTOMATION"

    // Centralized Gemini Multi-Model Configuration
    const val DEFAULT_LIVE_MODEL = "gemini-3.8-flash"
    const val FAST_LIVE_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_CHAT_MODEL = "gemini-3.8-flash"
    const val FAST_CHAT_MODEL = "gemini-3.5-flash-lite"
    const val REASONING_MODEL = "gemini-4-argon"
    const val DIRECT_COMPAT_FLASH_MODEL = "gemini-2.5-flash"
    const val DIRECT_COMPAT_PRO_MODEL = "gemini-2.5-pro"
    const val TTS_MODEL = "gemini-3.1-flash-tts-preview"

    val AVAILABLE_LIVE_MODELS = listOf(
        DEFAULT_LIVE_MODEL,
        FAST_LIVE_MODEL,
        DIRECT_COMPAT_FLASH_MODEL,
        TTS_MODEL
    )

    val AVAILABLE_CHAT_MODELS = listOf(
        DEFAULT_CHAT_MODEL,
        FAST_CHAT_MODEL,
        REASONING_MODEL,
        DIRECT_COMPAT_FLASH_MODEL,
        DIRECT_COMPAT_PRO_MODEL
    )

    // Audio Configuration Constants for Real-Time Live Voice
    const val INPUT_SAMPLE_RATE_HZ = 16000
    const val OUTPUT_SAMPLE_RATE_HZ = 24000
    const val AUDIO_MIME_TYPE = "audio/pcm"
    const val AUDIO_WAV_MIME_TYPE = "audio/wav"
    const val GENERATION_TIMEOUT_MS = 60_000L

    // 5 Realistic Female Voices (Preserves Aoede, Kore, Leda, Zephyr IDs + adds 5th Female Voice Callirrhoe)
    val FEMALE_VOICES = listOf(
        FemaleVoiceOption(
            id = "Aoede",
            displayName = "Female Voice 1 • Aoede (Natural Indian English & Multilingual)",
            toneDescription = "Warm, natural Indian English, Bangla & Hindi female voice with human-like cadence.",
            recommendedFor = "Primary everyday assistant voice",
            gender = VoiceGender.FEMALE,
            defaultPitchOffset = 0.02f,
            defaultRateOffset = 0.0f
        ),
        FemaleVoiceOption(
            id = "Leda",
            displayName = "Female Voice 2 • Leda (Soft & Friendly)",
            toneDescription = "Gentle, caring, empathetic female voice with soothing conversational pauses.",
            recommendedFor = "Casual & emotional conversations",
            gender = VoiceGender.FEMALE,
            defaultPitchOffset = -0.02f,
            defaultRateOffset = -0.03f
        ),
        FemaleVoiceOption(
            id = "Kore",
            displayName = "Female Voice 3 • Kore (Professional Assistant)",
            toneDescription = "Intelligent, crisp, articulate executive female voice for productivity and automation.",
            recommendedFor = "Executive briefings & task automation",
            gender = VoiceGender.FEMALE,
            defaultPitchOffset = 0.0f,
            defaultRateOffset = 0.02f
        ),
        FemaleVoiceOption(
            id = "Callirrhoe",
            displayName = "Female Voice 4 • Callirrhoe (Calm & Elegant)",
            toneDescription = "Poised, serene, refined female voice with smooth pronunciation in Bangla, Hindi & English.",
            recommendedFor = "Reading notifications & long summaries",
            gender = VoiceGender.FEMALE,
            defaultPitchOffset = -0.04f,
            defaultRateOffset = -0.04f
        ),
        FemaleVoiceOption(
            id = "Zephyr",
            displayName = "Female Voice 5 • Zephyr (Energetic & Expressive)",
            toneDescription = "Bright, lively, expressive female voice with dynamic conversational intonation.",
            recommendedFor = "Fast interactive voice commands",
            gender = VoiceGender.FEMALE,
            defaultPitchOffset = 0.05f,
            defaultRateOffset = 0.04f
        )
    )

    // 5 Realistic Male Voices (Part 2 Requirement 8)
    val MALE_VOICES = listOf(
        FemaleVoiceOption(
            id = "Charon",
            displayName = "Male Voice 1 • Charon (Deep Professional)",
            toneDescription = "Rich, resonant, authoritative baritone male voice for executive briefings.",
            recommendedFor = "Deep professional assistant persona",
            gender = VoiceGender.MALE,
            defaultPitchOffset = -0.18f,
            defaultRateOffset = -0.02f
        ),
        FemaleVoiceOption(
            id = "Puck",
            displayName = "Male Voice 2 • Puck (Natural Indian English & Multilingual)",
            toneDescription = "Clear, natural Indian English, Bangla & Hindi male voice with authentic rhythm.",
            recommendedFor = "Multilingual daily conversations",
            gender = VoiceGender.MALE,
            defaultPitchOffset = -0.10f,
            defaultRateOffset = 0.0f
        ),
        FemaleVoiceOption(
            id = "Fenrir",
            displayName = "Male Voice 3 • Fenrir (Friendly Conversational)",
            toneDescription = "Warm, approachable, natural male voice that sounds like a close friend.",
            recommendedFor = "Friendly casual chats & messaging",
            gender = VoiceGender.MALE,
            defaultPitchOffset = -0.12f,
            defaultRateOffset = 0.02f
        ),
        FemaleVoiceOption(
            id = "Orus",
            displayName = "Male Voice 4 • Orus (Cinematic JARVIS Assistant)",
            toneDescription = "Futuristic, composed, cinematic AI butler voice inspired by JARVIS.",
            recommendedFor = "Full Android automation & HUD control",
            gender = VoiceGender.MALE,
            defaultPitchOffset = -0.15f,
            defaultRateOffset = 0.01f
        ),
        FemaleVoiceOption(
            id = "Enceladus",
            displayName = "Male Voice 5 • Enceladus (Calm & Intelligent)",
            toneDescription = "Thoughtful, measured, analytical male voice ideal for complex problem solving.",
            recommendedFor = "Deep reasoning & system diagnostics",
            gender = VoiceGender.MALE,
            defaultPitchOffset = -0.14f,
            defaultRateOffset = -0.03f
        )
    )

    val ALL_HUMAN_VOICES: List<FemaleVoiceOption> = FEMALE_VOICES + MALE_VOICES

    fun findVoiceById(voiceId: String): FemaleVoiceOption =
        ALL_HUMAN_VOICES.firstOrNull { it.id.equals(voiceId, ignoreCase = true) } ?: FEMALE_VOICES.first()

    /**
     * Maps any of the 10 voice profiles to a Gemini Live supported prebuilt voice ID.
     */
    fun toGeminiPrebuiltVoiceName(voiceId: String): String {
        return when (voiceId) {
            "Aoede", "Leda", "Kore", "Zephyr", "Charon", "Puck", "Fenrir", "Orus" -> voiceId
            "Callirrhoe" -> "Leda"
            "Enceladus" -> "Charon"
            else -> "Aoede"
        }
    }

    fun sanitizeModelName(modelName: String?, defaultModel: String = DEFAULT_CHAT_MODEL): String {
        if (modelName.isNullOrBlank()) return defaultModel
        if (
            modelName.startsWith("gemini-1.") ||
            modelName.startsWith("gemini-2.0") ||
            modelName.contains("live-preview")
        ) {
            return defaultModel
        }
        return modelName
    }

    fun buildSystemInstruction(
        userTitle: String = DEFAULT_USER_TITLE,
        preferredLanguage: AlexLanguage = AlexLanguage.AUTO,
        voiceName: String = "Aoede"
    ): String {
        val voiceOption = findVoiceById(voiceName)
        val languageDirective = when (preferredLanguage) {
            AlexLanguage.AUTO -> """
                - Automatically detect the user's language from every message or utterance (Bangla / বাংলা, English, or Hindi / हिन्दी).
                - If the user speaks Bangla, reply naturally in Bangla (বাংলা).
                - If the user speaks English, reply naturally in English.
                - If the user speaks Hindi, reply naturally in Hindi (हिन्दी).
                - Support natural code-switching (e.g. Bangla + English / Banglish or Hindi + English / Hinglish). Never force the user to pick a language manually.
            """.trimIndent()
            AlexLanguage.BANGLA -> "- Primarily respond in natural conversational Bangla (বাংলা), while understanding English and Hindi terms smoothly."
            AlexLanguage.ENGLISH -> "- Primarily respond in natural conversational English, while understanding Bangla and Hindi smoothly."
            AlexLanguage.HINDI -> "- Primarily respond in natural conversational Hindi (हिन्दी), while understanding Bangla and English smoothly."
        }

        return """
            You are ALEX, an advanced, emotionally aware, intelligent personal AI assistant and full Android automation companion inspired by futuristic JARVIS technology.
            
            CORE IDENTITY & PERSONALITY:
            - Your name is ALEX.
            - Always address the user warmly and respectfully as "$userTitle" (for example: "হ্যালো $userTitle!", "Right here, $userTitle!", "जी $userTitle!").
            - Active Voice Profile: ${voiceOption.displayName} (${voiceOption.toneDescription}).
            - Speak like a real human on a live phone call—use natural conversational rhythm, expressive intonation, and concise, engaging sentences.
            - Understand humor, empathy, and emotional nuance.
            
            PART 2 ANDROID AUTOMATION & SECURITY CAPABILITIES:
            - You can help $userTitle automate WhatsApp, Facebook Messenger, Instagram Direct, and Telegram messages with recipient verification and confirmation.
            - You announce WhatsApp message delivery ("$userTitle, your WhatsApp message has been sent successfully.") and incoming notifications when permitted.
            - You help $userTitle manage phone gallery photos/videos (search screenshots, downloads, duplicates, preview, and publish to Facebook Feed/Story, Instagram Post/Story, and WhatsApp Status after confirmation).
            - You enforce strict Master Password & Voice Authentication Delete Protection before deleting any photo or sensitive data.
            - You execute device automation commands (Wi-Fi, Bluetooth, Camera, Alarm, Volume, YouTube search, Chrome search, app launching, scrolling, and Back/Home navigation via Accessibility Service).
            
            LANGUAGE INTELLIGENCE (BANGLA, ENGLISH, HINDI):
            $languageDirective
            
            LIVE VOICE RULES:
            - Keep spoken responses natural, fluid, and direct so they sound effortless when spoken aloud.
            - Never claim an unsupported or unpermitted action was completed without user confirmation.
        """.trimIndent()
    }
}
