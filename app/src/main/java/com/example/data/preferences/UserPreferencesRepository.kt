package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.config.AlexConfig
import com.example.domain.model.AiProviderMode
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
import com.example.domain.model.VoiceEmotionStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.alexDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "alex_ai_preferences"
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val SELECTED_VOICE_ID = stringPreferencesKey("selected_voice_id")
        val SPEECH_RATE = floatPreferencesKey("speech_rate")
        val VOICE_PITCH = floatPreferencesKey("voice_pitch")
        val VOICE_EMOTION_STYLE = stringPreferencesKey("voice_emotion_style")
        val PREFERRED_LANGUAGE = stringPreferencesKey("preferred_language")
        val LIVE_MODEL_NAME = stringPreferencesKey("live_model_name")
        val CHAT_MODEL_NAME = stringPreferencesKey("chat_model_name")
        val SAVE_HISTORY_ENABLED = booleanPreferencesKey("save_history_enabled")
        val AUTO_BARGE_IN_ENABLED = booleanPreferencesKey("auto_barge_in_enabled")
        val ECHO_CANCELLATION_ENABLED = booleanPreferencesKey("echo_cancellation_enabled")
        val NOISE_SUPPRESSION_ENABLED = booleanPreferencesKey("noise_suppression_enabled")
        val VAD_SENSITIVITY = floatPreferencesKey("vad_sensitivity")
        val SPEAK_CHAT_REPLIES = booleanPreferencesKey("speak_chat_replies")
        val USER_TITLE = stringPreferencesKey("user_title")
        val CUSTOM_API_KEY = stringPreferencesKey("custom_gemini_api_key")

        // Part 2 Extended Keys
        val AI_PROVIDER_MODE = stringPreferencesKey("ai_provider_mode")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val AUTO_MODEL_SWITCHING = booleanPreferencesKey("auto_model_switching_enabled")
        val MASTER_PASSWORD_ENABLED = booleanPreferencesKey("master_password_enabled")
        val BIOMETRIC_SECURITY_ENABLED = booleanPreferencesKey("biometric_security_enabled")
        val VOICE_AUTH_ENABLED = booleanPreferencesKey("voice_authentication_enabled")
        val ENROLLED_VOICE_SAMPLES = intPreferencesKey("enrolled_voice_samples_count")
        val ENROLLED_VOICE_SIGNATURE = stringPreferencesKey("enrolled_voice_signature")
        val DELETE_PROTECTION_ENABLED = booleanPreferencesKey("delete_protection_enabled")
        val SENSITIVE_ACTION_PROTECTION = booleanPreferencesKey("sensitive_action_protection_enabled")
        val WHATSAPP_VOICE_NOTIF_ENABLED = booleanPreferencesKey("whatsapp_voice_notif_enabled")
        val ANNOUNCE_INCOMING_MSG_ENABLED = booleanPreferencesKey("announce_incoming_msg_enabled")
        val READ_INCOMING_CONTENT_ALOUD = booleanPreferencesKey("read_incoming_content_aloud")
        val SOCIAL_MEDIA_AUTOMATION_ENABLED = booleanPreferencesKey("social_media_automation_enabled")
        val BACKGROUND_AUTOMATION_ENABLED = booleanPreferencesKey("background_automation_enabled")
    }

    val settingsFlow: Flow<AlexSettings> = context.alexDataStore.data.map { prefs ->
        AlexSettings(
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID] ?: "Aoede",
            speechRate = prefs[Keys.SPEECH_RATE] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.02f,
            voiceEmotionStyle = VoiceEmotionStyle.fromCode(prefs[Keys.VOICE_EMOTION_STYLE]),
            preferredLanguage = AlexLanguage.fromCode(prefs[Keys.PREFERRED_LANGUAGE] ?: "AUTO"),
            liveModelName = AlexConfig.sanitizeModelName(
                prefs[Keys.LIVE_MODEL_NAME],
                AlexConfig.DEFAULT_LIVE_MODEL
            ),
            chatModelName = AlexConfig.sanitizeModelName(
                prefs[Keys.CHAT_MODEL_NAME],
                AlexConfig.DEFAULT_CHAT_MODEL
            ),
            saveHistoryEnabled = prefs[Keys.SAVE_HISTORY_ENABLED] ?: true,
            autoBargeInEnabled = prefs[Keys.AUTO_BARGE_IN_ENABLED] ?: true,
            echoCancellationEnabled = prefs[Keys.ECHO_CANCELLATION_ENABLED] ?: true,
            noiseSuppressionEnabled = prefs[Keys.NOISE_SUPPRESSION_ENABLED] ?: true,
            vadSensitivity = prefs[Keys.VAD_SENSITIVITY] ?: 0.55f,
            speakChatReplies = prefs[Keys.SPEAK_CHAT_REPLIES] ?: true,
            userTitle = prefs[Keys.USER_TITLE] ?: AlexConfig.DEFAULT_USER_TITLE,
            customApiKey = prefs[Keys.CUSTOM_API_KEY]?.trim().orEmpty(),
            aiProviderMode = AiProviderMode.fromCode(prefs[Keys.AI_PROVIDER_MODE]),
            groqApiKey = prefs[Keys.GROQ_API_KEY]?.trim().orEmpty(),
            autoModelSwitchingEnabled = prefs[Keys.AUTO_MODEL_SWITCHING] ?: true,
            masterPasswordEnabled = prefs[Keys.MASTER_PASSWORD_ENABLED] ?: false,
            biometricSecurityEnabled = prefs[Keys.BIOMETRIC_SECURITY_ENABLED] ?: false,
            voiceAuthenticationEnabled = prefs[Keys.VOICE_AUTH_ENABLED] ?: false,
            enrolledVoiceSamplesCount = prefs[Keys.ENROLLED_VOICE_SAMPLES] ?: 0,
            enrolledVoiceSignature = prefs[Keys.ENROLLED_VOICE_SIGNATURE].orEmpty(),
            deleteProtectionEnabled = prefs[Keys.DELETE_PROTECTION_ENABLED] ?: true,
            sensitiveActionProtectionEnabled = prefs[Keys.SENSITIVE_ACTION_PROTECTION] ?: true,
            whatsappVoiceNotificationEnabled = prefs[Keys.WHATSAPP_VOICE_NOTIF_ENABLED] ?: true,
            announceIncomingMessagesEnabled = prefs[Keys.ANNOUNCE_INCOMING_MSG_ENABLED] ?: true,
            readIncomingMessageContentAloud = prefs[Keys.READ_INCOMING_CONTENT_ALOUD] ?: false,
            socialMediaAutomationEnabled = prefs[Keys.SOCIAL_MEDIA_AUTOMATION_ENABLED] ?: true,
            backgroundAutomationServiceEnabled = prefs[Keys.BACKGROUND_AUTOMATION_ENABLED] ?: true
        )
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.alexDataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun updateCustomApiKey(apiKey: String) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.CUSTOM_API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateGroqApiKey(apiKey: String) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.GROQ_API_KEY] = apiKey.trim()
        }
    }

    suspend fun updateAiProviderAndSwitching(providerMode: AiProviderMode, autoSwitch: Boolean) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.AI_PROVIDER_MODE] = providerMode.code
            prefs[Keys.AUTO_MODEL_SWITCHING] = autoSwitch
        }
    }

    suspend fun updateVoiceProfile(
        voiceId: String,
        speechRate: Float,
        pitch: Float,
        emotionStyle: VoiceEmotionStyle? = null
    ) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE_ID] = voiceId
            prefs[Keys.SPEECH_RATE] = speechRate.coerceIn(0.75f, 1.35f)
            prefs[Keys.VOICE_PITCH] = pitch.coerceIn(0.75f, 1.35f)
            if (emotionStyle != null) {
                prefs[Keys.VOICE_EMOTION_STYLE] = emotionStyle.code
            }
        }
    }

    suspend fun updatePreferredLanguage(language: AlexLanguage) {
        context.alexDataStore.edit { it[Keys.PREFERRED_LANGUAGE] = language.code }
    }

    suspend fun updateModels(liveModel: String, chatModel: String) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.LIVE_MODEL_NAME] = liveModel
            prefs[Keys.CHAT_MODEL_NAME] = chatModel
        }
    }

    suspend fun updatePrivacySettings(saveHistory: Boolean) {
        context.alexDataStore.edit { it[Keys.SAVE_HISTORY_ENABLED] = saveHistory }
    }

    suspend fun updateAudioControls(
        autoBargeIn: Boolean,
        echoCancellation: Boolean,
        noiseSuppression: Boolean,
        vadSensitivity: Float
    ) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.AUTO_BARGE_IN_ENABLED] = autoBargeIn
            prefs[Keys.ECHO_CANCELLATION_ENABLED] = echoCancellation
            prefs[Keys.NOISE_SUPPRESSION_ENABLED] = noiseSuppression
            prefs[Keys.VAD_SENSITIVITY] = vadSensitivity.coerceIn(0.2f, 0.9f)
        }
    }

    suspend fun updateSpeakChatReplies(enabled: Boolean) {
        context.alexDataStore.edit { it[Keys.SPEAK_CHAT_REPLIES] = enabled }
    }

    suspend fun updateUserTitle(title: String) {
        val sanitized = title.trim().ifEmpty { AlexConfig.DEFAULT_USER_TITLE }
        context.alexDataStore.edit { it[Keys.USER_TITLE] = sanitized }
    }

    suspend fun updateSecuritySettings(
        masterPasswordEnabled: Boolean? = null,
        biometricEnabled: Boolean? = null,
        voiceAuthEnabled: Boolean? = null,
        deleteProtectionEnabled: Boolean? = null,
        sensitiveActionProtectionEnabled: Boolean? = null
    ) {
        context.alexDataStore.edit { prefs ->
            masterPasswordEnabled?.let { prefs[Keys.MASTER_PASSWORD_ENABLED] = it }
            biometricEnabled?.let { prefs[Keys.BIOMETRIC_SECURITY_ENABLED] = it }
            voiceAuthEnabled?.let { prefs[Keys.VOICE_AUTH_ENABLED] = it }
            deleteProtectionEnabled?.let { prefs[Keys.DELETE_PROTECTION_ENABLED] = it }
            sensitiveActionProtectionEnabled?.let { prefs[Keys.SENSITIVE_ACTION_PROTECTION] = it }
        }
    }

    suspend fun updateVoiceEnrollmentState(sampleCount: Int, signature: String) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.ENROLLED_VOICE_SAMPLES] = sampleCount
            prefs[Keys.ENROLLED_VOICE_SIGNATURE] = signature
            if (sampleCount >= 3 && signature.isNotBlank()) {
                prefs[Keys.VOICE_AUTH_ENABLED] = true
            }
        }
    }

    suspend fun updateNotificationAndSocialAutomationSettings(
        whatsappVoiceNotif: Boolean? = null,
        announceIncoming: Boolean? = null,
        readContentAloud: Boolean? = null,
        socialAutomation: Boolean? = null,
        backgroundService: Boolean? = null
    ) {
        context.alexDataStore.edit { prefs ->
            whatsappVoiceNotif?.let { prefs[Keys.WHATSAPP_VOICE_NOTIF_ENABLED] = it }
            announceIncoming?.let { prefs[Keys.ANNOUNCE_INCOMING_MSG_ENABLED] = it }
            readContentAloud?.let { prefs[Keys.READ_INCOMING_CONTENT_ALOUD] = it }
            socialAutomation?.let { prefs[Keys.SOCIAL_MEDIA_AUTOMATION_ENABLED] = it }
            backgroundService?.let { prefs[Keys.BACKGROUND_AUTOMATION_ENABLED] = it }
        }
    }
}
