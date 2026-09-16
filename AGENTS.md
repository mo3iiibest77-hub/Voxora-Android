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
│       ├── reader/                  ← Voxora Reader feature (new; isolated)
│       │   ├── ReaderScreen.kt
│       │   ├── ReaderViewModel.kt
│       │   ├── ReaderService.kt
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

### Pipeline State Machine
```
IDLE → FILE_PICKED → EXTRACTING → CHUNKED → [REWRITING + SPEAKING loop] → DONE
                                                ↑__________________________|
```

### TextChunk model
```kotlin
data class TextChunk(
    val index: Int,
    val rawText: String,
    val rewrittenText: String? = null,
    val audioReady: Boolean = false,
    val state: ChunkState = ChunkState.PENDING
)

enum class ChunkState { PENDING, REWRITING, READY, SPEAKING, DONE, ERROR }
```

### Chunk size
- Target: ~400–600 words per chunk
- Split on paragraph boundaries first, then word count
- Never cut mid-sentence

### Backend API contract (configurable URL in UserPrefs)
```
POST {readerBackendUrl}/rewrite
Content-Type: application/json

Request:
{
  "text": "...",
  "mode": "simple" | "faithful" | "academic" | "colloquial" | "custom",
  "custom_prompt": "..." // only when mode=custom
}

Response:
{
  "rewritten_text": "...",
  "tokens_used": 123  // optional
}
```

- If `readerBackendUrl` is empty → skip rewrite, use raw text
- Timeout: 30 seconds per chunk
- Retry: 2 times with exponential backoff on 5xx

### TTS (Android built-in for MVP)
```kotlin
// Use TextToSpeech with QUEUE_FLUSH for current, QUEUE_ADD for next
// Pitch: 1.0f, Speech rate: user-configurable (0.7 – 1.5)
// Language: match app locale or user selection
```

### Reader UI layout
```
┌─────────────────────────────────┐
│  📄 filename.pdf          [✕]   │  ← TopAppBar
├─────────────────────────────────┤
│                                 │
│   [Current chunk text display]  │  ← Scrollable, highlights current sentence
│   Chunk 3 of 24                 │
│                                 │
├─────────────────────────────────┤
│  Mode: [Simple ▾]               │  ← Dropdown chip
│                                 │
│  ◀◀   ⏸   ▶▶      🔊 1.0x     │  ← Playback controls
│  ████████░░░░░░░  3 / 24       │  ← Progress bar
└─────────────────────────────────┘
```

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
<!-- Required for Reader feature -->
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE"
    android:maxSdkVersion="32" />
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />

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
- `com.squareup.okhttp3:okhttp` — HTTP client for rewrite backend
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
*Applies to: all agents (OpenCode, Claude Code, Grok, ChatGPT, etc.)*
