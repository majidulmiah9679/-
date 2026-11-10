package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.core.config.AlexConfig
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexSettings
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
    }

    val settingsFlow: Flow<AlexSettings> = context.alexDataStore.data.map { prefs ->
        AlexSettings(
            hasCompletedOnboarding = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false,
            selectedVoiceId = prefs[Keys.SELECTED_VOICE_ID] ?: "Aoede",
            speechRate = prefs[Keys.SPECH_RATE_FALLBACK] ?: prefs[Keys.SPEECH_RATE] ?: 1.0f,
            voicePitch = prefs[Keys.VOICE_PITCH] ?: 1.02f,
            preferredLanguage = AlexLanguage.fromCode(prefs[Keys.PREFERRED_LANGUAGE] ?: "AUTO"),
            liveModelName = prefs[Keys.LIVE_MODEL_NAME] ?: AlexConfig.DEFAULT_LIVE_MODEL,
            chatModelName = prefs[Keys.CHAT_MODEL_NAME] ?: AlexConfig.DEFAULT_CHAT_MODEL,
            saveHistoryEnabled = prefs[Keys.SAVE_HISTORY_ENABLED] ?: true,
            autoBargeInEnabled = prefs[Keys.AUTO_BARGE_IN_ENABLED] ?: true,
            echoCancellationEnabled = prefs[Keys.ECHO_CANCELLATION_ENABLED] ?: true,
            noiseSuppressionEnabled = prefs[Keys.NOISE_SUPPRESSION_ENABLED] ?: true,
            vadSensitivity = prefs[Keys.VAD_SENSITIVITY] ?: 0.55f,
            speakChatReplies = prefs[Keys.SPEAK_CHAT_REPLIES] ?: true,
            userTitle = prefs[Keys.USER_TITLE] ?: AlexConfig.DEFAULT_USER_TITLE
        )
    }

    private val Keys.SPECH_RATE_FALLBACK get() = Keys.SPEECH_RATE

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.alexDataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun updateVoiceProfile(voiceId: String, speechRate: Float, pitch: Float) {
        context.alexDataStore.edit { prefs ->
            prefs[Keys.SELECTED_VOICE_ID] = voiceId
            prefs[Keys.SPEECH_RATE] = speechRate.coerceIn(0.75f, 1.35f)
            prefs[Keys.VOICE_PITCH] = pitch.coerceIn(0.85f, 1.25f)
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
}
