# ALEX AI — Advanced Native Android Personal Live AI Assistant (Kotlin + Java)

**ALEX AI** is a native Android personal live voice and chat AI assistant built with **Kotlin**, **Java (`NativeSystemBridge.java`)**, **Jetpack Compose**, **Google Gemini Live API**, and **Firebase AI Logic**. Inspired by futuristic JARVIS interfaces, ALEX communicates naturally in **Bangla (বাংলা)**, **English**, and **Hindi (हिन्दी)** with a warm human-like female voice and addresses the user as **"Boss"**.

---

## 🚀 GitHub Direct APK Auto-Build (গিটহাবে পুশ করলেই অটো APK বিল্ড)

এই প্রজেক্টে **`.github/workflows/build-apk.yml`** ওয়ার্কফ্লো সম্পূর্ণ সেটআপ করা আছে।

### কীভাবে গিটহাবে অটোমেটিক APK বিল্ড ও ডাউনলোড করবেন:
1. **Push to GitHub**: AI Studio থেকে সরাসরি **Push to GitHub** করুন অথবা আপনার GitHub রিপোজিটরিতে কোড পুশ করুন।
2. **Automatic Build Start**: কোড গিটহাবে পুশ হওয়ার সাথে সাথেই **Actions** ট্যাবে **`Build ALEX AI Android APK (Native Kotlin + Java)`** ওয়ার্কফ্লো স্বয়ংক্রিয়ভাবে চালু হয়ে যাবে।
3. **Download Installable APK**:
   - **Actions Tab থেকে**: বিল্ড শেষ হলে ওয়ার্কফ্লো রানের নিচে **Artifacts** সেকশন থেকে **`ALEX-AI-Android-APK`** ডাউনলোড করুন।
   - **Releases Tab থেকে**: রিপোজিটরির **Releases (`latest-apk-build`)** সেকশন থেকে সরাসরি **`ALEX-AI-v1.0.0-debug.apk`** ফাইলটি ফোনে ডাউনলোড ও ইনস্টল করুন।
4. **অ্যাপে নিজের API Key বসানো**:
   - অ্যাপ ওপেন করে **`SET API KEY`** বাটনে ট্যাপ করুন → আপনার Gemini API Key (`AIzaSy...`) পেস্ট করুন → **`SAVE & GO ONLINE`** বাটনে চাপ দিন। সাথে সাথে অ্যাপ অনলাইন হয়ে যাবে!

---

## 1. Key Capabilities

- **Native Kotlin + Java Architecture**:
  - **Kotlin + Jetpack Compose** for reactive MVVM state, Coroutines/Flows, Room database, DataStore, and 9 futuristic JARVIS screens.
  - **Native Java (`com.example.core.system.NativeSystemBridge.java`)** for low-level Android hardware inspection, CPU ABI detection, low-latency audio buffer metrics, and RAM telemetry.
- **Full-Duplex Real-Time Gemini Live Voice (`com.example.audio.*`)**:
  - Supports both **Direct Gemini Live WebSocket (`BidiGenerateContent`)** with a manual API key and **Firebase AI Logic (`Firebase.ai.liveModel`)** + **Real-Time VAD Multimodal Audio Duplex** (`16kHz` PCM input / `24kHz` PCM output).
  - Hardware **Acoustic Echo Cancellation (AEC)** and **Noise Suppression (NS)** bound to the `AudioRecord` session.
  - Real-time **Voice Activity Detection (VAD)** and **Barge-In Interruption**—ALEX immediately stops speaking the moment you start speaking.
- **Warm Female Voice (`Aoede`, `Kore`, `Leda`, `Zephyr`)**:
  - Configured in Gemini Live and backed by `FemaleVoiceSynthesizer` with adjustable speaking rate and pitch.
- **Automatic Multilingual Intelligence (`LanguageDetector`)**:
  - Automatically detects **Bangla (বাংলা)**, **English**, and **Hindi (हिन्दी)** and responds in the same language.

---

## 2. Project Structure (Native Kotlin + Java)

```text
.github/workflows/
└── build-apk.yml                           # GitHub Actions auto-build APK + Release publisher
app/src/main/java/com/example/
├── MainActivity.kt                         # App Check initialization + 9-screen NavHost (Kotlin)
├── core/
│   ├── config/AlexConfig.kt                # Centralized Gemini models, female voices & system prompt
│   ├── di/AppContainer.kt                  # Constructor dependency injection container
│   └── system/NativeSystemBridge.java      # Native Java hardware & audio telemetry bridge (Java)
├── domain/
│   ├── model/AlexModels.kt                 # Domain models, status enums & settings state
│   └── language/LanguageDetector.kt        # Automatic Bangla, English & Hindi detection
├── data/
│   ├── local/                              # Room entities, DAO & AlexDatabase
│   ├── preferences/                        # DataStore UserPreferencesRepository (persists customApiKey)
│   └── repository/ConversationRepository.kt
├── network/
│   ├── DirectGeminiApiClient.kt            # Direct Gemini REST & Live WebSocket client for manual API key
│   └── GeminiBrainService.kt               # Unified Gemini Brain (Manual API Key + Firebase AI Logic)
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
    └── settings/SettingsAndInfoScreens.kt  # Settings, Manual API Key Config & About ALEX screens
```

---

## 3. Local Build & Verification Commands

1. **Compile the Debug APK**:
   ```bash
   gradle assembleDebug
   ```
2. **Run Unit & Robolectric Tests**:
   ```bash
   gradle :app:testDebugUnitTest
   ```
