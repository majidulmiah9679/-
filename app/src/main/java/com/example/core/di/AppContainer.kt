package com.example.core.di

import android.content.Context
import com.example.audio.FemaleVoiceSynthesizer
import com.example.audio.GeminiLiveSessionController
import com.example.audio.RealTimeAudioEngine
import com.example.data.local.AlexDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.repository.ConversationRepository
import com.example.network.GeminiBrainService

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AlexDatabase by lazy {
        AlexDatabase.getInstance(appContext)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(appContext)
    }

    val conversationRepository: ConversationRepository by lazy {
        ConversationRepository(database.conversationDao())
    }

    val audioEngine: RealTimeAudioEngine by lazy {
        RealTimeAudioEngine(appContext)
    }

    val voiceSynthesizer: FemaleVoiceSynthesizer by lazy {
        FemaleVoiceSynthesizer(appContext)
    }

    val geminiBrainService: GeminiBrainService by lazy {
        GeminiBrainService(appContext)
    }

    val liveSessionController: GeminiLiveSessionController by lazy {
        GeminiLiveSessionController(
            context = appContext,
            brainService = geminiBrainService,
            audioEngine = audioEngine,
            voiceSynthesizer = voiceSynthesizer,
            conversationRepository = conversationRepository
        )
    }
}
