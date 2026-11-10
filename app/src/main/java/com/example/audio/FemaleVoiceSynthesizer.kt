package com.example.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import com.example.core.config.AlexConfig
import com.example.domain.language.LanguageDetector
import com.example.domain.model.AlexLanguage
import com.example.domain.model.VoiceEmotionStyle
import com.example.domain.model.VoiceGender
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "FemaleVoiceSynthesizer"

class FemaleVoiceSynthesizer(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsAmplitude = MutableStateFlow(0f)
    val ttsAmplitude: StateFlow<Float> = _ttsAmplitude.asStateFlow()

    private var currentVoiceProfileId: String = "Aoede"
    private var currentSpeechRate: Float = 1.0f
    private var currentPitch: Float = 1.02f
    private var currentEmotionStyle: VoiceEmotionStyle = VoiceEmotionStyle.NATURAL

    var onSpeechStarted: (() -> Unit)? = null
    var onSpeechCompleted: (() -> Unit)? = null

    init {
        try {
            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isInitialized = true
                    configureDefaultHumanVoice(AlexLanguage.ENGLISH)
                } else {
                    Log.w(TAG, "TextToSpeech init returned status: $status")
                }
            }.apply {
                setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        _isSpeaking.value = true
                        _ttsAmplitude.value = 0.65f
                        onSpeechStarted?.invoke()
                    }

                    override fun onDone(utteranceId: String?) {
                        _isSpeaking.value = false
                        _ttsAmplitude.value = 0f
                        onSpeechCompleted?.invoke()
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        _isSpeaking.value = false
                        _ttsAmplitude.value = 0f
                        onSpeechCompleted?.invoke()
                    }

                    override fun onRangeStart(
                        utteranceId: String?,
                        start: Int,
                        end: Int,
                        frame: Int
                    ) {
                        val wordLength = (end - start).coerceIn(1, 12)
                        _ttsAmplitude.value = (0.35f + (wordLength / 18f)).coerceIn(0.3f, 0.92f)
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unable to initialize TextToSpeech", e)
        }
    }

    fun updateVoiceConfig(
        voiceId: String,
        speechRate: Float,
        pitch: Float,
        emotionStyle: VoiceEmotionStyle = currentEmotionStyle
    ) {
        currentVoiceProfileId = voiceId
        currentEmotionStyle = emotionStyle
        val voiceOption = AlexConfig.findVoiceById(voiceId)

        val effectiveRate = (speechRate + voiceOption.defaultRateOffset) * emotionStyle.rateMultiplier
        val effectivePitch = (pitch + voiceOption.defaultPitchOffset) * emotionStyle.pitchMultiplier

        currentSpeechRate = effectiveRate.coerceIn(0.70f, 1.45f)
        currentPitch = effectivePitch.coerceIn(0.70f, 1.35f)
        tts?.setSpeechRate(currentSpeechRate)
        tts?.setPitch(currentPitch)
    }

    private fun configureDefaultHumanVoice(language: AlexLanguage) {
        val engine = tts ?: return
        if (!isInitialized) return

        val voiceOption = AlexConfig.findVoiceById(currentVoiceProfileId)
        val prefersIndianEnglish = currentVoiceProfileId == "Aoede" || currentVoiceProfileId == "Puck"

        val targetLocale = when (language) {
            AlexLanguage.BANGLA -> Locale("bn", "BD")
            AlexLanguage.HINDI -> Locale("hi", "IN")
            AlexLanguage.ENGLISH, AlexLanguage.AUTO -> {
                if (prefersIndianEnglish) Locale("en", "IN") else Locale.US
            }
        }

        try {
            val availability = engine.isLanguageAvailable(targetLocale)
            if (availability >= TextToSpeech.LANG_AVAILABLE) {
                engine.language = targetLocale
            } else if (language == AlexLanguage.BANGLA) {
                val fallbackBn = Locale("bn", "IN")
                if (engine.isLanguageAvailable(fallbackBn) >= TextToSpeech.LANG_AVAILABLE) {
                    engine.language = fallbackBn
                }
            } else if (prefersIndianEnglish) {
                engine.language = Locale.US
            }

            val voices: Set<Voice>? = engine.voices
            val matchingLanguageVoices = voices?.filter { v ->
                v.locale.language.equals(targetLocale.language, ignoreCase = true) &&
                    !v.isNetworkConnectionRequired
            }.orEmpty()

            val preferredVoice = if (voiceOption.gender == VoiceGender.MALE) {
                matchingLanguageVoices.firstOrNull { v ->
                    val nameLower = v.name.lowercase()
                    nameLower.contains("male") && !nameLower.contains("female") ||
                        nameLower.contains("#m") ||
                        nameLower.contains("m0") ||
                        nameLower.contains("iom") ||
                        nameLower.contains("tpd")
                } ?: matchingLanguageVoices.lastOrNull()
            } else {
                matchingLanguageVoices.firstOrNull { v ->
                    val nameLower = v.name.lowercase()
                    nameLower.contains("female") ||
                        nameLower.contains("#f") ||
                        nameLower.contains("f0") ||
                        nameLower.contains("sfg") ||
                        nameLower.contains("tpf") ||
                        nameLower.contains("bn-bd-x") ||
                        nameLower.contains("hi-in-x")
                } ?: matchingLanguageVoices.firstOrNull()
            } ?: voices?.firstOrNull { v ->
                v.locale.language.equals(targetLocale.language, ignoreCase = true)
            }

            if (preferredVoice != null) {
                engine.voice = preferredVoice
            }
            engine.setSpeechRate(currentSpeechRate)
            engine.setPitch(currentPitch)
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection fallback: ${e.message}")
        }
    }

    fun speak(
        text: String,
        languageOverride: AlexLanguage? = null,
        flushExisting: Boolean = true
    ) {
        val cleanText = text
            .replace(Regex("[*_`#~>]+"), "")
            .trim()
        if (cleanText.isEmpty() || !isInitialized) return

        val detectedLanguage = if (languageOverride == null || languageOverride == AlexLanguage.AUTO) {
            LanguageDetector.detectLanguage(cleanText)
        } else {
            languageOverride
        }

        configureDefaultHumanVoice(detectedLanguage)

        val queueMode = if (flushExisting) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val utteranceId = "alex_utt_${UUID.randomUUID()}"
        tts?.speak(cleanText, queueMode, params, utteranceId)
    }

    fun stopImmediately() {
        try {
            if (tts?.isSpeaking == true || _isSpeaking.value) {
                tts?.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
        } finally {
            _isSpeaking.value = false
            _ttsAmplitude.value = 0f
        }
    }

    fun release() {
        stopImmediately()
        try {
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        isInitialized = false
    }
}
