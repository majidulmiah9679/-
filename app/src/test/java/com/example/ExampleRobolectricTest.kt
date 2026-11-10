package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audio.RealTimeAudioEngine
import com.example.core.di.AppContainer
import com.example.data.local.AlexDatabase
import com.example.data.repository.ConversationRepository
import com.example.domain.model.AlexLanguage
import com.example.domain.model.AlexLiveStatus
import com.example.domain.model.ConversationModality
import com.example.domain.model.MessageSender
import com.example.feature.AlexViewModel
import com.example.security.SecurityAuditManager
import com.example.security.configureAppCheck
import com.example.ui.theme.MyApplicationTheme
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var inMemoryDb: AlexDatabase
    private lateinit var conversationRepository: ConversationRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        inMemoryDb = Room.inMemoryDatabaseBuilder(context, AlexDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        conversationRepository = ConversationRepository(inMemoryDb.conversationDao())
    }

    @After
    fun tearDown() {
        inMemoryDb.close()
    }

    @Test
    fun `app name, AppCheck security, and NativeSystemBridge Java-Kotlin interop succeed`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Alex AI", appName)

        configureAppCheck(context, null)
        val diag = SecurityAuditManager.diagnostics.value
        assertTrue(diag.zeroHardcodedKeysVerified)

        val hwSnapshot = com.example.core.config.NativeSystemBridge.captureHardwareSnapshot(context)
        assertTrue(hwSnapshot.deviceModel.isNotBlank())
        assertTrue(hwSnapshot.primaryCpuAbi.isNotBlank())
        assertTrue(hwSnapshot.toSummaryLine().contains("Android"))
    }

    @Test
    fun `real time audio engine computes PCM RMS, waveform, mute, and barge-in flush`() {
        val audioEngine = RealTimeAudioEngine(context)

        // Generate 16-bit PCM buffer with loud signal
        val samples = ShortArray(320) { 12000 }
        val byteBuffer = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        samples.forEach { byteBuffer.putShort(it) }
        val pcmBytes = byteBuffer.array()

        val rms = audioEngine.computeNormalizedRms(pcmBytes)
        assertTrue("Expected positive RMS for non-zero PCM input", rms > 0.2f)

        // Test microphone mute toggle
        assertFalse(audioEngine.isMicMuted.value)
        audioEngine.toggleMicMute()
        assertTrue(audioEngine.isMicMuted.value)
        audioEngine.toggleMicMute()
        assertFalse(audioEngine.isMicMuted.value)

        // Test barge-in interruption flush
        audioEngine.enqueueModelAudioChunk(pcmBytes)
        audioEngine.flushPlaybackForBargeIn()
        assertFalse(audioEngine.isModelPlayingAudio.value)
        assertEquals(0f, audioEngine.outputAmplitude.value, 0.001f)

        // Verify 44-byte RIFF/WAVE header wrapper for live multimodal audio turns
        val wavBytes = audioEngine.pcmToWavBytes(pcmBytes, 16000)
        assertEquals(44 + pcmBytes.size, wavBytes.size)
        assertEquals("RIFF", String(wavBytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(wavBytes, 8, 4, Charsets.US_ASCII))
    }

    @Test
    fun `conversation history repository saves, retrieves, deletes individual and clears all`() = runBlocking {
        val convId = conversationRepository.createConversation(
            title = "Bangla Voice Call",
            modality = ConversationModality.LIVE_VOICE,
            language = AlexLanguage.BANGLA
        )
        conversationRepository.appendTurn(
            conversationId = convId,
            sender = MessageSender.USER,
            text = "হ্যালো অ্যালেক্স!",
            language = AlexLanguage.BANGLA
        )
        conversationRepository.appendTurn(
            conversationId = convId,
            sender = MessageSender.ALEX,
            text = "হ্যালো Boss! কেমন আছো?",
            language = AlexLanguage.BANGLA
        )

        val allConversations = conversationRepository.allConversations.first()
        assertEquals(1, allConversations.size)
        assertEquals(2, allConversations.first().messageCount)

        val turns = conversationRepository.getTranscriptSnapshot(convId)
        assertEquals(2, turns.size)
        assertEquals("হ্যালো Boss! কেমন আছো?", turns[1].text)

        // Delete individual conversation
        conversationRepository.deleteConversation(convId)
        assertTrue(conversationRepository.allConversations.first().isEmpty())

        // Create two and clear all
        conversationRepository.createConversation("Session 1", ConversationModality.TEXT_CHAT, AlexLanguage.ENGLISH)
        conversationRepository.createConversation("Session 2", ConversationModality.LIVE_VOICE, AlexLanguage.HINDI)
        assertEquals(2, conversationRepository.allConversations.first().size)
        conversationRepository.clearAllHistory()
        assertTrue(conversationRepository.allConversations.first().isEmpty())
    }

    @Test
    fun `UI navigation across Home, Live Voice, Chat, History, Settings, API Config, and About works`() {
        val container = AppContainer(context)
        val viewModel = AlexViewModel(container)

        composeTestRule.setContent {
            MyApplicationTheme {
                AlexNavigationHost(
                    viewModel = viewModel,
                    startDestination = AlexRoutes.HOME
                )
            }
        }
        composeTestRule.waitForIdle()

        // Verify Home Screen elements
        composeTestRule.onNodeWithTag("home_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("alex_holographic_orb", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_waveform_visualizer").assertIsDisplayed()
        composeTestRule.onNodeWithTag("start_live_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("mic_mute_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_button").assertIsDisplayed()
        assertEquals(AlexLiveStatus.READY, viewModel.liveStatus.value)

        // Navigate to Live Voice HUD Screen and back
        composeTestRule.onNodeWithTag("nav_live_hud_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("live_voice_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("live_voice_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Text Chat Screen and back
        composeTestRule.onNodeWithTag("nav_chat_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("chat_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("chat_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Conversation History Screen and back
        composeTestRule.onNodeWithTag("nav_history_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("history_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("history_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to Settings Screen -> API Config Screen -> back to Home
        composeTestRule.onNodeWithTag("settings_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("settings_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to API Config Screen, verify manual API key input and save button, and go back
        composeTestRule.onNodeWithTag("home_api_key_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_api_key_chip").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("api_config_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("manual_api_key_input").assertIsDisplayed()
        composeTestRule.onNodeWithTag("save_api_key_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("api_config_back_button").performClick()
        composeTestRule.waitForIdle()

        // Navigate to About Screen
        composeTestRule.onNodeWithTag("nav_about_button").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("about_screen").assertIsDisplayed()
    }
}
