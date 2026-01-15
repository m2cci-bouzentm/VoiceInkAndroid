# VoiceInk Android - Claude Context Memory

This file serves as persistent memory for Claude across sessions. It documents the project setup, build process, issues encountered, and their solutions.

## Project Overview

VoiceInk Android is a speech-to-text MVP app supporting local and cloud transcription. It's the Android equivalent of the macOS VoiceInk app.

**Tech Stack:**
- Language: Kotlin
- UI: Jetpack Compose + Material 3
- Architecture: MVVM + Clean Architecture
- DI: Hilt
- Async: Kotlin Coroutines + Flow
- Networking: Retrofit + OkHttp + Kotlinx Serialization
- Local STT: Sherpa-ONNX (Parakeet TDT v3, Whisper) - pending full integration

## Build Requirements

### Java Version
**IMPORTANT:** This project requires **Java 17**. Java 25 is incompatible with Gradle 8.5/Kotlin 1.9.21 due to version parsing issues in the Kotlin compiler.

```bash
# Install Java 17 if not present
brew install openjdk@17

# Set JAVA_HOME before building
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

### Android SDK
SDK location: `~/Library/Android/sdk`

Required components:
- platform-tools
- platforms;android-34
- build-tools;34.0.0

To install SDK components:
```bash
export ANDROID_HOME=~/Library/Android/sdk
$(brew --prefix)/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager \
  --sdk_root="$ANDROID_HOME" \
  "platform-tools" "platforms;android-34" "build-tools;34.0.0"
```

### Gradle
Uses Gradle wrapper (8.5). Always use `./gradlew` instead of system Gradle to avoid version mismatches.

## Build Commands

```bash
# Set Java 17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Clean build
./gradlew clean assembleDebug

# Install on connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Project Structure

```
app/src/main/
├── java/com/voiceink/android/
│   ├── di/                          # Hilt dependency injection
│   │   └── AppModule.kt             # Provides singletons
│   ├── data/
│   │   ├── audio/
│   │   │   └── AudioRecorder.kt     # Records audio as WAV (16kHz, mono, 16-bit)
│   │   ├── model/
│   │   │   └── ModelDownloadManager.kt  # Downloads & extracts local models
│   │   └── preferences/
│   │       └── SettingsRepository.kt # DataStore for API keys & settings
│   ├── domain/
│   │   ├── model/
│   │   │   └── TranscriptionModel.kt # Model definitions (Whisper, SenseVoice, Gemini, OpenAI)
│   │   └── transcription/
│   │       ├── TranscriptionService.kt       # Interface
│   │       ├── TranscriptionRegistry.kt      # Routes to correct service
│   │       ├── LocalTranscriptionService.kt  # Sherpa-ONNX (Whisper, SenseVoice)
│   │       ├── GeminiTranscriptionService.kt # Gemini 2.5/2.0 Flash API
│   │       └── OpenAITranscriptionService.kt # OpenAI Whisper API
│   ├── services/
│   │   ├── OverlayService.kt           # Floating overlay button (draggable, record from any app)
│   │   ├── TextInjectionService.kt     # Accessibility service for text injection
│   │   └── VoiceInkInputMethodService.kt # Voice-only IME (custom keyboard)
│   ├── ui/
│   │   ├── theme/
│   │   │   ├── Color.kt
│   │   │   ├── Theme.kt
│   │   │   └── Type.kt
│   │   ├── screens/
│   │   │   ├── home/
│   │   │   │   ├── HomeScreen.kt    # Main UI + notification permission request
│   │   │   │   └── HomeViewModel.kt # Recording & transcription logic
│   │   │   └── settings/
│   │   │       ├── SettingsScreen.kt   # API keys, model selection, download UI
│   │   │       └── SettingsViewModel.kt
│   │   └── navigation/
│   │       └── NavGraph.kt
│   ├── MainActivity.kt
│   └── VoiceInkApplication.kt       # Hilt application
├── res/
│   ├── drawable/
│   │   ├── ic_launcher_foreground.xml  # Microphone vector icon
│   │   ├── ic_launcher_background.xml  # Purple background
│   │   ├── ic_mic.xml                  # Notification mic icon
│   │   └── ic_stop.xml                 # Notification stop icon
│   ├── mipmap-*/                        # Adaptive icons for all densities
│   ├── xml/
│   │   ├── accessibility_service_config.xml  # Accessibility service config
│   │   └── input_method.xml                  # IME (voice keyboard) configuration
│   └── values/
│       ├── strings.xml
│       └── themes.xml
└── AndroidManifest.xml              # Permissions & service declarations
```

## Supported Transcription Providers

### Local (Sherpa-ONNX) - Requires Model Download

1. **SenseVoice (Multilingual)** - RECOMMENDED
   - ~230MB model (int8)
   - Languages: Chinese, English, Japanese, Korean, Cantonese
   - Model ID: `sense-voice`
   - Model path: `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17`
   - Fast and accurate for Asian languages
   - Download: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17.tar.bz2

2. **Whisper Small (99+ Languages)** - Multilingual
   - ~460MB model
   - Supports 99+ languages (auto-detect)
   - Model ID: `whisper-small`
   - Model path: `sherpa-onnx-whisper-small`
   - Download: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-small.tar.bz2

3. **Whisper Tiny (English)** - WORKING
   - ~40MB model
   - English only (fast)
   - Model ID: `whisper-tiny-en`
   - Model path: `whisper-tiny-en`
   - Download: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.en.tar.bz2

4. **Parakeet TDT v3** - BROKEN (crashes with SIGABRT)
   - 600M parameter model (int8 quantized ~150MB)
   - Model ID: `parakeet-tdt-v3`
   - **Status:** Marked as broken in app, excluded from model list
   - Crashes on Android despite featureDim=128 fix

### Cloud (Fully Working)
1. **Gemini 2.5 Flash** (gemini-2.5-flash) - DEFAULT
   - Requires: `GEMINI_API_KEY` in settings
   - Endpoint: `generativelanguage.googleapis.com`
   - Newest and fastest Gemini model

2. **Gemini 2.0 Flash** (gemini-2.0-flash) - WORKING
   - Requires: `GEMINI_API_KEY` in settings
   - Endpoint: `generativelanguage.googleapis.com`

3. **OpenAI Whisper** (whisper-1) - WORKING
   - Requires: `OPENAI_API_KEY` in settings
   - Endpoint: `api.openai.com/v1/audio/transcriptions`
   - Multipart form-data upload

**Note:** Gemini 1.5 Flash was retired by Google in April 2025. Groq was removed in favor of OpenAI.

## Features

### Floating Overlay Button
- Draggable floating microphone button that works from any app
- Enable in Settings > Floating Button (requires "Display over other apps" permission)
- Tap to start recording, tap again to stop and transcribe
- Shows visual feedback: purple (idle), red (recording), spinner (processing)
- Auto-starts when app launches if previously enabled
- Persists when app goes to background
- **Direct text injection**: After transcription, text is inserted at cursor in focused app (if accessibility enabled)

### Text Injection (Accessibility Service)
- `TextInjectionService.kt` uses Android Accessibility API to inject text into any app's input field
- User must enable in Settings > Accessibility > VoiceInk
- Falls back to clipboard paste if direct injection fails
- If accessibility not enabled, copies transcription to clipboard and shows notification
- Settings screen shows "Text Injection" toggle to open accessibility settings

### Model Download Manager
- `ModelDownloadManager.kt` handles downloading and extracting local models
- Downloads tar.bz2 archives from GitHub releases
- Extracts using Apache Commons Compress library
- Shows download progress (0-100%) in Settings screen
- Model files stored in `context.filesDir/models/<model-path>/`
- Delete button to remove downloaded models

### Local Model Status
- Model definitions added to `TranscriptionModel.kt`
- `LocalTranscriptionService.kt` checks for model files
- Download button shows for models not yet downloaded
- Can only select local models after downloading
- Sherpa-ONNX library ready to integrate (`com.bihe0832.android:lib-sherpa-onnx:6.25.12`)

### Voice Input Method (IME - Custom Keyboard)
- `VoiceInkInputMethodService.kt` extends `InputMethodService` as a voice-only "keyboard"
- User switches to VoiceInk IME when they want to speak, then switches back to regular keyboard
- Works in **any app** with text input - voice text inserted directly via `currentInputConnection`
- More reliable than RecognitionService approach (OEM-restricted on Samsung and other devices)

**Setup:** Settings → System → Languages & Input → On-screen keyboard → Manage keyboards → Enable VoiceInk

**How it works:**
1. User switches to VoiceInk keyboard (via keyboard switcher)
2. A minimal UI appears with mic button and "Switch keyboard" option
3. Tap mic to record, tap again to stop and transcribe
4. Transcribed text is inserted via `currentInputConnection.commitText()`
5. User taps "Switch keyboard" to return to their regular keyboard

**Files:**
- `services/VoiceInkInputMethodService.kt` - InputMethodService with Compose UI
- `res/xml/input_method.xml` - IME configuration

**Note:** The RecognitionService approach was attempted but didn't work reliably on Samsung devices (only showed Google and Samsung voice input options). IME approach is the standard way to provide text input on Android.

### Abort/Cancel Recording
Allows users to stop recording **without processing/transcribing** the audio. The recorded file is discarded.

**Triggers by interface:**
| Interface | Trigger | Feedback |
|-----------|---------|----------|
| **HomeScreen** | Long-press record button OR tap "Cancel" button | UI reverts to idle |
| **Floating Button** | Long-press (500ms) while recording | Vibration + Toast |
| **Voice Keyboard (IME)** | Long-press mic OR tap "Cancel" button | Toast |

**Core implementation:**
- `AudioRecorder.cancelRecording()` - Stops recording, deletes audio file, resets to IDLE
- All UIs show "Long-press to cancel" hint during recording

**Key files:**
- `data/audio/AudioRecorder.kt` - Core `cancelRecording()` method (lines 142-161)
- `ui/screens/home/HomeScreen.kt` - Long-press on record button + Cancel text button
- `ui/screens/home/HomeViewModel.kt` - `cancelRecording()` method
- `services/OverlayService.kt` - `abortRecording()` with long-press detection (500ms)
- `services/VoiceInkInputMethodService.kt` - `abortRecording()` for IME

## Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
```

## Issues & Solutions Log

### Issue 1: Gradle 9.x Incompatibility
**Error:** `'org.gradle.api.file.FileCollection org.gradle.api.artifacts.Configuration.fileCollection(org.gradle.api.specs.Spec)'`
**Cause:** System Gradle (9.x) was used instead of wrapper (8.5)
**Solution:** Always use `./gradlew` and ensure wrapper is executable (`chmod +x gradlew`)

### Issue 2: Java 25 Version Parsing Error
**Error:** `java.lang.IllegalArgumentException: 25.0.1` in `JavaVersion.parse()`
**Cause:** Kotlin compiler in Gradle 8.5 can't parse Java 25 version string
**Solution:** Install and use Java 17:
```bash
brew install openjdk@17
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
```

### Issue 3: Missing Launcher Icons
**Error:** `AAPT: error: resource mipmap/ic_launcher not found`
**Solution:** Created adaptive icons in all mipmap directories

### Issue 4: Missing SelectionContainer Import
**Error:** `Unresolved reference: SelectionContainer`
**Solution:** Added import: `import androidx.compose.foundation.text.selection.SelectionContainer`

### Issue 5: Sherpa-ONNX Maven Dependency Not Found
**Error:** `Could not find com.k2fsa.sherpa:onnx-android:1.10.31`
**Cause:** Wrong Maven coordinates
**Solution:** Use `com.bihe0832.android:lib-sherpa-onnx:6.25.12` from Maven Central, or add JitPack repository. Currently commented out until model download is implemented.

### Issue 6: Kotlin If Expression Missing Else Branch
**Error:** `'if' must have both main and 'else' branches if used as an expression`
**Cause:** Using `?.let { }` with an `if` statement inside caused Kotlin to treat it as an expression
**Solution:** Refactor to use explicit null check instead of `let`:
```kotlin
// Before (error)
lastFocusedNode?.let { node ->
    if (node.isEditable) { return node }
}

// After (fixed)
val cachedNode = lastFocusedNode
if (cachedNode != null) {
    if (cachedNode.isEditable) { return cachedNode }
}
```

### Issue 7: Model File Name Mismatch (Model Downloaded But Not Recognized)
**Error:** Model downloads successfully but app says "model not downloaded" when trying to use it
**Cause:** File name checks in `ModelDownloadManager.kt` and `LocalTranscriptionService.kt` didn't match actual extracted file names
**Solution:** Updated both files with correct file names from the actual archives:

**Parakeet TDT v3** (from `sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8.tar.bz2`):
```kotlin
// Wrong: encoder-epoch-99-avg-1.int8.onnx
// Correct:
File(modelDir, "encoder.int8.onnx").exists()
File(modelDir, "decoder.int8.onnx").exists()
File(modelDir, "joiner.int8.onnx").exists()
File(modelDir, "tokens.txt").exists()
```

**Whisper Tiny EN** (from `sherpa-onnx-whisper-tiny.en.tar.bz2`):
```kotlin
// Wrong: encoder.onnx, decoder.onnx, tokens.txt
// Correct:
File(modelDir, "tiny.en-encoder.onnx").exists()
File(modelDir, "tiny.en-decoder.onnx").exists()
File(modelDir, "tiny.en-tokens.txt").exists()
```

**Tip:** Always verify actual archive contents with `tar -tjf <file>.tar.bz2 | head -20` before implementing file checks.

### Issue 8: Local Model "Not Yet Integrated" Error
**Error:** "Local model ready but transcription not yet integrated. Please use a cloud model for now."
**Cause:** LocalTranscriptionService had placeholder code that just returned an error instead of actually transcribing
**Solution:**
1. Uncommented sherpa-onnx dependency: `implementation("com.bihe0832.android:lib-sherpa-onnx:6.25.12")`
2. Implemented actual transcription using `OfflineRecognizer`:
```kotlin
// Constructor requires assetManager (null for file-based) and config
recognizer = OfflineRecognizer(assetManager = null, config = config)

// Create stream, decode, get result
val stream = recognizer.createStream()
stream.acceptWaveform(samples, SAMPLE_RATE)
recognizer.decode(stream)
val result = recognizer.getResult(stream)
```

**Note:** VoiceInk macOS uses [FluidAudio](https://github.com/FluidInference/FluidAudio) for Parakeet, which is macOS/iOS only. Android uses sherpa-onnx.

### Issue 9: Local Model Returns Empty Text (No Speech Detected)
**Error:** Transcription completes but returns empty text, showing "No speech detected"
**Cause:** `OfflineModelConfig` was missing the `modelType` parameter which tells sherpa-onnx which model architecture to use
**Solution:** Add `modelType` to both Whisper and Parakeet configs:
```kotlin
// For Whisper models:
val modelConfig = OfflineModelConfig(
    whisper = whisperConfig,
    tokens = File(modelDir, "tiny.en-tokens.txt").absolutePath,
    numThreads = 2,
    modelType = "whisper"  // CRITICAL: Must specify model type
)

// For Parakeet/Transducer models:
val modelConfig = OfflineModelConfig(
    transducer = transducerConfig,
    tokens = File(modelDir, "tokens.txt").absolutePath,
    numThreads = 2,
    modelType = "transducer"  // CRITICAL: Must specify model type
)
```

**Also added for Whisper:**
- `language = "en"`
- `task = "transcribe"`
- `tailPaddings = 1000`
- `decodingMethod = "greedy_search"` in OfflineRecognizerConfig

**Reference:** [sherpa-onnx Kotlin API](https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/OfflineRecognizer.kt)

### Issue 10: Parakeet Model Crashes with SIGABRT (Fatal Signal 6)
**Error:** App crashes with `Fatal signal 6 (SIGABRT)` when using Parakeet TDT v3 model. Whisper Tiny works fine.
**Cause:** Wrong `featureDim` parameter in `getFeatureConfig()`. Parakeet expects 128-dimensional mel features, but code was using 80.
**Diagnosis:** Check logcat for sherpa-onnx output:
```
W sherpa-onnx: feat_dim=128          <- Model expects 128
W sherpa-onnx: model_type=EncDecRNNTBPEModel
```
**Solution:** Use different `featureDim` values for different models:
```kotlin
// For Parakeet TDT v3 (128-dim features):
val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 128)

// For Whisper (80-dim features):
val featConfig = getFeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80)
```

**Tip:** Always check the model's metadata in logcat (`feat_dim=X`) to determine correct feature dimension.

## Dependencies (build.gradle.kts)

Key dependencies:
- Compose BOM: 2024.02.00
- Hilt: 2.50
- Retrofit: 2.9.0
- OkHttp: 4.12.0
- Kotlinx Serialization: 1.6.2
- DataStore Preferences: 1.0.0
- Apache Commons Compress: 1.26.0 (for tar.bz2 extraction)
- Sherpa-ONNX: `com.bihe0832.android:lib-sherpa-onnx:6.25.12` (ENABLED - local transcription working)

## Repositories (settings.gradle.kts)

```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}
```

## Current Build Status

**BUILD SUCCESSFUL** - All features implemented and compiling.

Deprecation warnings (safe to ignore):
- `recycle()` on AccessibilityNodeInfo (deprecated but still functional)
- `FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY` (deprecated but needed for compatibility)
- `nextTarEntry` getter in Apache Commons Compress
- `Icons.Default.ArrowBack` (should use `Icons.AutoMirrored.Filled.ArrowBack`)

## User Workflow

### Setup (One-time)
1. Install APK on device
2. Grant microphone permission when prompted
3. Go to Settings > Enable Floating Button > Grant "Display over other apps" permission
4. (Optional) Enable Text Injection in Accessibility settings
5. (Optional) Download local model for offline use

### Daily Use (Floating Button)
1. Open any app with a text field (Messenger, WhatsApp, Notes, etc.)
2. Tap the text field to focus it
3. Tap the floating microphone button
4. Speak your message
5. Tap the button again to stop
6. Text is automatically inserted where cursor was focused (or copied to clipboard)

## Future Work

- [x] Model download UI with progress indicator
- [x] Text injection via accessibility service
- [x] Floating overlay button (record from any app)
- [ ] Full Sherpa-ONNX integration (uncomment dependency, implement recognition)
- [ ] Transcription history
- [ ] Export/share functionality
- [ ] Widgets
- [ ] Wear OS support

## Quick Reference

```bash
# Full build from scratch
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew clean assembleDebug

# APK output location
app/build/outputs/apk/debug/app-debug.apk

# Check connected devices
adb devices

# Install APK
adb install -r app/build/outputs/apk/debug/app-debug.apk

# View logs
adb logcat | grep -i voiceink
```

## Key Files Modified/Created (Session 2)

### New Files:
- `services/AudioRecordingService.kt` - Foreground service with notification
- `services/RecordingActionReceiver.kt` - Broadcast receiver for notification actions
- `res/drawable/ic_mic.xml` - Microphone icon for notification
- `res/drawable/ic_stop.xml` - Stop icon for notification

### Modified Files:
- `domain/model/TranscriptionModel.kt` - Added Parakeet TDT v3 model
- `domain/transcription/LocalTranscriptionService.kt` - Added model checking, download URLs
- `AndroidManifest.xml` - Added notification & foreground service permissions
- `ui/screens/home/HomeScreen.kt` - Added notification permission request, service start
- `app/build.gradle.kts` - Added Sherpa-ONNX dependency (commented)
- `settings.gradle.kts` - Added JitPack repository

## Key Files Modified/Created (Session 3)

### New Files:
- `data/model/ModelDownloadManager.kt` - Downloads & extracts local models from GitHub
- `services/TextInjectionService.kt` - Accessibility service for injecting text into other apps
- `res/xml/accessibility_service_config.xml` - Accessibility service configuration

### Modified Files:
- `services/AudioRecordingService.kt` - Added text injection after transcription, clipboard fallback
- `ui/screens/settings/SettingsScreen.kt` - Added model download UI, accessibility toggle
- `ui/screens/settings/SettingsViewModel.kt` - Added download states, model management
- `AndroidManifest.xml` - Added accessibility service declaration
- `res/values/strings.xml` - Added accessibility service description
- `app/build.gradle.kts` - Added Apache Commons Compress for tar.bz2 extraction

## Key Files Modified/Created (Session 4)

### New Files:
- `services/OverlayService.kt` - Floating overlay button service (draggable, record/stop)
- `res/layout/overlay_button.xml` - Layout for floating button
- `res/drawable/overlay_button_background.xml` - Purple background for idle state
- `res/drawable/overlay_button_recording.xml` - Red background for recording state

### Modified Files:
- `AndroidManifest.xml` - Added SYSTEM_ALERT_WINDOW permission, replaced AudioRecordingService with OverlayService
- `data/preferences/SettingsRepository.kt` - Added overlayEnabled preference
- `ui/screens/settings/SettingsScreen.kt` - Added floating button toggle with permission flow
- `ui/screens/settings/SettingsViewModel.kt` - Added overlay state management
- `ui/screens/home/HomeScreen.kt` - Removed notification service, added overlay hint
- `res/values/strings.xml` - Added overlay notification strings

### Deleted Files:
- `services/AudioRecordingService.kt` - Replaced by OverlayService
- `services/RecordingActionReceiver.kt` - No longer needed

## Key Files Modified/Created (Session 5)

### Modified Files:
- `domain/transcription/LocalTranscriptionService.kt` - Added modelType parameter to sherpa-onnx config (Issue 9 fix)

## Key Files Modified/Created (Session 6)

### Modified Files:
- `domain/transcription/LocalTranscriptionService.kt` - Added comprehensive debug logging and validation:
  - Verify all required model files exist with non-zero size before initialization
  - Log full file paths for model components
  - Add audio quality checks (silence detection, minimum length)
  - Better error messages for troubleshooting local model issues

## Key Files Modified/Created (Session 7)

### UI Redesign - Premium Dark Theme
Complete redesign of the app with a modern, premium dark theme inspired by macOS VoiceInk.

### Modified Files:
- `ui/theme/Color.kt` - New premium color palette with:
  - Primary purple/violet gradients
  - Glass effect colors with transparency
  - Success/warning/error semantic colors
  - Recording red with glow effects
  
- `ui/theme/Type.kt` - Modern typography system:
  - SF Pro-inspired font weights
  - Proper text hierarchy (display, headline, body, label)
  
- `ui/theme/Theme.kt` - Dark-first premium theming:
  - Material3 color scheme overrides
  - Glass surface colors
  - Proper dark mode defaults

- `ui/screens/home/HomeScreen.kt` - Complete redesign:
  - Premium top bar with app branding
  - Animated hint cards with icons
  - Glass-effect transcription result card
  - Recording animation with pulsing concentric rings
  - Premium gradient record button with glow effects
  - Status indicators (idle, recording, processing)

- `ui/screens/settings/SettingsScreen.kt` - Complete redesign:
  - Modern section headers with icons
  - Glass-effect cards with subtle borders
  - Provider icons for model selection (local chip, cloud icons)
  - Premium toggle switches with animations
  - Styled API key input fields
  - Download progress indicators
  - Model status chips (downloaded, cloud, download button)

- `services/OverlayService.kt` - Added processing state display during transcription

## Key Files Modified/Created (Session 8)

### New Multilingual Local Models
Added two new multilingual local models as alternatives to the broken Parakeet model:
- **SenseVoice**: Fast multilingual model (Chinese, English, Japanese, Korean, Cantonese) - ~230MB
- **Whisper Small**: 99+ language support - ~460MB

### Modified Files:
- `domain/model/TranscriptionModel.kt`:
  - Added `isBroken` flag to LocalModel to mark models that don't work
  - Added `modelType` field (transducer, whisper, sense_voice)
  - Added SenseVoice and Whisper Small models to PredefinedModels
  - Removed Parakeet from active model list (marked as broken)

- `data/model/ModelDownloadManager.kt`:
  - Added download URLs for SenseVoice and Whisper Small
  - Added file checks for new models
  - Added `getModelSizeString()` helper function

- `domain/transcription/LocalTranscriptionService.kt`:
  - Added `OfflineSenseVoiceModelConfig` import
  - Added broken model check in transcribe()
  - Added `createSenseVoiceConfig()` for SenseVoice model
  - Added `createWhisperSmallConfig()` for Whisper Small multilingual
  - Renamed `createWhisperConfig()` to `createWhisperTinyEnConfig()`
  - Updated `isModelAvailable()` for new models
  - Updated `getModelSize()` and `getModelDownloadUrl()` for new models

- `services/OverlayService.kt`:
  - Removed pulse animation (was annoying - button no longer scales up/down)
  - Removed ValueAnimator and AccelerateDecelerateInterpolator imports
  - Button now just changes color (purple idle, red recording)

## Key Files Modified/Created (Session 9)

### Cloud Provider Updates
- Removed Groq, added OpenAI Whisper API
- Updated Gemini models (retired 1.5, added 2.5 Flash)

### New Files:
- `domain/transcription/OpenAITranscriptionService.kt` - Multipart/form-data upload to OpenAI Whisper API

### Modified Files:
- `domain/model/TranscriptionModel.kt`:
  - Changed `ModelProvider.GROQ` to `ModelProvider.OPENAI`
  - Added `gemini25Flash` (gemini-2.5-flash)
  - Renamed `geminiFlash` to `gemini20Flash` (gemini-2.0-flash)
  - Removed `gemini15Flash` (deprecated by Google April 2025)
  - Added `openaiWhisper` (whisper-1)
  - Removed `groqWhisper`

- `domain/transcription/TranscriptionRegistry.kt`:
  - Routes `OPENAI` provider to OpenAITranscriptionService
  - Removed Groq routing

- `data/preferences/SettingsRepository.kt`:
  - Replaced `groqApiKey` with `openaiApiKey`
  - Default model changed to `gemini-2.5-flash`

- `ui/screens/settings/SettingsViewModel.kt`:
  - Updated for OpenAI API key instead of Groq

- `ui/screens/settings/SettingsScreen.kt`:
  - OpenAI API key field (green color #10A37F)
  - Removed Groq references
  - Updated provider label: "OpenAI Cloud"

### Deleted Files:
- `domain/transcription/GroqTranscriptionService.kt`

## Key Files Modified/Created (Session 10)

### Voice Input Method (IME) - Replaces Failed VoiceInputService
The VoiceInputService (RecognitionService) approach didn't work on Samsung devices - only Google and Samsung voice input appeared in settings. Replaced with a proper IME (InputMethodService) approach.

### Initial Implementation Issues & Solutions:

**Issue 1: Hilt @EntryPoint annotation error**
- Error: `Unresolved reference: EntryPoint`
- Fix: Changed `import dagger.hilt.android.EntryPoint` to `import dagger.hilt.EntryPoint` and added proper imports for `InstallIn` and `SingletonComponent`

**Issue 2: ViewTreeLifecycleOwner crash with Compose UI**
- Error: `java.lang.IllegalStateException: ViewTreeLifecycleOwner not found from android.widget.LinearLayout`
- Initial attempted fix: Changed `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` to `ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycle)`
- Final solution: **Switched from Compose to XML layout** for simpler, more reliable implementation

**Issue 3: OverlayService auto-start crash**
- Error: `SecurityException: Starting FGS with type microphone...requires permissions...and the app must be in the eligible state`
- Cause: `VoiceInkApplication.onCreate()` was auto-starting OverlayService when IME launched (background context)
- Fix: Removed auto-start code from VoiceInkApplication, keeping it only in MainActivity

**Issue 4: IME not showing when text field focused on Samsung**
- Cause: Samsung One UI overrides programmatic keyboard selection
- Fix: User must manually switch keyboards via Samsung Keyboard's keyboard switcher icon or long-press spacebar

### New Files:
- `services/VoiceInkInputMethodService.kt` - Voice-only keyboard IME with:
  - **XML layout** (not Compose) for simpler lifecycle management
  - Audio recording to WAV file
  - Transcription via TranscriptionRegistry (Hilt EntryPoint pattern)
  - Text insertion via `currentInputConnection.commitText()`
  - **Double-tap detection** for clearing input (300ms timeout)
  - Switch keyboard button using `InputMethodManager.showInputMethodPicker()`

- `res/layout/keyboard_view.xml` - Clean, compact IME layout:
  - Status text showing current state
  - Large mic button with overlay button background
  - Hint text ("Double-tap to clear")
  - Switch keyboard button

- `res/drawable/ime_background.xml` - Gradient background (#1a1a2e to #16213e)

- `res/drawable/ime_switch_button_bg.xml` - Rounded button with semi-transparent white

- `res/xml/input_method.xml` - IME configuration with voice subtype

### Modified Files:
- `AndroidManifest.xml`:
  - Added VoiceInkInputMethodService with `BIND_INPUT_METHOD` permission
  - Removed VoiceInputService and VoiceInputActivity

- `VoiceInkApplication.kt`:
  - Removed OverlayService auto-start (caused crashes when IME launches in background)
  - Added comment explaining why overlay service is started from MainActivity only

- `res/values/strings.xml`:
  - Added `ime_subtype_voice` string

- `res/values/themes.xml`:
  - Removed VoiceInput theme (no longer needed)

### Deleted Files:
- `services/VoiceInputService.kt` - RecognitionService didn't work on Samsung
- `ui/voiceinput/VoiceInputActivity.kt` - No longer needed
- `res/xml/voice_input_service.xml` - No longer needed

### Key Features:
1. **Double-tap to clear**: Quickly clear the current input with a double-tap on the mic button
2. **Clean design**: Gradient background, properly sized mic button, minimal UI
3. **Switch keyboard**: Easy button to return to regular keyboard

### Why IME Works Better Than RecognitionService
1. **Standard Android API**: InputMethodService is the official way to provide text input
2. **No OEM restrictions**: Unlike RecognitionService which Samsung/Google lock down
3. **Direct text insertion**: Uses `currentInputConnection` instead of callbacks
4. **User controls when to use it**: Switch to VoiceInk "keyboard" when needed, switch back after

## Key Files Modified/Created (Session 11)

### Abort/Cancel Recording Feature
Added the ability to cancel a recording without processing/transcribing it. Implemented consistently across all three recording interfaces.

### Modified Files:
- `data/audio/AudioRecorder.kt`:
  - Added `cancelRecording()` method (lines 142-161)
  - Stops recording, releases resources, deletes partial recording file
  - Returns boolean indicating success

- `ui/screens/home/HomeScreen.kt`:
  - Added long-press gesture on record button to cancel (`onLongClick`)
  - Added "Cancel" text button below record button (visible during recording)
  - Added "Long-press to cancel" hint text during recording

- `ui/screens/home/HomeViewModel.kt`:
  - Added `cancelRecording()` method that calls `audioRecorder.cancelRecording()`

- `services/OverlayService.kt`:
  - Added long-press detection (500ms threshold) using `Handler` and `Runnable`
  - Added `abortRecording()` method with haptic feedback (100ms vibration) and toast
  - Long-press timer starts on `ACTION_DOWN` if recording, cancels if user drags

- `services/VoiceInkInputMethodService.kt`:
  - Added `abortRecording()` method
  - Added long-press listener on mic button
  - Added visible Cancel button (hidden by default, shown during recording)
  - Hint text changes to "Long-press to cancel" during recording

- `res/values/strings.xml`:
  - Added `recording_cancelled` string
  - Added `cancel` string
  - Added `long_press_to_cancel` string

- `res/layout/keyboard_view.xml`:
  - Added Cancel button (initially hidden)

## Key Files Modified/Created (Session 12)

### Usage Tracking & Subscription Foundation (Phase 1 of Premium Features)
Implemented usage tracking infrastructure for monetization. Free tier limits: 60 min local / 5 min cloud per month.

### New Files:
- `data/preferences/UsageRepository.kt` - Tracks transcription usage:
  - Stores local/cloud minutes used in DataStore
  - Monthly reset logic (automatic on first transcription of new month)
  - `canTranscribeFile()` checks limits before transcription
  - `trackUsage()` records usage after successful transcription
  - `getAudioDurationMinutes()` calculates WAV file duration
  - `UsageStats` data class for complete usage snapshot
  - Constants: `FREE_LOCAL_MINUTES = 60f`, `FREE_CLOUD_MINUTES = 5f`

- `data/subscription/SubscriptionRepository.kt` - Subscription management stub:
  - `SubscriptionTier` enum (FREE, PRO)
  - `isPro` property (always returns false for now)
  - `purchase()` and `restorePurchases()` stubs for RevenueCat integration
  - `ProFeature` enum defining which features require pro tier
  - Ready for RevenueCat integration later

### Modified Files:
- `domain/transcription/TranscriptionRegistry.kt`:
  - Added `UsageRepository` and `SubscriptionRepository` dependencies
  - Checks usage limits before transcription
  - Returns error if free limit exceeded
  - Tracks usage after successful transcription

### How It Works:
1. User initiates transcription
2. `TranscriptionRegistry` checks `canTranscribeFile()` against limits
3. If over limit and not Pro: returns error message prompting upgrade
4. If within limit: proceeds with transcription
5. On success: calls `trackUsage()` to record minutes used
6. On month change: usage automatically resets

### Pending (Future Sessions):
- Settings UI for usage display (Phase 2)
- RevenueCat billing integration (Phase 6)
- Room database for transcription history (Phase 2)

---
*Last updated: January 14, 2026*
*Session 1: Initial project setup, build fixes*
*Session 2: Added Parakeet TDT v3 model, notification recording controls*
*Session 3: Model download UI, text injection, fixed file name mismatch (Issue 7), implemented sherpa-onnx transcription (Issue 8)*
*Session 4: Replaced notification recording with floating overlay button (draggable, works from any app)*
*Session 5: Fixed local model empty text issue (Issue 9) - added modelType parameter to sherpa-onnx config*
*Session 6: Added comprehensive debug logging and validation to LocalTranscriptionService for troubleshooting*
*Session 7: Complete UI redesign with premium dark theme (HomeScreen, SettingsScreen, Color, Type, Theme)*
*Session 8: Added SenseVoice & Whisper Small multilingual models, marked Parakeet as broken, removed floating button animation*
*Session 9: Replaced Groq with OpenAI Whisper, updated Gemini models (2.5/2.0)*
*Session 10: Replaced failed VoiceInputService with VoiceInkInputMethodService (IME) for reliable voice input from any keyboard*
*Session 11: Added abort/cancel recording feature (long-press to cancel without transcribing)*
*Session 12: Usage tracking foundation - UsageRepository, SubscriptionRepository stub, limit enforcement in TranscriptionRegistry*
*Session 13: Premium features - Transcription history (Room), Auto-punctuation, Streaming infrastructure, AdManager, Usage display*

## Key Files Modified/Created (Session 13)

### Premium Features & Monetization Implementation
Complete implementation of premium features infrastructure including transcription history, auto-punctuation, real-time streaming, ads, and usage display.

### New Files - Transcription History (Room Database):
- `data/database/TranscriptionEntity.kt` - Room entity for history entries:
  ```kotlin
  @Entity(tableName = "transcriptions")
  data class TranscriptionEntity(
      @PrimaryKey val id: String,
      val text: String,
      val modelId: String,
      val modelName: String,
      val provider: String,
      val timestamp: Long,
      val durationSeconds: Float,
      val wasStreaming: Boolean,
      val hadAutoPunctuation: Boolean
  )
  ```

- `data/database/TranscriptionDao.kt` - Data Access Object:
  - `getAllFlow()` - Get all transcriptions as Flow
  - `search(query)` - Search by text content
  - `insert()`, `delete()`, `deleteAll()`

- `data/database/VoiceInkDatabase.kt` - Room database singleton

- `data/history/TranscriptionHistoryRepository.kt` - Repository wrapping DAO

- `ui/screens/history/HistoryScreen.kt` - Full history UI:
  - Search functionality
  - Delete individual items
  - Copy to clipboard
  - Swipe to delete

- `ui/screens/history/HistoryViewModel.kt` - ViewModel for history screen

### New Files - Auto-punctuation:
- `domain/postprocessing/AutoPunctuationService.kt`:
  - Uses Gemini API to add punctuation
  - `punctuate(rawText): String` - Main method
  - `isAvailable()` - Check if API key is set

### New Files - Real-time Streaming:
- `domain/transcription/StreamingTranscriptionService.kt` - Interface:
  ```kotlin
  sealed class StreamingResult {
      data class Partial(val text: String) : StreamingResult()
      data class Final(val text: String) : StreamingResult()
      data class Error(val message: String) : StreamingResult()
      data object Complete : StreamingResult()
  }
  ```

- `domain/transcription/LocalStreamingService.kt`:
  - Sherpa-ONNX OnlineRecognizer for streaming
  - Real-time transcription as audio is recorded

### New Files - Ads:
- `data/ads/AdManager.kt` - AdMob integration:
  - Banner ad support
  - Interstitial after every 5 transcriptions (free users)
  - `shouldShowBannerAd` property
  - Test ad unit IDs (replace before production)

### Modified Files:
- `build.gradle.kts (app)`:
  - Added Room dependencies
  - Added Google AdMob SDK
  - Added RevenueCat SDK (v8.10.7)

- `domain/model/TranscriptionModel.kt`:
  - Added `supportsStreaming` to LocalModel
  - Added `StreamingModel` data class
  - Added `streamingModels` to PredefinedModels

- `data/audio/AudioRecorder.kt`:
  - Added `startStreamingRecording()` returning Flow<FloatArray>
  - Added `stopStreamingRecording()`

- `data/preferences/SettingsRepository.kt`:
  - Added `autoPunctuationEnabled` setting
  - Added `streamingEnabled` setting
  - Added `selectedStreamingModelId` setting

- `ui/screens/settings/SettingsViewModel.kt`:
  - Added UsageRepository and SubscriptionRepository dependencies
  - Added usageStats and subscriptionTier to UI state
  - Combine flow now includes 9 data sources

- `ui/screens/settings/SettingsScreen.kt`:
  - Added "Usage & Subscription" section
  - UsageCard composable with:
    - Plan badge (FREE/PRO)
    - Local usage progress bar (X/60 min)
    - Cloud usage progress bar (X/5 min)
    - Next reset date
    - Upgrade button (for free users)
  - Added Auto-punctuation toggle
  - Added Real-time Streaming toggle

- `ui/navigation/VoiceInkNavHost.kt`:
  - Added History screen route

- `ui/screens/home/HomeScreen.kt`:
  - Added History button to top bar
  - Integrated history saving

- `ui/screens/home/HomeViewModel.kt`:
  - Injected TranscriptionHistoryRepository
  - Injected AutoPunctuationService
  - Saves transcriptions to history
  - Applies auto-punctuation to local model results

- `services/OverlayService.kt`:
  - Saves transcriptions to history

- `services/VoiceInkInputMethodService.kt`:
  - Saves transcriptions to history via EntryPoint

- `di/AppModule.kt`:
  - Added provideVoiceInkDatabase()
  - Added provideTranscriptionDao()

### Dependencies Added:
```kotlin
// Room (local database)
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Google Mobile Ads (AdMob)
implementation("com.google.android.gms:play-services-ads:22.6.0")

// RevenueCat (subscriptions)
implementation("com.revenuecat.purchases:purchases:8.10.7")
```

### Issue Fixed - RevenueCat Version:
- Error: Version 7.9.2 not found
- Fix: Updated to version 8.10.7
