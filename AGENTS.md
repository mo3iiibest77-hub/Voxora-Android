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
│       │   ├── HomeScreen.kt         ← Voxora product chooser (Live Dub + Reader entries)
│       │   ├── DubScreen.kt          ← Live Dub working surface (start/stop, status, error)
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

- `TextExtractor` reads selectable-text PDF or UTF-8 TXT using the system document picker and returns `ExtractedDocument(name, chunks)`, where `name` is the provider display name. Scanned PDFs require OCR outside the app.
- `ChunkQueue` owns extracted strings and the current index; its current splitter uses a 500-word target. Paragraph/sentence-aware splitting is a future improvement, not an existing guarantee.
- `ReaderState` exposes `phase`, `chunk`, `total`, `segment`, `segmentTotal`, `text`, `segments`, `documentName`, and `error`; `ReaderPhase` includes connection, playback, pause, stop, completion, and failure states. `text` is the current chunk and `segments` its narration units.
- `narrationText: StateFlow<String>` exposes the current narration preview separately. **Source text and AI narration are distinct surfaces and are never merged** — the UI shows the extracted source and Gemini's transcript in separate cards.

### PDF reading order — fix, guarantees, and limits
- The bug: text from some PDFs appeared visually scrambled. The responsible layer is `TextExtractor`, not the UI. PDFBox collects glyphs in **content-stream order** and only sorts them when `sortByPosition` is enabled, and the default is `false`.
- Do **not** "fix" this by setting `sortByPosition = true`. That was measured against the real engine: it repairs a reversed single-column page but **row-interleaves a column-major two-column page** (it sorts the whole page into horizontal lines, so left/right columns alternate).
- The actual fix: keep `sortByPosition = false` and override `PDFTextStripper.writePage()` to re-order each entry of `charactersByArticle` with `PdfReadingOrder.order(fragments)` before `super.writePage()`.
- `PdfReadingOrder` is a pure-geometry `internal object` (no PDFBox, no `android.*`): it detects column gutters at **fragment level**, groups fragments into lines by vertical proximity, orders lines top-to-bottom, and orders columns left-to-right. It returns a stable permutation of its input.
- Guarantees: reading order is preserved for pages whose content stream is not painted in visual order; multi-column row-major and column-major layouts are de-interleaved; already-correct pages are left unchanged; a full-width element (e.g. a header) suppresses gutter detection for that band.
- Limits, stated honestly: **arbitrary layouts cannot always be reconstructed.** Tables, sidebars, marginalia, rotated text, footnotes and overlapping columns may still come out imperfectly. `PdfReadingOrder` only reorders text PDFBox already extracted; it cannot recover text that was never in the content stream (scanned PDFs). Do not claim otherwise in UI copy.
- Regression coverage: `app/src/test/java/com/voxora/app/reader/PdfReadingOrderTest.kt` pins the reversed-order repair, already-ordered preservation, multi-column de-interleaving, column-major preservation, the full-width-header fallback, that an ordinary word gap is not treated as a gutter, and the permutation/empty/single/determinism properties.

### Gemini narration — no rewrite backend or device TTS
- `GeminiReaderSession` is an independent text-to-AUDIO session over Gemini BidiGenerateContent. Never route Reader through `GeminiLiveSession`, Dub services, capture, or overlays.
- Send each document chunk with the instruction produced by `ReaderNarrationModes.instruction(mode, language)`. The prompt lives once in `core/.../gemini/ReaderNarrationModes.kt`; never scatter narration policy into UI code.
- The catalog of valid modes, their normalization, and their prompt text are owned by `ReaderNarrationModes`. `ReaderController` and `ReaderViewModel` delegate to it and never hard-code a mode list.
- Use only the saved Gemini API key from `UserPrefs`, shared with Live Dub. Do not add another key, backend URL, rewrite endpoint, or phone TextToSpeech fallback. Reader is key-only text→audio.
- Connection attempts use Reader-specific narration model candidates and bounded timeouts. Never change Dub model configuration to repair Reader.
- Stream returned PCM through `ReaderPlayback`; complete a chunk only after queued audio has played. Cancellation must release the session, audio output, and focus without corrupting a newer run.
- `narrate` must rethrow the **exact exception object** the caller's PCM sink threw. Awaiting the turn's `CompletableDeferred` applies coroutine stack-trace recovery, so `await()` hands back a *copy* of the cause whenever JVM assertions are enabled. `GeminiReaderSession` therefore records the sink exception on the turn (`Turn.sinkFailure`) and rethrows that object. Never classify sink failures from the awaited throwable, and never wrap or replace it.

### Narration modes — exact contract (Faithful vs Fluent)
The two modes are a **product contract**, not prompt decoration. They are defined in `core/src/main/java/com/voxora/core/gemini/ReaderNarrationModes.kt` and pinned by `ReaderNarrationModesTest`. Default is `faithful`; unknown values normalize to `faithful`.

**Faithful** — preserve the actual wording of the source document as much as possible, making only the minimum structural adjustments necessary for natural, understandable narration.
- Priority order: the original words, the original sentences, the original terminology, the original ordering, the original factual content, and the author's meaning.
- Allowed: repairing extraction artifacts, broken whitespace, line wrapping, OCR noise, and punctuation; splitting or joining a sentence only where the source is unreadable otherwise.
- Forbidden: free paraphrasing, replacing vocabulary, summarizing, explaining, inventing, and any new translation behaviour.

**Fluent** — understand the meaning of the source first, then rewrite that same content into the clearest, most natural, easy-to-understand spoken form possible, without changing the original meaning, facts, intent, or important details.
- Allowed: restructuring, combining, or splitting sentences for clarity.
- Forbidden: summarizing, shortening away meaning, inventing facts, adding information, changing claims or intent, translating into another language, turning the text into commentary, or adding explanations.

Both modes must speak in the document's language and preserve every fact. **Faithful is not a weaker Fluent and Fluent is not a license to summarize** — the difference is how much of the original wording survives, not how much of the content survives. Mode selection is disabled while narration is connecting or active (`CONNECTING` counts as active).

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
`ReaderScreen` is a single `LazyColumn` whose keyed items follow the listening workflow. This order is the information architecture — do not reshuffle it into a flat settings list:

1. top bar with back
2. **document identity** — display name (or "no document loaded") + pick/change button
3. **reading mode** — one card per mode, each with a visible one-line description of what it does
4. **narration language** — a tappable row showing flag + selected language, opening a searchable `ModalBottomSheet`
5. **playback** — status dot + phase label, chunk/segment progress, progress bar, prominent Play/Pause, Stop/Prev/Next
6. **error card** — only when `state.error` or a settings error is present
7. **source segments** — section header plus one card per narration unit, with the audibly playing segment highlighted and tappable to start there
8. **narration preview** — Gemini's transcript, visually separate from the source
9. privacy note

- Keep mode selection, PDF/TXT picker, progress/status, Play/Pause and Stop, error text, and the narration preview.
- Treat `CONNECTING` as active playback so Pause remains available and mode changes are disabled while connecting.
- Hoist previewable content (`ReaderContent` is stateless), use lifecycle-aware state collection and theme tokens, and provide a `Modifier` parameter. `ReaderScreen` is the only stateful wrapper.
- Explain background playback in both English and Persian; do not claim that leaving Reader pauses playback.
- The source card and the narration card are deliberately different surfaces. Never render Gemini's text where the source belongs, and never label the source as narration.
- Use `MaterialTheme.colorScheme` tokens only. `Theme.kt` must keep the container/outline roles (`primaryContainer`, `surfaceContainer*`, `outlineVariant`, `errorContainer`, …); without them Material 3 tints the grouped surfaces purple.

### Language flags — policy
- `ReaderLanguageFlags` (in `:core`) is the single source of truth. It maps each catalog code to one **explicit** representative ISO 3166-1 alpha-2 country and encodes the emoji from the Unicode regional-indicator range (`U+1F1E6..U+1F1FF`).
- Never derive a flag by manipulating the language subtag, and never hand-type the emoji. Guessing produced wrong and inconsistent flags.
- Pluricentric languages use one documented representative so the list stays consistent: `en → GB`, `es → ES`, `ar → SA`. Variants stay distinct: `pt-BR → BR` vs `pt-PT → PT`, `zh-Hans → CN` vs `zh-Hant → TW`.
- Languages with no single associated state (`eu`, `ca`, `ku`, `qu`) show the neutral globe `NEUTRAL` (🌐) rather than a misleading national flag.
- `fa → IR`. Do not substitute a Lion-and-Sun emblem or any unofficial asset unless an approved asset is added to the repo.
- A language added to `ReaderLanguages.all` without a mapping is a bug, and `ReaderLanguageFlagsTest.everyCatalogLanguageIsMappedDeliberatelyRatherThanByAccident` fails to surface it.
- Flags are decorative in the UI: each is wrapped in `Modifier.clearAndSetSemantics {}` so screen readers announce the language name, not the emoji.

---

## 5A. PRODUCT SHELL — VOXORA HOME & NAVIGATION

Voxora is presented as **two separate products** behind one neutral entry point.

```
App launch → Voxora Home → Live Dub | Voxora Reader   (Settings reachable from Home)
```

- `HomeScreen` is the product root and a **chooser only**. It shows the Voxora header and two equally weighted product cards — Live Dub and Voxora Reader — plus a Settings entry. It must never host product controls, status surfaces, or a Reader link rendered as a secondary text button.
- `HomeScreen` is stateless: `onOpenDub`, `onOpenReader`, `onOpenSettings`, `modifier`. Both product cards use the same `Surface(onClick = …)` surface so neither product looks secondary.
- `DubScreen` owns the Live Dub working surface (status dot, live waveform, error banner with Settings/Retry, Start/Stop, hints). It drives only the already-public `DubService` start/stop API and must not reach into `dub/` internals.
- Navigation lives in `VoxoraNav` as a private `VoxoraScreen` enum (`ONBOARDING`, `HOME`, `DUB`, `READER`, `SETTINGS`, `LOGS`) held in local Compose state. Do not add Navigation Compose or any second navigation framework for the product shell.
- Back behavior: Home is the root; system back from `DUB` / `READER` / `SETTINGS` returns to Home, and from `LOGS` returns to Settings. `VoxoraNav` registers a nav-level `BackHandler`; `ReaderScreen` registers its own later in composition and therefore wins for the Reader destination.
- Reader playback is owned by `ReaderController` / `ReaderService`, never by navigation state. Leaving the Reader destination — back press, Home, or any other destination change — must not pause or stop narration. `ReaderViewModel` deliberately has no `onCleared` stop.
- Settings is preserved unchanged and stays reachable from Home and from `DubScreen`.

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
- [ ] Unit tests for both modules compile and pass (`:core:testDebugUnitTest`, `:app:testDebugUnitTest`)
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
8. **JVM unit tests and `org.json`**: Android unit tests run against the mockable `android.jar`, whose `org.json` methods throw "not mocked". Any `:core` test that touches `JSONObject`/`JSONArray` needs `testImplementation("org.json:json:…")`; the real implementation takes classpath precedence over the stub. Pure-JVM code (`GeminiReaderSession`, `ReaderLanguages`, `ReaderNarrationModes`, `ReaderLanguageFlags`, `ChunkQueue`, `ReaderSpool`, `PdfReadingOrder`) must stay free of `android.*` APIs so it stays unit-testable without Robolectric. `PdfReadingOrder` deliberately takes plain geometry (`Fragment`) rather than `TextPosition` for exactly this reason — `TextExtractor` is the only place that bridges the two.
9. **Gradle enables JVM assertions**: the `Test` task runs its JVM with `-ea`, which turns on kotlinx-coroutines stack-trace recovery. Any exception crossing a `Deferred.await()` or `withTimeout` boundary comes back as a *copy* of the original object, so `assertSame` against an awaited cause passes under a plain `java -cp` run but fails under Gradle. When validating `:core` contract tests outside Gradle, always run with `-ea` to match CI, and never rely on awaited exception identity without preserving the original object first. The Reader sink path is guarded by `GeminiReaderSessionTest.sinkFailurePreservesOriginalExceptionAndSanitizesStatus`; keep it green.

---

## 14. TEST & VALIDATION FOUNDATION

- Unit tests live in `core/src/test/` (Gemini session, narration-mode and language-flag contracts) and `app/src/test/` (chunking, spooling, PDF reading order).
- Reader contract tests and what they pin:
  - `GeminiReaderSessionTest` — sink-failure exception identity and session status sanitization.
  - `ReaderNarrationModesTest` — the Faithful/Fluent prompt text contains the contractual semantics (Faithful's priority order, "Faithful is not a weaker Fluent", the forbidden paraphrase/summarize lists, the language substitution); uses a whitespace-normalizing helper because `trimIndent()` keeps source line breaks inside phrases.
  - `ReaderLanguageFlagsTest` — representative flags, `pt-BR`/`pt-PT` and `zh-Hans`/`zh-Hant` distinctness, the neutral set, regional-indicator validation, and that every catalog language is mapped deliberately.
  - `PdfReadingOrderTest` — reading-order repair and multi-column de-interleaving (see §5).
  - `ChunkQueueTest`, `ReaderSpoolTest` — chunking and spool contracts.
- Do not weaken or delete these tests to make a change pass. If a contract genuinely changes, update the contract text in `AGENTS.md` and the test in the same commit.
- `:core` owns its own test dependencies (`junit`, `org.json`) in `core/build.gradle.kts` — do not assume the `:app` test classpath applies to a library module.
- GitHub Actions runs `:core:testDebugUnitTest` and `:app:testDebugUnitTest` in a `unit-tests` job that is independent of the APK job, so a contract regression is visible without withholding the debug artifact.
- `android-ci.yml` triggers on pushes to `main` **and** `feat/reader-segmented-spooling`, on PRs targeting `main`, and on manual dispatch. Work that must be CI-verified has to land on one of those refs.
- Gradle runs its test JVM with `-ea`. Reproduce that flag when running tests by hand outside Gradle (see §13.9), otherwise stack-trace-recovery differences will hide real failures.
- The sink-failure exception contract is CI-verified: `GeminiReaderSessionTest.sinkFailurePreservesOriginalExceptionAndSanitizesStatus` passed on `feat/reader-segmented-spooling` at `4e4e17a` (Android CI run #63, `unit-tests` job success, 2026-09-17). The workflow has no `continue-on-error`, so a green `unit-tests` job is a genuine pass.
- Local pre-CI validation without Gradle is allowed and encouraged: compile the changed pure-JVM/Android sources with `kotlinc` against the pinned dependency jars and run the JUnit classes directly with `-ea`. This never substitutes for CI — the branch must still go green in Actions.
- Prefer pure-JVM, deterministic tests with `TemporaryFolder` for file-backed code; avoid Robolectric unless an Android API genuinely cannot be avoided.

---

*Last updated: auto-generated by Claude for Voxora project*
