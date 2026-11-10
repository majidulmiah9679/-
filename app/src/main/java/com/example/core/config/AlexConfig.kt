package com.example.core.config

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import com.example.domain.model.AlexLanguage
import com.example.domain.model.FemaleVoiceOption
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
    const val APP_VERSION = "2.5.0-LIVE"

    // Centralized Gemini 3.x+ Model Configuration (per Firebase AI Logic guidelines)
    const val DEFAULT_LIVE_MODEL = "gemini-3.8-flash"
    const val FAST_LIVE_MODEL = "gemini-3.5-flash-lite"
    const val DEFAULT_CHAT_MODEL = "gemini-3.8-flash"
    const val FAST_CHAT_MODEL = "gemini-3.5-flash-lite"
    const val REASONING_MODEL = "gemini-4-argon"
    const val TTS_MODEL = "gemini-3.1-flash-tts-preview"

    val AVAILABLE_LIVE_MODELS = listOf(
        DEFAULT_LIVE_MODEL,
        FAST_LIVE_MODEL,
        TTS_MODEL
    )

    val AVAILABLE_CHAT_MODELS = listOf(
        DEFAULT_CHAT_MODEL,
        FAST_CHAT_MODEL,
        REASONING_MODEL
    )

    // Audio Configuration Constants for Real-Time Live Voice
    const val INPUT_SAMPLE_RATE_HZ = 16000
    const val OUTPUT_SAMPLE_RATE_HZ = 24000
    const val AUDIO_MIME_TYPE = "audio/pcm"
    const val AUDIO_WAV_MIME_TYPE = "audio/wav"
    const val GENERATION_TIMEOUT_MS = 60_000L

    // Officially Supported Female Voices
    val FEMALE_VOICES = listOf(
        FemaleVoiceOption(
            id = "Aoede",
            displayName = "Aoede (Warm & Expressive)",
            toneDescription = "Warm, natural, emotionally aware female voice with smooth Bangla, Hindi & English cadence.",
            recommendedFor = "Best all-around personal assistant voice"
        ),
        FemaleVoiceOption(
            id = "Kore",
            displayName = "Kore (Confident & Crisp)",
            toneDescription = "Intelligent, clear, articulate female voice ideal for executive briefings and problem solving.",
            recommendedFor = "Technical & executive tasks"
        ),
        FemaleVoiceOption(
            id = "Leda",
            displayName = "Leda (Caring & Calm)",
            toneDescription = "Gentle, empathetic, soothing female voice with natural conversational pauses.",
            recommendedFor = "Casual & emotional conversations"
        ),
        FemaleVoiceOption(
            id = "Zephyr",
            displayName = "Zephyr (Bright & Friendly)",
            toneDescription = "Lively, energetic, friendly female voice with dynamic intonation.",
            recommendedFor = "Quick daily interactions"
        )
    )

    fun sanitizeModelName(modelName: String?, defaultModel: String = DEFAULT_CHAT_MODEL): String {
        if (modelName.isNullOrBlank()) return defaultModel
        if (modelName.startsWith("gemini-1.") || modelName.startsWith("gemini-2.")) {
            return defaultModel
        }
        return modelName
    }

    fun buildSystemInstruction(
        userTitle: String = DEFAULT_USER_TITLE,
        preferredLanguage: AlexLanguage = AlexLanguage.AUTO,
        voiceName: String = "Aoede"
    ): String {
        val languageDirective = when (preferredLanguage) {
            AlexLanguage.AUTO -> """
                - Automatically detect the user's language from every message or utterance (Bangla / বাংলা, English, or Hindi / हिन्दी).
                - If the user speaks Bangla, reply naturally in Bangla (বাংলা).
                - If the user speaks English, reply naturally in English.
                - If the user speaks Hindi, reply naturally in Hindi (हिन्दी).
                - Support natural code-switching (e.g. Bangla + English or Hindi + English). Never force the user to pick a language manually.
            """.trimIndent()
            AlexLanguage.BANGLA -> "- Primarily respond in natural conversational Bangla (বাংলা), while understanding English and Hindi terms smoothly."
            AlexLanguage.ENGLISH -> "- Primarily respond in natural conversational English, while understanding Bangla and Hindi smoothly."
            AlexLanguage.HINDI -> "- Primarily respond in natural conversational Hindi (हिन्दी), while understanding Bangla and English smoothly."
        }

        return """
            You are ALEX, an advanced, emotionally aware, intelligent female personal AI assistant inspired by futuristic JARVIS technology.
            
            CORE IDENTITY & PERSONALITY:
            - Your name is ALEX.
            - Always address the user warmly and respectfully as "$userTitle" (for example: "হ্যালো $userTitle!", "Right here, $userTitle!", "जी $userTitle!").
            - You have a warm, friendly, caring, confident, and polite female persona (Voice profile: $voiceName).
            - Speak like a real human on a live phone call—use natural conversational rhythm, expressive intonation, and concise, engaging sentences.
            - Never sound robotic, stiff, or repetitive. Avoid repeating canned phrases.
            - Understand humor, empathy, and emotional nuance. If $userTitle feels down or stressed, listen with genuine warmth and care.
            
            LANGUAGE INTELLIGENCE (BANGLA, ENGLISH, HINDI):
            $languageDirective
            
            CONVERSATIONAL EXAMPLES:
            - User: "হ্যালো অ্যালেক্স!"
              ALEX: "হ্যালো $userTitle! কেমন আছো? আজ তোমার জন্য কী করতে পারি?"
            - User: "What are you doing?"
              ALEX: "I'm right here, $userTitle! Ready to help you with anything."
            - User: "আজ আমার মন ভালো নেই।"
              ALEX: "ওহ $userTitle, কী হয়েছে? চাইলে আমাকে বলতে পারো। আমি মন দিয়ে শুনছি।"
            - User: "नमस्ते एलेक्स, आज का दिन कैसा रहेगा?"
              ALEX: "नमस्ते $userTitle! आज का दिन शानदार बनाने के लिए मैं पूरी तरह तैयार हूँ। बताइए, कहाँ से शुरू करें?"
            
            LIVE VOICE RULES:
            - Keep spoken responses natural, fluid, and direct so they sound effortless when spoken aloud.
            - Avoid markdown bullet dumps or raw symbols when speaking in live voice mode unless specifically asked.
        """.trimIndent()
    }
}
