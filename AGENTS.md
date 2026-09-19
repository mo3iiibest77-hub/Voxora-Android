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
│       ├── dub/                     ← Live Dub feature (see §6 before changing anything)
│       │   ├── DubService.kt         ← service wiring; feeds the synchronizer
│       │   ├── DubPlayback.kt        ← output track, source ducking, volume-key session
│       │   ├── FloatingBubbleService.kt
│       │   ├── SystemAudioCapture.kt
│       │   ├── DelayedScreenOverlay.kt    ← DISABLED stub; must stay unconstructed
│       │   ├── SyncStatusVisual.kt        ← Pure-JVM sync tone mapping
│       │   └── sync/                      ← the synchronization layer
│       │       ├── MonotonicClock.kt      ← pure; the only clock sync may read
│       │       ├── SyncConfig.kt          ← pure; every bound (no fixed latency)
│       │       ├── SyncState.kt           ← pure; SyncState + SyncDecision
│       │       ├── ExternalPlayer.kt      ← pure; the seam that makes sync testable
│       │       ├── SourceVolumeDuck.kt    ← pure; duck/restore arithmetic
│       │       ├── LatencyTimeline.kt     ← pure; monotonic stage instrumentation
│       │       ├── PlaybackTimeline.kt    ← pure; the dub playhead and backlog policy
│       │       ├── PlaybackTimelineConfig.kt ← pure; the adaptive backlog tolerance
│       │       ├── PlaybackHead.kt        ← pure; unwraps AudioTrack's 32-bit head
│       │       ├── DubSyncController.kt   ← pure; the adaptive state machine
│       │       ├── MediaControlAccess.kt  ← Android; the permission question
│       │       ├── MediaSessionExternalPlayer.kt ← Android; only MediaSessionManager user
│       │       └── VoxoraNotificationListenerService.kt ← Android; empty opt-in listener
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

There are **two** complete appearances and one role map. Each appearance owns its own palette file — `app/src/main/java/com/voxora/app/ui/theme/OriginalDarkPalette.kt` and `LightTest2Palette.kt` — holding raw ARGB values (pure Kotlin, no Compose, so they are unit-tested). `app/src/main/java/com/voxora/app/ui/theme/Theme.kt` is the only place a value becomes a `Color`, and those three files are the **only** files allowed to contain colour literals. A static guard (`themecheck.py` in the local harness) fails the build if any other Kotlin file has one.

```kotlin
// ORIGINAL VOXORA DARK — the primary identity and the default. Verified against Git:
// 4228f1b "feat: Voxora dark gold Material3 theme", plus the container/status ramp from f25cc5b.
// The two semantic swaps of the redesign are marked below.
Gold      #D4AF37  primary, and the tint of every tonal surface
GoldDim   #B8962E  secondary (the muted gold); `tertiary` is derived from the same family
NearBlack #0A0A0B  background / surfaceContainerLowest
SurfaceDark #141416 surface      Card #1C1C1F surfaceVariant / surfaceContainerHigh
onSurface #C4BBA8  onSurfaceVariant #F5F0E6  explanation #3DDC84  neutral #8E8A7E
success #7DD3FC  warning #E6B422  danger/error #E85D5D
// The redesign swapped two pairs of values between roles, and nothing else:
//   status `#3DDC84` <-> explanation `#7DD3FC`   — the green and the icy blue exchanged hues;
//   onSurface `#F5F0E6` <-> onSurfaceVariant `#C4BBA8` — primary content and supporting text.
// So in this theme the status role is the icy blue and the explanation role is the green, and the
// **primary** content role (`onSurface`) is deliberately the *dimmer* of the two text roles. The
// roles are ordered by purpose, never by brightness: ask for the role you mean.
// `VoxoraBrand.waveGreen #3DDC97` is a decorative waveform stop, not the status role, and was not
// touched. Contrast is re-derived by OriginalDarkPaletteTest; every content/help role clears AA.

// VOXORA LIGHT (the LIGHT_TEST_2 slot) — warm cream + gold leaf; the final light appearance.
// Its own complete palette, NOT a lightened dark theme and NOT a variation on any other appearance.
background #F5F2EC  surface #EDE9DF  surfaceVariant(card) #E4DFD3  outlineVariant #D0C9BC
surfaceContainer #EAE6DC  surfaceContainerHigh #DEDAD0  surfaceContainerHighest #D5D0C4
outline #B8B0A0
primary(gold) #8B6914  secondary(dim gold) #A07820  primaryContainer #F0E4B8  onPrimaryContainer #5C4A10
secondaryContainer #E8DFC8  onSecondaryContainer #4A3E20
onSurface #1A1610  onSurfaceVariant #4A4438  explanation(info navy) #0369A1  neutral #6B6558
disabled #A09888
success #1A9E57  warning #B88A10  danger/error #C0392B  errorContainer #FAD7D7  onErrorContainer #7A1515
glow rgba(139,105,20,0.12)  shadow rgba(100,80,30,0.12)   // the light design language
// Supplied verbatim: nothing is darkened or "corrected" for contrast, and the gold stays exactly
// #8B6914 / #A07820. The measured shortfalls are documented in LightTest2Palette.kt and pinned by
// LightPaletteTest: explanation 4.46 and neutral 4.35 on the card (AA is 4.5), primary 3.83
// and secondary 3.61 as text on the cream surfaces. Never #FFFFFF as the page.

// Status roles are declared per palette, and the two appearances do NOT share values: Original Dark
// uses success #7DD3FC / warning #E6B422 / error #E85D5D, Voxora Light the values above.
// Roles Material 3 does not model (VoxoraColors.*): success / warning / danger / neutral /
// explanation / disabled / glow.
```

**Theme selection:**
- The user's choice is `core/.../prefs/ThemeMode.kt` — `ORIGINAL_DARK` (default), `LIGHT_TEST_2` — persisted through `UserPrefs`/DataStore (`themeMode`, stored as the enum's stable `id` so reordering the enum cannot change a choice). `MainActivity` collects it and passes it to `VoxoraTheme(mode = …)`; `SettingsScreen` exposes a `ThemeSelector` that writes it. Never hold the selection in a composable's own `remember`, and never read `isSystemInDarkTheme()` anywhere.
- **`ThemeMode.DEFAULT` is `ORIGINAL_DARK`, and that must not change.** The original Voxora dark theme is the product's primary identity and the baseline appearance; the light appearance may not become the default, and the Google-style palette must not come back. `ThemeMode.normalize` is total (unknown/blank → `DEFAULT`) and migrates the previous `system`/`dark`/`light` ids (`system`/`dark` → dark, `light` → the light appearance), so a corrupt or old preference can never leave the app themeless or silently drop a light user into dark. Pinned by `ThemeModeTest`.
- **The two appearances are independent by construction.** Each is a complete palette in its own file and they share no constant or mutable state. A change to the light appearance cannot move a dark value, and a change to the dark appearance cannot move a light one. Removing an appearance later means deleting its palette file, its scheme + semantics block in `Theme.kt`, its `ThemeMode` entry and its string. Never introduce a shared mutable palette or a base-plus-overrides hierarchy, and never build the light appearance by lightening the dark theme's tokens.
- **Dark is the original Voxora gold identity — not a Google or Nova look.** Gold is `primary`, `secondary` and the tint of the tonal surfaces; the text is warm; there is no cyan, indigo or purple. Voxora Light keeps the gold identity on the light side (a darker gold `#8B6914` and a dim gold `#A07820`) on warm cream surfaces.
- **Light is a real appearance, not an inverted dark one.** Every role is a chosen value with deliberate contrast. Voxora Light is warm parchment — never pure white — with near-black content `#1A1610`, a warm brown secondary `#4A4438`, a navy information tone `#0369A1`, and the darker gold carrying action. It is not "the dark theme made lighter".
- **Contrast is measured and recorded, not assumed.** A palette the owner supplies is measured against the surfaces it will sit on, and the measured ratios are recorded in the palette's KDoc and pinned by `OriginalDarkPaletteTest` / `LightPaletteTest`. When a supplied value falls below WCAG AA the owner's instruction is to keep it **verbatim** and document the shortfall — never silently darken the supplied identity colours. A supplied value is adjusted only when the owner explicitly asks for the correction, minimally and in the same hue family, and the adjustment is recorded. `Voxora Light`'s measured shortfalls are listed in §3 above and in `LightTest2Palette.kt`.

**Compose rules:**
- Always use `MaterialTheme.colorScheme.*` tokens — never hardcode hex in composables. The only accepted literal in a composable is `Color.Transparent` (a framework constant, not a brand colour).
- **The semantic roles are the contract.** `Theme.kt` exposes `VoxoraSemanticColors` through `VoxoraColors.success` / `.warning` / `.danger` / `.neutral` / `.explanation` / `.disabled` / `.glow` (a `staticCompositionLocalOf`, read via `@Composable @ReadOnlyComposable`). Ask for the role, never a number. The hierarchy is **screen title → section title → primary content (`onSurface`) → secondary content (`onSurfaceVariant`) → explanation (`VoxoraColors.explanation`) → status / action**. A screen must not invent a role or pick an alpha on `onSurface` to mean "less important".
- **`ReaderStatusVisual` decides which tone a phase gets**, and the composable only asks for it: **speaking and playing are green, paused is yellow and never green, stopped and failed are red, connecting and preparing are neutral and must not falsely show green.** The same pattern holds for `UsageStatusVisual` and `LogSeverity` — a pure-JVM mapper owns the decision so no composable picks a colour inline.
- **One explanation role.** Every help, hint, caption or "why this is unavailable" line uses `VoxoraColors.explanation`. Do not reach for `onSurfaceVariant` for one note and `outline` for the next; a single token is what makes the hierarchy consistent across Reader, Settings, usage and account surfaces. Secondary content (`onSurfaceVariant`) stays for supporting labels, values and subtitles — do not "unify" the two by repainting every secondary label, which would erase the distinction the role exists to make. **Each appearance states its own explanation colour and the role is not defined by being dimmer:** in Original Dark it is the green `#3DDC84` (the hue the status role used to carry — the two swapped in the redesign, and the role each colour *means* did not change); in Voxora Light it is the deeper navy-blue `#0369A1`, where it is the lighter of the two by luminance and so still reads as the quieter role. Never make it a brand gold or an accent, and never make it an action or a status colour — that is what made explanatory text read as a brand accent.
- **`neutral` is a status, not a failure.** Idle, connecting and an expected data gap are neutral; only a refusal or a network problem is `danger`. Never dress "we cannot show this" as an error.
- `VoxoraBrand` (`waveGold`, `waveGreen`) is decorative only — the Live bubble waveform. It is **not** a text or surface colour and must never be used for status.
- **Every text role clears WCAG AA where it holds, and the shortfalls are pinned where it does not.** `OriginalDarkPaletteTest` and `LightPaletteTest` compute the contrast ratios. Original Dark's content roles clear AA on its card and page; Voxora Light's content and help roles clear AA on the page and surface, and its measured shortfalls (explanation 4.46 / neutral 4.35 on the card; primary 3.83, secondary 3.61 as text on the cream surfaces) are asserted as documented facts rather than hidden or "fixed" (disabled content is exempt but must stay visible). Adding a colour means adding it to the reach of the test for its appearance.
- **The accent wash is a role, not an alpha on `primary`.** The gold wash behind an icon or a brand mark is `VoxoraColors.glow`, resolved per appearance — Original Dark `#D4AF37` @15 %, Voxora Light `#8B6914` @12 % (the supplied `rgba(139,105,20,0.12)`). Never write `primary.copy(alpha = …)` at a call site: that is exactly how a light screen would inherit the dark appearance's wash.
- **The light shadow language is warm brown, never black.** Any light-appearance shadow uses `LightTest2Palette.Shadow` (`rgba(100,80,30,0.12)`), not `rgba(0,0,0,…)`. Nothing draws a custom shadow today (the UI uses `tonalElevation`), so the value is a declared token; a future shadow must use it.
- An active-state pulse must modify alpha, glow or scale of the semantic colour. Never introduce a separate neon colour for animation, and never run an infinite animation for a phase that is not active.
- **Typography is the Material 3 default — do not bundle a font.** `Theme.kt` calls `MaterialTheme(colorScheme, content)` with **no** `typography` argument, so the app uses the stock Material 3 type scale and the **device's own font**, for Persian and every other language. Use `MaterialTheme.typography.*` — never hardcode `sp` sizes and never set a `fontFamily` at a call site; the only sanctioned exception is the log list's monospace, because a timestamp and a bracketed tag are code, not prose. There is deliberately **no** `ui/theme/Type.kt`, no `res/font/` directory and no bundled font: a bundled Persian-only cut was tried and rejected because it pushed every Latin product name through font fallback and changed the app's appearance on every device. Do not reintroduce one. See `AgentMD.md` for the standing rule.
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
- **The visible text is a function of the selected language *and* the selected narration style.** Faithful and Fluent are two independent selections over the same source, and the instruction Gemini receives differs, so a transcript produced for one is not a rendering for the other. `app/src/main/java/com/voxora/app/reader/ReaderDisplayModes.kt` is the layer that enforces this: it owns **one `ReaderDisplayText` per mode** (`ReaderNarrationModes.all`), and `ReaderController` reads and writes through `displayTexts.forMode(mode)`. The mode is the cache's identity, so "the other style's wording is on screen" is unrepresentable rather than merely unlikely. Do not collapse the modes back into one cache, and never let a composable choose which cache to read — it reads `ReaderState.narrationMode`, which is the mode the text was actually rendered with.
- **The display text is `selected language + selected narration mode + current chunk + current segment`.** `ReaderState` carries the two selections it was produced under (`outputLanguage`, `narrationMode`), and `publish` derives the reading text from those rather than from whatever the user last tapped. Never publish text for a mode or language other than the one recorded with it.
- `ReaderController.setNarrationMode(mode)` updates the mode and republishes, mirroring `setOutputLanguage`; `ReaderViewModel` calls it on init and from `setMode`, so the controller is always in step with the persisted selection. Never let the UI keep its own copy.
- A unit that has not been narrated in the selected language yet has no rendering, and `readingText` falls back to the extracted source for that unit. The published list therefore always keeps the canonical chunk's length, order and boundaries. This is an honest limitation, not a bug: display text appears as Gemini narrates, so an unplayed chunk still shows the document's original text. Do not claim in UI copy that the whole document is translated up front.
- Blank transcripts are rejected rather than stored, so an empty rendering can never overwrite a usable one or hide the extracted source behind an empty string.
- `ReaderController` records each unit's transcript under **the language the run's instruction was built with** (the same value passed to `ReaderNarrationModes.instruction`), so the instruction and the reading text can never disagree within a run. It clears the cache when a new document replaces the queue, because chunk indices then refer to different content.
- **Recording a transcript is not enough — the Reader must republish.** `ReaderDisplayText.shouldRepublish(language, chunk, displayedLanguage, displayedChunk)` is the production rule for turning a stored rendering into visible state, and `ReaderController.produce` calls it right after `record`, republishing `publish(state.value.phase)` when it returns true. Requiring **both** the chunk and the language to match is what keeps the visible state owned by the current position: a producer renders whole chunks ahead of playback, so republishing unconditionally would drag the UI onto a prefetched chunk or back onto a language the reader has already left. Without this step the reading text lagged the narration by a chunk — the audio switched language while the screen kept the extracted source. Never remove it, and never widen it to "republish on every record".
- `publish` preserves `state.error` when it republishes the `ERROR` phase and clears it on any other phase transition. Republishing is now routine (it happens on every narrated unit), so dropping the message would silently erase the failure reason the user needs to see.
- `ReaderController.setOutputLanguage(language)` updates the display language and republishes immediately. `ReaderViewModel` calls it on init (before `restoreLastDocument`) and from `setOutputLang`, so the controller is always in step with the persisted selection. Never let the UI keep its own copy of the display language.
- **The displayed language switches when the chunk becomes current, not when the chunk finishes.** `publish` derives the reading text for `queue.index` in the same synchronous step that makes that chunk current, so arriving at Chunk N immediately shows N's selected-language renderings. It must never wait for N to finish narrating, and it must never keep showing Chunk N−1's language while N's transcript is still being produced. The reason this is free is the prefetch architecture: the producer renders whole chunks ahead of playback, so the chunk about to be narrated already has its renderings cached and the turn is a pure state read. Do not "fix" this by making the UI wait for a translation step — that is exactly the ordering that would insert a pause between chunks.
- `ReaderDisplayText.pending(language, chunk, unitCount)` returns the units of a chunk with no selected-language rendering yet, and `publish` reports them as `ReaderState.pendingSegments`. This exists because `readingText` falls back to the extracted source for an unrendered unit: without it, "not rendered yet" is indistinguishable from "the selection happens to read like the source", and the source language would be presented as if it were the selected one. `ReaderPageText` (`isPreparing`, `isPreparingWholePage`) is the pure-JVM rule for when that temporary treatment is actually shown — only while the chunk is being narrated (`ReaderGates.isNarrating`), never while browsing, paused or stopped, because a reader who has not pressed Play still has to be able to read their document.
- **A run's first audible frame waits for the first segment's selected-language rendering.** The reading text *is* the Gemini transcript, so "text before audio" can only mean the producer finishes the unit the run starts on before any PCM is written; playback then starts from the audio the spool buffered while that unit was produced. `ReaderController.awaitInitialRendering` is that wait, and it is the **only** place the narration path waits for text — exactly one unit, so nothing blocks on the whole document and nothing is translated up front. `output.start()` is deliberately called *after* the gate: starting the track earlier held audio focus in silence for the whole synthesis of the first unit. The decision itself is `ReaderInitialPlayback.gate`, pure JVM and pinned by `ReaderInitialPlaybackTest`. **Every gate read happens under the controller lock** — the producer ends the unit and records the rendering in one critical section but publishes the spool snapshot *before* recording, so observing the snapshot alone would not make the rendering visible, and reading `ReaderDisplayText` outside the lock would race its map. A gate failure is terminal and must not drain partial audio: playing a unit whose transcript never arrived is precisely the "audio first, text later" sequence the gate exists to prevent. Do not add a "the source language is the selection, so the source counts" shortcut — Voxora detects no document language anywhere, so the source was never a valid rendering for a selected language; the real fast path is a unit an earlier run already rendered, which returns immediately.
- **The same wait applies between units, not just at the start of a run.** The lifecycle the Reader must hold is `current audio -> prepare N+1 -> N+1 text correct -> N+1 audio`, never `source-language N+1 -> wait -> text changes -> audio`. `ReaderController.awaitNextUnitRendering` is that wait: after the spool promotes the next chunk (and after the empty-promoted retry), it blocks until `ReaderInitialPlayback.gate` reports the upcoming unit's rendering exists **for the run's language and mode**, so the text the reader sees when N+1 begins is the text that was prepared, not the extracted source. It is deliberately the *same* rule object as the first-frame gate — do not fork a second gate — and it only ever adds the wait for text: a producer failure is handed to the consumer, which drains partial audio and reports the precise message, rather than being turned into a terminal error here. Keep it out of the audio path's ownership: it reads the display cache and the spool snapshot, and never writes PCM or advances a cursor.
- **This look-ahead is preparation, not pre-translation.** It waits for at most the unit about to be spoken; it must never be widened into rendering the whole document or the whole next chunk up front, which would reintroduce the "translate everything first" pause the architecture exists to avoid. Pinned by `app/src/test/java/com/voxora/app/reader/ReaderNextUnitPreparationTest.kt`, which composes `ReaderInitialPlayback.gate` with `ReaderDisplayModes` for the next unit and fails if a rendering for another mode or language is allowed to release it.
- **The display layer is derived and must stay derived.** `publish` only reads `ReaderDisplayText` and the canonical `ChunkQueue`; it never appends PCM, advances a consumer cursor or completes a spool unit. The producer and the consumer never read `ReaderState`. That one-way dependency is what guarantees a display-language change cannot insert silence between chunks — keep it that way, and never make the spool or the consumer consult display state to decide how much audio to play.
- `ReaderState.pendingSegments` is raw state; whether the temporary treatment is shown is `ReaderPageText`'s decision, because it depends on the phase. Do not branch on the set directly in a composable.
- **The initial preparation state is real state, not a delay.** `ReaderState.preparing` is set from `preparingFirstUnit`, which is toggled around the actual first-unit gate in `play` — true for exactly as long as the run is waiting for the first rendering, false the moment it is ready or the run is cancelled. The UI must transition on that flag and never on a timer, a fake progress bar or a fixed "this may take a moment" sleep. The copy shown while it is true names both transformations truthfully ("preparing the first segment in the selected language and narration style"), because the wait can be a translation, a rewrite, or both; it must not say "translation" when the mode only rewrites. Strings live in `values/strings.xml` and `values-fa/strings.xml` — never inline Kotlin.
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
- Explicit Pause preserves the current chunk; resume restarts that chunk. **Stop ends playback but never throws away the reader's place**: it keeps the queue's position and persists it, so the next Play resumes at the same chunk. Resetting the queue in `stop()` was the defect that sent the reader back to chunk one. The only transition that deliberately returns to the first chunk is replaying a book that reached `COMPLETE`, and that also clears the persisted completion because the reader chose to hear it again.

### Reader UI
`ReaderScreen` is a single `LazyColumn` whose keyed items follow the listening workflow. This order is the information architecture — do not reshuffle it into a flat settings list:

1. top bar with back and the floating-bubble toggle (see "Ownership and background lifecycle")
2. **document identity** — display name (or "no document loaded") + pick/change button
3. **library** — one expandable list of every saved book, shown only when the library is non-empty (see "Reader library UI")
4. **reading mode** — one card per mode, each with a visible one-line description of what it does
5. **narration voice** — one card per semantic choice (Female / Male) plus a one-line explanation
6. **narration language** — a tappable row showing flag + selected language, opening a searchable `ModalBottomSheet`
7. **playback** — status dot + phase label, chunk/segment progress, progress bar, prominent Play/Pause, Stop
8. **error card** — only when `state.error` or a settings error is present
9. **about this book** — the source-backed Book Intelligence card for the active book, including its identifying/not-found/unavailable states (see "Book intelligence")
10. **reading page** — the current chunk as one page (see below), not the whole document
11. **floating-bubble explanation** — a short card saying what the bubble does (keeps narration active in the background, quick access), that it can be enabled/disabled here, and that it is independent of Live Dub; the toggle sits on the top bar
12. **narration preview** — Gemini's live transcript, visually separate from the reading text
13. privacy note

The library sits directly under the document card because it is the reader's own shelf: a returning reader sees what they were reading before they see any settings. The section files (`ReaderLibrarySection.kt`, `ReaderVoiceSection.kt`, `BookIntelCard.kt`, `ReaderCoverImage.kt`) live in `reader/` and use the shared `SectionHeader`; `ReaderScreen` remains a stateless `ReaderContent` over `ReaderViewModel`.

**The floating bubble is explained where it is toggled.** The card in item 11 exists so a reader can discover the bubble without leaving the Reader, and it states the two facts that are otherwise invisible: that the bubble keeps narration running when the app is backgrounded, and that it has nothing to do with Live Dub. It is a discoverability surface only — do not move bubble behaviour into it, and do not make it a second settings panel. Copy lives in both locales (`reader_bubble_section`, `reader_bubble_explain`, `reader_bubble_toggle`). The bubble itself stays under `reader/` (`ReaderBubbleService`) and must never import `dub/`.

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

### Narration voice — two semantic choices, one curated mapping
The reader chooses **Female** or **Male**; `ReaderVoice` (`core/.../gemini/ReaderVoice.kt`) is the single mapping from that choice to a Gemini prebuilt voice name. Nothing else in the app names a voice.

- **Gemini publishes no gender and no pitch field.** The documented prebuilt set is 30 names, each with only a *tone* descriptor (`Aoede` "Breezy", `Charon` "Informative", `Kore` "Firm"). There is no request parameter that asks for a feminine or masculine voice. So `ReaderVoice.geminiVoiceName` is a **curated perceptual mapping, not an API-declared fact**, and the code says so. Do not claim the API declares a gender, and do not "fix" the gap with DSP pitch shifting: the model's own voice is the intended instrument and shifting PCM would degrade it. If Google ever ships a real gender/pitch parameter, it belongs behind these same two ids and nothing else changes.
- **`id` is the persisted identity, `geminiVoiceName` is the wire value.** They are separate on purpose: `UserPrefs` stores Voxora's word (`reader_voice`, normalized to `DEFAULT` when absent or unknown), and only the session setup sends Gemini's.
- **`GeminiReaderSession.connect(apiKey, instruction, model, voice, withSpeechConfig)` takes the voice as a required argument.** It used to be a private `READER_VOICE` constant, so a caller could neither forget it nor choose it; a default value now would let a new call site silently narrate in the wrong voice. `ReaderController.play` resolves it once per run from `prefs.readerVoice` — exactly as it resolves the narration language — and passes it through `runPipeline` → `produce` to **every** new session, including the ones opened after a pause and after a chunk boundary. A blank name falls back to the default rather than sending an empty `voiceName`.
- The voice is a **global Reader preference, not per-book data** (see §5 "Persistent library"). It describes how the product sounds, not what the book is; copying it into every record would create two sources of truth for one decision.
- Pinned by `ReaderVoiceTest` (both ids map to names in the documented 30, the two differ, normalization is total) and by the two setup-payload cases in `GeminiReaderSessionTest` (the requested voice reaches `speechConfig.voiceConfig.prebuiltVoiceConfig.voiceName`, and a blank one becomes the default).

### Persistent library — records, documents, and what is stored where
The Reader remembers the books the reader imported. This replaced a single `last_doc_uri` string, which could not survive a provider that moved or a permission that lapsed.

- **`ReaderBookRepository` (app, `reader/library/`) is the only persistence for the library.** It is a shell around two pure core objects: `ReaderLibrary` (the rules) and `ReaderBookCodec` (the bytes). Records live in their own DataStore file (`voxora_reader_library`, one versioned JSON document), deliberately **not** in `UserPrefs`: the library is a collection with its own lifecycle, and mixing it into the global preference file would make every settings read carry it. There is no second persistence system and no database dependency.
- **The document is copied into app storage at import** (`ReaderDocumentStore`, `filesDir/reader/books/<id>.<ext>`) and the record stores that path, not the provider `Uri`. A `content://` URI is a handle into another app's storage — it survives only while the granting app keeps its side of the permission — so it is not a durable reference. The copy has a second, load-bearing benefit: **re-extraction is deterministic**, which is what makes a persisted chunk index a valid resume point across restarts. The copy is bounded by the same 20 MB limit `TextExtractor` enforces and is deleted with the book.
- **What a record holds:** id, local path, display title, source type, chunk count, current chunk, state, imported/last-read timestamps, the cached metadata, the lookup state, the identification signals, and the cached per-language Book Intelligence overviews. **What it deliberately does not hold:** the document's text, any PCM offset, the narration voice, or the output language. The last two are global preferences.
- **`ReaderPhase` is not persisted.** The stored state is the coarse `ReaderBookState` (`NOT_STARTED` / `IN_PROGRESS` / `COMPLETED` / `UNAVAILABLE`) because it is what is still true after the process dies; there is no `PLAYING`, since a killed process is not playing anything and writing that would be a claim the app cannot honour on the next launch. Live transport state stays in memory.
- **The document's type is resolved by one rule, and a known type always wins** (`ReaderDocumentType`, core). The order is: the persisted `sourceType` → the provider MIME type → the extension of a **file name**. Voxora's own copy is a `file://` URI with **no MIME type**, so a reopen falls through to the name — and the name it is given is the book's *display title*, which automatic identification replaces with the catalogue title (`"The Selfish Gene"`, no extension). Deriving the type from that rejected a perfectly readable copy as an unsupported file type. **A display title is not a file name**; `TextExtractor.identify`/`extract` take the record's `sourceType` as `knownType` and never second-guess it. Pinned by `ReaderDocumentTypeTest`, including the regression itself.
- **Import is staged, then committed.** `stage` copies the file, extraction runs, and only a successful extraction calls `commit`; a failure calls `discard`, so a document that cannot be read never appears in the library as an empty book. A book replaced mid-extraction by a newer load is removed rather than left behind.
- **The one-time migration** of the old `last_doc_uri` runs only when the library is empty and only once (a stored flag), through the ordinary import path, so an existing reader keeps their document. It is treated as a *restore*, so a document that can no longer be read clears the stale reference and leaves the Reader idle instead of showing an error on launch.
- **Every mutation goes through one serialized read-modify-write** (`updateLibrary`): `ensureLoaded` first (it takes the same lock), then the lock, then the pure transform, then the write. Two concurrent saves cannot lose one another, and the in-memory library stays authoritative for the session if a write fails.
- Pinned by `ReaderBookTest`, `ReaderLibraryTest`, `ReaderBookCodecTest` and `ReaderRecencyTest`: a second import leaves the first book's progress alone; a move on a completed book is refused while an explicit replay clears completion; a save for an unknown id is a no-op rather than a resurrection; `mostRecent` is a decision (recency, then import time, then id) rather than list order; the codec round-trips, caps a provider description, keeps an unknown page count unknown rather than `0`, clamps an out-of-range chunk, and skips a damaged record instead of discarding the library; a clock that moved backwards reports "just now".

### Resume — chunk level, at safe transitions only
- The persisted position is the **chunk index**, the granularity the reader perceives. PCM offsets are deliberately not persisted: they are not stable across re-extraction, and sample-level restoration is not a promise the Reader makes.
- Saves happen at the transitions that matter and nowhere else: import, a chunk becoming current, pause, stop, explicit chunk navigation, and completion. **Never per PCM callback** — that is what makes chunk-level persistence affordable, and `savePositionLocked` is deliberately fire-and-forget on the document scope so a slow disk can never stall audio.
- Opening a book re-extracts Voxora's own copy and `jumpTo`s the saved chunk before publishing `READY`, so the screen shows "Chunk 73 of 200" immediately and never has to be told twice. `ChunkQueue.jumpTo` clamps, so a record written against an older extraction cannot point past the end.
- **A record whose file has disappeared is marked `UNAVAILABLE`, never deleted.** The reader's saved position and cached Book Intelligence are theirs, and the copy may come back (a restored backup, a storage permission); deleting the record would destroy both and would make the failure indistinguishable from a book that was never imported. The state is persisted, so the library stops offering a Continue that cannot work and the Reader stops re-attempting extraction on every open. An automatic restore stays quiet and opens idle; an explicit open says exactly what is wrong (`reader_book_unavailable`).
- **Opening the book that is already open is a no-op.** Reopening it would cancel running narration and re-extract a document already in the queue — which is what turned one failure into a repeated "Document extraction failed" line per tap.
- **The chunk is the unit of resume, and it is also the unit of the cache.** When the chunk the reader resumes at is still cached, Continue plays it from disk immediately and asks Gemini for nothing; when it is not, the run re-synthesises from that exact chunk. Either way the position is the persisted logical chunk index — never an approximate text, character, PCM or sample offset, and never transient memory. See "The chunk cache".

### Book intelligence — source-backed identification, never invention
Importing a document starts an automatic lookup in the public catalogues. The rule that governs the whole feature is: **the section is source-backed or it is absent.**

- **Signals come from the document, never from the network's imagination.** `BookSignalsReader` reads an ISBN (accepted only when it passes its own check-digit algorithm, which is why an identifier outranks a title), a title (the first line that does not look like page furniture), an author (an explicit `by …` marker, or a name line under the title) and the file name as the weakest fallback. A file name is never trusted on its own.
- **`BookMetadataLookup` asks the catalogues and `BookMatch` decides.** Google Books (`GoogleBooksSource`) is primary and Open Library (`OpenLibrarySource`) is the fallback; both are asked for *candidates* and the policy is applied **once, to the union**, so "the title is similar in both but the author disagrees" resolves to a refusal rather than to whichever source was asked first. Trust order: exact ISBN → title + author → a near-exact title with supporting metadata and no contradicting author. A tie is never broken (`Ambiguous`), a generic title can never carry a match, and an ISBN printed in the document that a candidate contradicts rejects that candidate however similar its title.
- **The three non-matches are kept apart** because they mean different things: `NOT_FOUND` (the catalogues answered, nothing fitted), `AMBIGUOUS` (several fitted and none was proven), `UNAVAILABLE` (the lookup could not run or be understood). A failed lookup is never presented as evidence about the book.
- **Metadata is cached with the book**, so opening the library is never a network operation and the section works offline once retrieved. The lookup runs once per book automatically (the record starts in `NONE` and a finished attempt moves it on, which is what stops an offline reader retrying in a loop); retrying is then an explicit action.
- **`BookIntel` builds the facts and never invents one.** An unknown author is not shown, a missing year is not derived from an ISBN prefix, subjects come only from the catalogue's own headings, and the description is the publisher's text unedited and untranslated. `workKind` reads fiction/non-fiction from the subject headings alone — never from the description's wording — and returns `UNKNOWN` rather than guessing, so the label follows what the catalogue says the work is. Author biography is deliberately not modelled: neither catalogue carries it, and inventing one is exactly what this rule forbids.
- **Explanatory content follows the Reader output language, and only through a labelled generated overview.** No public catalogue carries a Persian description for most titles, so the reader's language cannot come from the provider — `langRestrict` is deliberately **not** used for the query, because it filters *books* by language and would exclude the correct edition. Instead the catalogue record is turned into a short overview by a **separate, one-shot text call** (`BookIntelOverviewGenerator` / `GeminiHttpTextTransport`, core) that sends **the metadata only — never the document, never an excerpt, and never anything about the reader's position** — and is not the narration session. The result is cached per language on the record (`ReaderBook.overviews`, pruned to the newest few), so switching languages back and forth is free and offline, and it is labelled in the UI as AI-generated with a note that it is not a catalogue fact. The catalogue's own description is still shown, unedited, with its source language named when it differs from the reading language. A missing key, an offline device, a rate limit or a rejected model all end in "no overview": the book is unaffected and the source-backed facts stay on screen. Generation is attempted once per book **per language**, so a failure costs one call, not one per recomposition.
- **Failure can never block import.** The book is committed before any lookup starts; a lookup that cannot run only moves the state to `UNAVAILABLE`. Nothing here is on the Main thread, and narration never waits for it.
- **The subject headings follow the reading language too.** A catalogue's own subjects are always in the catalogue's language ("Evolution", "Biology"), so a Persian reader would otherwise see English words under a Persian heading. The same one-shot call therefore asks for the record's subjects rendered as short headings **in the target language**, and they are stored **inside** the per-language overview record rather than beside it — which is what makes a language switch unable to show another language's themes: the other language's overview is a different record entirely. The card shows the generated headings in place of the catalogue's own when they exist, and falls back to the catalogue's headings (with the existing source-language note) when they do not; a book with no subjects has nothing to translate and must not lose its prose because of it. A model that answers in plain prose instead of the requested JSON shape is still accepted — a usable overview matters more than the shape it arrives in — but a JSON answer with no usable `overview` field is rejected outright rather than shown as prose. The prompt version is part of the cache key (`BookIntelOverview.promptVersion`), so improving the prompt regenerates rather than being masked by an answer already paid for; `ReaderViewModel` tests staleness with `BookIntelOverviewPrompt.isUsableFor`, **not** "an entry exists", so both layers agree on what "cached" means. Pinned by `BookIntelOverviewTest` and `ReaderBookOverviewTest` (the codec round-trips the themes inside their language, and their absence decodes to empty rather than to a failure).
- Covers use a **minimal hand-written loader** (`ReaderCoverImage` / `ReaderCoverLoader`: OkHttp + `BitmapFactory` + a bounded memory LRU + a `cacheDir` disk cache) rather than a new image dependency. A missing cover — no URL, no network, a timeout, a non-image body, a decode failure, no cache directory — always ends in the placeholder and never in an exception or a permanent loading state.
- The HTTP layer is public read-only: **no API key, no OAuth token, and nothing derived from the Gemini key ever travels to a catalogue**, and the document's text is never uploaded. Only the query built from the document's own signals leaves the device. A blank body is `NotFound`, an unreadable body is `PARSE_ERROR` (never "no such book"), `429` is `RATE_LIMITED` (never a network fault), and cancellation is rethrown rather than reported as a catalogue failure.
- Pinned by `BookSignalsReaderTest`, `BookMatchTest`, `BookIntelTest`, `GoogleBooksSourceTest`, `OpenLibrarySourceTest` and `BookMetadataLookupTest`.

### Reader library UI
- `ReaderLibrarySection` renders **one** list of every saved book inside a single card — deliberately not a separate "continue" card plus a list of everything else, which made the same book appear twice and forced the reader to work out which one to press. The card is collapsed by default and shows the selected book's compact summary (cover, title, real chunk position, state) plus its Continue. It expands on a **downward vertical drag** or a tap, and collapses on an upward drag. The gesture is vertical because the section is a card in a vertical page; a horizontal gesture would fight the page's own scroll direction and mean nothing here. The drag is confined to the header row, so the page still scrolls normally everywhere else.
- **Selecting is not opening.** Tapping a book selects it and reveals its detail — progress bar, "Chunk 12 of 210", state, "Last read …" — together with Continue and Remove. Nothing is loaded and nothing makes sound until Continue is pressed, so browsing the library can never interrupt what is playing. The book the section speaks for is the one picked here, else the one the Reader has open, else the most recently read; every fallback is looked up in the current library, so a removed book can never be named by a stale selection.
- Continue opens the book at its persisted chunk and **never re-imports**. The action's wording follows the book's real state — "Start" for one never opened, "Continue" for one in progress, "Listen again" for a finished one — because one word for three situations is a small untruth. A book whose copy is gone shows "Not available" and offers **no action that could only fail**.
- Rows are ordered by recency and the currently loaded book is marked. The section appears once the library is non-empty; while it is empty the document card's picker is the import path, and a second import button would only duplicate it.
- **Opening the Reader must never start audio.** The collapsed summary restores nothing and plays nothing; it offers Continue.
- Removing a book asks first and states exactly what is deleted: the library record, Voxora's copy **and that book's cached narration audio** — never the original file.
- Every new string exists in both locales, the Persian follows the canonical standard (§10), and the UI uses theme tokens only — no literal colour, no `sp`, no `fontFamily`.

### The chunk cache — Stop → Continue without paying twice
A chunk costs a Gemini round trip, and stop-then-start is the reader's most common action. Without a cache every Continue re-synthesised the chunk the reader was already listening to — the same words, the same voice, paid for twice.

- **`ReaderChunkCache` (app, `reader/`) is the persisted cache of whole chunks**: the PCM and the transcript of every unit, one directory per chunk under `cacheDir/reader-chunks/`. It is pure JVM (no `android.*`) so the reuse and eviction rules are unit-testable. It is read while a chunk is *prepared* and written once a chunk has *finished* — never from a PCM callback, and never on the audio path.
- **A stored chunk is reused only when every part of its key still matches**: the book, the chunk index, the chunk count, the unit count, a hash of the units' own text, the output language, the narration mode, the voice and the model. There is no "close enough". The unit-text hash is what makes a re-extraction that produced different words a *different* chunk even at the same index, and the language/mode/voice/model parts are what stop audio produced for one selection being replayed under another.
- **The unit boundaries are validated, not trusted.** Every unit must be present once, in order, each ending further into the file than the last, and the last must land exactly at the end of the audio. An entry that fails any of this — a truncated file, a hand-edited record, an unreadable one — is **deleted and treated as a miss**. It is never partially restored, because a partially restored chunk plays one unit's audio under another unit's text.
- **Only a whole chunk is stored.** A spool whose producer was cancelled mid-unit has no boundary for its last unit, so `store` refuses it rather than letting a later Continue play a truncated chunk as if it were complete.
- **A restored chunk launches no producer at all.** `prepare` seeds the display cache with the stored transcripts *before* the slot is published, then hands the consumer a complete, read-only `ReaderSpool.fromCache`. Seeding first is load-bearing: a restored spool is complete from the moment it exists, and a first-unit gate that found no rendering would report a perfectly good chunk as *failed*. Because no producer runs, content already on disk is never requested again.
- **A chunk restored from the cache is replayed from unit zero or not at all.** The entry holds the whole chunk from its first unit, so starting a reader mid-chunk from it would play unit zero's audio under a later unit's text; a mid-chunk resume therefore re-synthesises, which is the honest answer.
- **Stop → Continue is the case this exists for.** The run's own first chunk is only reachable from the run's `finally` block, so the cache is written there as well as at promotion — that is exactly the chunk a reader who stops mid-listen is on. A chunk whose producer finished while the reader was listening is kept; one still being produced is refused.
- **Bounded and deterministic.** Two entries per book (the one being read and the next), and a total byte budget across all books; the least recently used go first, and the entry just written is never the one evicted. A store **renames** the spool's existing file rather than copying it, so a ~10 MB chunk costs a directory entry instead of a disk write that would stall the next chunk.
- **Nothing here can fail a run.** A cache that cannot be read is a chunk that has to be synthesised, never an error: `loadCached`/`keepCached` swallow everything except cancellation. Removing a book drops its entries with it (`ReaderBookRepository.remove`), because an entry keyed by a book that no longer exists could never be read again.
- Pinned by `app/src/test/java/com/voxora/app/reader/ReaderChunkCacheTest.kt` (reuse only under an identical key, a changed key is never served, a re-extraction at the same index is a different chunk, truncated audio / inconsistent boundaries / a blank transcript / an unreadable record are all dropped, an incomplete chunk is not stored, a restored chunk is not re-stored, per-book and byte budgets with LRU, the entry just stored survives, removal per book, and the fingerprint's stability) and by `ReaderSpoolTest` (`fromCache` round-trip, a restored spool is complete and rejects append/fail, `detach` hands the file over without deleting it, `detach` refuses a spool that does not own its file, and closing a restored spool leaves the entry in place).

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
- **The usage ring draws only a measured ratio, and nothing when there is none.** `UsageRing` in `ApiUsageScreen` is the dashboard's one visualisation, and it is fed `ApiUsageSnapshot.successShare()`: successes ÷ requests among Voxora's **own observed** requests this month — the only fraction on the screen with a real denominator. It is deliberately **not** a quota gauge; nothing is divided by a limit Google never reported, and `successShare()` returns `null` (so the ring draws no arc and the caption says nothing was recorded) whenever either count is missing or the month had no requests. An arc at zero or full when nothing was measured is a fake percentage — do not add one, and do not "improve" the ring by inventing a quota denominator. Arc colour uses the `success`/`warning`/`danger` roles, never a new literal.
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
- **Configuration is not failure, and it is not "unimplemented" either.** While
  `default_web_client_id` holds the shipped `REPLACE_…` placeholder, `GoogleCloudAuthorizer.configured`
  is false and `requestAuthorization` reports `CONFIGURATION_MISSING` **without touching the
  authorization provider**, so an unconfigured build says the real thing and manual API-key use keeps
  working. The rule itself is `core/.../cloud/CloudOAuthConfig.isConfigured` — pure JVM, pinned by
  `CloudOAuthConfigTest`, checking presence rather than validity (only Google can judge a client ID).
- **Four states, four different actions, and the card must render them distinctly:**
  `NotConfigured` (the *build* has no Google client), `SignedOut` (configured, not connected),
  `Authorizing` (consent open), `Authorized` (a grant is held). `CloudAuthStateTest` pins that they
  are mutually exclusive. In `NotConfigured` the sign-in button stays **visible but disabled** and
  the copy says Voxora supports sign-in but this build is not set up for it — hiding the button made
  a supported feature read as "sign-in is not implemented". Never describe a missing client ID as a
  user failure, and never call it a Cloud Console or OAuth concept in user-facing copy.
- **The whole account card is the tap target** when a tap can do something (`SignedOut` or a retry
  after `Failed`); it stays inert while a grant is held or the consent screen is open, so a stray
  tap cannot launch a second request.
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
- **A saved API key is never shown again, and that is structural.** The Settings key card reads
  `UserPrefs.apiKeyConfigured` — a boolean derived from `ApiKeyMask.isConfigured` — and never
  `UserPrefs.apiKey`. `app/src/main/java/com/voxora/app/ui/ApiKeyFieldState.kt` is the field's whole
  state, and the only string it can hold is the user's in-progress draft; there is no field for the
  stored secret, so the screen cannot prefill the field even if a later edit forgets the rule. A
  configured install shows "API key configured" with **Replace** and **Remove**; the entry field is
  masked (`PasswordVisualTransformation`, password keyboard) and opens only when there is no key or
  the user asked to replace one. Saving writes the user's own draft and clears it from UI state; the
  global Save button deliberately does **not** touch the key. Pinned by `ApiKeyFieldStateTest`.
- **The key is never displayed, logged, or put in a preview.** Do not reintroduce a "show key"
  affordance — the `action_show_key`/`action_hide_key` strings were removed with it. Do not seed a
  preview or a test fixture with a realistic key, and do not add the key to a log line, an analytics
  event, a crash message or an accessibility description. `ApiUsageScreen` may show `ApiKeyMask.mask`
  (first/last four characters only) because a masked value is not the secret.

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

## 6. LIVE DUB — THE PIPELINE AND ITS SYNCHRONIZATION CONTRACT

These files are stable and in production. Do NOT refactor, rename or restructure them casually:
- `DubService.kt`
- `SystemAudioCapture.kt`
- `DubPlayback.kt`
- `FloatingBubbleService.kt`
- `GeminiLiveSession.kt`
- `GeminiLiveConfig.kt`

If you find a bug in them, report it — do not silently fix it. The synchronization layer added in
this cycle is the one sanctioned change to them, and it is described here.

### The problem, stated correctly

The source (video, podcast, music) plays in real time. Voxora captures it, sends it to Gemini and
plays the translation back, so the dubbed audio is always **behind the source by the pipeline's
latency `L`** — most of it Gemini's own translation time. `L` itself cannot be removed from the
client. The audible defect is the **avoidable delay on top of `L`**: an output backlog, an oversized
hand-off buffer, a tolerance that adapted upward. That delay is what the user actually hears as
"the dub is 4 seconds behind", and it is what this layer removes.

### The model: a measured floor, not an accepted baseline

Both sides have a **content clock** in nanoseconds of audio: how much source audio was captured
(`SourceClock`, from the device's own capture frame position) and how much dubbed audio the device
has actually presented (`DubPlayback.playedNanos`, from `AudioTrack.playbackHeadPosition`). Their
difference is the offset. In a healthy pipeline both advance at real time, so the offset is constant.

The earlier version of this layer learned that constant as a **baseline** and corrected only
deviations from it. That is correct for drift and wrong for everything else: whatever avoidable delay
had settled by the time the baseline was learned — a full hand-off buffer, an output backlog, a
tolerance that had adapted to 800 ms — became permanent, and a constant 4 s offset reported
`SYNCED` forever.

The model now learns a **[`PipelineLatencyEstimator`] floor** instead: the *smallest* offset seen in
a sliding 30 s window, sampled only while the pipeline is genuinely active. The floor is the best the
pipeline has demonstrated, and everything above it is delay the app introduced:

```
excess = offset − floor
```

`excess` is what `DubSyncController` corrects, by pausing the source so the dub drains its backlog
until the offset is back at the floor. A constant pipeline latency is still reported
`SyncState.SYNCED` — it is the pipeline, not a fault — but it can no longer be an inflated baseline,
because the floor keeps moving down as the app's own buffering is removed.

- `SyncConfig` holds only bounds (warm-up ceiling, tolerance, minimum correction, maximum pause,
  cooldown, rate budget, stall timeout). It has no latency constant.
- A constant latency of 200 ms, 3 s or 4.5 s all report `SYNCED` with **zero** corrections, and the
  floor equals that latency. `DubSyncControllerTest` pins each case.
- A burst of late audio raises the *current* offset but **cannot raise the floor**, so it can never
  become the new normal; the controller corrects back down to the demonstrated best.
- `SourceClock` counts only audio that is actually audible, not held by a correction, and actually
  being sent (`GeminiLiveSession.isReady`). Counting the pre-connection setup would inflate the
  measured latency by the whole handshake, because `sendPcm16k` drops that audio.

### The correction order: bound the backlog, then trim the rate, never seek

1. **The playback timeline bounds the backlog** — see the next section.
2. **`PlaybackRatePolicy` trims the output rate** by at most three percent to drain a small excess
   without skipping audio. It returns exactly `1.0` inside an 80 ms dead band and whenever nothing
   is queued (speeding up with nothing to drain would only underrun), moves at most one step per two
   seconds, and is clamped. `DubPlayback.setRate` applies it best-effort: a track that refuses the
   rate keeps playing at `1.0`, and `currentRate()` reports what is actually applied.
3. **The controller pauses the source** only for excess above `minCorrectionNanos`, subject to the
   cooldown and the per-minute budget.
4. **Nothing ever seeks or flushes the output.** Dropping the arriving chunk bounds the delay; it
   deliberately does not jump the content forward.

### The dub-side playhead, and why both halves are needed

Pausing the source is the right correction, but it is unavailable on a device where the user has not
granted media control — and on those devices the dub had **no** defence against a backlog. If Gemini
stalled and then delivered several seconds of audio at once, all of it was played, and the dub ended
up permanently further behind. That is the failure mode `PlaybackTimeline` exists to prevent.

- The timeline keeps one number: the **backlog** — dubbed audio received but not yet heard. That
  number *is* the added delay, and it is derived from the **playhead**, not from bytes written:
  `scheduledNanos` is what was accepted for playback, `playedNanos` is what the device has actually
  presented (`DubPlayback.playedNanos()`, from `AudioTrack.playbackHeadPosition`, unwrapped by
  `PlaybackHead`). `DubService` feeds the controller the playhead, never the write cursor; the write
  cursor survives only as a diagnostic, because the gap between the two *is* the output buffer's
  contribution to the delay and is logged separately as `buffer`.
- In a healthy pipeline the backlog sits at the output buffer's own occupancy and holds still. When
  Gemini bursts, it spikes past the **tolerance**; the timeline then refuses to feed the output until
  it is back inside, so the surplus is discarded instead of being played out. The consumer receives
  oldest-first, so refusing the arriving chunk **bounds** the total delay rather than reducing it —
  the queued audio already in the output buffer still plays. That is the deliberate trade: bound the
  lag, never seek the content forward. The controller's source pause is what actually drains it.
- The timeline's decision uses the **total** unheard audio: the output backlog *plus*
  `handoffBacklogNanos`, the audio still queued in Gemini's hand-off flow. That buffer reports no
  occupancy, so `DubService` estimates it from the difference between `emittedAudioChunks` and the
  chunks the consumer has collected, clamped to the buffer's capacity. Leaving it out was how a full
  hand-off buffer added delay that nothing measured — the single largest avoidable term found.
- The tolerance **adapts**: running dry means it was too tight, so it loosens by one step; a quiet
  stretch tightens it again. Both moves are clamped (`minToleranceNanos`..`maxToleranceNanos`) and
  rate-limited by `adaptationCooldownNanos`, so one burst cannot make the buffer oscillate. The
  ceiling is 450 ms (it was 800 ms), because a jittery session must not sit most of a second further
  behind for its whole run. There is no fixed backlog constant, for the same reason there is no
  fixed latency constant.
- `DubService` must ask `PlaybackTimeline.onChunkArrived` about **every** chunk and branch on
  `ChunkAction.PLAY`; writing straight from the Gemini flow bypasses the policy. `dubguard.py`
  enforces this, along with the playhead requirement and the purity of the timeline.
- On a Gemini reconnect the timeline is **rebased**, not zeroed: the audio in flight is gone, but the
  output track is still playing, and zeroing the playhead would make the backlog look enormous and
  discard audio that should have played. The rate trim is reset to `1.0` at the same time.
- The timeline is **thread-safe**: the audio consumer calls `onChunkArrived` while the synchronizer's
  tick calls `onPlayed`, so every mutator is `@Synchronized` and every reported counter is `@Volatile`.
  A lost update would let the backlog grow unbounded — the one thing this class exists to prevent.
- `GeminiLiveSession`'s hand-off buffer was cut from 48 chunks (~2.9 s) to 12 and is now **4**. It is
  a hand-off, not a queue; the timeline owns backlog policy, and the caller accounts for what is
  still in flight so the buffer can never hide latency again.

**This is additive, not a replacement.** `DubSyncController` still owns source-side drift and the
`ExternalPlayer` seam is unchanged. The two compose: the timeline bounds the backlog locally and
needs no permissions, and the controller corrects whatever residual drift remains when control is
available. Neither is a substitute for the other.

### Source control, and the honest limit

`ExternalPlayer` is the seam; `MediaSessionExternalPlayer` is the only file that knows about
`MediaSessionManager`. Android grants `getActiveSessions()` **only** to a `NotificationListenerService`
(or a system app), so `VoxoraNotificationListenerService` exists as an empty listener and the user
opts in through the system settings page from the Live Dub screen. Nothing is requested silently, and
**without the grant Live Dub still works, as audio-only** (`SyncState.AUDIO_ONLY`).

**External video frames cannot be delayed or frozen.** Pausing the source through its media session
corrects *drift*; it does not make the translation land on the right lip movement. Do not claim
otherwise in UI copy or documentation, and do not reintroduce `DelayedScreenOverlay` — a
VirtualDisplay plus fullscreen overlay caused recursive frame-in-frame capture and froze the UI, and
it cannot delay another app's video either.

### Safety — every path must release the source

The controller is the only thing that may pause the source, and it only ever pauses a source it has
observed to be **playing**. It must never fight the user: a pause it did not cause is detected as a
user action, the learned offset is discarded and re-measured, and the source is never resumed by
Voxora. Every path that could leave the source paused — control lost, a stalled dub, a Gemini
reconnect, `stop()`, service teardown — resumes it. `DubService.stopAll()` calls `sync.stop()` before
`playback.stop()`, so the source is released before the volume is restored.

### Latency instrumentation

`LatencyTimeline` records monotonic timestamps (`SystemClock.elapsedRealtimeNanos()` through
`MonotonicClock`) for capture, send, first model audio, dub chunk, audio write, scheduled playback,
actual playback, dropped chunk, and pause/resume. It is synchronized (marked from three threads) and
its log line is rate-limited to one per two seconds, forced on a state change — Live Dub runs for
hours and must not flood the Logs ring buffer. It answers "where is the delay": capture→send,
send→response, send→scheduled, send→played, write→played. The two playback stages are separate on
purpose: writing a chunk and hearing it are separated by the whole output buffer, and only
`ACTUAL_PLAYBACK` — the platform's own `AudioTrack.getTimestamp` — can show how large that gap really
is. Gemini's `DROP_OLDEST` buffer cannot report drops through `tryEmit`, so
`GeminiLiveSession.emittedAudioChunks` is compared with what the consumer received and the difference
is reported as `DROPPED n` instead of being silent; chunks discarded by the playback timeline are
counted separately as `dropped n`.

`SyncDiagnostics` formats the single `[DUB_SYNC]` line, emitted at most once a second and forced on a
state change, with every number measured or explicitly unavailable:

```
[DUB_SYNC] sourceMs=… dubMs=… driftMs=… estimatedPipelineLatencyMs=… floorMs=… excessMs=…
           captureTimestampMs=… geminiFirstAudioMs=… handoffMs=… outputQueuedMs=…
           outputPlayedMs=… bufferMs=… rate=… correction=… drops=… underruns=…
```

- `captureTimestampMs` is `AudioRecord.getTimestamp`'s age — the capture pipeline's own contribution,
  and the only field a device may legitimately not provide. It renders `—`, never `0`: "the platform
  did not tell us" and "the value is zero" are different answers.
- `floorMs` and `excessMs` are the model: the pipeline's demonstrated best, and the avoidable delay
  above it. `driftMs` is the raw offset. Comparing them is how the line answers "is the 4 s the
  model, or is it us?".
- `handoffMs` is the previously invisible Gemini hand-off backlog; `bufferMs` is the output buffer's
  occupancy. They are reported separately because they are different buffers with different owners.

### Five pipeline bugs fixed with this work

1. `DubService` launched a **new coroutine per audio emission** to write to the track, so chunks
   could be written out of order under load. There is now one ordered consumer, and
   `WRITE_BLOCKING` is the backpressure.
2. `DubPlayback` sized the `AudioTrack` buffer at `minBuf * 8`, floored at a full second of audio —
   a full second of added lip-sync delay before the first dubbed word. It is now `minBuf * 2` floored
   at ~125 ms.
3. The dub's content clock was the **write cursor**, so audio sitting unplayed in the output buffer
   counted as progress and the pipeline could not see its own backlog. It is now the playhead.
4. The model accepted the current offset as a **baseline**, so any avoidable delay that had already
   settled became permanent and a constant 4 s reported `SYNCED`. It now targets a measured
   **floor**, so the app's own buffering is corrected away instead of accepted.
5. The Gemini **hand-off buffer was invisible** to the model: the `PlaybackTimeline` only saw chunks
   that reached it, so up to 12 chunks of received-but-unscheduled audio added delay nothing
   measured. The buffer is now 4 chunks and its occupancy is part of the drop decision and the
   `[DUB_SYNC]` line.

The source ducking rule (~28 %, never silent) is `SourceVolumeDuck`, pure and unit-tested; the
original level is saved and restored exactly. Volume keys still control the dub through the local
`MediaSession`/`VolumeProvider` and are untouched.

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

Live Dub's synchronization declares one extra service — the empty notification listener that makes
`MediaSessionManager.getActiveSessions()` legal. It must stay `exported="true"` (required with an
intent filter on API 31+) and must stay protected by
`android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`, so only the system can
bind it:

```xml
<service
    android:name=".dub.sync.VoxoraNotificationListenerService"
    android:exported="true"
    android:label="@string/sync_listener_label"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
    <intent-filter>
        <action android:name="android.service.notification.NotificationListenerService" />
    </intent-filter>
</service>
```

It reads no notification: the class overrides nothing. Adding notification-reading behaviour to it
would be a product and privacy change, not a refactor.

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
- **Live Dub's synchronization instrumentation uses `VoxoraLog` and is rate-limited.** `LatencyTimeline` emits at most one summary line every two seconds (forced on a sync-state change), tagged `DubSync`, and `DubSyncController` logs each measurement, correction and release as it happens. Do not add a per-chunk log line: Live Dub runs for hours and would push everything else out of the 800-entry ring buffer. The summary is the "where is the delay" answer — capture→send, send→first audio, send→dub, dub→write, plus an accounted `DROPPED n`.

### The Logs viewer
- The viewer's chrome is product UI; the **log lines are not**. Keep the exact `formatted()` output, the monospace font and the explicit LTR list region — a timestamp and a bracketed tag are technical identifiers, not prose, and mirroring them under a Persian UI would make the list unreadable.
- **Severity colours are semantic, not literal.** `LogSeverity` maps a severity to a role and `LogsScreen` only asks for the role: INFO → `VoxoraColors.success`, WARN → `VoxoraColors.warning`, ERROR → `VoxoraColors.danger`, DEBUG → `onSurfaceVariant`. An unrecognised severity is neutral and must never be dressed up as success. Do not hardcode a hex here.
- **Each entry is its own `SelectionContainer`**, so a long press selects inside that line and Copy takes exactly that entry rather than the whole buffer. Keep the header's Copy-all, Share and Clear as well — sending everything is a different job from sending one line. `LogLineFormat` is the single rule behind all of them, so a single-entry copy and the Copy-all payload can never drift apart; never re-implement the line shape at a call site.

---

## 10. STRINGS & i18n

### The canonical Persian standard — permanent and project-wide

> **The current Persian UI writing and localization style is the canonical Persian standard for
> Voxora and must be preserved across all future features.**

This is a permanent **Localization and UI Design Principle**, not a visual preference. Every future
feature — every screen, component, dialog, Bottom Sheet, Toast, Snackbar, error message, status
message, Settings item, Reader/PDF Reader UI, Live Dub UI, Onboarding, Navigation, Notification,
permission message, API/cloud message, login/connection state, and any other Persian text — must
follow exactly the same Persian writing and presentation standard used by the current version.

**Before adding or modifying any Persian text, inspect the existing Persian text in the current
version first and match it.** The current implementation is the reference; the list below is what
that reference already does, not a licence to invent a new style:

- follow the same wording style and sentence structure as the strings already in the app;
- write natural, modern Iranian Persian — never machine-translated Persian;
- use the correct Persian characters consistently (Persian `ک`/`ی`, not the Arabic forms);
- preserve correct Persian spacing and نیم‌فاصله (ZWNJ);
- preserve natural Persian punctuation;
- handle mixed Persian/English/numbers with correct RTL/BiDi behaviour (the FSI/PDI isolate rule
  below), and never hand-reverse a string or pad it with spaces;
- do not use unnecessary LRM/RLM/LTR/RTL hacks unless a specific BiDi case technically requires it;
- preserve the established terminology below, and **extend the existing Persian language system
  rather than creating a new writing style**.

`AgentMD.md` §2 carries the same contract for day-to-day UI work; `bidi_fa.py` and `stringcheck.py`
are the mechanical checks.

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
  `LocalLayoutDirection` / auto-mirrored icons. Never reverse a word by hand, never pad with
  spaces to push a word to the other side, and never set `TextAlign.Center` on a whole screen just
  to make a mixed string "look right" — `Center` belongs only where the surrounding layout really
  is centred. Use auto-mirrored icons (`Icons.AutoMirrored.*`) for anything directional.
- **Embedded Latin is isolated, not reordered.** A Persian sentence that contains a Latin run —
  `Gemini API`, `Google AI Studio`, `Live Dub` — wraps that run in a Unicode isolate:
  **FSI (U+2068) … PDI (U+2069)**. This is the Unicode Bidirectional Algorithm's own mechanism for
  saying "this run is LTR"; the surrounding Persian then orders correctly and the Latin keeps its
  internal order. It is *not* the same thing as the forbidden direction marks: never use LRM/RLM
  (U+200E/U+200F), never hand-reverse a phrase, and never pad with spaces. A pure-Latin string
  (`app_name`, `reader_title`) needs no isolate — the platform renders it LTR on its own — and a
  bare `%1$d` / `%1$s` placeholder is left alone, because it is substituted at runtime and
  isolating it makes the value detach from its sentence. `bidi_fa.py` in the local harness applies
  and verifies the isolates.
- Technical identifiers (project ids, emails, keys, log lines) stay selectable LTR runs; put a whole
  technical region in an explicit LTR container only when the region itself is technical, as the log
  list does.
- **Persian typography is the platform's.** Persian strings are rendered by the device's system font
  through the stock Material 3 type scale — the app bundles no font and overrides no `fontFamily`.
  If a Persian screen looks wrong, fix the layout, the string or the type *role*; do not add a font.

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
- [ ] New UI text uses `MaterialTheme.typography.*` (no `sp`/`fontFamily` at the call site), and any Latin run inside a Persian string is isolated (see `AgentMD.md`)
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
8. **JVM unit tests and `org.json`**: Android unit tests run against the mockable `android.jar`, whose `org.json` methods throw "not mocked". Any `:core` test that touches `JSONObject`/`JSONArray` needs `testImplementation("org.json:json:…")`; the real implementation takes classpath precedence over the stub. Pure-JVM code (`GeminiReaderSession`, `ReaderLanguages`, `ReaderNarrationModes`, `ReaderLanguageFlags`, `ChunkQueue`, `ReaderSpool`, `PdfReadingOrder`, `ReaderDisplayText`, `ReaderGates`, `ReaderState`/`ReaderPhase`, `AppLocales`, `ReaderStatusVisual`, `ReaderPager`, `ReaderPageText`, `CloudAuthState`, `CloudSelection`, `CloudUsage`, `CloudLoadState`, `CloudResult`, `CloudProject`, `CloudApiKey`, `CloudScopes`, `GoogleCloudDirectory`/`CloudParsers`, `CloudAuthFailureClassifier`, `UsageStatusVisual`, the whole `core/.../usage/` package, `ReaderVoice`, the whole `core/.../reader/` package (`ReaderBook`, `ReaderLibrary`, `ReaderBookCodec`, `BookSignals`/`BookSignalsReader`, `BookMatch`, `BookMetadata`, `BookIntel`, `BookMetadataSource`/`BookMetadataLookup`, `GoogleBooksSource`, `OpenLibrarySource`, `MetadataHttp`), and app-side `ReaderRecency`) must stay free of `android.*` APIs so it stays unit-testable without Robolectric. `PdfReadingOrder` deliberately takes plain geometry (`Fragment`) rather than `TextPosition` for exactly this reason — `TextExtractor` is the only place that bridges the two. The `core/.../reader/` package is the same rule applied to the library and the metadata layer: the repository (`ReaderBookRepository`), the document store and the Compose screens are the only Android edges, and every decision they make lives in the pure objects above. `ReaderPhase`/`ReaderState` live in their own file precisely so `ReaderGates` can be tested on a plain JVM; do not move them back into `ReaderService.kt`.
9. **Gradle enables JVM assertions**: the `Test` task runs its JVM with `-ea`, which turns on kotlinx-coroutines stack-trace recovery. Any exception crossing a `Deferred.await()` or `withTimeout` boundary comes back as a *copy* of the original object, so `assertSame` against an awaited cause passes under a plain `java -cp` run but fails under Gradle. When validating `:core` contract tests outside Gradle, always run with `-ea` to match CI, and never rely on awaited exception identity without preserving the original object first. The Reader sink path is guarded by `GeminiReaderSessionTest.sinkFailurePreservesOriginalExceptionAndSanitizesStatus`; keep it green.
10. **A Service's field initialisers run before `attachBaseContext`.** `applicationContext` is not available yet, so anything context-bound (`MediaSessionExternalPlayer`, `DubPlayback`) must be constructed in `onCreate` and held in a `lateinit var`. Building it as a field initialiser compiles and then crashes on a null base context. `stopAll()` guards those `lateinit`s with `::x.isInitialized` because it also runs from `onDestroy` when `onCreate` failed early.
11. **Synchronization reads a monotonic clock only.** `SystemClock.elapsedRealtimeNanos()` via `MonotonicClock`; `System.currentTimeMillis()` is forbidden in the synchronizer, because a user or network clock change would appear as a huge phantom drift and trigger a correction for nothing. `dubguard.py` enforces this.
12. **A constant source/dub offset is not drift — do not "correct" it.** The dub is *supposed* to sit one model-latency behind. Pausing the source for a constant offset would make Live Dub pause the user's video on every run for no reason. Only a growing offset (the dub falling behind its own measured baseline) is drift.
13. **`AudioTrack` buffer size is lip-sync latency.** Every byte buffered in `DubPlayback` delays the first dubbed word; the previous `minBuf * 8` (a full second at 24 kHz) was itself a latency bug. Keep it at `minBuf * 2` floored at ~125 ms unless a measured underrun says otherwise.

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
  - `CloudAuthStateTest` (core) — the token-expiry policy (no stated expiry, well in the future, expired, inside the skew window, overridden skew); only `Authorized` carries an account; only an in-flight authorization is busy; only a real grant counts as authorized; `Authorized` carries no token field; **the four user-facing states are mutually exclusive** (so a card branching on `isAuthorized`/`isBusy` can never light up two branches); configuration missing is distinct from every other failure; and the scope set is read-only, unique and never contains a write scope (see §5A).
  - `CloudOAuthConfigTest` (core) — "is Google sign-in configured in this build?": the shipped `REPLACE_…` placeholder is not configured, the check is case-insensitive and trims, an absent/blank value is not configured, and any non-placeholder value counts as present because only Google can judge validity (see §5A).
  - `ApiKeyFieldStateTest` (app) — the Settings key field never receives the stored secret: a configured install carries no key text and an empty field, the resting state never prefills, saving clears the draft and returns to the configured state, removing returns to an empty entry field, Replace opens an empty field and drops any abandoned draft, Cancel drops the draft, a blank draft is never saveable, and the only string the state can hold is what the user typed (see §5A).
  - `OriginalDarkPaletteTest` (app) — the restored dark identity is pinned to the verified historical values (`Gold #D4AF37`, `GoldDim #B8962E`, `NearBlack #0A0A0B`, `SurfaceDark #141416`, `Card #1C1C1F`, the status tones): the palette contains **no Google colour language** and none of the de-warmed neutrals the broken pass introduced, every value is fully opaque, the surface ramp is monotonic, the card is distinguishable from the page, the status roles are distinct from each other and from content, and **every text role clears WCAG AA (4.5:1) on the card and page it sits on**. It also pins the owner's **two semantic swaps** as swaps: the status and explanation hues exchanged places (`Success #7DD3FC`, `Explanation #3DDC84`, and neither is `VoxoraBrand.waveGreen #3DDC97`), and the two text roles exchanged their values (`OnSurface #C4BBA8`, `OnSurfaceVariant #F5F0E6`) with the documented consequence that the primary content role is deliberately the dimmer of the two (see §3).
  - `LightPaletteTest` (app) — Voxora Light is a complete, independent appearance: it declares its specified surfaces, accents, text/status tokens and gradient exactly; it is neither the dark theme nor the deleted Google-style palette; its background is genuinely light; every value is fully opaque; the card differs from its page; and **every text role clears WCAG AA (4.5:1) on the card and page it sits on**, with its supplied shortfalls asserted as documented facts rather than hidden (see §3).
  - `CloudParsersTest` (core) — project and key parsing (dropped when unaddressable, name falling back to the id), the userinfo `email` claim (present, absent, blank and malformed all handled without producing a blank account), Monitoring request-count summation across points and both value spellings, a genuinely empty window being `0`, and a malformed body being `null` rather than `0`; quota limit selection and its absent cases (see §5A).
  - `CloudUsageTest` (core) — every figure needs authorization before a grant; a refusal propagates to every figure; **an unavailable metric is never equal to a zero**; an observed count is reported while tokens, remaining quota and billing stay `NOT_OFFERED`; an explicit zero is a real zero; project usage is always sourced from Google, never from local observation (see §5A).
  - `GoogleCloudHttpTest` (core) — the authorized reads against `MockWebServer`: the token in the `Authorization` header and **never in the URL**; `401` as re-authorization, `403` as no access; a `5xx` and an unparseable `200` as failures rather than empty lists; an unreachable host as a network problem; the userinfo read behind the account email; key metadata from the project keys endpoint; the two-call usage read; a quota refusal not discarding a real request count; and a refused monitoring read refusing the whole usage read (see §5A).
  - `CloudLoadStateTest` (core) — the presentation mapping: empty ≠ loaded, and denied, unauthorized, network and failure each stay distinct (see §5A).
  - `CloudAuthFailureClassifierTest` (app) — cancellation, network, no-account, provider-unavailable, permission-denied, unsupported and unknown classification by status code, type name and message (see §5A).
  - `LogSeverityTest` (app) — the severity → role mapping behind the Logs colours: INFO → success, WARN → warning, ERROR → danger, DEBUG → neutral, every severity the product emits has a role, DEBUG is the only neutral one, an unrecognised severity is neutral and **never** success, and the lookup is case-insensitive (see §9).
  - `LogLineFormatTest` (app) — the one line shape every copy path shares: timestamp then level then `[tag]` then message, millisecond precision, every level padded to the same width so the tag starts in one column, the message appended verbatim (a log line is evidence), and empty fields still producing the shape. Pins the default `TimeZone` to UTC and restores it, so the expected string does not depend on where the suite runs (see §9).
  - `ChunkQueueTest`, `ReaderSpoolTest` — chunking and spool contracts.
  - `ReaderVoiceTest` (core) — the voice mapping: both semantic ids map to names in the documented 30-voice Gemini set, the two map to **different** voices, the ids are stable and distinct, normalization is total (null/blank/unknown → the default), and `isValid` accepts only stored ids (see §5).
  - `ReaderBookTest` (core) — one record's rules: progress is derived from the chunk and is exactly `1f` when completed, `displayChunk` is one-based, a move records `IN_PROGRESS` and stamps recency, a move is clamped to the document, a move on a **completed** book is refused (completion is a fact about the document) while an explicit replay clears it, `touched` does not move the position, matched metadata upgrades the display title but a blank one does not, and a lookup state is recorded without touching anything else (see §5).
  - `ReaderLibraryTest` (core) — the library's rules as pure functions: importing a second book leaves the first one's progress alone, a save for an unknown id is a no-op rather than a resurrection, a completed book stays completed, `mostRecent` is a decision (recency, then import time, then id) rather than list order, `byRecency` is deterministic, removal is exact, and an upsert of an existing id replaces rather than duplicates (see §5).
  - `ReaderBookCodecTest` (core) — the stored form: a full round-trip preserves every field, the document is versioned, a provider description is capped, an unknown page count stays unknown rather than becoming `0`, an out-of-range chunk is clamped, blank/absent/non-object/damaged payloads decode to an empty library rather than throwing, **one bad record does not discard the rest**, a minimal record decodes with sensible defaults, a newer version still decodes what it can, and the encoded payload contains only the expected keys (see §5).
  - `BookSignalsReaderTest` (core) — reading the document's own signals: ISBN-13 and ISBN-10 are found with separators, an `X` check digit is accepted, a number that looks like an ISBN but fails the checksum is rejected, a page number or year is not an ISBN, ISBN-10 converts to its ISBN-13 form, the title is the first line that is not page furniture (copyright/ISBN/contents/chapter/running header/sentence/bare number all rejected), the author comes from an explicit `by …` marker or an all-caps name line, **an ordinary sentence can never become an author**, a subtitle is not mistaken for one, and the file name is only ever the last-resort title (see §5).
  - `BookMatchTest` (core) — the matching policy, including its refusals: an exact ISBN is accepted alone, an ISBN-10 in the document matches the ISBN-13 in the catalogue, a candidate that **contradicts** the printed ISBN is rejected however similar the title, title + author is accepted (including `Dawkins, Richard` vs `Richard Dawkins`), a disagreeing author is rejected, a near-exact title with supporting metadata is accepted only when it is the sole candidate, a bare near-exact title is not enough, **two equally plausible candidates are never arbitrarily resolved** (`Ambiguous`), a strictly better candidate wins, a generic title can never carry a match, similarity ignores case/accents/punctuation/word order and never makes two different books look alike, and an `http` cover URL is not stored (see §5).
  - `BookIntelTest` (core) — the facts are source-backed or absent: an unknown field is omitted rather than shown as unknown, subjects are de-duplicated and order-preserving, fiction/non-fiction is read from subject headings alone (compound Google subjects included) and is `UNKNOWN` when the subjects do not say, the description is the provider's text or null, and the provider name is always known once metadata exists (see §5).
  - `GoogleBooksSourceTest` / `OpenLibrarySourceTest` (core) — the catalogue contracts against `MockWebServer`: the query is built from the strongest signal (an ISBN query is sent alone, otherwise fielded `intitle:`/`inauthor:` terms), a blank body is `NotFound` and an unreadable body is `PARSE_ERROR` (never "no such book"), `429` is `RATE_LIMITED` rather than a network fault, a `5xx` is `SERVER_ERROR`, an unreachable host is `OFFLINE`, a timeout is `TIMEOUT`, cancellation is rethrown, identifiers and cover URLs are parsed and only `https` covers are kept, Open Library's MARC language codes are mapped or dropped rather than displayed raw, and **no API key or authorization header is ever sent** (see §5).
  - `BookMetadataLookupTest` (core) — the chained policy: the primary is asked first, the fallback is asked only when it produced nothing usable, candidates from both sources are judged together so a title that agrees in both but an author that disagrees resolves to a refusal, a source that cannot answer does not abort the lookup, `NotFound` from one source is evidence while `Unavailable` from all of them is not, several equally plausible matches become `Ambiguous` rather than a match, and the `fetchedAt` timestamp is the injected clock's (see §5).
  - `ReaderRecencyTest` (app) — the "Last read …" rule: under a minute is "just now", the boundaries at minute/hour/day/week/month are exact, the count rounds **down** rather than overstating (90 seconds is "1 minute", 90 minutes is "1 hour"), and a clock that moved backwards reports "just now" rather than a negative age (see §5).
  - `ReaderDubIsolationTest` (app) — the scope rule as a test: no file under `app/.../reader/` imports the Dub package or names `DubService`, `SystemAudioCapture`, `DubPlayback`, `GeminiLiveSession`, `GeminiLiveConfig`, `FloatingBubbleService` or `DelayedScreenOverlay`. The symbol scan runs on **code with comments stripped**, because `ReaderService` and `ReaderBubbleService` legitimately *describe* the isolation in KDoc ("Mirrors `DubService.syncBubble`") and a comment is not a dependency; the import check runs on the raw text, since an import can never be inside a comment. A second case asserts the scan actually found the reader sources, so the test cannot pass vacuously if the directory moves (see §5, §6).
  - `DubSyncControllerTest` (app) — the adaptive synchronizer, driven by a fake clock and a fake player through a small pipeline model, with **no Android and no coroutines**. Pins: zero, small, 3 s, 4.5 s and 700 ms latencies all become a measured **floor** with **zero** corrections; a wobble inside the tolerance stays synced; a burst cannot raise the floor (it is corrected back to the demonstrated best) while the reported excess is the delay above it; a drift spike pauses the source once and resumes when caught up; a correction is bounded by `maxPauseNanos`; corrections are rate-limited and spaced by the cooldown; a controllable playing source may be corrected (the video fallback); a user pause is never fought and a user resume re-engages; an uncontrollable source is audio-only and never paused; losing the session releases the source; a stalled dub withdraws the claim and never leaves the source paused; stopping while synchronized leaves the source alone, stopping while correcting resumes it; and a Gemini reconnect releases the source, re-measures and settles (see §6).
  - `SourceClockTest` (app) — the source clock's contract: the content clock converts the device frame count to audio time; frames captured while silent do not advance it; uneven reads lose no frames; the capture latency is the device timestamp subtracted from now; without a device timestamp the latency is **unavailable, never zero**; a frame counter that goes backwards cannot shrink the clock; and `reset` clears the run (see §6).
  - `PipelineLatencyEstimatorTest` (app) — the floor's contract: there is no floor before the first sample; the floor is the smallest offset in the window; **a burst raises the current offset but never the floor**; the floor forgets an old sample once the window has passed; the current offset is smoothed rather than raw; excess is zero when the offset holds at the floor; and `reset` clears it (see §6).
  - `PlaybackRatePolicyTest` (app) — the rate trim's contract: an excess inside the dead band leaves the rate at unity; an excess **with a backlog** ramps it to the ceiling; an excess with **nothing queued** keeps it at unity (speeding up with nothing to drain would only underrun); it moves at most one step per cooldown; it returns to unity when the excess clears; it never leaves its bounds; and `reset` returns to unity (see §6).
  - `SyncDiagnosticsTest` (app) — the diagnostic line's contract: every requested field is present; an unavailable device measurement renders as unavailable rather than a fabricated zero; and the rate uses a dot whatever the device locale (see §6).
  - `SourceVolumeDuckTest` (app) — the source ducking contract: the source is reduced but never muted, a low level keeps one audible step, there is nothing to duck into at the bottom of the range, an unusable range is left alone, restoring returns exactly the saved level, and every duckable level produces a strictly lower positive level (see §6).
  - `LatencyTimelineTest` (app) — the instrumentation contract: first and last occurrences tracked separately, a missing stage reported as absent rather than zero, a backwards timestamp reported as absent rather than negative, the summary naming every stage and its milliseconds, the write/played stages kept distinct, accounted drops, rate-limited logging with a forced override, the context appended verbatim, `reset`, and marking through the injected monotonic clock (see §6).
  - `PlaybackTimelineTest` (app) — the dub playhead and backlog contract, driven by a fake clock and a fake device playhead: a steady realtime stream is played in full with no drops and no starvation; the backlog reflects the output buffer rather than the write cursor; a burst is trimmed back inside the tolerance instead of becoming permanent delay, while still playing what it can; a single late chunk is absorbed without discarding anything; variable chunk sizes accumulate no backlog; running dry loosens the tolerance and a quiet stretch tightens it back, both clamped; a reconnect rebases the playhead rather than reporting a phantom backlog and does not carry the previous session's counters; a playhead that goes backwards is rebased; stopping withdraws the policy; discarded audio is accounted exactly; a concurrent arrival/playhead smoke test asserts the backlog stays bounded with two threads mutating the timeline at once; and the configuration rejects an underrun threshold that would make a healthy pipeline look starved (see §6).
  - `PlaybackHeadTest` (app) — the `AudioTrack` head unwrap: the first read becomes the total, successive reads accumulate, a repeated read does not advance, a wrapped head continues past four billion frames, a second wrap continues correctly, and the unwrapped total is always monotonic (see §6).
  - `SyncStatusVisualTest` (app) — the Live Dub status-tone contract: only a synced pipeline is active, measuring and catching up are in-progress rather than success, and audio-only/unavailable are **neutral, never a failure**; the external-video note appears only once synced; the media-control opt-in is offered only while live and without the grant (see §6).
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
- The Settings/Gemini reframe (`f5088a8`), the background-playback surface (`80a0244`), the wording alignment (`9007129`) and the documentation commit `c6f5727` on top are CI-verified at `c6f5727c3` (Android CI runs `35351894535` (run **#100**, event `push`) and `35351900352` (run **#101**, event `pull_request`), created 2026-09-18T13:43Z, completed 13:46Z): **both jobs success in both runs** — `Unit tests` including `Run unit tests`, and `Assemble debug APK` including `Assemble debug` and `Upload debug APK`. This is the load-bearing check for the half of the cycle the local harness cannot compile: `Assemble debug` is the only real compile check for `CloudAccountCard.kt`/`SettingsScreen.kt`/`ApiUsageScreen.kt` after the reorder and relabel, for the new `ReaderBubbleService.kt` (Hilt `@AndroidEntryPoint` service, `WindowManager` overlay, `MediaStyle`), for the `ReaderService` transport rework (`PendingIntent.getForegroundService`, `MediaSession` callbacks, the pause-keeps-the-service lifecycle), and for the new `readerBubble` DataStore preference in `UserPrefs.kt`. **No real-device testing was performed for this half either**; the bubble, its overlay-permission path, and every notification transport control are device-verification items.
- The Reader voice selection, persistent multi-book library, chunk-level resume and Book Intelligence slice (`de89612`, documentation `cb07e2a`) is CI-verified at `8293a98` (Android CI push run `35439870596` and pull_request run `35439873171`, created 2026-09-19T11:05Z): **both jobs success in both runs** — `Unit tests` 10/10 steps including `Run unit tests`, and `Assemble debug APK` 14/14 steps including `Assemble debug` and `Upload debug APK`. The `Assemble debug` step is the only real compile check for `ReaderVoiceSection.kt`, `ReaderLibrarySection.kt`, `BookIntelCard.kt` and `ReaderCoverImage.kt`; the dev server has no Compose artifacts and must never run Gradle. The push run's `Unit tests` job is the CI counterpart of the local **642 tests OK across 55 classes**. **No real-device testing was performed**; the voice's perceived character, the cover loader, the library layout under RTL and resume across a real process death are device-verification items.
- **The first push of that slice FAILED both jobs on three defects that were in the committed tree only**, and they are worth recording because two of them were outside every local check. (1) `TextExtractor.kt` had two imports collapsed onto one line — `import …PDFBoxResourceLoaderimport com.tom_roush…PDDocument` — an invalid Kotlin statement; fixed in `22a1b69`. (2) `ReaderVoiceSection.kt` used `VoxoraColors.explanation` with no import; (3) `TextExtractor.kt` typed `ExtractedDocument.signals` as `BookSignals` with no import; both fixed in `8293a98`. The exact errors (`:app:compileDebugKotlin FAILED` with `Unresolved reference 'VoxoraColors'` / `'BookSignals'`) were read from the job logs, not guessed. The structural reason they escaped: `validate-reader-android.sh` substitutes a **stub** for `TextExtractor.kt` (the harness has no PDFBox jar) and does not cover the Compose files at all, and the import guard watched only Compose/AndroidX symbols — a **project** symbol like `VoxoraColors` was invisible to it. `checkimports.py` now also carries a `WATCHED_PROJECT` list (`VoxoraColors`, `VoxoraTheme`, `VoxoraLog`, the `core.reader` types and `ReaderVoice`) checked on any bare mention, member access included, after stripping comments and string literals; it was verified to report clean on the fixed tree and to report exactly those two symbols against the `de89612` files.
- The theme restore and the two light test variants (`bff6ca9`) are CI-verified at `bff6ca9cc` (Android CI run `35381294377`, event `push`, branch `feat/reader-segmented-spooling`, created 2026-09-18T18:37:47Z, completed 18:40:18Z): **both jobs success** — `Unit tests` including `Run unit tests` (job `105717811438`) and `Assemble debug APK` including `Assemble debug` and `Upload debug APK` (job `105717811622`). The `Assemble debug` step is the only real compile check for the restored colour schemes in `Theme.kt`, the three palette files, the `ThemeSelector` labels in `SettingsScreen.kt` and the `ThemeMode` wiring in `MainActivity.kt`; the dev server has no Compose artifacts and must never run Gradle. The push run's `Unit tests` job is the CI counterpart of the local **478 tests OK across 43 classes**. **No real-device testing was performed**; the themes' appearance, the selector under Persian/RTL, and each light palette's contrast are device-verification items. (That commit shipped two light appearances; the second, `LIGHT_TEST_1`, was later **removed entirely** — see §3 and the entry below.)
- **The two-appearance consolidation, the Dark semantic swaps, the redesigned "Your Books" library and the persisted chunk cache (`99a54ac`) failed `Unit tests` on the first push, and again on the fix, in two rounds of the same underlying fault: the Android unit-test runtime stubs the APIs app code calls.** `Assemble debug APK` passed both times, so the Compose changes compiled. **Round 1** — 19 of 388 failing, all in the new `ReaderChunkCacheTest`, all `RuntimeException` at its shared `store` helper: the app module's unit tests run against a **stubbed `org.json`**, and `ReaderChunkCache` is the first app-module code any unit test has reached that writes JSON. `:core` already declared `testImplementation("org.json:json:20240303")`; `app` did not, and the same dependency was added. **Round 2** — 1 of 388 failing, `anUnreadableEntryIsAMissRatherThanAFailure` at `ReaderChunkCacheTest.kt:217`: with the JSON fixed, that test reached the cache's error path, which logs through `VoxoraLog` → `android.util.Log`, itself a stub that throws. `testOptions { unitTests.isReturnDefaultValues = true }` was added to `app/build.gradle.kts` (test-only). **Why the local harness missed both:** `validate-cloud.sh` supplies a real `json-20240303.jar` *and* a plain-JVM `VoxoraLog` stub, so neither stub exists locally — the same family as the §5 defects above, faults that live only in the committed tree. New `apptestguard.py` closes both statically and was verified both ways. **The cycle is CI-green at `1b2da81`** — push run `35451720666` and `pull_request` run `35451723199`, both jobs success in both runs (`Unit tests` 10/10 including `Run unit tests`, `Assemble debug APK` 14/14 including `Assemble debug` and `Upload debug APK`).
- Local pre-CI validation without Gradle is allowed and encouraged: compile the changed pure-JVM/Android sources with `kotlinc` against the pinned dependency jars and run the JUnit classes directly with `-ea`. This never substitutes for CI — the branch must still go green in Actions. The `1504669` pass was validated locally this way: `kotlinc 2.0.21` plus JUnit `-ea` gave **134 tests OK** across `ReaderDisplayLanguageTest`, `ReaderDisplayRefreshTest`, `ReaderStartupGatesTest`, `LanguageCatalogTest`, `AppLocalesTest`, `ReaderPipelineOrderTest`, `ChunkQueueTest`, `PdfReadingOrderTest`, `ReaderSpoolTest`, `ReaderNarrationModesTest`, `ReaderLanguageFlagsTest` and `GeminiReaderSessionTest`. Two harness details matter and cost a round each when forgotten: `internal` declarations need `-Xfriend-paths=<main-out>` on the test compile, and `ReaderSpool`'s `VoxoraLog` dependency needs a plain-JVM stub because the real one touches `android.util.Log`.
- Prefer pure-JVM, deterministic tests with `TemporaryFolder` for file-backed code; avoid Robolectric unless an Android API genuinely cannot be avoided.
- **The local harness cannot compile Compose, so run an import guard before pushing.** A missing `import androidx.compose.runtime.LaunchedEffect` reached CI once and failed **both** jobs; because the dev server has no Compose artifacts, nothing local caught it. It also produced four errors for one mistake — the unresolved reference plus three cascading "suspend function should be called only from a coroutine", since without `LaunchedEffect` the lambda is not a suspend scope. Before pushing, check that no file uses a Compose or AndroidX symbol it has not imported. **Read CI job logs with `GET /repos/…/actions/jobs/<job_id>/logs` and an `Authorization` header** — unauthenticated it returns 403 (`Must have admin rights to Repository`), and `gh` is not logged in in this environment; `~/.git-credentials` holds a token that works. The response is a 302 to a signed URL, so follow the redirect *without* the `Authorization` header. The useful line is `e: file:///…/File.kt:97:5 Unresolved reference 'X'.`
- **Seven local checks worth running on every change, all cheap and all caught real bugs:** a `R.string.*` cross-check of every Kotlin reference against `values/` and `values-fa/` (it caught a `reader_page_chunk` key that no locale declared); an unused-import sweep of changed files; a **colour-literal guard** that fails if `Color(0x…)` or a named `Color.White`/`Color.Black`/… appears anywhere outside `ui/theme/Theme.kt` and the three `ui/theme/*Palette.kt` files (`Color.Transparent` is allowed); a **theme guard** (`themeguard.py`) that fails on a `ThemeMode` with no branch in `Theme.kt`, on a palette that assigns another palette's value, on a leftover of a deleted palette, and on a bundled font being reintroduced (`res/font/` or a `FontFamily` override in the theme layer); a **bidi guard** (`bidi_fa.py`) that isolates every Latin run embedded in a Persian string and verifies the format specifiers are untouched; an **app-test runtime guard** (`apptestguard.py`) that fails if any `app/src/main` Kotlin file imports `org.json.*` while `app/build.gradle.kts` declares no real `org.json` on the **unit-test** classpath, or imports `android.util.Log` while the module does not set `unitTests.isReturnDefaultValues = true` (the Android test runtime stubs both, and the local harness supplies a real jar and a plain-JVM `VoxoraLog`, so only CI sees the difference); and a **dub guard** (`dubguard.py`) that fails if the disabled `DelayedScreenOverlay` is constructed, if a fixed-lag constant or a `Thread.sleep` returns to `dub/`, if the pure sync core (including the playback timeline and head unwrap) imports `android.*`, if `MediaSessionManager` is used outside its adapter, if the synchronizer reads wall-clock time, if `DubService` writes audio without consulting `PlaybackTimeline.onChunkArrived` and branching on `ChunkAction.PLAY`, if it goes back to feeding the synchronizer the write cursor instead of the playhead, or if `DubPlayback` stops reading the device playback position. Each of those rules was verified to fail on an injected violation. The harness cannot compile Compose, so these guards are what stop a stray hex, a dead theme, a mangled Persian string, a stubbed-API test failure or a reintroduced fixed delay from reaching CI.

---

*Last updated: auto-generated by Claude for Voxora project — Live Dub's synchronization was rebuilt
around a **measured latency floor** instead of an accepted baseline: the source clock now comes from
the device's own capture frame position and `AudioRecord.getTimestamp`, the model corrects the
*excess above the floor* rather than any deviation from whatever offset happened to settle, the
Gemini hand-off buffer was cut to 4 chunks and its occupancy is now counted (it was the largest
invisible delay), the playback tolerance ceiling dropped from 800 ms to 450 ms, a bounded ±3 %
playback-rate trim drains small backlogs without skipping audio, and a rate-limited `[DUB_SYNC]` line
reports every stage with real numbers; the `LIGHT_TEST_2` slot holds the final **Voxora Light**
appearance (warm cream, gold leaf, warm brown shadows, gold-at-12 % glow), built as a completely
independent light UI variant with the owner's palette used verbatim and its measured WCAG shortfalls
documented rather than designed around; the accent wash behind icons became the per-appearance
`VoxoraColors.glow` role so a light screen can never inherit the dark gold wash; the canonical Persian
writing/localization standard was made permanent project law (§10 here and `AgentMD.md` §2); and
earlier in the line, the bundled Vazirmatn type stack removed so the app renders with the platform
font through the stock Material 3 type scale, the Persian strings audited and bidi-isolated for mixed
Persian/Latin text, and Live Dub given an adaptive source/dub synchronization layer that measures the
pipeline's own latency (no fixed delay), corrects drift through an opt-in media-session layer,
accounts for dropped audio, and logs monotonic stage timings — together with two real latency fixes:
the per-emission audio coroutine replaced by one ordered consumer, and the one-second `AudioTrack`
buffer reduced to ~125 ms. The standing rules for future UI work live in `AgentMD.md`; the Live Dub
contract is §6 here. **No real-device testing was performed for the Live Dub synchronization work or
for the Voxora Light appearance** — the sync behaviour, the media-session pause/resume, the measured
floor and the actual share of the delay that is Gemini's, the rate trim's pitch effect on devices
that resample, the light appearance's on-device look and the gold-on-cream legibility at small sizes
are all device-verification items.*
