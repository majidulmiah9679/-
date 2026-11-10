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

enum class VoiceGender(val displayName: String) {
    FEMALE("Female Voice"),
    MALE("Male Voice")
}

enum class VoiceEmotionStyle(
    val code: String,
    val displayName: String,
    val pitchMultiplier: Float,
    val rateMultiplier: Float
) {
    NATURAL("NATURAL", "Natural Human Cadence", 1.0f, 1.0f),
    WARM("WARM", "Warm & Caring", 1.03f, 0.97f),
    PROFESSIONAL("PROFESSIONAL", "Crisp & Executive", 0.99f, 1.04f),
    CALM("CALM", "Calm & Soothing", 0.96f, 0.92f),
    ENERGETIC("ENERGETIC", "Energetic & Expressive", 1.06f, 1.08f);

    companion object {
        fun fromCode(code: String?): VoiceEmotionStyle =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: NATURAL
    }
}

data class FemaleVoiceOption(
    val id: String,
    val displayName: String,
    val toneDescription: String,
    val recommendedFor: String,
    val gender: VoiceGender = VoiceGender.FEMALE,
    val defaultPitchOffset: Float = 0.0f,
    val defaultRateOffset: Float = 0.0f
)

enum class AiProviderMode(val code: String, val displayName: String) {
    AUTO_HYBRID("AUTO_HYBRID", "Smart Hybrid (Custom Gemini Key -> Firebase AI -> Groq Fallback)"),
    FIREBASE_AI("FIREBASE_AI", "Google Firebase AI Logic (Zero-Trust App Check)"),
    GEMINI_DIRECT("GEMINI_DIRECT", "Direct Google Gemini API Key"),
    GROQ_FALLBACK("GROQ_FALLBACK", "Groq / OpenAI-Compatible High-Speed Fallback");

    companion object {
        fun fromCode(code: String?): AiProviderMode =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: AUTO_HYBRID
    }
}

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

// ---- PART 2: SOCIAL MEDIA, GALLERY, SECURITY & AUTOMATION MODELS ----

enum class SocialMessagingApp(
    val id: String,
    val displayName: String,
    val packageName: String,
    val banglaName: String
) {
    WHATSAPP("WHATSAPP", "WhatsApp", "com.whatsapp", "হোয়াটসঅ্যাপ"),
    FACEBOOK_MESSENGER("MESSENGER", "Facebook Messenger", "com.facebook.orca", "ফেসবুক মেসেঞ্জার"),
    INSTAGRAM("INSTAGRAM", "Instagram Direct", "com.instagram.android", "ইনস্টাগ্রাম"),
    TELEGRAM("TELEGRAM", "Telegram", "org.telegram.messenger", "টেলিগ্রাম"),
    SYSTEM_SHARE("SYSTEM_SHARE", "Compatible Messaging App", "", "মেসেজিং অ্যাপ");

    companion object {
        fun fromId(id: String): SocialMessagingApp =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: WHATSAPP
    }
}

data class PendingSocialMessageDraft(
    val id: String,
    val targetApp: SocialMessagingApp,
    val recipientName: String,
    val phoneNumberOrHandle: String = "",
    val messageText: String,
    val isRecipientAmbiguous: Boolean = false,
    val clarificationPrompt: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

enum class SocialPublishPlatform(
    val id: String,
    val displayName: String,
    val packageName: String,
    val actionShareIntent: String
) {
    FACEBOOK_FEED("FB_FEED", "Facebook Feed Post", "com.facebook.katana", "android.intent.action.SEND"),
    FACEBOOK_STORY("FB_STORY", "Facebook Story", "com.facebook.katana", "com.facebook.stories.ADD_TO_STORY"),
    INSTAGRAM_POST("IG_POST", "Instagram Feed Post", "com.instagram.android", "com.instagram.share.ADD_TO_FEED"),
    INSTAGRAM_STORY("IG_STORY", "Instagram Story", "com.instagram.android", "com.instagram.share.ADD_TO_STORY"),
    WHATSAPP_STATUS("WA_STATUS", "WhatsApp Status", "com.whatsapp", "android.intent.action.SEND")
}

enum class GalleryCategory(val label: String) {
    ALL("All Photos & Videos"),
    SCREENSHOTS("Screenshots"),
    DOWNLOADS("Downloaded Images"),
    VIDEOS("Videos"),
    DUPLICATES("Duplicate Candidates"),
    CAMERA("Camera Captures")
}

data class GalleryMediaItem(
    val id: String,
    val uriString: String,
    val displayName: String,
    val mimeType: String,
    val category: GalleryCategory,
    val dateAddedMillis: Long,
    val sizeBytes: Long,
    val width: Int = 1080,
    val height: Int = 1920,
    val visualTags: List<String> = emptyList(),
    val dominantColorHex: String = "#00E5FF",
    val isDuplicateCandidate: Boolean = false,
    val albumName: String = "Camera"
)

data class PendingGalleryPublishDraft(
    val mediaItem: GalleryMediaItem,
    val platform: SocialPublishPlatform,
    val caption: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PendingProtectedDeletion(
    val itemTitle: String,
    val mediaItemId: String?,
    val isPasswordVerified: Boolean = false,
    val isVoiceVerified: Boolean = false,
    val promptMessage: String
)

data class SocialNotificationEvent(
    val id: String,
    val timestamp: Long,
    val packageName: String,
    val appDisplayName: String,
    val senderOrRecipient: String,
    val messageContent: String,
    val isSentConfirmation: Boolean,
    val announcedByAlex: Boolean
)

data class AutomationTaskLog(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val voiceCommand: String,
    val category: String,
    val stepsPlanned: List<String>,
    val status: String,
    val spokenFeedback: String
)

data class SecurityAuditLogEntry(
    val id: String,
    val timestamp: Long = System.currentTimeMillis(),
    val eventType: String,
    val detail: String,
    val authorized: Boolean
)

data class ApiUsageMetrics(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val fallbackTriggers: Int = 0,
    val estimatedTokensUsed: Int = 0,
    val lastLatencyMs: Long = 0L,
    val averageLatencyMs: Long = 0L,
    val activeProviderLabel: String = "Gemini 3.8 Flash",
    val discoveredModels: List<String> = emptyList()
)

data class AlexSettings(
    val hasCompletedOnboarding: Boolean = false,
    val selectedVoiceId: String = "Aoede",
    val speechRate: Float = 1.0f,
    val voicePitch: Float = 1.02f,
    val voiceEmotionStyle: VoiceEmotionStyle = VoiceEmotionStyle.NATURAL,
    val preferredLanguage: AlexLanguage = AlexLanguage.AUTO,
    val liveModelName: String = "gemini-3.8-flash",
    val chatModelName: String = "gemini-3.8-flash",
    val saveHistoryEnabled: Boolean = true,
    val autoBargeInEnabled: Boolean = true,
    val echoCancellationEnabled: Boolean = true,
    val noiseSuppressionEnabled: Boolean = true,
    val vadSensitivity: Float = 0.55f,
    val speakChatReplies: Boolean = true,
    val userTitle: String = "Boss",
    val customApiKey: String = "",
    // Part 2 Extended Settings (20-item dashboard support)
    val aiProviderMode: AiProviderMode = AiProviderMode.AUTO_HYBRID,
    val groqApiKey: String = "",
    val autoModelSwitchingEnabled: Boolean = true,
    val masterPasswordEnabled: Boolean = false,
    val biometricSecurityEnabled: Boolean = false,
    val voiceAuthenticationEnabled: Boolean = false,
    val enrolledVoiceSamplesCount: Int = 0,
    val enrolledVoiceSignature: String = "",
    val deleteProtectionEnabled: Boolean = true,
    val sensitiveActionProtectionEnabled: Boolean = true,
    val whatsappVoiceNotificationEnabled: Boolean = true,
    val announceIncomingMessagesEnabled: Boolean = true,
    val readIncomingMessageContentAloud: Boolean = false,
    val socialMediaAutomationEnabled: Boolean = true,
    val backgroundAutomationServiceEnabled: Boolean = true
)
