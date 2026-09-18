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
│       │   ├── ReaderService.kt      ← Android mediaPlayback foreground service + transport controls
│       │   ├── ReaderBubbleService.kt ← Reader floating bubble (separate from the Live bubble)
│       │   ├── ReaderController.kt   ← Hilt singleton; document and narration state
│       │   ├── ReaderPlayback.kt     ← Local PCM playback and audio focus
│       │   ├── ReaderDisplayText.kt  ← Selected-language reading text, keyed by language
│       │   ├── ReaderInitialPlayback.kt ← Pure-JVM gate for a run's first audible frame
│       │   ├── ReaderGates.kt        ← Pure-JVM UI gating rules (what extraction allows)
│       │   ├── ReaderState.kt        ← ReaderPhase + ReaderState (pure JVM, no android.*)
│       │   ├── ChunkQueue.kt
│       │   └── TextExtractor.kt
│       ├── auth/
│       │   ├── GoogleCloudAuthorizer.kt   ← Identity Services OAuth (Cloud access token)
│       │   ├── CloudAuthFailureClassifier.kt ← Pure-JVM failure classification
│       │   ├── CloudRepository.kt         ← Hilt singleton: grant, discovery, selection
│       │   └── CloudEntryPoint.kt         ← Hilt entry point for plain composables
│       ├── ui/
│       │   ├── HomeScreen.kt         ← Voxora product chooser (Live Dub + Reader entries)
│       │   ├── DubScreen.kt          ← Live Dub working surface (start/stop, status, error)
│       │   ├── SettingsScreen.kt
│       │   ├── ApiUsageScreen.kt     ← Key check + observed usage; never invents quota
│       │   ├── UsageStatusVisual.kt  ← Pure-JVM usage tone mapping
│       │   ├── LogSeverity.kt        ← Pure-JVM severity → semantic role mapping
│       │   ├── CloudAccountCard.kt   ← Google account → Gemini project → key, in product language
│       │   ├── LogsScreen.kt
│       │   ├── OnboardingScreen.kt
│       │   └── VoxoraNav.kt
│       └── util/
│           ├── VoxoraLog.kt
│           ├── LogLineFormat.kt      ← Pure-JVM line shape, shared by every copy path
│           └── StatusToast.kt
└── core/
    └── src/main/java/com/voxora/core/
        ├── gemini/GeminiLiveSession.kt
        ├── gemini/GeminiReaderSession.kt ← Independent text-to-AUDIO Gemini session
        ├── gemini/ReaderLanguages.kt     ← The one language catalog (99 codes)
        ├── gemini/ReaderLanguageFlags.kt ← One explicit flag per catalog code
        ├── i18n/AppLocales.kt            ← UI locales shipped in the APK (subset)
        ├── usage/GeminiKeyProbe.kt       ← Official models.list key check (no tokens)
        ├── usage/GeminiKeyStatus.kt      ← Pure-JVM probe classification
        ├── usage/GeminiUsageMetadata.kt  ← Optional usageMetadata from server messages
        ├── usage/GeminiUsageLedger.kt    ← Bounded observed-usage record + JSON codec
        ├── usage/UsageRecorder.kt        ← Batched writer, discards usage on failure
        ├── usage/ApiKeyMask.kt           ← Display-safe key masking
        ├── usage/ApiUsageSnapshot.kt     ← Dashboard model; every gap has a reason
        ├── usage/UsageStore.kt           ← Usage ledger storage port
        ├── cloud/CloudScopes.kt          ← The only scope set Voxora requests (read-only)
        ├── cloud/CloudAuthState.kt       ← Authorization state + token-expiry policy (no token)
        ├── cloud/CloudProject.kt         ← Resource Manager project + parser
        ├── cloud/CloudApiKey.kt          ← API key **metadata** (never a secret) + parser
        ├── cloud/CloudResult.kt          ← Read outcome: refusal vs failure vs success
        ├── cloud/CloudLoadState.kt       ← Presentation state per read (empty ≠ denied)
        ├── cloud/CloudUsage.kt           ← Project usage, every gap carries a reason
        ├── cloud/CloudSelection.kt       ← Account → project → key + invalidation rules
        ├── cloud/GoogleCloudDirectory.kt ← Directory port + pure-JVM payload parsers
        ├── cloud/GoogleCloudHttp.kt      ← Official Cloud REST client (Bearer token)
        └── cloud/CloudEndpoints.kt       ← Injectable API hosts (tests only)
        ├── GeminiLiveConfig.kt
        └── prefs/UserPrefs.kt
        └── prefs/UsagePrefs.kt           ← Separate DataStore for the usage ledger
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

// Status colors — semantic roles, not literals. `Theme.kt` is authoritative;
// these are the values `VoxoraColors` exposes and they are what the Live
// bubble already used.
Success:  #3DDC84  (active narration / live / speaking)
Warning:  #E6B422  (paused, ready, preparing — the warm Voxora accent)
Error:    #E85D5D  (stopped or failed; also `colorScheme.error`)
Neutral:  colorScheme.outline  (idle, connecting, extracting)
```

**Compose rules:**
- Always use `MaterialTheme.colorScheme.*` tokens — never hardcode hex in composables
- **Status is semantic.** Material 3 models no success or warning role, so `Theme.kt` exposes `VoxoraSemanticColors` through `VoxoraColors.success` / `.warning` / `.danger` (a `staticCompositionLocalOf`, read via `@Composable @ReadOnlyComposable`). Ask for "the active colour", never a number. `ReaderStatusVisual` maps each `ReaderPhase` to a tone, and that mapping — not the composable — decides which role a phase gets: **speaking and playing are green, paused is gold and never green, stopped and failed are red, connecting and preparing stay neutral and must not falsely show green.**
- An active-state pulse must modify alpha, glow or scale of the semantic colour. Never introduce a separate neon colour for animation, and never run an infinite animation for a phase that is not active.
- Use `MaterialTheme.typography.*` — never hardcode `sp` sizes directly
- Shapes: `RoundedCornerShape(12.dp)` for cards, `CircleShape` for FABs/bubbles
- Elevation: use `tonalElevation`, not shadow tricks
- Animations: use `animateFloatAsState`, `Animatable`, `rememberInfiniteTransition` / `AnimatedVisibility` — not `Handler.postDelayed`

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
- `ReaderState` exposes `phase`, `chunk`, `total`, `segment`, `segmentTotal`, `text`, `segments`, `documentName`, and `error`; `ReaderPhase` includes connection, playback, pause, stop, completion, and failure states. `text` is the canonical extracted chunk exactly as `ChunkQueue` holds it; `segments` is that chunk's reading text in the selected narration language (see "Display language" below).
- `narrationText: StateFlow<String>` exposes the current narration preview separately. **The reading text and AI narration are distinct surfaces and are never merged** — the reading text is the chunk rendered in the selected language, the narration is the live transcript of the unit being spoken, and they are shown in separate cards.

### PDF reading order — fix, guarantees, and limits
- The bug: text from some PDFs appeared visually scrambled. The responsible layer is `TextExtractor`, not the UI. PDFBox collects glyphs in **content-stream order** and only sorts them when `sortByPosition` is enabled, and the default is `false`.
- Do **not** "fix" this by setting `sortByPosition = true`. That was measured against the real engine: it repairs a reversed single-column page but **row-interleaves a column-major two-column page** (it sorts the whole page into horizontal lines, so left/right columns alternate). It also produces the *same* row-interleaved output as the bug for a header-carrying two-column page, so it is not a substitute for the geometry pass.
- The actual fix: keep `sortByPosition = false` and override `PDFTextStripper.writePage()` to re-order each entry of `charactersByArticle` with `PdfReadingOrder.order(fragments)` before `super.writePage()`. PDFBox calls `writePage()` once per page with one article, so the reorder is genuinely per-page.
- `PdfReadingOrder` is a pure-geometry `internal object` (no PDFBox, no `android.*`). It returns a stable permutation of its input and never invents text. Its pipeline is: group fragments into **lines** by vertical proximity → detect **column gutters** → read the page as a stack of **regions**, where a gutter-crossing line is emitted in place and the runs of ordinary lines between those are read column by column.
- **Gutters are measured per line, not per raw fragment.** This is the load-bearing detail and the verified root cause of the reported "chunk 1 is fine, every later chunk is scrambled" bug: the original detector took a page-wide union of every fragment's x-extent, so a single full-width element — a running header or footer — covered the gutter for the whole page, the page was then read as one column, and the two columns came out **row-interleaved** (`Left one Right one Left two Right two …`). Because the first page of a section often has no running header while every later page carries one, page 1 read correctly and pages 2+ were scrambled, exactly as reported. Measuring crossing by line means a minority of lines may cross a gutter (headers/footers) without erasing it.
- **Regions are banded by the page's gutters, not by gutters re-derived from the region.** Re-deriving them was wrong: a short region (two lines under a running header) falls below `MIN_LINES_FOR_COLUMNS` and would silently collapse back to row-interleaved order. A gutter-crossing line can never reach a region — `order` emits those on its own — so every fragment in a region belongs to exactly one band.
- Guarantees: reading order is preserved for pages whose content stream is not painted in visual order; multi-column row-major and column-major layouts are de-interleaved; a full-width running header or footer no longer destroys the column structure of the page it sits on; already-correct pages are left unchanged.
- Limits, stated honestly: **arbitrary layouts cannot always be reconstructed.** Tables, sidebars, marginalia, rotated text, footnotes and overlapping columns may still come out imperfectly. A gap that is *inside* a line but large and repeated across most lines (a tab-aligned label, a table-of-contents leader, a widely letter-spaced heading) can still be mistaken for a column gutter; that layout is genuinely ambiguous from geometry alone and was already mis-ordered before this rework, so no threshold was tuned against a synthetic page for it. `PdfReadingOrder` only reorders text PDFBox already extracted; it cannot recover text that was never in the content stream (scanned PDFs). Do not claim otherwise in UI copy.
- Regression coverage: `app/src/test/java/com/voxora/app/reader/PdfReadingOrderTest.kt` pins the reversed-order repair, already-ordered preservation, multi-column de-interleaving, column-major preservation, that a gutter-crossing running header leaves the columns de-interleaved, that a full-width footer is read after both columns, that a page below the line floor has no column structure to infer, that an ordinary word gap is not treated as a gutter, and the permutation/empty/single/determinism properties. The four header/footer/line-floor tests fail against the pre-fix implementation and pass against the current one, so they genuinely reproduce the defect rather than restating it.
- `app/src/test/java/com/voxora/app/reader/ReaderPipelineOrderTest.kt` pins the layers *after* extraction on the real `ChunkQueue`: chunks follow document order and rebuild the source, chunk 1/2/3 do not overlap or reach ahead, every chunk's segments rebuild that chunk in order, segments never carry text from another chunk, a new queue does not inherit a previous document's cached segments, advancing yields the next chunk rather than a stale one, and the narration instruction is identical for every chunk and segment. These rule out chunking, segmenting and caching as corruption sites.
- Chunking invariants, stated as the contract they are: `normalize(fullSource) == normalize(join(allChunks))` and, for every chunk, `normalize(chunk) == normalize(join(segments(chunk)))`. Compare through the normalization contract, never raw strings.

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

### Display language — the reading text follows the selection
The reading text shown for the current chunk must be in the selected narration language, not in the document's own language. The canonical extracted document is never destroyed or rewritten to achieve that.

- `app/src/main/java/com/voxora/app/reader/ReaderDisplayText.kt` is the layer that holds the selected-language rendering of individual narration units. It is pure JVM (no `android.*`) so the language contract stays unit-testable.
- **The Reader Gemini path is the only translation mechanism.** The Reader does not call a second backend, an external translation API, or device text-to-speech, and it never sends the API key anywhere new. `GeminiReaderSession.narrate` already returns each unit's transcript in the selected language, so no new session type or request shape is required.
- **Every entry is keyed by language as well as chunk and segment.** A language change can therefore never surface a rendering produced for a different language, and two languages can never share an entry. This is the property that stops stale text from being shown after a language change.
- A unit that has not been narrated in the selected language yet has no rendering, and `readingText` falls back to the extracted source for that unit. The published list therefore always keeps the canonical chunk's length, order and boundaries. This is an honest limitation, not a bug: display text appears as Gemini narrates, so an unplayed chunk still shows the document's original text. Do not claim in UI copy that the whole document is translated up front.
- Blank transcripts are rejected rather than stored, so an empty rendering can never overwrite a usable one or hide the extracted source behind an empty string.
- `ReaderController` records each unit's transcript under **the language the run's instruction was built with** (the same value passed to `ReaderNarrationModes.instruction`), so the instruction and the reading text can never disagree within a run. It clears the cache when a new document replaces the queue, because chunk indices then refer to different content.
- **Recording a transcript is not enough — the Reader must republish.** `ReaderDisplayText.shouldRepublish(language, chunk, displayedLanguage, displayedChunk)` is the production rule for turning a stored rendering into visible state, and `ReaderController.produce` calls it right after `record`, republishing `publish(state.value.phase)` when it returns true. Requiring **both** the chunk and the language to match is what keeps the visible state owned by the current position: a producer renders whole chunks ahead of playback, so republishing unconditionally would drag the UI onto a prefetched chunk or back onto a language the reader has already left. Without this step the reading text lagged the narration by a chunk — the audio switched language while the screen kept the extracted source. Never remove it, and never widen it to "republish on every record".
- `publish` preserves `state.error` when it republishes the `ERROR` phase and clears it on any other phase transition. Republishing is now routine (it happens on every narrated unit), so dropping the message would silently erase the failure reason the user needs to see.
- `ReaderController.setOutputLanguage(language)` updates the display language and republishes immediately. `ReaderViewModel` calls it on init (before `restoreLastDocument`) and from `setOutputLang`, so the controller is always in step with the persisted selection. Never let the UI keep its own copy of the display language.
- **The displayed language switches when the chunk becomes current, not when the chunk finishes.** `publish` derives the reading text for `queue.index` in the same synchronous step that makes that chunk current, so arriving at Chunk N immediately shows N's selected-language renderings. It must never wait for N to finish narrating, and it must never keep showing Chunk N−1's language while N's transcript is still being produced. The reason this is free is the prefetch architecture: the producer renders whole chunks ahead of playback, so the chunk about to be narrated already has its renderings cached and the turn is a pure state read. Do not "fix" this by making the UI wait for a translation step — that is exactly the ordering that would insert a pause between chunks.
- `ReaderDisplayText.pending(language, chunk, unitCount)` returns the units of a chunk with no selected-language rendering yet, and `publish` reports them as `ReaderState.pendingSegments`. This exists because `readingText` falls back to the extracted source for an unrendered unit: without it, "not rendered yet" is indistinguishable from "the selection happens to read like the source", and the source language would be presented as if it were the selected one. `ReaderPageText` (`isPreparing`, `isPreparingWholePage`) is the pure-JVM rule for when that temporary treatment is actually shown — only while the chunk is being narrated (`ReaderGates.isNarrating`), never while browsing, paused or stopped, because a reader who has not pressed Play still has to be able to read their document.
- **A run's first audible frame waits for the first segment's selected-language rendering.** The reading text *is* the Gemini transcript, so "text before audio" can only mean the producer finishes the unit the run starts on before any PCM is written; playback then starts from the audio the spool buffered while that unit was produced. `ReaderController.awaitInitialRendering` is that wait, and it is the **only** place the narration path waits for text — exactly one unit, so nothing blocks on the whole document and nothing is translated up front. `output.start()` is deliberately called *after* the gate: starting the track earlier held audio focus in silence for the whole synthesis of the first unit. The decision itself is `ReaderInitialPlayback.gate`, pure JVM and pinned by `ReaderInitialPlaybackTest`. **Every gate read happens under the controller lock** — the producer ends the unit and records the rendering in one critical section but publishes the spool snapshot *before* recording, so observing the snapshot alone would not make the rendering visible, and reading `ReaderDisplayText` outside the lock would race its map. A gate failure is terminal and must not drain partial audio: playing a unit whose transcript never arrived is precisely the "audio first, text later" sequence the gate exists to prevent. Do not add a "the source language is the selection, so the source counts" shortcut — Voxora detects no document language anywhere, so the source was never a valid rendering for a selected language; the real fast path is a unit an earlier run already rendered, which returns immediately.
- **The display layer is derived and must stay derived.** `publish` only reads `ReaderDisplayText` and the canonical `ChunkQueue`; it never appends PCM, advances a consumer cursor or completes a spool unit. The producer and the consumer never read `ReaderState`. That one-way dependency is what guarantees a display-language change cannot insert silence between chunks — keep it that way, and never make the spool or the consumer consult display state to decide how much audio to play.
- `ReaderState.pendingSegments` is raw state; whether the temporary treatment is shown is `ReaderPageText`'s decision, because it depends on the phase. Do not branch on the set directly in a composable.
- Changing the language must not leave narration from the previous language on screen: `publish` clears `narrationText` for every phase other than `SPEAKING`, and language selection is disabled while narration is active (`canConfigure()` in `ReaderViewModel`).
- The instruction prompt is a pure function of `(mode, language)`. Never make the visible-text requirement change the audio semantics, the Faithful/Fluent contracts, or `PdfReadingOrder`.
- Pinned by `app/src/test/java/com/voxora/app/reader/ReaderDisplayLanguageTest.kt`: language/chunk/segment scoping, that a language never sees another language's rendering, that a change does not surface the previous language's text, blank rejection, trimming, replace-not-duplicate, clear, and the pipeline contract above the real `ChunkQueue` — length/order/boundary preservation, every chunk obeying the same contract, no cross-language mixing, no inheritance across documents, and that the canonical source is unchanged. Four of these tests fail when the cache key is mutated to ignore the language, so they reproduce the defect rather than restating it.
- The refresh rule is pinned by `app/src/test/java/com/voxora/app/reader/ReaderDisplayRefreshTest.kt`: a rendering for the displayed chunk and language is published immediately, one for a prefetched chunk or an abandoned language is cached without moving the screen, a rejected (blank) rendering never triggers a refresh, each narrated unit appears as it arrives while the chunk keeps its length and order, switching language shows only the new language's renderings, and the canonical source is never changed. Making `shouldRepublish` return `true` unconditionally fails three of them.
- The chunk-start timing contract is pinned by `app/src/test/java/com/voxora/app/reader/ReaderChunkStartLanguageTest.kt`: arriving at a chunk shows its cached selected-language text without waiting for narration or rendering anything new, a chunk with no rendering starts in the selected language and is reported as preparing rather than showing the previous chunk's language, the pending set shrinks unit by unit, a late transcript updates the current chunk, a transcript from an abandoned generation **or** revision cannot reach the page, prefetch never moves the page ahead of the narration, switching language never leaves the other language's text on screen, switching back restores the cached rendering immediately, and the canonical chunk is untouched.
- Audio continuity is pinned by `app/src/test/java/com/voxora/app/reader/ReaderDisplayAudioContinuityTest.kt`, against the **real** `ReaderSpool` rather than a mock: recording display text does not change the spool snapshot, switching language leaves the spool and the queue untouched, consuming a chunk renders nothing for it, publishing between units does not change the bytes the consumer writes, every unit is still read once and in order, the chunk promoted next is already rendered and already committed before the turn, the turn adds no work to the audio path, and display state is never written back into the document. These assert ownership and ordering, not timing — do not convert them into sleep-based tests.

### One language catalog — Reader, Settings and Live Dub
There is exactly **one** language catalog: `core/.../gemini/ReaderLanguages.kt`. Never hand-type a second language list in a screen or a view model; that is what produced three lists that disagreed (14 dubbing languages, 7 app languages, 99 narration languages).

- `app/src/main/java/com/voxora/app/reader/ReaderLanguages.kt` → `languageOptions(locale, query)` is the shared option builder over `ReaderLanguages.all` (code, localized label, English name, catalog flag, search text). **Both** the Reader's narration sheet and the Settings "Dubbing language" dropdown call it, so they are identical in count, order, labels and flags by construction. Do not add a Settings-only list.
- `core/.../i18n/AppLocales.kt` is the app's own **UI locale** set — deliberately smaller, because a UI language only works if the APK packages that translation (`resourceConfigurations` in `app/build.gradle.kts`). `AppLocales.shipped` must stay in step with it. It still resolves names and flags through `ReaderLanguages`, so a language is never described two ways; it names each entry in its own language (an endonym), because someone who cannot read the current UI language still has to recognise their own.
- These are three genuinely different things and must not be collapsed: the **app UI locale** (what the interface is written in), the **Reader narration language** (`prefs.readerOutputLang`), and the **Live Dub target language** (`prefs.targetLanguage`). They have different defaults (`en` vs `en` vs `fa`) and different scopes; unify the *catalog*, never the *preference*.
- Reader narration and Live Dub share the catalog because both are Gemini output languages, but they are bounded differently at the model: the Reader builds a prompt instruction, while Live Dub sets `translationConfig.targetLanguageCode`. If Live Translate turns out to support a narrower set, encode that as a capability **filter over `ReaderLanguages.all`** with a documented source — never as a second hand-typed list.
- `ReaderLanguages.languageOrNull(code)` is the honest lookup (null when the catalog does not describe the code); `language(code)` normalizes unknown codes to `DEFAULT`. Pickers must use `languageOrNull` so they can never invent a language.
- **A language is only ever described in a locale Voxora actually ships.** `Locale.getDisplayName(locale)` names a language *in* that locale, so passing a raw device locale leaks a language the app never ships: a phone set to Chinese with the Voxora UI in English labelled every language in Chinese while the rest of the screen stayed English. Voxora ships no Chinese translation, so it must never *describe* languages in Chinese either. `AppLocales.resolve(locale)` is the only place a display locale may be produced from outside input — it matches the full tag first, then the language subtag (`fa-IR` → `fa`, `es-MX` → `es`), and falls back to `DEFAULT`. Every display-name boundary must go through it; `ReaderScreen` resolves `LocalConfiguration.current.locales[0]` and passes the result to the ViewModel rather than resolving inside the option builder.
- The rule is pinned by `core/src/test/java/com/voxora/core/i18n/AppLocalesTest.kt`. A device locale with no Voxora translation must resolve to a shipped locale, never to itself.

### Ownership and background lifecycle
- `ReaderController` is an injectable Hilt `@Singleton` owning document, queue, narration state, Gemini session, and local playback. It exposes `state`, `narrationText`, synchronous `load(Uri)`, `pause()`, `stop()`, and suspending `play(mode: String)`.
- `play` must suspend until narration completes, cancels, or fails. It must not launch detached narration and return early. Commands must be safe across ViewModel and service IO callers; old cleanup must not stop a newer generation.
- `ReaderViewModel` observes the singleton and delegates load, pause, stop, and settings work off Main. Play starts `ReaderService` with `ContextCompat.startForegroundService` and the mode extra; it never calls `controller.play` directly.
- `ReaderService` is a real `@AndroidEntryPoint` Android `Service`, not an injected pipeline class. Promote immediately with foreground type `mediaPlayback`, then run narration in its own IO coroutine scope.
- Use a Reader-branded media notification with previous / pause-or-resume / next / stop actions and a content intent opening `MainActivity`. It is a `MediaStyle` notification backed by the service's `MediaSession`; the body states the phase and **never the document**, because a lock screen must not leak what the reader is listening to. Stop calls `controller.stop()` and ends the service. Completion and errors remove foreground state and stop the service without clearing the loaded document.
- **A pause must not tear the foreground service down.** Only a *terminal* phase (`IDLE`, `STOPPED`, `COMPLETE`, `ERROR`) ends the service; `PAUSED` leaves it, the session and the notification alive, which is what makes Resume possible from the notification. Ending the service on pause would remove the very control the user needs next. The notification is rebuilt on every phase change, so pause flips the action to Resume and the body to "Narration paused."
- **Notification previous/next move exactly one segment and are bounded by the current chunk.** They go through `ReaderController.jumpToSegment`, which clamps to the chunk's units, so a step at either end is a truthful no-op rather than a move into the neighbouring chunk — the chunk stays reachable only from the app. They are deliberately **not** routed through the service's command queue: that queue cancels the running command, which would end background narration. The running play loop already re-runs its pipeline when the navigation revision changes, so a synchronous jump is all that is needed. The bounds themselves are the ones pinned by `ReaderSegmentNavigationTest`; do not add a second step rule.
- **The Reader has its own floating bubble, and it is a separate service.** `ReaderBubbleService` lives in `reader/` because `FloatingBubbleService` imports `DubService` and the reader package must never depend on `dub/`. It mirrors the Live bubble's contract — overlay-permission check, `TYPE_APPLICATION_OVERLAY`, `WindowManager` add/remove in a try/catch, whole-widget drag, tap to reveal the stop disc, double-tap to open the app, an oval in the brand palette, teardown that never leaks a window — with the flat `● READER` label (green dot and letters, a larger bold gold `R`) and a **solid** gold wave rather than the Live gradient. It is gated on the `reader_bubble` preference (absent means on, matching Live), shown and hidden from `ReaderService` off the phase exactly as `DubService.syncBubble` does, and hidden in `onDestroy`. The Reader top bar owns the toggle; a stop from the bubble clears the same preference, so a dismissal persists until the toggle turns it back on. **Do not fold the two bubbles into one class and do not touch the Live bubble while working on this one.**
- Repeated starts must be serialized (`cancelAndJoin` the previous run before a new one); a stale job must never remove the notification or stop a newer service run. Destruction cancels service coroutines and pauses only a still-active owned playback run, never a normally completed document.
- The service owns a local `MediaSession` with `setPlaybackToLocal` and `USAGE_MEDIA`/speech attributes, including pause and stop callbacks. Never use remote volume providers; hardware volume keys control ordinary media volume.
- Back navigation, composition disposal, Activity `ON_STOP`, and ViewModel clearing must not pause or close the singleton. Narration continues when switching apps or turning off the screen while the foreground service is alive; process death does not automatically resume narration.
- Explicit Pause preserves the current chunk; resume restarts that chunk. Stop resets the queue position while retaining the document.

### Reader UI
`ReaderScreen` is a single `LazyColumn` whose keyed items follow the listening workflow. This order is the information architecture — do not reshuffle it into a flat settings list:

1. top bar with back and the floating-bubble toggle (see "Ownership and background lifecycle")
2. **document identity** — display name (or "no document loaded") + pick/change button
3. **reading mode** — one card per mode, each with a visible one-line description of what it does
4. **narration language** — a tappable row showing flag + selected language, opening a searchable `ModalBottomSheet`
5. **playback** — status dot + phase label, chunk/segment progress, progress bar, prominent Play/Pause, Stop
6. **error card** — only when `state.error` or a settings error is present
7. **reading page** — the current chunk as one page (see below), not the whole document
8. **narration preview** — Gemini's live transcript, visually separate from the reading text
9. privacy note

**The reading surface is a page, not a scroll.**
- `ChunkPage` composes **exactly one narration segment** of the current chunk — never every chunk, and never a whole chunk as one card. A 210-chunk PDF with 8 units each still composes one unit, so the page is bounded by the 80-word narration split, not by the document. Do not add a document-wide list.
- **The horizontal swipe moves one narration segment, never a chunk.** This is the distinction the model exists to keep: a document with 129 chunks and 8 units each is swiped `Segment 1 → Segment 2 → … → Segment 8` *inside the current chunk*. A swipe must never express `Chunk 1 → Chunk 2`; that is what the explicit Previous/Next controls are for. Swiping at the first or last unit of a chunk does nothing — it does not roll into the neighbouring chunk.
- **Chunk navigation and segment navigation are two independent operations.** Chunk moves go through the controller's `jumpToChunk`; segment moves go through `jumpToSegment`. Both are bounded and single-step, both return whether the position actually moved, and neither may be implemented in terms of the other. Do not add a second navigation state machine and do not add a pager library — the custom drag is deliberately dependency-free.
- `ReaderPager` is the pure-JVM model and the only place turn logic lives: `target` (bounded, single-step), `swipeStep` (the whole swipe rule, bounded by the current chunk's unit count, so it cannot produce a step outside it), `segmentIndex` (safe zero-based derivation from the one-based state), `turnFor` (gesture → logical turn) and `enterOffset` (which side the incoming unit comes from). Unit-tested in `ReaderPagerTest` and `ReaderSegmentNavigationTest`.
- **The gesture state machine always returns to rest.** A release below the threshold animates the offset back to **exactly** zero — never a partially shifted card and never an accumulated offset. A release above it recedes and fades the current unit towards the side the finger was already moving, steps the segment exactly once, and brings the arriving unit in from the opposite side. The resting offset is restored by a `LaunchedEffect` keyed on the displayed unit, and a turn guard plus a cancellable settle job make rapid repeated swipes resolve one at a time. Never use `Handler.postDelayed` or an arbitrary delay.
- **Only the text card moves.** The swipe gesture and its transform belong to `SegmentCard`, and `key(segmentIndex)` plus the enter animation wrap **only** the card. The section header, the chunk identity card, the segment position row and the chunk buttons are a fixed frame: a turn must never translate or fade them, and `requestChunkTurn` fades only the card too. Ownership is deliberate — `drag`, `presence`, `turning` and the settle job stay in `ChunkPage` because they drive the controller jump and have to outlive the card being swapped; only the visual transform and the gesture are card-scoped. Do not move the turn state into the card, and do not reattach the gesture to the page column: a swipe that moves the whole screen is the defect this contract exists to prevent.
- **"Next" and "previous" are logical, not physical.** The same finger movement means opposite things in LTR and RTL, and `turnFor` maps each layout's gesture to the same logical turn. A Persian reader must never get reversed segment or chunk order. Use auto-mirrored icons (`Icons.AutoMirrored.Filled.KeyboardArrowLeft/Right`) for the arrows.
- The page header states the chunk and total compactly (`reader_page_chunk`, "Chunk 12 of 210"), the segment progress, and the narration language. Do **not** put a bare `12 / 210` numeric indicator next to the arrows: it reorders unpredictably under RTL bidi. This is an audiobook reader, not a diagnostics panel — keep the surface compact.
- **RTL is a layout property, not a text hack.** Persian must shape, align and order correctly through Compose's RTL-aware layout (`LocalLayoutDirection`). Fix the real cause — a fixed-width container, a wrong `TextAlign`, a forced LTR, bad icon/text spacing, an inappropriate single-line constraint. Do not insert invisible Unicode directional characters to paper over a layout bug.
- A unit still waiting for its selected-language rendering shows the temporary "preparing" treatment (see "Display language"), not the source language presented as the selection.

- Keep mode selection, PDF/TXT picker, progress/status, Play/Pause and Stop, error text, and the narration preview.
- Treat `CONNECTING` as active playback so Pause remains available and mode changes are disabled while connecting.
- **Extraction must never look like a frozen screen.** Restoring the last PDF and extracting a large one both hold `EXTRACTING` for a while, so the document card shows an indeterminate progress bar plus a "reading document" title and body instead of the "no document loaded" copy, and the reading mode, narration language and file picker stay usable. All of it is expressed through `ReaderGates` (`app/src/main/java/com/voxora/app/reader/ReaderGates.kt`), which is pure JVM and the single place these rules live — the screen and `ReaderViewModel` both call it, so they cannot disagree. Never re-derive a gate inline in a composable.
- The safety gates `ReaderGates` preserves, and why: playback stays off until the queue is ready (a transport request against an empty queue has nothing to narrate); chunk/segment navigation needs a document; mode and language freeze only while narration is actually running; picking another document stays available during extraction because `ReaderController.startLoad` already serializes loads (`cancelAndJoin` behind a generation bump), so it is a supported cancellation rather than a race. Leaving the screen is never gated.
- Pinned by `app/src/test/java/com/voxora/app/reader/ReaderStartupGatesTest.kt`: settings stay editable during extraction, another document can be picked during extraction, playback never starts before the queue is ready, navigation needs a document and no extraction, every phase offers either configuration or playback (the Reader is never fully locked), and configuration freezes only while narration is running. Restoring the old "EXTRACTING locks the settings" behaviour fails three of them.
- Hoist previewable content (`ReaderContent` is stateless), use lifecycle-aware state collection and theme tokens, and provide a `Modifier` parameter. `ReaderScreen` is the only stateful wrapper.
- Explain background playback in both English and Persian; do not claim that leaving Reader pauses playback.
- The reading page and the narration card are deliberately different surfaces. Never render Gemini's live transcript where the reading text belongs, and never label the reading text as narration. The page header must state the selected narration language, because the page is the selected-language rendering and the reader has to be able to tell which language they are looking at.
- Use `MaterialTheme.colorScheme` tokens, plus the semantic roles in `VoxoraColors` for success/warning/danger (Material 3 models neither). `Theme.kt` must keep the container/outline roles (`primaryContainer`, `surfaceContainer*`, `outlineVariant`, `errorContainer`, …); without them Material 3 tints the grouped surfaces purple.

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
- Navigation lives in `VoxoraNav` as a private `VoxoraScreen` enum (`ONBOARDING`, `HOME`, `DUB`, `READER`, `SETTINGS`, `USAGE`, `LOGS`) held in local Compose state. Do not add Navigation Compose or any second navigation framework for the product shell.
- Back behavior: Home is the root; system back from `DUB` / `READER` / `SETTINGS` returns to Home, and from `USAGE` / `LOGS` returns to Settings. `VoxoraNav` registers a nav-level `BackHandler`; `ReaderScreen` registers its own later in composition and therefore wins for the Reader destination.
- Reader playback is owned by `ReaderController` / `ReaderService`, never by navigation state. Leaving the Reader destination — back press, Home, or any other destination change — must not pause or stop narration. `ReaderViewModel` deliberately has no `onCleared` stop.
- Settings is reachable from Home and from `DubScreen`. Every control it had before is still present: app language, Gemini API key, Open AI Studio, dubbing language, save, account sign-in/out, overlay permission and logs.

### Visual system — Reader is the reference
`ReaderScreen` is the product's visual reference. Any screen that presents grouped content must reuse its design language rather than inventing a second one:

- one keyed `LazyColumn`, `contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)`, `verticalArrangement = spacedBy(18.dp)`, background + `safeDrawingPadding()`
- a top bar that is a back `IconButton` plus a `titleLarge` `SemiBold` title
- a section header (`titleMedium` `SemiBold`) above each group
- rounded, outlined cards — `RoundedCornerShape(20.dp)`, `surfaceContainerHigh`, `BorderStroke(1.dp, outlineVariant)`, 18 dp inner padding
- a 44 dp circular icon badge tinted `primary` at 15% alpha for card headers
- `MaterialTheme.colorScheme` and `MaterialTheme.typography` only — never a hardcoded hex or `sp` size inside a composable
- a stateless content composable with a `Modifier` parameter, plus `@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)`; the screen composable is the only stateful wrapper

`SettingsScreen` follows this (stateful `SettingsScreen` → stateless `SettingsContent`, two previews). Do not let it drift back into a flat, ungrouped scroll.

Both Settings language pickers are built from the one catalog (see "One language catalog"): the dubbing dropdown through the same `languageOptions` the Reader uses, and the app-language dropdown through `AppLocales.shipped`. A language list written inside `SettingsScreen.kt` is a bug.

### Overlay UI — the Live bubble
`app/src/main/java/com/voxora/app/dub/FloatingBubbleService.kt` is the only system overlay UI in the app. It is a plain Android `View` hierarchy, not Compose, so it cannot use `MaterialTheme`; it must instead mirror the same brand palette as `ui/theme/Theme.kt` (gold `#D4AF37`, live green `#3DDC84`, error `#E85D5D`, `surfaceContainerHigh` fill `#1C1C1F`, a thin outline, and a gold→green waveform gradient matching the Live Dub waveform).

**Visual-only changes here.** The bubble's behaviour — drag, tap-to-toggle-Stop, tap-Stop-to-stop, double-tap-to-open, the 50 ms amplitude poll, and the window params — is production behaviour and must stay byte-identical. Never change the audio/session pipeline to restyle the bubble.

### API usage — never invent a number

`ApiUsageScreen` (`app/src/main/java/com/voxora/app/ui/ApiUsageScreen.kt`) answers "what is my Gemini access doing", and its defining rule is that **a figure is shown only when it was observed**. A missing figure is never rendered as `0`, because a zero is a claim and "unknown" is the truth.

- **Two usage sources must never be conflated.** *Local observed usage* is what Voxora counted from the requests it made on this device (`ApiUsageSnapshot`), and it can never answer "what is my project quota doing". *Google project usage* is what Google officially reports for the selected authorized project (`CloudProjectUsage`, `CloudUsageSource.GOOGLE_PROJECT`). They are shown in separate sections and neither is labelled as the other.
- **An API key still cannot read project quota or billing**, and the key path must not pretend otherwise: `ApiUsageSnapshot.projectQuota`/`.billing` stay `UsageUnavailable.AUTH_REQUIRED`, and `accountLinkedToKey` stays hard-coded `false` because nothing can verify that the key belongs to the signed-in account. Do not add a Cloud call behind an API key — it cannot succeed.
- **With a Cloud grant, the project figures are read for real, and each gap still names its reason.** `CloudProjectUsage` reports request count and quota limit only when Google returned them; tokens, remaining quota and billing are `NOT_OFFERED` because no official API in this path exposes them, and a refusal or network problem propagates as `PERMISSION_DENIED`/`NETWORK_ERROR`. Never estimate, never substitute a local count, and never render an unavailable figure as `0`.
- **The key check uses only official, non-generative surface.** `GeminiKeyProbe` calls the documented `models.list` endpoint (`https://generativelanguage.googleapis.com/v1beta/models`), which accepts an API key and generates no content, so it consumes no tokens. Never point a key test at a generative method.
- **The key travels in the `x-goog-api-key` header, not a `?key=` query parameter.** A URL is the part of a request most likely to reach a log, a crash report or a proxy trace, so the secret must not be in one. Never log the probe URL or any request-specific URL.
- **Classification prefers the body's `error.status` over the HTTP code**, because the code alone is ambiguous: Google returns `400` for both a malformed request and an invalid key, and `403` for both a blocked API and a permission problem. An unparseable body falls back to the code; an unrecognisable answer is `UNKNOWN`, never a confident guess. `GeminiKeyClassifier` is pure JVM and every branch is unit-tested against a synthetic response.
- **A blank, placeholder or absent key short-circuits to `CONFIGURATION_INCOMPLETE` without any network call**, so an unconfigured install cannot generate traffic or a misleading error.
- **`usageMetadata` is optional and must be treated as such.** The Live API reference says server messages *may* carry it. `GeminiUsageMetadata.fromMessage` reads it from the top level of the message — before the `serverContent` early-return in `handleMessage`, because a usage-only message would otherwise be dropped — and returns `null` when nothing recognisable is present. A field the server did not send stays `null`; only an explicit numeric value (including an explicit `0`) is kept. `optInt` alone is wrong here because it cannot tell "the server said zero" from "the server said nothing".
- **Both wire spellings of the output count are accepted:** Gemini Live sends `responseTokenCount`, Vertex Live sends `candidatesTokenCount`. Do not narrow this to one, and do not add a backend flag.
- **`GeminiReaderSession.onUsage` is a hook, and the narration path never reads usage back.** Recording is purely observational; it cannot influence audio, ordering or timing. Do not make the pipeline depend on it, and do not record usage inside `synchronized` blocks — the recorder suspends.
- **The ledger stores counts, reported tokens and short categories, and nothing else.** No key, no ID token, no prompt, no document text, no audio, no raw response. It is bounded to `MAX_DAYS = 90`, batched to one disk write per `PERSIST_EVERY = 10` records with a forced `flush()` at each run boundary, and a damaged, truncated or future-versioned payload degrades to an empty ledger rather than throwing — losing usage history must never block Settings. One bad row is skipped, not the whole file.
- **A failed request records only a category, never the message.** An HTTP client can put the key-bearing request URL into an exception message, so `UsageFailureCategory` maps to a short identifier and `ReaderController.usageCategoryFor` prefers the `NarrationFailure` string resource over the exception. Audio and session categories are tested **before** the generic network words, because real messages like "Audio output is unavailable" contain "unavailable" and would otherwise be misrecorded as a connectivity problem.
- **Usage reported by a failed turn is discarded.** A turn that produced no audio did not produce usable output; counting its tokens would overstate usage.
- `ApiKeyMask.mask` reveals only the first and last four characters and always uses a fixed bullet run, so the mask does not leak the key's length either. A key too short to mask safely is replaced entirely, and the shipped placeholder masks to an empty string so it can never be rendered as though a key were configured.
- **Tones are semantic, not alarming.** `UsageStatusVisual` maps each state to `OK` / `WARNING` / `ERROR` / `NEUTRAL` onto `VoxoraColors`. An unconfigured key, a source needing credentials, and a figure the API does not expose are all **expected** gaps and stay neutral; only a refusal or a network problem is coloured. Do not turn "we cannot read this" into a red error.
- The usage screen is its own destination reached from Settings, using the existing `VoxoraScreen` enum — do not add a second navigation framework for it.

### Google account — the Gemini product model, not a Cloud Console

The account system is **Google authorization**, presented in the product's own language: **sign in
with Google → the Google account → Gemini / Google AI Studio access → the key → usage**. The
official Cloud APIs underneath are unchanged, but the surface must not read like the Google Cloud
Console. An ID token identifies the user but authorises no read, so it could never discover a
project or a key; it is no longer the Settings entry point, and the Credential Manager identity
layer was removed with it. Settings leads with the account card and keeps the manual key directly
under it, because a manual key is a fallback for the same job, not a different feature.

- **The project level is named for what it is to the user.** A Gemini API key belongs to a Google
  Cloud project, so the card says "Gemini project" and explains that relationship once, in help
  text; it never presents a Cloud Console, and its failure and not-configured copy talks about
  signing in with Google rather than about Cloud access or OAuth client ids.
- **Rate limits are per project, not per key — say so, and link out.** The documented rule is that
  limits apply to the project, so a second key cannot buy a second quota and a real limit can only
  be read in AI Studio. The key card states the rule and the usage screen links to Google's
  rate-limit page; Voxora never estimates a limit and never renders one as `0`. Key creation is
  likewise AI Studio's job — `list`/`get` return no secret, so the app links out rather than
  implying it can recover a key.
- **`GoogleCloudAuthorizer`** requests an OAuth access token through Google Identity Services
  (`Identity.getAuthorizationClient(activity).authorize(request)`), asking for exactly
  `CloudScopes.ALL` — `cloud-platform.read-only`, `monitoring.read` and `userinfo.email`, all
  read-only. It never asks for write access and never invents a scope.
- **The account email comes from the OpenID Connect userinfo endpoint, not from the grant.**
  `AuthorizationResult` exposes no account (and `toGoogleSignInAccount()` is deprecated), so
  `GoogleCloudDirectory.accountEmail` reads `v1/userinfo` with the same access token. This is why
  `userinfo.email` is requested: without it the app could not say *which* account is connected, which
  is the top level of the hierarchy. An email Google does not return is passed through as `null`, and
  `withAccount(null)` conservatively clears the previous account's project and key.
- **Consent is a two-step flow.** When `AuthorizationResult.hasResolution()` is true the caller must
  launch the returned `PendingIntent` and then call `requestAuthorization` again; that second call is
  what yields the access token. `CloudAccountCard` owns that loop.
- **The access token is never UI state.** `CloudAuthState` deliberately has no token field, and
  `CloudRepository` holds the token in memory only — never DataStore, never a log, never a saved-state
  bundle. It is dropped on sign-out, on account switch, and whenever Google answers `401`
  (`CloudAuthFailure.EXPIRED`).
- **Configuration is not failure.** While `default_web_client_id` holds the shipped `REPLACE_…`
  placeholder, `GoogleCloudAuthorizer.configured` is false and `requestAuthorization` reports
  `CONFIGURATION_MISSING` **without touching the authorization provider**, so an unconfigured build
  says the real thing and manual API-key use keeps working.
- **`CloudSelection` owns the invalidation rules, and they are unit-tested.** `withAccount` drops the
  previous account's projects, selection, keys and active key, so account B seeing account A's project
  is unrepresentable rather than merely unlikely. `withProjects`/`withKeys` drop a selection that
  disappeared from a refresh; `selectProject` clears keys because keys are project-scoped.
- **`keyLinkedToAccount` is the only thing the UI may use to imply a link**, and it is true only when
  the key came from discovery *and* a project *and* an account are present. A manually pasted key can
  never satisfy it, so the app cannot claim a relationship it cannot verify.
- **Every failure has its own reason** — configuration missing, cancelled, no account, provider
  unavailable, network, permission denied, expired, unsupported, unknown — because the user's next
  action differs. `CloudAuthFailureClassifier` classifies by type name, message and the Google status
  code so it stays testable on a plain JVM.
- **The UI disables its action while an operation is in flight and ignores a second tap**, so two
  authorization requests cannot race their results onto the same card.
- **Manual API-key mode remains a first-class fallback** and is unaffected by sign-out: the key is not
  a Google credential and stays where the user put it. The card labels the mode and says the key is
  not linked to the signed-in account.

### Google Cloud discovery — official APIs only

`core/.../cloud/` is pure JVM and talks to documented Google Cloud REST endpoints through
`GoogleCloudHttpDirectory`. Nothing is fabricated, and an unavailable answer is never a zero.

- **Projects** come from Resource Manager v1 `projects.list`, which returns only what the account may
  see. Voxora never assumes the AI Studio project is the only one, and never invents one.
- **Keys** come from API Keys v1 `projects.locations.keys.list` — **metadata only**. `list` and `get`
  do not return the key secret, so `CloudApiKey` has no field for one and the UI shows a name, never a
  value. `keys.getKeyString` is deliberately not called.
- **Usage** comes from Cloud Monitoring v3 `timeSeries` for
  `serviceruntime.googleapis.com/api/request_count`, with Cloud Quotas v1 `quotaInfos` read as a
  second, independent call so a quota refusal cannot discard a real request count.
- **401 and 403 stay distinct.** `401` means the grant is gone and re-authorization is required;
  `403` means the account is fine but not allowed to read that project. Both are `CloudResult`
  variants, not exceptions, and `CloudLoadState` keeps empty, loading, denied, network and failure
  apart so the screen can say which one it is.
- **The token travels in the `Authorization: Bearer` header, never a URL**, and no request URL,
  response body or credential is ever logged. `CloudEndpoints` is injectable so the whole HTTP
  contract is driven against `MockWebServer` without reaching Google.
- **Parsers never turn "unreadable" into a number.** A malformed body is `null`; only a genuinely
  empty Monitoring window is `0`.

### Onboarding — both products, honestly described

`OnboardingScreen` is four pages: Live Dub, Voxora Reader, what the app can and cannot access, and the Gemini key. Voxora is two products behind one entry point, so the flow must name both — the earlier three-page flow described only Live Dub and never mentioned the Reader.

- The privacy page states that the key, documents and audio go only to Google's Gemini API because Voxora has no server of its own, and that screen capture belongs to Live Dub alone. Do not claim the app uses the camera, and do not imply the Reader captures the screen.
- The key page says one key powers both features, that it is stored on the device, and that Google sign-in is optional. **Do not gate the app behind an account**, and do not require sign-in for a feature that does not need it.
- Typography tokens only — no hardcoded `sp`.

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

### Google Play Services (added with explicit owner approval)
- `com.google.android.gms:play-services-auth:21.2.0` — Google Identity Services **Authorization**
  (`Identity.getAuthorizationClient(...).authorize(...)`). Credential Manager and `googleid` can only
  return an ID token, which authorises no Cloud API; the Cloud scope grant needs this. Do not replace
  it with Credential Manager, and do not use the ID token as if it were an access token.
- `com.squareup.okhttp3:mockwebserver:4.12.0` — **test only**, drives the Cloud HTTP contract locally.

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

- Never log a credential: no API key, ID token, OAuth access token, `Authorization` header, or any URL that carries a key in its query string. `GoogleCloudAuthorizer` and `CloudRepository` log only a classification and an exception's class name — never a token, never a message verbatim, and never a Cloud request URL. `GoogleCloudHttpDirectory` does not log at all.
- The remaining direct `android.util.Log` calls are inside `dub/**`, which is protected — do not "tidy" them without an explicit request.

### The Logs viewer
- The viewer's chrome is product UI; the **log lines are not**. Keep the exact `formatted()` output, the monospace font and the explicit LTR list region — a timestamp and a bracketed tag are technical identifiers, not prose, and mirroring them under a Persian UI would make the list unreadable.
- **Severity colours are semantic, not literal.** `LogSeverity` maps a severity to a role and `LogsScreen` only asks for the role: INFO → `VoxoraColors.success`, WARN → `VoxoraColors.warning`, ERROR → `VoxoraColors.danger`, DEBUG → `onSurfaceVariant`. An unrecognised severity is neutral and must never be dressed up as success. Do not hardcode a hex here.
- **Each entry is its own `SelectionContainer`**, so a long press selects inside that line and Copy takes exactly that entry rather than the whole buffer. Keep the header's Copy-all, Share and Clear as well — sending everything is a different job from sending one line. `LogLineFormat` is the single rule behind all of them, so a single-entry copy and the Copy-all payload can never drift apart; never re-implement the line shape at a call site.

---

## 10. STRINGS & i18n

- Every user-visible string goes in `res/values/strings.xml`
- English is default — add Persian (`values-fa`) translations for every new string
- Format: `snake_case` key names, e.g. `reader_start_button`, `reader_chunk_progress`
- Never hardcode strings in Kotlin/Compose files
- **Persian is written, not translated.** The Persian UI uses one register — the informal second
  person (`انتخاب کن`, not `انتخاب کنید`). Mixing registers is the clearest "this was translated"
  tell, so do not introduce formal-plural imperatives into a new string.
- **Product and technical names stay Latin:** Gemini, Google, Google Cloud, Google AI Studio, API,
  PDF, TXT, OCR, URL, Reader, Live Dub, Voxora. Transliterating Gemini to `جمینا` was a defect, not a
  style choice.
- **Terminology is context-dependent, and two levels never share a word.** In the Reader, `document`
  is `فایل` (and `کتاب` when the source really is a book) — never `سند`, which in ordinary Persian
  means a deed. `chunk` is `بخش` and a narration `segment` is `قطعه`. `narration` is `روایت`,
  `usage` is `مصرف`, `quota` is `سهمیه`, `billing` is `صورت‌حساب`, `logs` is `لاگ‌ها`.
- **Do not give two different states the same Persian label.** `NOT_OFFERED` is `API این را گزارش
  نمی‌کند`, `UNSUPPORTED` is `پاسخ نامفهوم`, `UNKNOWN` is `نامشخص`.
- Numbers stay Western Arabic digits, matching what `%d` renders everywhere else.
- **RTL is a layout property, not a text hack.** Fix the real cause (a fixed width, a wrong
  `TextAlign`, a forced LTR, bad spacing, an over-tight single-line constraint) and use
  `LocalLayoutDirection` / auto-mirrored icons. Never insert invisible Unicode direction marks to
  paper over a layout bug, and never concatenate a literal arrow glyph into a label.
- Technical identifiers (project ids, emails, keys, log lines) stay selectable LTR runs; put a whole
  technical region in an explicit LTR container only when the region itself is technical, as the log
  list does.

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
8. **JVM unit tests and `org.json`**: Android unit tests run against the mockable `android.jar`, whose `org.json` methods throw "not mocked". Any `:core` test that touches `JSONObject`/`JSONArray` needs `testImplementation("org.json:json:…")`; the real implementation takes classpath precedence over the stub. Pure-JVM code (`GeminiReaderSession`, `ReaderLanguages`, `ReaderNarrationModes`, `ReaderLanguageFlags`, `ChunkQueue`, `ReaderSpool`, `PdfReadingOrder`, `ReaderDisplayText`, `ReaderGates`, `ReaderState`/`ReaderPhase`, `AppLocales`, `ReaderStatusVisual`, `ReaderPager`, `ReaderPageText`, `CloudAuthState`, `CloudSelection`, `CloudUsage`, `CloudLoadState`, `CloudResult`, `CloudProject`, `CloudApiKey`, `CloudScopes`, `GoogleCloudDirectory`/`CloudParsers`, `CloudAuthFailureClassifier`, `UsageStatusVisual`, and the whole `core/.../usage/` package) must stay free of `android.*` APIs so it stays unit-testable without Robolectric. `PdfReadingOrder` deliberately takes plain geometry (`Fragment`) rather than `TextPosition` for exactly this reason — `TextExtractor` is the only place that bridges the two. `ReaderPhase`/`ReaderState` live in their own file precisely so `ReaderGates` can be tested on a plain JVM; do not move them back into `ReaderService.kt`.
9. **Gradle enables JVM assertions**: the `Test` task runs its JVM with `-ea`, which turns on kotlinx-coroutines stack-trace recovery. Any exception crossing a `Deferred.await()` or `withTimeout` boundary comes back as a *copy* of the original object, so `assertSame` against an awaited cause passes under a plain `java -cp` run but fails under Gradle. When validating `:core` contract tests outside Gradle, always run with `-ea` to match CI, and never rely on awaited exception identity without preserving the original object first. The Reader sink path is guarded by `GeminiReaderSessionTest.sinkFailurePreservesOriginalExceptionAndSanitizesStatus`; keep it green.

---

## 14. TEST & VALIDATION FOUNDATION

- Unit tests live in `core/src/test/` (Gemini session, narration-mode and language-flag contracts) and `app/src/test/` (chunking, spooling, PDF reading order).
- Reader contract tests and what they pin:
  - `GeminiReaderSessionTest` — sink-failure exception identity and session status sanitization.
  - `ReaderNarrationModesTest` — the Faithful/Fluent prompt text contains the contractual semantics (Faithful's priority order, "Faithful is not a weaker Fluent", the forbidden paraphrase/summarize lists, the language substitution); uses a whitespace-normalizing helper because `trimIndent()` keeps source line breaks inside phrases.
  - `ReaderLanguageFlagsTest` — representative flags, `pt-BR`/`pt-PT` and `zh-Hans`/`zh-Hant` distinctness, the neutral set, regional-indicator validation, and that every catalog language is mapped deliberately.
  - `PdfReadingOrderTest` — reading-order repair, multi-column de-interleaving, and the running-header/footer regression that erased the page gutter (see §5).
  - `ReaderPipelineOrderTest` — the post-extraction ordering contract on the real `ChunkQueue`: chunk and segment reconstruction, cross-chunk bleed, cache isolation, chunk transition, and that the Faithful/Fluent instruction is identical for every chunk and segment (see §5).
  - `ReaderDisplayLanguageTest` — the display-language contract on `ReaderDisplayText` and above the real `ChunkQueue`: language/chunk/segment scoping, no cross-language reuse, no stale text after a language change, blank rejection, trimming, replace-not-duplicate, clear, length/order/boundary preservation, every chunk obeying the same contract, no inheritance across documents, and an unchanged canonical source (see §5).
  - `ReaderDisplayRefreshTest` — when a stored rendering becomes visible state: immediate republish for the displayed chunk and language, no republish for a prefetched chunk or an abandoned language, no refresh on a rejected rendering, per-unit appearance with the chunk's shape preserved, language switching, and an unchanged canonical source (see §5).
  - `ReaderStartupGatesTest` — the extraction/restoration gating rules in `ReaderGates`: settings and the file picker stay available during extraction, playback never starts before the queue is ready, navigation needs a document, no phase is fully locked, and configuration freezes only while narrating (see §5).
  - `LanguageCatalogTest` (app) — the shared option builder: full-catalog coverage without duplicates, labels/flags taken from the catalog, deterministic order, search that filters without reordering, endonym/secondary-label rules, the app-locale subset, and unknown-code normalization.
  - `AppLocalesTest` (core) — the shipped UI locale set: uniqueness, membership in the one catalog, no silently dropped locale, display name and flag present, and that it stays a strict subset of the Gemini output catalog.
  - `ReaderChunkStartLanguageTest` (app) — the language-at-chunk-start contract: arriving at a chunk shows its cached selected-language text without waiting or re-rendering, a chunk with no rendering starts in the selected language and is reported as preparing, the pending set shrinks unit by unit, a late transcript updates the current chunk, a transcript from an abandoned generation or revision cannot reach the page, prefetch never moves the page ahead, switching language never leaves the other language's text on screen, switching back restores the cached rendering, and the canonical chunk is untouched (see §5).
  - `ReaderDisplayAudioContinuityTest` (app) — display updates never disturb audio, driven against the **real** `ReaderSpool`: the spool snapshot is unchanged by recording or switching language, consuming renders nothing, publishing between units does not change the bytes the consumer writes, units are still read once and in order, the promoted chunk is already rendered and already committed before the turn, the turn adds no work to the audio path, and display state is never written back into the document (see §5).
  - `ReaderStatusVisualTest` (app) — the status-tone contract: speaking/playing is active, only the active tone pulses, paused is ready and **not** green, stopped/error are stopped, connecting/extracting/preparing are neutral and never falsely green, every phase has a tone, and the tones stay distinct (see §3).
  - `ReaderPagerTest` (app) — the page model: single-step bounded navigation, nothing to turn without a document, exactly one chunk is ever the page, walking either direction visits every chunk once and stops, a short drag is not a turn, LTR and RTL gestures map to the same logical turn, a Persian reader never gets reversed chunk order, and the arriving page comes from the side it was turned towards (see §5).
  - `ReaderSegmentNavigationTest` (app) — the swipe contract and its separation from chunk navigation: a swipe moves exactly one segment, reports the direction it moved, a short swipe is not a step, the first and last unit of a chunk do nothing, a swipe is bounded by the chunk and not the document, no gesture or repetition can produce an index outside the chunk, walking either direction visits every unit once and stops, LTR and RTL map to the same logical segment order, the leaving unit exits opposite the entering side, and an out-of-range segment position has no index (see §5).
  - `ReaderInitialPlaybackTest` (app) — the gate in front of a run's first audible frame: a unit with no rendering must not play, it plays once its rendering exists, a blank rendering is not a rendering, a finished unit with no rendering fails instead of playing, a rendering wins over the finished flag, every input resolves to exactly one outcome, and — the load-bearing case — **the source fallback is not mistaken for a selected-language rendering**, because the page's reading text falls back to the extracted source while `ReaderDisplayText.text` stays null. Also pins that a rendering for another language, for another unit, or a run starting mid-chunk is gated on the unit it actually starts on, and that a unit rendered by an earlier run is ready immediately (see §5).
  - `ApiKeyMaskTest` (core) — the mask reveals only the first and last four characters, never contains the key's body, does not vary in length with the key, fully hides a key too short to mask safely, and reports the shipped placeholder as not configured rather than masking it (see §5A).
  - `GeminiUsageMetadataTest` (core) — both output-count spellings, the snake_case variant, an absent `usageMetadata`, an unrecognised object, an absent field staying unknown rather than becoming 0, an explicit 0 being kept, an explicit JSON null treated as absent, numeric strings accepted, non-numeric values ignored, and the cached/thoughts fields (see §5A).
  - `GeminiKeyClassifierTest` (core) — every status code, the body reason winning over an ambiguous code, `200`-range success, server errors, network and timeout failures, an unreadable answer staying `UNKNOWN`, a malformed body falling back to the code, and the model count being read only from a single complete page (see §5A).
  - `GeminiKeyProbeTest` (core) — success with a model count, a trimmed key, invalid/unauthorized/quota/network classification, a throwing transport not escaping, partial data staying unknown, and **no request at all** for a blank, absent or placeholder key. A fake transport stands in for the network, so nothing asserts a real quota or model count (see §5A).
  - `GeminiUsageLedgerTest` (core) — per-UTC-day grouping, month summation, a request with no reported usage still counting as a request, partial usage adding only the fields it carried, failures tracked separately from successes, the 90-day retention bound, JSON round-trip, empty and damaged and future-versioned payloads degrading to empty, one bad row not discarding the rest, negative counts clamped, `clear`, snapshot independence, and that the stored payload contains only the expected keys (see §5A).
  - `UsageRecorderTest` (core) — usage committed only by a successful turn, discarded by a failure, not carried across requests, lazy load picking up stored history, batching to one write per ten records with an exact in-memory view, `flush`, a throwing store not interrupting the caller, a new snapshot per record, `clear`, and isolation between two recorders (see §5A).
  - `ApiUsageSnapshotTest` (core) — a number is shown only when observed; unconfigured and unknown are distinct; tokens are "not offered" when the server never reported them; coverage is stated; project quota and billing are always auth-required and never invented; signing in never asserts the key/account link; a probe with no count stays unknown; the displayed key is always masked; and activity timestamps pass through (see §5A).
  - `UsageFailureCategoryTest` (core) — the timeout/quota/unauthorized/audio/session/network mapping, a real Reader audio message not being mistaken for a network problem, an unrecognised failure staying unknown, and the categories being unique, space-free identifiers (see §5A).
  - `UsageStatusVisualTest` (app) — only a healthy connection is active and only it pulses; refused or broken is an error; transient problems warn; an unconfigured build and an unchecked key are neutral; expected data gaps stay neutral while a refusal is an error and a network gap warns; every status and reason has a tone (see §5A).
  - `CloudSelectionTest` (core) — the account → project → key model: switching account drops the previous projects, selection, keys and active key; account B can never see account A's project; re-authorizing the same account keeps the selection; sign-out clears everything; a project or key that disappeared from a refresh is dropped; a key that was never listed cannot be selected; manual mode is available and never claims a link; signing in alone does not link a manual key (see §5A).
  - `CloudAuthStateTest` (core) — the token-expiry policy (no stated expiry, well in the future, expired, inside the skew window, overridden skew); only `Authorized` carries an account; only an in-flight authorization is busy; only a real grant counts as authorized; `Authorized` carries no token field; configuration missing is distinct from every other failure; and the scope set is read-only, unique and never contains a write scope (see §5A).
  - `CloudParsersTest` (core) — project and key parsing (dropped when unaddressable, name falling back to the id), the userinfo `email` claim (present, absent, blank and malformed all handled without producing a blank account), Monitoring request-count summation across points and both value spellings, a genuinely empty window being `0`, and a malformed body being `null` rather than `0`; quota limit selection and its absent cases (see §5A).
  - `CloudUsageTest` (core) — every figure needs authorization before a grant; a refusal propagates to every figure; **an unavailable metric is never equal to a zero**; an observed count is reported while tokens, remaining quota and billing stay `NOT_OFFERED`; an explicit zero is a real zero; project usage is always sourced from Google, never from local observation (see §5A).
  - `GoogleCloudHttpTest` (core) — the authorized reads against `MockWebServer`: the token in the `Authorization` header and **never in the URL**; `401` as re-authorization, `403` as no access; a `5xx` and an unparseable `200` as failures rather than empty lists; an unreachable host as a network problem; the userinfo read behind the account email; key metadata from the project keys endpoint; the two-call usage read; a quota refusal not discarding a real request count; and a refused monitoring read refusing the whole usage read (see §5A).
  - `CloudLoadStateTest` (core) — the presentation mapping: empty ≠ loaded, and denied, unauthorized, network and failure each stay distinct (see §5A).
  - `CloudAuthFailureClassifierTest` (app) — cancellation, network, no-account, provider-unavailable, permission-denied, unsupported and unknown classification by status code, type name and message (see §5A).
  - `LogSeverityTest` (app) — the severity → role mapping behind the Logs colours: INFO → success, WARN → warning, ERROR → danger, DEBUG → neutral, every severity the product emits has a role, DEBUG is the only neutral one, an unrecognised severity is neutral and **never** success, and the lookup is case-insensitive (see §9).
  - `LogLineFormatTest` (app) — the one line shape every copy path shares: timestamp then level then `[tag]` then message, millisecond precision, every level padded to the same width so the tag starts in one column, the message appended verbatim (a log line is evidence), and empty fields still producing the shape. Pins the default `TimeZone` to UTC and restores it, so the expected string does not depend on where the suite runs (see §9).
  - `ChunkQueueTest`, `ReaderSpoolTest` — chunking and spool contracts.
- **The background-playback surfaces add no new pure-JVM rule, deliberately.** The notification's previous/next and the bubble's stop call the same `ReaderController.jumpToSegment` / `stop` the screen already uses, and their bounds are the ones `ReaderSegmentNavigationTest` pins; the bubble preference is a plain boolean with a DataStore default. Adding a second step rule (and a second test) would be duplication, so the honest statement is that these surfaces are compile-checked by CI and device-verification items, not that they carry a new unit contract.
- Do not weaken or delete these tests to make a change pass. If a contract genuinely changes, update the contract text in `AGENTS.md` and the test in the same commit.
- A regression test is only worth having if it fails against the bug. The four header/footer/line-floor cases in `PdfReadingOrderTest` were verified to fail against the pre-fix implementation at `707cc3c` and pass against the fix; keep that property when editing them.
- `:core` owns its own test dependencies (`junit`, `org.json`) in `core/build.gradle.kts` — do not assume the `:app` test classpath applies to a library module.
- GitHub Actions runs `:core:testDebugUnitTest` and `:app:testDebugUnitTest` in a `unit-tests` job that is independent of the APK job, so a contract regression is visible without withholding the debug artifact.
- `android-ci.yml` triggers on pushes to `main` **and** `feat/reader-segmented-spooling`, on PRs targeting `main`, and on manual dispatch. Work that must be CI-verified has to land on one of those refs.
- Gradle runs its test JVM with `-ea`. Reproduce that flag when running tests by hand outside Gradle (see §13.9), otherwise stack-trace-recovery differences will hide real failures.
- The sink-failure exception contract is CI-verified: `GeminiReaderSessionTest.sinkFailurePreservesOriginalExceptionAndSanitizesStatus` passed on `feat/reader-segmented-spooling` at `4e4e17a` (Android CI run #63, `unit-tests` job success, 2026-09-17). The workflow has no `continue-on-error`, so a green `unit-tests` job is a genuine pass.
- The Reader quality pass is CI-verified at `35120bf` (Android CI run #65, run id `35287036680`, 2026-09-17): `Unit tests` success (including the `Run unit tests` step) and `Assemble debug APK` success. The APK job is the only real compile check for the redesigned Compose UI, since the dev server has no Compose artifacts and must never run Gradle.
- The Settings/overlay redesign (`a9b9336`) and the display-language sync (`7f47805`) are CI-verified at `7f478057b412c0ec3c7e65b3ff99f25565b09a09` (Android CI run `35294468431`, event `push`, branch `feat/reader-segmented-spooling`, created 2026-09-18T01:13:51Z): `Unit tests` success (including `Run unit tests`) and `Assemble debug APK` success (including `Assemble debug` and `Upload debug APK`). That APK job is the real compile check for `SettingsScreen.kt`, `ReaderScreen.kt` and `FloatingBubbleService.kt`, which the dev server cannot compile.
- The display-refresh fix (`1c0df1e`), the extraction gates (`882ab27`) and the one-catalog refactor (`1504669`) are CI-verified at `1504669bce7f2f563de7238e7d9ac1af1e170b21` (Android CI run `35297963994`, run **#70**, event `push`, branch `feat/reader-segmented-spooling`, created 2026-09-18T02:05:29Z): `Unit tests` success (including `Run unit tests`) and `Assemble debug APK` success (including `Assemble debug` and `Upload debug APK`). The `Assemble debug` step is the real compile check for the `ReaderGates` wiring in `ReaderScreen.kt`/`ReaderViewModel.kt` and for the catalog-backed pickers in `SettingsScreen.kt`. The documentation commit `14bd894` on top is CI-verified at run `35298147134` (run **#71**, 2026-09-18T02:13Z), both jobs success.
- The page/status pass (`7d9a51b`), the display-locale fix (`1b9eb4f`) and the chunk-start language fix (`e88f85a`) are CI-verified at `e88f85acf97b84461b674d39709718dadfd6d080` (Android CI run `35301607886`, run **#74**, event `push`, branch `feat/reader-segmented-spooling`, created 2026-09-18T03:01:25Z, completed 03:03:21Z): **both jobs success** — `Unit tests` 10/10 steps including `Run unit tests`, and `Assemble debug APK` 14/14 steps including `Assemble debug` and `Upload debug APK`. The `Assemble debug` step is the only real compile check for `ChunkPage`/`ReaderPager`/`ReaderStatusVisual` usage in `ReaderScreen.kt`, the `VoxoraColors` composition local in `Theme.kt`, and the retokenised status dot in `DubScreen.kt`. **No real-device testing was performed for this change**; the page-turn feel, the active halo and the RTL gesture direction remain device-verification items.
- The usage/auth/onboarding slice (`982786b`, `4d9608d`, `db831d2`) is CI-verified at `4176883` — Android CI run `35304322802` (run **#82**, event `push`, created 2026-09-18T03:4xZ): `Unit tests` 10/10 steps including `Run unit tests`, `Assemble debug APK` 14/14 steps including `Assemble debug` and `Upload debug APK`. The same commit's `pull_request` run `35304324635` (run **#83**) is green on both jobs as well.
- **The slice's first push FAILED, and it is worth recording why.** Run `35304003121` (run **#80**, `db831d24`) failed both jobs on a **single missing import** — `androidx.compose.runtime.LaunchedEffect` in `ApiUsageScreen.kt` — which surfaced as four errors (the unresolved reference plus three cascading "suspend function should be called only from a coroutine"). The fix is `4176883`. The lesson is in §14: the local harness cannot compile Compose, so run the import guard before pushing. **No real-device testing was performed for this change** either; the key check, the usage screen and the onboarding flow are all device-verification items.
- The Reader segment swipe, the Persian localization pass, the Logs modernization and the Google Cloud authorization/discovery layer are CI-verified at `9dfc204` (Android CI run `35340531929`, event `push`, branch `feat/reader-segmented-spooling`, created 2026-09-18T11:37:17Z): **both jobs success** — `Unit tests` 10/10 steps including `Run unit tests`, and `Assemble debug APK` 14/14 steps including `Assemble debug` and `Upload debug APK`. The same commit's `pull_request` run `35340536468` is green on both jobs too. `Assemble debug` is the only real compile check for the Compose screens in this change (`CloudAccountCard.kt`, `SettingsScreen.kt`, `ApiUsageScreen.kt`, `LogsScreen.kt`, `DubScreen.kt`), which the dev server cannot compile.
- **This slice needed three CI rounds, and each failure was a real compile error the local harness could not see.** (1) `7c12b7d` and `2d953fa` had never been pushed, so the Logs screen's missing `LocalLayoutDirection` import and the authorizer's non-existent `AuthorizationResult.account` were first seen by CI; the import guard was widened to also match the `Symbol provides value` form, which is exactly the shape it had missed. (2) `setRequestedScopes` takes `List<Scope>`, not `List<String>` — an argument-type error no import check can catch. (3) The email now comes from the OpenID Connect userinfo endpoint, because `AuthorizationResult` exposes no account and its `toGoogleSignInAccount()` is deprecated. The general lesson: the import guard is necessary but not sufficient, and a branch that has not been pushed has not been compiled.
- **No real-device testing was performed for any part of this slice.** The segment swipe feel and RTL direction, the Persian layout under RTL, the Logs screen, and every Cloud screen (authorization, project and key lists, project usage) are all device-verification items, and Cloud authorization additionally needs the external OAuth client configuration described in §5A before it can run at all.
- The card-only turn (`d7ba68c`), the initial-playback gate (`ab726f7`) and the Logs severity/selection pass (`f4ee4f1`), with the documentation commit `82ddc11` on top, are CI-verified at `82ddc110e` (Android CI runs `35349503534` (run **#96**, event `push`) and `35349507139` (run **#97**, event `pull_request`), created 2026-09-18T13:19Z, completed 13:21Z): **both jobs success in both runs** — `Unit tests` including `Run unit tests`, and `Assemble debug APK` including `Assemble debug` and `Upload debug APK`. The `Assemble debug` step is the only real compile check for the `SegmentCard`-scoped `graphicsLayer`/`key()` change in `ReaderScreen.kt`, the `awaitInitialRendering` wiring in `ReaderController.kt`, and the `SelectionContainer`/`LogSeverity` usage in `LogsScreen.kt`; the dev server has no Compose artifacts and must never run Gradle. The push run's `Unit tests` job is the CI counterpart of the local **404 tests OK across 36 classes**. **No real-device testing was performed for this change either**; the card-only motion and its RTL direction, the first-frame wait, and per-entry Logs selection are all device-verification items.
- Local pre-CI validation without Gradle is allowed and encouraged: compile the changed pure-JVM/Android sources with `kotlinc` against the pinned dependency jars and run the JUnit classes directly with `-ea`. This never substitutes for CI — the branch must still go green in Actions. The `1504669` pass was validated locally this way: `kotlinc 2.0.21` plus JUnit `-ea` gave **134 tests OK** across `ReaderDisplayLanguageTest`, `ReaderDisplayRefreshTest`, `ReaderStartupGatesTest`, `LanguageCatalogTest`, `AppLocalesTest`, `ReaderPipelineOrderTest`, `ChunkQueueTest`, `PdfReadingOrderTest`, `ReaderSpoolTest`, `ReaderNarrationModesTest`, `ReaderLanguageFlagsTest` and `GeminiReaderSessionTest`. Two harness details matter and cost a round each when forgotten: `internal` declarations need `-Xfriend-paths=<main-out>` on the test compile, and `ReaderSpool`'s `VoxoraLog` dependency needs a plain-JVM stub because the real one touches `android.util.Log`.
- Prefer pure-JVM, deterministic tests with `TemporaryFolder` for file-backed code; avoid Robolectric unless an Android API genuinely cannot be avoided.
- **The local harness cannot compile Compose, so run an import guard before pushing.** A missing `import androidx.compose.runtime.LaunchedEffect` reached CI once and failed **both** jobs; because the dev server has no Compose artifacts, nothing local caught it. It also produced four errors for one mistake — the unresolved reference plus three cascading "suspend function should be called only from a coroutine", since without `LaunchedEffect` the lambda is not a suspend scope. Before pushing, check that no file uses a Compose or AndroidX symbol it has not imported. Read CI job logs with `GET /repos/…/actions/jobs/<job_id>/logs` (works, HTTP 200) rather than the run-level archive endpoint (403); the useful line is `e: file:///…/File.kt:97:5 Unresolved reference 'X'.`
- **Two local checks worth running on every change, both cheap and both caught real bugs:** a `R.string.*` cross-check of every Kotlin reference against `values/` and `values-fa/` (it caught a `reader_page_chunk` key that no locale declared), and an unused-import sweep of changed files.

---

*Last updated: auto-generated by Claude for Voxora project — the segment-card-only turn, the
initial-playback gate that waits for the selected-language text, the severity/selection pass over
the Logs viewer, the Gemini product model in Settings, and the Reader's floating bubble and
notification transport controls.*
