# ALEX AI — Advanced Android Personal Live AI Assistant

**ALEX AI** is a native Android personal live voice and chat AI assistant built with **Kotlin**, **Jetpack Compose**, **Google Gemini Live API**, and **Firebase AI Logic**. Inspired by futuristic JARVIS interfaces, ALEX communicates naturally in **Bangla (বাংলা)**, **English**, and **Hindi (हिन्दी)** with a warm human-like female voice and addresses the user as **"Boss"**.

---

## 1. Key Capabilities

- **Full-Duplex Real-Time Gemini Live Voice (`com.example.audio.*`)**:
  - Uses `Firebase.ai.liveModel(...)` and `LiveSession` for bidirectional real-time PCM audio streaming (`16kHz` 16-bit mono PCM microphone capture and `24kHz` 16-bit mono PCM audio output).
  - Hardware **Acoustic Echo Cancellation (AEC)** and **Noise Suppression (NS)** bound to the `AudioRecord` session.
  - Real-time **Voice Activity Detection (VAD)** and **Barge-In Interruption**—ALEX immediately stops speaking the moment you start speaking.
  - Foreground service (`AlexLiveForegroundService`) with `foregroundServiceType="microphone"` and persistent notification controls.
- **Warm Female Voice (`Aoede`, `Kore`, `Leda`, `Zephyr`)**:
  - Configured via `SpeechConfig(Voice(selectedVoiceId))` in Gemini Live and backed by `FemaleVoiceSynthesizer` with adjustable speaking rate and pitch.
- **Automatic Multilingual Intelligence (`LanguageDetector`)**:
  - Automatically detects **Bangla (বাংলা)**, **English**, and **Hindi (हिन्दी)** (including natural Bangla-English and Hindi-English code-switching) and responds in the same language.
- **Futuristic JARVIS HUD & 9 Screens**:
  1. **Splash Screen** (`SplashScreen`)
  2. **Welcome Screen** (`WelcomeScreen`)
  3. **Main AI Assistant Screen** (`HomeScreen`)
  4. **Live Voice Screen** (`LiveVoiceScreen`)
  5. **Text Chat Screen** (`ChatScreen`)
  6. **Conversation History Screen** (`HistoryScreen`)
  7. **Settings Screen** (`SettingsScreen`)
  8. **API Configuration Screen** (`ApiConfigScreen`)
  9. **About ALEX Screen** (`AboutScreen`)
- **Zero-Trust Security (`AppCheckConfig.kt`)**:
  - Uses **Firebase AI Logic** + **Firebase App Check** (`DebugAppCheckProviderFactory` in debug builds, `RecaptchaAppCheckProviderFactory` in release builds). No permanent unrestricted Gemini API key is embedded in the APK.

---

## 2. Project Architecture

```text
app/src/main/java/com/example/
├── MainActivity.kt                         # App Check initialization + 9-screen NavHost
├── core/
│   ├── config/AlexConfig.kt                # Centralized Gemini models, female voices & system prompt
│   └── di/AppContainer.kt                  # Constructor dependency injection container
├── domain/
│   ├── model/AlexModels.kt                 # Domain models, status enums & settings state
│   └── language/LanguageDetector.kt        # Automatic Bangla, English & Hindi detection
├── data/
│   ├── local/                              # Room entities, DAO & AlexDatabase
│   ├── preferences/                        # DataStore UserPreferencesRepository
│   └── repository/ConversationRepository.kt
├── network/
│   └── GeminiBrainService.kt               # Firebase.ai.liveModel, generativeModel & tool execution
├── audio/
│   ├── RealTimeAudioEngine.kt              # 16kHz AudioRecord, 24kHz AudioTrack, VAD & waveform RMS
│   ├── FemaleVoiceSynthesizer.kt           # Multilingual female voice synthesis & barge-in stop
│   ├── GeminiLiveSessionController.kt      # Full-duplex LiveSession lifecycle & auto-reconnection
│   └── AlexLiveForegroundService.kt        # Microphone foreground service & notification
├── security/
│   └── AppCheckConfig.kt                   # Zero-trust Firebase App Check initialization
└── feature/
    ├── AlexViewModel.kt                    # Unified MVVM StateFlow ViewModel
    ├── components/AlexHudComponents.kt     # Holographic AI Orb, Live Waveform & Status Badges
    ├── onboarding/OnboardingScreens.kt     # Splash & Welcome screens
    ├── home/HomeScreen.kt                  # Main JARVIS AI Assistant HUD
    ├── livevoice/LiveVoiceScreen.kt        # Full-screen Live Voice Call HUD
    ├── chat/ChatScreen.kt                  # Multilingual streaming Text Chat
    ├── history/HistoryScreen.kt            # Conversation Archive with delete/clear controls
    └── settings/SettingsAndInfoScreens.kt  # Settings, API Configuration & About ALEX screens
```

---

## 3. Build & Verification Instructions

1. **Compile the Debug APK**:
   ```bash
   gradle assembleDebug
   ```
2. **Run Unit & Robolectric Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
3. **Generate Installable APK / AAB in AI Studio**:
   - Use the **Export / Generate APK** option in the AI Studio settings menu, or run `gradle assembleDebug` to output `app/build/outputs/apk/debug/app-debug.apk`.
