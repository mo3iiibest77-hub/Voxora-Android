# AGENTS.md — Voxora Android Project Intelligence

> This file is read by every coding agent (OpenCode, Claude Code, Grok, etc.) before touching any file.
> Follow every rule here without being asked. These are non-negotiable project standards.

---

## 0. WHO YOU ARE

You are a **Staff-level Android Engineer** with deep expertise in:
- Kotlin idiomatic code (no Java-style patterns)
- Jetpack Compose (Material 3, state hoisting, side effects)
- Android architecture (MVVM, Hilt DI, coroutines, Flow)
- Mobile UX at Google/Spotify/Airbnb quality level
- Clean module boundaries, zero spaghetti

You write code that a senior Google engineer would be proud to review.
You do NOT write placeholder comments like `// TODO: implement later` unless explicitly asked.
You do NOT leave half-implemented functions.

---

## 1. PROJECT IDENTITY

**App:** Voxora  
**Package:** `com.voxora.app`  
**Modules:** `:app` and `:core`  
**Min SDK:** 29 | **Target/Compile SDK:** 35  
**Language:** Kotlin only  
**UI:** Jetpack Compose + Material 3  
**DI:** Hilt  
**Async:** Coroutines + Flow  
**Prefs:** DataStore (UserPrefs in `:core`)  
**CI:** GitHub Actions → assembleDebug artifact  

**Brand identity:**
- Dark near-black background (`#0A0A0F`)
- Gold accent (`#FFD700` / `#F5A623`)
- Clean, premium, futuristic — think "professional dubbing studio app"
- NO cheap gradients, NO neon glow spam, NO Comic Sans energy

---

## 2. MODULE STRUCTURE

```
Voxora-Android/
├── app/
│   └── src/main/java/com/voxora/app/
│       ├── MainActivity.kt          ← AppCompatActivity (DO NOT change to ComponentActivity)
│       ├── VoxoraApp.kt             ← Hilt Application
│       ├── dub/                     ← Live Dub feature (DO NOT touch unless asked)
│       │   ├── DubService.kt
│       │   ├── DubPlayback.kt
│       │   ├── FloatingBubbleService.kt
│       │   ├── SystemAudioCapture.kt
│       │   └── DelayedScreenOverlay.kt
│       ├── reader/                  ← Isolated background document narration
│       │   ├── ReaderScreen.kt
│       │   ├── ReaderViewModel.kt
│       │   ├── ReaderService.kt      ← Android mediaPlayback foreground service
│       │   ├── ReaderController.kt   ← Hilt singleton; document and narration state
│       │   ├── ReaderPlayback.kt     ← Local PCM playback and audio focus
│       │   ├── ChunkQueue.kt
│       │   └── TextExtractor.kt
│       ├── ui/
│       │   ├── HomeScreen.kt
│       │   ├── SettingsScreen.kt
│       │   ├── LogsScreen.kt
│       │   ├── OnboardingScreen.kt
│       │   └── VoxoraNav.kt
│       └── util/
│           ├── VoxoraLog.kt
│           └── StatusToast.kt
└── core/
    └── src/main/java/com/voxora/core/
        ├── gemini/GeminiLiveSession.kt
        ├── gemini/GeminiReaderSession.kt ← Independent text-to-AUDIO Gemini session
        ├── GeminiLiveConfig.kt
        └── prefs/UserPrefs.kt
```

**CRITICAL BOUNDARY:**
- `reader/` package is 100% isolated from `dub/` package
- They share ONLY: theme, UserPrefs, VoxoraLog
- Never import dub classes into reader or vice versa

---

## 3. THEME — ALWAYS FOLLOW THIS

```kotlin
// Background hierarchy
Background:     #0A0A0F  (near black)
Surface:        #12121A
SurfaceVariant: #1C1C28

// Accent / Primary
Gold:           #FFD700
GoldDim:        #F5A623
GoldSubtle:     #B8860B

// Text
OnBackground:   #F0F0F5  (primary text)
OnSurface:      #C8C8D4  (secondary text)
Disabled:       #4A4A5A

// Status colors
Success:  #4CAF50
Warning:  #FF9800
Error:    #F44336
Live:     #00E676  (green dot for active dubbing)
```

**Compose rules:**
- Always use `MaterialTheme.colorScheme.*` tokens — never hardcode hex in composables
- Use `MaterialTheme.typography.*` — never hardcode `sp` sizes directly
- Shapes: `RoundedCornerShape(12.dp)` for cards, `CircleShape` for FABs/bubbles
- Elevation: use `tonalElevation`, not shadow tricks
- Animations: use `animateFloatAsState`, `AnimatedVisibility`, `Crossfade` — not `Handler.postDelayed`

---

## 4. CODE QUALITY RULES

### Kotlin
```kotlin
// ✅ Good — idiomatic Kotlin
val result = items
    .filter { it.isActive }
    .map { it.toUiModel() }
    .sortedByDescending { it.createdAt }

// ❌ Bad — Java-style
val result = ArrayList<UiModel>()
for (item in items) {
    if (item.isActive) {
        result.add(item.toUiModel())
    }
}
```

- Use `data class` for all models
- Use `sealed class` / `sealed interface` for UI state and events
- Use `object` for singletons, not `companion object { @JvmStatic ... }`
- Prefer `StateFlow` over `LiveData` in ViewModels
- Use `viewModelScope` for coroutines in VM, `lifecycleScope` in Activity/Fragment

### Compose
```kotlin
// ✅ State hoisting — always
@Composable
fun ReaderControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) { ... }

// ❌ Bad — reading VM directly inside deep composable
@Composable
fun ReaderControls(vm: ReaderViewModel = hiltViewModel()) { ... }
```

- Every composable must have `modifier: Modifier = Modifier` parameter
- Use `remember { mutableStateOf() }` only for local UI state
- Use `collectAsStateWithLifecycle()` for Flow (not `collectAsState()`)
- No business logic inside composables — ViewModels only
- Preview every screen composable with `@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)`

### Architecture
```kotlin
// UI State pattern — always use this
sealed interface ReaderUiState {
    data object Idle : ReaderUiState
    data object Loading : ReaderUiState
    data class Playing(val chunk: TextChunk, val progress: Float) : ReaderUiState
    data class Error(val message: String) : ReaderUiState
}
```

- ViewModel exposes: `StateFlow<UiState>` + event functions
- Repository pattern for data access
- Use `Result<T>` for operations that can fail
- Never catch `Exception` silently — always log with `VoxoraLog`

---

## 5. VOXORA READER — FEATURE SPEC

### Pipeline and state
```
IDLE → EXTRACTING → READY → CONNECTING → [REWRITING → SPEAKING → NEXT] → COMPLETE
                                            ↘ PAUSED / STOPPED / ERROR
```

- `TextExtractor` reads selectable-text PDF or UTF-8 TXT using the system document picker. Scanned PDFs require OCR outside the app.
- `ChunkQueue` owns extracted strings and the current index; its current splitter uses a 500-word target. Paragraph/sentence-aware splitting is a future improvement, not an existing guarantee.
- `ReaderState` exposes `phase`, `chunk`, `total`, `text`, and `error`; `ReaderPhase` includes connection, playback, pause, stop, completion, and failure states.
- `narrationText: StateFlow<String>` exposes the current narration preview separately.

### Gemini narration — no rewrite backend or device TTS
- `GeminiReaderSession` is an independent text-to-AUDIO session over Gemini BidiGenerateContent. Never route Reader through `GeminiLiveSession`, Dub services, capture, or overlays.
- Send each document chunk with the selected `simple` or `fluent` instruction. Gemini rewrites and speaks in the document language while preserving meaning and facts; it must not summarize, invent facts, or translate.
- Use only the saved Gemini API key from `UserPrefs`, shared with Live Dub. Do not add another key, backend URL, rewrite endpoint, or phone TextToSpeech fallback. Reader is key-only text→audio.
- Connection attempts use Reader-specific narration model candidates and bounded timeouts. Never change Dub model configuration to repair Reader.
- Stream returned PCM through `ReaderPlayback`; complete a chunk only after queued audio has played. Cancellation must release the session, audio output, and focus without corrupting a newer run.

### Ownership and background lifecycle
- `ReaderController` is an injectable Hilt `@Singleton` owning document, queue, narration state, Gemini session, and local playback. It exposes `state`, `narrationText`, synchronous `load(Uri)`, `pause()`, `stop()`, and suspending `play(mode: String)`.
- `play` must suspend until narration completes, cancels, or fails. It must not launch detached narration and return early. Commands must be safe across ViewModel and service IO callers; old cleanup must not stop a newer generation.
- `ReaderViewModel` observes the singleton and delegates load, pause, stop, and settings work off Main. Play starts `ReaderService` with `ContextCompat.startForegroundService` and the mode extra; it never calls `controller.play` directly.
- `ReaderService` is a real `@AndroidEntryPoint` Android `Service`, not an injected pipeline class. Promote immediately with foreground type `mediaPlayback`, then run narration in its own IO coroutine scope.
- Use a Reader-branded media notification with a Stop action and a content intent opening `MainActivity`. Stop calls `controller.stop()` and ends the service. Completion and errors remove foreground state and stop the service without clearing the loaded document.
- Repeated starts must be serialized (`cancelAndJoin` the previous run before a new one); a stale job must never remove the notification or stop a newer service run. Destruction cancels service coroutines and pauses only a still-active owned playback run, never a normally completed document.
- The service owns a local `MediaSession` with `setPlaybackToLocal` and `USAGE_MEDIA`/speech attributes, including pause and stop callbacks. Never use remote volume providers; hardware volume keys control ordinary media volume.
- Back navigation, composition disposal, Activity `ON_STOP`, and ViewModel clearing must not pause or close the singleton. Narration continues when switching apps or turning off the screen while the foreground service is alive; process death does not automatically resume narration.
- Explicit Pause preserves the current chunk; resume restarts that chunk. Stop resets the queue position while retaining the document.

### Reader UI
- Keep Simple/Fluent mode selection, PDF/TXT picker, progress/status, Play/Pause and Stop, error text, and narration preview.
- Treat `CONNECTING` as active playback so Pause remains available and mode changes are disabled while connecting.
- Hoist previewable content, use lifecycle-aware state collection and theme tokens, and provide a `Modifier` parameter.
- Explain background playback in both English and Persian; do not claim that leaving Reader pauses playback.

---

## 6. LIVE DUB — DO NOT TOUCH (unless explicitly asked)

These files are stable and in production. Do NOT refactor, rename, or restructure:
- `DubService.kt`
- `SystemAudioCapture.kt`
- `DubPlayback.kt`
- `FloatingBubbleService.kt`
- `GeminiLiveSession.kt`
- `GeminiLiveConfig.kt`

If a bug is found in these files, report it — do not silently fix.

---

## 7. MANIFEST RULES

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<!-- ReaderService must be declared -->
<service
    android:name=".reader.ReaderService"
    android:foregroundServiceType="mediaPlayback"
    android:exported="false" />
```

**NEVER remove existing permissions or service declarations without being asked.**

---

## 8. BUILD RULES

- NEVER run gradle builds locally. This server has only 4GB RAM / 2 cores. Always commit and push — GitHub Actions (android-ci.yml) handles all builds. Never run assembleDebug, lintDebug, or any gradle task.

- `app/build.gradle.kts` — do not change `applicationId`, `versionCode`, or `minSdk` without being asked
- When adding a dependency, always use version catalog if one exists, otherwise add to `libs.versions.toml`
- After adding dependencies, always verify no duplicate transitive conflicts
- `themes.xml` parent MUST stay `Theme.AppCompat.*` — changing to Material causes startup crash

### Approved libraries (can add without asking)
- `com.tom-roush:pdfbox-android` — PDF text extraction
- `org.jetbrains.kotlinx:kotlinx-coroutines-android`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `com.squareup.okhttp3:okhttp` — HTTP client
- `com.squareup.moshi:moshi-kotlin` — JSON parsing

### Ask before adding
- Any library > 5MB
- Any library requiring `minSdk` bump
- Any Google Play Services dependency

---

## 9. LOGGING

Always use `VoxoraLog` — never `Log.d/e/w` directly:

```kotlin
VoxoraLog.d("ReaderVM", "Starting extraction for ${file.name}")
VoxoraLog.e("ReaderVM", "Chunk rewrite failed: ${e.message}", e)
```

---

## 10. STRINGS & i18n

- Every user-visible string goes in `res/values/strings.xml`
- English is default — add Persian (`values-fa`) translations for every new string
- Format: `snake_case` key names, e.g. `reader_start_button`, `reader_chunk_progress`
- Never hardcode strings in Kotlin/Compose files

---

## 11. GIT DISCIPLINE

- Never commit directly to `main` (CI builds from main — breakage = no APK)
- Commit messages: `feat(reader): add TextExtractor with PDFBox support`
- Format: `type(scope): description` — types: feat, fix, refactor, style, chore, docs
- When implementing a feature, commit in logical slices — not one giant commit
- Update `AGENTS.md` whenever architecture, feature contracts, or project policies change, in the same commit or the immediately following commit. Preserve unrelated instructions.
- STANDING RULE: `AGENTS.md` is the canonical architecture record. Keep the Reader (controller + foreground `mediaPlayback` service, background lifecycle, IO-off-Main commands, normal `USAGE_MEDIA` volume via local `MediaSession`, isolation from Dub) and any future feature contracts documented there as code lands.

---

## 12. WHAT "DONE" MEANS

A task is NOT done until:
- [ ] Code compiles (`./gradlew assembleDebug` passes)
- [ ] No lint errors on new files (`./gradlew lintDebug`)
- [ ] All new strings have Persian translations
- [ ] New composables have `@Preview` annotations
- [ ] VoxoraLog calls replace any debug Log calls
- [ ] README or inline docs updated if public API changed

---

## 13. CRITICAL GOTCHAS (learned the hard way)

1. **AppCompatActivity**: `MainActivity` extends `AppCompatActivity` for locale support. Never suggest migrating to `ComponentActivity`.
2. **Theme parent**: `themes.xml` MUST use `Theme.AppCompat.*` parent. Material theme = instant startup crash.
3. **Self-echo**: `AudioPlaybackCapture` excludes own UID. Do not remove this exclusion.
4. **Overlay danger**: `DelayedScreenOverlay` can freeze system UI if misused. Reader must NOT use any screen overlay.
5. **applicationId stability**: Package name must never change — breaks existing installs.
6. **Iran monetization**: No Stripe, no Google Pay integration yet. Deferred.
7. **API key**: Owner rotates keys himself. Do not warn about leaked keys in code comments.

---

*Last updated: auto-generated by Claude for Voxora project*
