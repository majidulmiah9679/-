package com.example.domain.model

enum class AlexLiveStatus(val label: String) {
    READY("READY"),
    CONNECTING("CONNECTING"),
    LISTENING("LISTENING"),
    THINKING("THINKING"),
    SPEAKING("SPEAKING"),
    DISCONNECTED("DISCONNECTED")
}

enum class AlexLanguage(
    val code: String,
    val displayName: String,
    val nativeLabel: String,
    val bcp47Tag: String
) {
    AUTO("AUTO", "Auto-Detect (Bangla / English / Hindi)", "AUTO • বাংলা / EN / हिं", "en-US"),
    BANGLA("BN", "Bangla (বাংলা)", "বাংলা", "bn-BD"),
    ENGLISH("EN", "English", "English", "en-US"),
    HINDI("HI", "Hindi (हिन्दी)", "हिन्दी", "hi-IN");

    companion object {
        fun fromCode(code: String): AlexLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO
    }
}

data class FemaleVoiceOption(
    val id: String,
    val displayName: String,
    val toneDescription: String,
    val recommendedFor: String
)

enum class MessageSender {
    USER,
    ALEX,
    SYSTEM
}

enum class ConversationModality {
    LIVE_VOICE,
    TEXT_CHAT
}

data class TranscriptTurn(
    val id: String,
    val sender: MessageSender,
    val text: String,
    val language: AlexLanguage,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val wasInterrupted: Boolean = false
)

data class ConversationSummary(
    val id: Long,
    val title: String,
    val previewText: String,
    val modality: ConversationModality,
    val primaryLanguage: AlexLanguage,
    val messageCount: Int,
    val createdAt: Long,
    val updatedAt: Long
)

data class AlexSettings(
    val hasCompletedOnboarding: Boolean = false,
    val selectedVoiceId: String = "Aoede",
    val speechRate: Float = 1.0f,
    val voicePitch: Float = 1.02f,
    val preferredLanguage: AlexLanguage = AlexLanguage.AUTO,
    val liveModelName: String = "gemini-2.0-flash-live-preview-04-09",
    val chatModelName: String = "gemini-3.8-flash",
    val saveHistoryEnabled: Boolean = true,
    val autoBargeInEnabled: Boolean = true,
    val echoCancellationEnabled: Boolean = true,
    val noiseSuppressionEnabled: Boolean = true,
    val vadSensitivity: Float = 0.55f,
    val speakChatReplies: Boolean = true,
    val userTitle: String = "Boss"
)
