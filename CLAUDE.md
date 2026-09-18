# CLAUDE.md — Voxora-Android Handoff File

This file exists so that any new Claude account can immediately
understand the full project context by reading this file + the
live GitHub repository state.

If you are a new Claude instance reading this:
1. Connect the GitHub MCP connector (URL: https://api.githubcopilot.com/mcp/)
2. Read AGENTS.md — it is the canonical project law
3. Read the latest commits on main
4. Read this file fully
5. Then continue from the CURRENT STATE section below

---

## WHO YOU ARE IN THIS WORKFLOW

You are the STRATEGY / REVIEW / ROADMAP agent for the Voxora product.

You are NOT the coding agent.

The coding agent is OpenCode running on a Linux server connected to
OpenRouter (currently using a free model window).

Your job:
- Read the real GitHub state
- Understand what changed
- Identify the most important problem or next step
- Write a precise English prompt for OpenCode
- Review what OpenCode actually committed
- Repeat

The owner (Mo3iBest) is the final decision-maker.
The owner speaks Persian. You respond in Persian.
OpenCode prompts must be written in English.

---

## REPOSITORIES

Primary: https://github.com/mo3iiibest77-hub/Voxora-Android
Branch: main
Package: com.voxora.app

Sister (context only, do NOT mix):
https://github.com/mo3iiibest77-hub/ParsLiveDub
(Chrome extension — architectural reference only)

---

## THE PRODUCT

Voxora is a global Android application with two main features:

### A) LIVE DUB — PROTECTED, DO NOT TOUCH
Captures system audio from other Android apps and produces
translated/dubbed audio via Gemini Live WebSocket.

Pipeline:
Android System Audio → MediaProjection/AudioPlaybackCapture →
PCM → Gemini Live translation WebSocket → translated PCM → playback

Protected files (never modify without explicit owner request):
- app/.../dub/**
- core/.../gemini/GeminiLiveSession.kt
- core/.../GeminiLiveConfig.kt

### B) VOXORA READER — CURRENT DEVELOPMENT FOCUS
User picks a PDF/TXT → text extracted → chunked → Gemini narrates
each chunk as audio → PCM → AudioTrack → background foreground service.

Pipeline:
PDF/TXT → TextExtractor (PDFBox + PdfReadingOrder) → ChunkQueue →
GeminiReaderSession (ReaderNarrationModes instruction) →
PCM → ReaderPlayback (AudioTrack) → foreground media service

Reader files:
- app/.../reader/ReaderController.kt (singleton, owns generation)
- app/.../reader/ReaderService.kt (foreground media service)
- app/.../reader/ReaderPlayback.kt (AudioTrack, USAGE_MEDIA)
- app/.../reader/ReaderViewModel.kt
- app/.../reader/ReaderScreen.kt
- app/.../reader/TextExtractor.kt (PDF/TXT extraction, returns ExtractedDocument)
- app/.../reader/PdfReadingOrder.kt (pure-JVM reading-order reconstruction)
- core/.../gemini/GeminiReaderSession.kt (Gemini WebSocket session)
- core/.../gemini/ReaderNarrationModes.kt (Faithful/Fluent contract + prompt)
- core/.../gemini/ReaderLanguageFlags.kt (deterministic flag mapping)

Key policy:
- Gemini API key from UserPrefs only (no external backend)
- Reader MUST be isolated from Live Dub
- Background playback like Live Dub (foreground service + notification)
- Volume follows hardware media keys (USAGE_MEDIA)
- Narration modes are a tested contract: Faithful preserves wording, Fluent
  rewrites for clarity — neither may summarize or invent. See AGENTS.md §5.
- The reading text is rendered in the selected narration language (derived from the
  Gemini transcript, keyed by language) and is a separate UI surface from the live
  narration transcript; they are never merged. The canonical extracted document is
  never rewritten. See the display-language change below.

---

## DEVELOPMENT ENVIRONMENT

- Coding agent: OpenCode + OpenRouter (free model window — limited)
- Build: GitHub Actions ONLY (server is weak, NO local Gradle)
- Validation path: commit → push main → GitHub Actions → APK
- Owner installs APK and reports real device test results

NEVER tell OpenCode to run:
- ./gradlew assembleDebug
- ./gradlew lintDebug
- any local Gradle task

---

## WORKFLOW LOOP
Owner reports bug/idea/log
↓
You read real GitHub state (use GitHub connector)
↓
Identify root cause or next priority
↓
Write precise English prompt for OpenCode
↓
Owner pastes prompt into OpenCode on server
↓
OpenCode commits + pushes to main
↓
Owner says "check" or "done" or pastes log
↓
You read GitHub again → review actual changes
↓
Choose next step → repeat
When owner says "status" → read GitHub, report state, give next prompt.
When owner says "check" or "push done" → read latest commit on main.
When owner pastes a log → treat it as runtime evidence, verify with GitHub.
When owner reports a bug → diagnose from code, write targeted fix prompt.

---

## CURRENT STATE (update this section after each major change)

> Naming note: this repository has no `CloudMD`/`AgentMD` files. The canonical
> pair is `AGENTS.md` (project law / architecture record) and this `CLAUDE.md`
> (handoff / cloud context). Update those two — do not add duplicate docs.

### Actual repository state (inspected, not assumed):
- `main`: `1f0a719 fix(reader): fix suspend output language persistence`.
- `feat/reader-segmented-spooling` (the implementation branch) HEAD is
  `db831d2 feat(onboarding): introduce both products and the shared key`, on top of
  `4d9608d feat(settings): harden the account card and add the API usage dashboard` and
  `982786b feat(usage): add the Gemini key probe and observed-usage model`, which sit on
  `70571cf`/`ee6d31b`/`e88f85a`/`7d9a51b`/`1b9eb4f`, `f66d0dd`, `14bd894`, the three code
  commits `1504669`/`882ab27`/`1c0df1e`, and the earlier `06a3bb3`/`7f47805`/`a9b9336`. It
  carries the Reader quality pass —
  `925ee1d feat(reader): redesign the reader screen`,
  `72527e7 feat(reader): expose the loaded document name`,
  `f644d2f feat(reader): map language catalog to deterministic flags`,
  `b0d556d fix(reader): preserve pdf reading order`,
  `a74db6c fix(reader): define fluent and faithful narration semantics` — plus the
  reading-order fix `6d826e0 fix(reader): de-interleave pdf columns when a page
  carries a running header` and the documentation commits. **31 commits ahead of
  `main` (`1f0a719`)**, pushed to `origin`, and **not merged**. CI is green at
  `7f47805` (run `35294468431`, both jobs) and at `707cc3c` (run #66); every later
  slice is CI-verified separately.
- `feat/ci-feature-branch` = `76f0c96` + `dcbf852 ci: run Android CI on feature
  branch`, with **PR #2 open to `main`** (still open; deliberately NOT merged).
- **PR #3 "Feat/reader segmented spooling"** (`feat/reader-segmented-spooling` →
  `main`) is also **open and not merged**, created 2026-09-18T02:20:08Z. It tracks this
  branch, so its head moves with every push, and it makes CI run twice per push
  (`push` + `pull_request`). **Do not merge PR #2 or PR #3.**
- IMPORTANT — how CI actually runs: the failing run `35279422099`
  (2026-09-17T21:56Z) was triggered by **`pull_request` from
  `feat/ci-feature-branch`**, not by a push. `android-ci.yml` triggers on pushes
  to `main` + `feat/reader-segmented-spooling`, on PRs to `main`, and on manual
  dispatch. Pushing to a branch with no open PR and an old workflow file
  produces **no run at all**.
- Reader is implemented well **beyond** the roadmap's Milestone 5–8 scope:
  `ReaderController` (singleton, generation-guarded), segmented `ReaderSpool`,
  `ChunkQueue` (500-word chunks + 80-word narration units), per-chunk
  `GeminiReaderSession`, language catalog, document persistence, and a
  foreground `mediaPlayback` service. Live Dub is untouched.
- **No Chinese text exists anywhere in the app.** An exhaustive CJK scan of
  `app/src/main`, `core/src/main` and every `res/**/*.xml` returns zero hits. The
  reported "Chinese labels" were a *locale* defect, not a string defect.
- **Google Sign-In is a local identity layer, not an account system**, and it is
  **not configured in this build**: `default_web_client_id` still holds
  `REPLACE_WITH_GOOGLE_WEB_CLIENT_ID`. Guest use with an API key is the working path.

### Last change (this session) — Gemini API usage, Google auth hardening, onboarding

Three commits, all pushed to `feat/reader-segmented-spooling`:

- `982786b feat(usage): add the Gemini key probe and observed-usage model`
- `4d9608d feat(settings): harden the account card and add the API usage dashboard`
- `db831d2 feat(onboarding): introduce both products and the shared key`

**The governing decision: an API key cannot read project quota, so nothing pretends it can.**
Gemini quota, billing and project identity belong to the Google Cloud **project**, not to an API
key. Reading them needs OAuth credentials for the owning project, which this app does not hold.
Rather than print a reassuring `0`, the new dashboard names each unavailable figure and says why.
`ApiUsageSnapshot.projectQuota` and `.billing` are therefore always
`UsageUnavailable.AUTH_REQUIRED`, and `accountLinkedToKey` is hard-coded `false` — nothing in this
app can verify that a signed-in Google account owns the configured key, so the UI must not imply it.

**DONE — a real, non-billable key check.** `GeminiKeyProbe` calls the documented `models.list`
endpoint (`https://generativelanguage.googleapis.com/v1beta/models`). It is official, it accepts an
API key, and it generates no content — so it consumes no tokens and cannot incur generation
charges. A generative endpoint is deliberately never used to test a key. Two details matter:

- the key travels in the **`x-goog-api-key` header, not a `?key=` query parameter**, so the secret
  never appears in a URL — the part of a request most likely to reach a log, a crash report or a
  proxy trace;
- classification **prefers the body's `error.status` over the HTTP code**, because the code alone is
  ambiguous: Google returns `400` for both a malformed request and an invalid key, and `403` for
  both a blocked API and a permission problem. The result is connected / invalid key / unauthorized
  / quota limited / network unavailable / service error / configuration incomplete / unknown. An
  unparseable body falls back to the code; an unrecognisable answer is `UNKNOWN`, never a guess. A
  blank, absent or placeholder key short-circuits to `configuration incomplete` **without any
  network call**.

**DONE — request-level usage, captured honestly.** `GeminiUsageMetadata.fromMessage` reads the
optional `usageMetadata` that Live API server messages *may* carry. It is read from the **top level
of the message, before the `serverContent` early-return** in `handleMessage`, because a usage-only
message would otherwise be dropped. Both wire spellings of the output count are accepted (Gemini
Live's `responseTokenCount`, Vertex's `candidatesTokenCount`). Crucially, `optInt` alone would have
been wrong: it cannot tell "the server said zero" from "the server said nothing", so a helper keeps
only values that were actually present — a field the server did not send stays `null`, and a message
with nothing recognisable records nothing. The session exposes this through an `onUsage` hook
matching the existing `onLog` style, and **nothing in the narration path reads usage back**, so
recording cannot influence audio, ordering or timing.

**DONE — a bounded ledger that cannot leak.** `GeminiUsageLedger` holds only Voxora's own request
counts, whatever token usage the server reported, and short error categories. No key, no ID token,
no prompt, no document text, no audio, no raw response. It is bounded to 90 days, batched to one
disk write per ten records with a forced `flush()` at each run boundary, and a damaged, truncated or
future-versioned payload degrades to an empty ledger rather than throwing — losing usage history
must never block Settings. Failures record a **category only**, never the message, because an HTTP
client can put the key-bearing request URL into an exception message;
`ReaderController.usageCategoryFor` prefers the `NarrationFailure` string resource over the
exception. Usage reported by a *failed* turn is discarded, since a turn that produced no audio
produced no usable output.

**DONE — Google auth hardened.** Three real defects were fixed, not just tidied:

1. **The failure was discarded entirely.** `SettingsScreen` only acted on `result.ok`, so a build
   with no Web client ID looked as though the button did nothing. Failures are now classified
   (configuration missing / cancelled / no credential / provider unavailable / network /
   unsupported credential / unknown) and each gets its own sentence, because the next action
   differs.
2. **`android.util.Log` was used directly**, violating AGENTS.md §9. It now goes through
   `VoxoraLog` and carries only the classification and the exception's class name — never a message
   verbatim, which a provider could populate with credential material. The ID token is never read
   into a variable, persisted or logged.
3. **No loading state and no re-entry guard.** A second tap could launch an overlapping credential
   request and race two results onto the same card. Both buttons are now disabled while an
   operation is in flight and a second tap is ignored.

`AuthUiState` replaces the loose `signedIn`/`displayName`/`email` booleans with a sealed hierarchy,
so **stale state after sign-out is unrepresentable rather than merely unlikely**: only `SignedIn`
carries an identity. `signOut()` always reports `SignedOut` even when the provider call throws,
because showing an account the user removed is worse than a failed cleanup. Configuration is not
failure: while `default_web_client_id` holds the shipped placeholder, `signIn()` returns
`CONFIGURATION_MISSING` **without touching the credential provider**, and guest use keeps working.

**DONE — onboarding now matches the product.** The first-run flow described only Live Dub and never
mentioned the Reader, which stopped being accurate when the Reader shipped. It is now four pages:
Live Dub, the Reader, what the app can and cannot access, and the Gemini key. The privacy page states
that the key, documents and audio go only to Google's Gemini API because Voxora has no server of its
own, and that screen capture belongs to Live Dub alone. The key page says one key powers both
features, that it is stored on the device, and that Google sign-in is optional. Hardcoded `sp` sizes
were replaced with typography tokens and the screen gained the preview it was missing.

**What was deliberately NOT done.** The Reader was left alone: the previous session verified its
status mapping, error copy (every Reader error already names an actionable next step), language
centralisation and background lifecycle, and §17 of the brief says not to redesign what works. No
translation-before-audio pipeline was introduced, no artificial wait was added, and the display
layer remains derived and one-way.

**Tests added (121 new, 296 total).** `ApiKeyMaskTest` (8), `GeminiUsageMetadataTest` (11),
`GeminiKeyClassifierTest` (13), `GeminiKeyProbeTest` (12), `GeminiUsageLedgerTest` (16),
`UsageRecorderTest` (12), `ApiUsageSnapshotTest` (14), `UsageFailureCategoryTest` (9),
`UsageStatusVisualTest` (10), `AuthUiStateTest` (13). All pure JVM; the probe tests use a **fake
transport**, so nothing asserts a real quota, a real model count or a real error from Google.

Five real defects were found by the tests while writing them, and fixed in the source rather than
weakened in the test: the `UsageFailureCategory` ordering misread "Audio output is unavailable" as a
network problem; it did not match a session failure in the *message*; `AuthFailureClassifier` did
not recognise "timed out"; `ApiUsageScreen` called the suspend `prefs.signedIn.first()` inside a
non-suspend `takeIf`; and my own retention-date arithmetic was wrong.

**Validation actually performed (no Gradle was run).** Compiled the pure-JVM sources with
`kotlinc 2.0.21` against the pinned dependency jars and ran the JUnit classes directly with `-ea`
(matching Gradle's test JVM): **296 tests, OK across 26 classes** — the new suites plus every
earlier Reader/core suite, which is what proves the `GeminiReaderSession` change did not regress the
Reader. Additional static checks: a full `R.string.*` cross-check against both locales (now 227 keys
in `values/`, 226 in `values-fa/`, with matching format args), an unused-import sweep of every
changed file, and a protected-system audit confirming no file under `dub/**`, `GeminiLiveSession.kt`
or `GeminiLiveConfig.kt` was touched.

**CI status: GREEN — VERIFIED, after one real failure that CI caught and the local harness could not.**

- **Run #80 (`35304003121`, event `push`, `db831d24`, 2026-09-18T03:39:08Z) — FAILED, both jobs.**
  `Unit tests` failed at step 6 `Run unit tests` and `Assemble debug APK` at step 8
  `Assemble debug`. The cause was a **single missing import** in the new `ApiUsageScreen.kt`:
  `LaunchedEffect`. It produced four errors — the unresolved reference plus three cascading
  "suspend function should be called only from a coroutine", because without `LaunchedEffect` the
  lambda was not a suspend scope. The dev server has no Compose artifacts, so nothing local could
  have caught it; this is the documented limitation of local validation, not a surprise.
- **Fix:** `4176883 fix(settings): import LaunchedEffect in the usage screen`.
- **Run #82 (`35304322802`, event `push`, `4176883`, 2026-09-18T03:4xZ) — SUCCESS.**
  `Unit tests` 10/10 steps including `Run unit tests`; `Assemble debug APK` 14/14 steps including
  `Assemble debug` and `Upload debug APK`.
- **Run #83 (`35304324635`, event `pull_request`, `4176883`) — SUCCESS**, both jobs, the same 10/10
  and 14/14. PR #3 tracks this branch, so both trigger paths run and both are green.

`android-ci.yml` has no `continue-on-error`, so a green `Run unit tests` step is a genuine pass and
a green `Assemble debug` step is a genuine compile check of the Compose changes the dev server
cannot compile.

**Validation actually performed locally (no Gradle was run).** `kotlinc 2.0.21` + JUnit `-ea`:
**296 tests OK across 26 classes**. Plus: a Compose/AndroidX **import guard** (added after the CI
failure above, and verified to flag that exact bug), a full `R.string.*` cross-check against both
locales (227 keys in `values/`, 226 in `values-fa/`, matching format args), an unused-import sweep
of every changed file, and a protected-system audit.

**Real-device verification was NOT performed** — nothing in this change was tested on a physical
device, and no device behaviour may be claimed.


**IN PROGRESS:** nothing — all commits are implemented, tested, pushed and CI-verified.

**BLOCKED:** nothing in the code. One item is blocked on **external configuration**, not on code:

- **`default_web_client_id` is still `REPLACE_WITH_GOOGLE_WEB_CLIENT_ID`.** Google Sign-In cannot
  work until the owner creates a Web client ID in Google Cloud Console (OAuth consent screen
  configured, an **Android** OAuth client with package `com.voxora.app` and the debug/release
  SHA-1 fingerprints registered, and a **Web** client whose ID goes into
  `app/src/main/res/values/strings.xml` at `default_web_client_id`). The code handles the missing
  value correctly and says so in the UI; it cannot be finished from inside the repository. Do not
  invent a client ID.
- **Project-level quota and billing remain unreadable by design.** Enabling them would require a
  Google Cloud service account or OAuth credentials plus the Cloud Quotas / Service Usage / Billing
  APIs, and a project identifier. That is a product/architecture decision, not a coding gap, and the
  dashboard is built to represent it as `AUTH_REQUIRED` today and to accept a real provider later.

**NEXT (do not start before the above is read):**
- **Real-device check — not performed, and must not be claimed.** Everything below needs a device:
  (1) the Reader page turn, the active halo, RTL swipe direction and the chunk-start language swap
  from the previous session; (2) "Test connection" against a real key, confirming a good key reads
  **Connected** and a deliberately broken one reads **Invalid key** rather than a generic failure;
  (3) that the usage screen shows masked-key text and no full key anywhere; (4) that a narrated
  chunk makes "Requests today" increase; (5) the four-page onboarding in both English and Persian,
  including that the Reader page renders correctly under RTL; (6) sign-in, which will report
  **configuration incomplete** until the client ID is supplied — that is the expected result, not a
  bug.
- If the owner supplies a real `default_web_client_id`, re-check sign-in on device and confirm the
  success and cancellation paths both leave the card in a correct state.
- A future authenticated quota provider should slot in behind `ApiUsageSnapshot`; the model already
  has the vocabulary for it. Do not add a Cloud API call behind an API key — it cannot succeed.
- The Reader's narration language picker still offers the full 99-code catalog. If the narration
  model supports a narrower set, encode that as a **capability filter over `ReaderLanguages.all`**
  with a documented source — never a second hand-typed list. This needs model information the
  repository does not contain, so no filter was invented.

### Previous change — Reader as a page, semantic status, language at chunk start

Three commits, all pushed to `feat/reader-segmented-spooling`:

- `1b9eb4f fix(i18n): never describe languages in a locale Voxora does not ship`
- `7d9a51b style(reader): present the document as a page with semantic status`
- `e88f85a fix(reader): switch the display language at the start of a chunk`

**The "Chinese text" report was a locale defect, not a string defect.** The brief
said Chinese labels appeared in three language cards. An exhaustive CJK scan of
`app/src/main`, `core/src/main` and every `res/**/*.xml` found **zero** Chinese
characters — the only CJK in the repository is test data in `ChunkQueueTest.kt`.
The real cause was `Locale.getDisplayName(locale)`, which names a language *in*
that locale: the pickers passed the raw device locale, so a phone set to Chinese
with the Voxora UI in English labelled every language in Chinese while the rest of
the screen stayed English. Voxora ships no Chinese translation, so it must never
*describe* languages in Chinese either.

`AppLocales.resolve(locale)` is the fix and is now the only place a display locale
is produced from outside input. It matches the full tag first, then the language
subtag (`fa-IR` → `fa`, `es-MX` → `es`), and falls back to `DEFAULT`. It is applied
at both display-name boundaries: `ReaderLanguages.languageOptions` (which the
Reader narration sheet *and* the Settings dubbing dropdown both call) and
`ReaderScreen`, which resolves `LocalConfiguration.current.locales[0]` once and
passes the result to the ViewModel. `SettingsScreen.appLanguageChoices` was already
safe because it names each entry in its own language (an endonym), which is a
deliberate choice, not leakage — it was left alone.

**DONE — the document is now a page, not a scroll.** The reading surface was one
long `LazyColumn` of every chunk, so a 210-chunk PDF composed 210 cards and the
reader had no sense of position. `ChunkPage` now composes **exactly one chunk** —
the current one — with a compact header (`Chunk 12 of 210`, segment progress,
narration language) and a page-turn row. Horizontal drags turn the page; the
leaving page recedes and fades while the arriving page enters from the side it was
turned towards. Turns go through the controller's existing `jumpToChunk`, so there
is no second navigation state machine and no per-frame work proportional to the
document. Prev/Next moved out of the playback card into the page.

`ReaderPager` (`app/.../reader/ReaderPager.kt`) is the pure-JVM page model and the
only place turn logic lives: `target` (bounded, single-step), `visible`, `turnFor`
(gesture → **logical** turn) and `enterOffset`. "Next" and "previous" are logical,
not physical — the same finger movement means opposite things in LTR and RTL, and
`turnFor` maps each layout's gesture to the same logical turn, so a Persian reader
cannot get reversed chunk order. The arrows use
`Icons.AutoMirrored.Filled.KeyboardArrowLeft/Right`. No bare `12 / 210` indicator
sits next to the arrows, because it reorders unpredictably under RTL bidi.

A dedicated pager library was **not** added: `androidx.compose.foundation` is not a
declared dependency (it arrives transitively via `material3`), so the drag is a
`detectHorizontalDragGestures` on the page, keeping the dependency set unchanged.

**DONE — status is semantic, and the active light is alive.** `ReaderStatusVisual`
maps every `ReaderPhase` to a tone, and `Theme.kt` now exposes the
success/warning/danger roles through `VoxoraSemanticColors` /
`VoxoraColors` (a `staticCompositionLocalOf` read via `@Composable
@ReadOnlyComposable`) instead of hex literals in composables. The values are the
ones the product already used (`#3DDC84` matches `FloatingBubbleService`'s
`BRAND_LIVE_GREEN`, `#E85D5D` matches `BRAND_ERROR`, `#E6B422` is the warm accent),
so the brand palette now has one source. Speaking is green with a slow,
low-amplitude halo (`rememberInfiniteTransition` + `animateFloat` + `RepeatMode.Reverse`,
1500 ms) that modifies alpha and scale only — no neon colour. Paused and ready are
gold and never green; stopped and failed are red; connecting, extracting and
preparing stay neutral (`colorScheme.outline`) and cannot falsely show green. The
halo is **not composed at all** when the phase is not active, so the animation stops
with playback rather than freezing behind a paused reader. `Handler.postDelayed` is
not used anywhere. The Live Dub status dot now reads the same tokens.

**DONE — the display language switches when the chunk starts.** `publish()` derives
the reading text *and* a new `pendingSegments` set for the chunk that is current at
that moment, so arriving at Chunk N resolves N's selected-language renderings in the
same synchronous step that makes N current. It does not wait for N to finish, and it
does not keep showing N−1's language while N's transcript is produced. This is free
because the producer already renders whole chunks ahead of playback: the chunk about
to be narrated normally has its renderings cached, so the turn is a pure state read.

`ReaderDisplayText.pending(language, chunk, unitCount)` is the new read of the cache
and mutates nothing. It exists because `readingText` silently falls back to the
extracted source for an unrendered unit, which is useful for browsing but must not be
mistaken for a finished rendering — without it, "not rendered yet" is
indistinguishable from "the selection happens to read like the source", and the
source language gets presented as the selection. `ReaderPageText` (`isPreparing`,
`isPreparingWholePage`, pure JVM) decides when that temporary treatment is shown:
only while the chunk is actually being narrated, never while browsing, paused or
stopped, because a reader who has not pressed Play still has to be able to read their
document.

**No artificial gap was introduced — and the change cannot introduce one.** The
display layer is *derived*: `publish` only reads `ReaderDisplayText` and the
canonical `ChunkQueue`; it never appends PCM, advances a consumer cursor or completes
a spool unit. The producer and the consumer never read `ReaderState`. That one-way
dependency is what guarantees a display-language change cannot insert silence
between chunks, and it is why the fix needed no change to `ReaderSpool`'s
architecture, the spool/consume ordering, or the prefetch pipeline. The
serialize-finish-audio → request-translation → wait → update-text → start-next-audio
ordering the brief forbids was never introduced.

**Feature B (iconography) needed no work in Settings.** The Settings app-language
card already used `Icons.Filled.Language` and the dubbing-language card already used
`Icons.Filled.Translate` — both introduced by the previous cycle's catalog refactor,
both meaningful Material icons, no emoji as a primary UI icon and no new image asset.
The Reader's narration row was still the generic case, so its 44 dp badge is now
`Icons.Filled.RecordVoiceOver` (narration), with the flag moved inline next to the
label instead of standing alone as a large decorative element.

**Live Dub is untouched.** No file under `app/.../dub/**`, `GeminiLiveSession.kt` or
`GeminiLiveConfig.kt` was modified, and no PCM capture, Live session, playback
routing, polling cadence, session lifecycle or drag/tap/double-tap behaviour changed.
The only Live Dub file touched at all is `DubScreen.kt`, and only its **status dot**,
which is explicitly in scope as status presentation: it now reads
`VoxoraColors.success` / `.warning` / `colorScheme.error` / `colorScheme.outline`
instead of the same values as literals. `LiveWaveform`'s decorative gold/green were
deliberately left alone.

**Two real defects were found and fixed while verifying, not just the requested work:**
- `ReaderScreen` referenced `R.string.reader_page_chunk`, which **neither locale
  declared** — that is a hard Gradle compile failure, caught by cross-checking every
  `R.string.*` reference against both string files. Added as "Chunk %1$d of %2$d"
  and "بخش %1$d از %2$d".
- `AppLocales.resolve` did not compile as first written (`toLanguageTag()` on a
  nullable receiver); caught by the local `kotlinc` pass before any push.

**Tests added (41 new, 175 total).**
`ReaderStatusVisualTest` (8) — speaking is active; only active pulses; paused is
ready and not green; stopped/error are stopped; connecting/extracting/preparing are
neutral and never green; every phase has a tone; the four tones stay distinct.
`ReaderPagerTest` (11) — single-step bounded navigation, nothing to turn without a
document, exactly one chunk is ever the page, walking either direction visits every
chunk once and stops, a short drag is not a turn, LTR/RTL gestures map to the same
logical turn, a Persian reader never gets reversed order, the arriving page comes
from the side it was turned towards.
`ReaderChunkStartLanguageTest` (14) — arriving at a chunk shows its cached
selected-language text without waiting or re-rendering; a chunk with no rendering
starts in the selected language and is reported as preparing rather than showing the
previous chunk's language; the pending set shrinks unit by unit; a late transcript
updates the current chunk; a transcript from an abandoned generation **or** revision
cannot reach the page; prefetch never moves the page ahead of the narration;
switching language never leaves the other language's text on screen; switching back
restores the cached rendering immediately; the canonical chunk is untouched.
`ReaderDisplayAudioContinuityTest` (8) — driven against the **real** `ReaderSpool`:
recording display text does not change the spool snapshot, switching language leaves
the spool and queue untouched, consuming a chunk renders nothing, publishing between
units does not change the bytes the consumer writes, every unit is still read once
and in order, the promoted chunk is already rendered and already committed before the
turn, the turn adds no work to the audio path, display state is never written back
into the document. These assert **ownership and ordering, not timing** — there are no
sleeps, because a timing assertion would measure the machine rather than the contract.

Each new contract was proven to catch its defect by mutation: making `pulses` return
`false` fails 1 test; making `PAUSED` resolve to the active tone fails 4; removing the
RTL gesture mirror fails 2; making `ReaderDisplayText.pending` always return an empty
set fails 4; making `isPreparingWholePage` return `false` fails 1.

**Validation actually performed (no Gradle was run).** Compiled the pure-JVM
Reader/core sources with `kotlinc 2.0.21` against the pinned dependency jars and ran
the JUnit classes directly with `-ea` (matching Gradle's test JVM): **175 tests, OK**
across 16 classes. `SettingsScreen.kt`, `ReaderScreen.kt`, `DubScreen.kt`,
`Theme.kt` and the other Android-dependent files cannot be compiled on the dev
server, so the debug-APK job is their real check. Additional static checks run
locally: a CJK scan of all main sources and resources (zero hits), a cross-check of
every `R.string.*` reference against both `values/` and `values-fa/` (now complete,
with matching format args), and an unused-import sweep of `ReaderScreen.kt`.

**CI status: GREEN — VERIFIED.** Pushing `e88f85a` triggered `Android CI` run
**`35301607886`** (run **#74**, event `push`, branch `feat/reader-segmented-spooling`,
created 2026-09-18T03:01:25Z, completed 2026-09-18T03:03:21Z): **both jobs success.**
`Unit tests` — 10/10 steps success, including the `Run unit tests` step that runs
`gradle :core:testDebugUnitTest :app:testDebugUnitTest`. `Assemble debug APK` — 14/14
steps success, including `Assemble debug` and `Upload debug APK`. The workflow has no
`continue-on-error`, so a green `Run unit tests` step is a genuine pass and a green
`Assemble debug` step is a genuine compile check of the Compose changes
(`ReaderScreen.kt`, `Theme.kt`, `DubScreen.kt`) that the dev server cannot compile.
The documentation commit on top, `ee6d31b`, is verified by run **`35301787713`**
(run **#76**, event `push`, created 2026-09-18T03:04:12Z): both jobs success. The
same commit also produced a `pull_request` run **`35301791595`** (run **#77**, from
the pre-existing **PR #3**) — both jobs success as well, so the branch is green on
both trigger paths. **Real-device verification was NOT performed** — nothing in this
change was tested on a physical device, and no device behaviour may be claimed.

**Note on PR #3.** `PR #3 "Feat/reader segmented spooling"` (`feat/reader-segmented-spooling`
→ `main`) is **open and not merged**. It was created at 2026-09-18T02:20:08Z, before
this session, and it tracks this branch automatically. It was deliberately left
alone: the standing instruction is to push to the feature branch and never merge to
`main`. Do not merge it, and do not merge **PR #2** either.

**IN PROGRESS:** nothing — all of the requested work is implemented, tested, pushed
and CI-verified on this branch.

**BLOCKED:** nothing.

**NEXT (do not start before the above is read):**
- **Real-device check — not performed, and must not be claimed.** The page turn,
  the active halo, the RTL page-turn direction and the chunk-start language swap are
  all UI behaviour. The following need a device: (1) a Persian PDF with English
  narration, confirming each chunk's visible text is English from the moment the
  chunk starts rather than from the moment it ends; (2) that narration flows into the
  next chunk with no audible gap while the page turns; (3) that a horizontal swipe
  advances in both an LTR and an RTL UI locale; (4) that the status light is green
  only while speaking and gold while paused. If (2) fails on device, the likely cause
  is the spool/consume path rather than the display layer, since the display layer is
  provably one-way. This is the single highest-value outstanding step.
- A very long chunk still composes every one of its own units inside the single page.
  That is bounded by the 80-word narration-unit split rather than by the document, so
  it is not the 210-card problem, but if a pathological chunk is ever observed to
  jank, the fix belongs in `ChunkQueue.readableUnits`, not in the page.
- `LogsScreen.kt` still uses its own hardcoded severity palette (error red, warn
  orange, info green, debug grey). That is a debug surface with deliberately different
  semantics from the status contract, so it was left alone rather than folded into
  `VoxoraColors`. Fold it in only if the log-severity colours are meant to be the
  product's status colours, which they are not.
- The Reader still shows the extracted source for units not yet narrated in the
  selected language, now marked as "preparing" while the chunk is being narrated.
  That is the documented contract, not a bug — do not "fix" it by translating ahead,
  and do not claim in UI copy that the document is translated up front.

### Previous change — Reader refresh, extraction gates, one language catalog

Three commits, all pushed to `feat/reader-segmented-spooling`:

- `1c0df1e fix(reader): refresh reading text as soon as a unit is narrated`
- `882ab27 fix(reader): keep the reader usable while a document is extracting`
- `1504669 refactor(i18n): drive every language picker from one catalog`

**DONE — 1. The reading text follows the narration in real time.**
The reported defect was a Persian PDF with English selected: the audio was English
but the visible text stayed Persian. The transcript was never wrong — `narrate()`
already returns it in the selected language, and `produce()` already stored it in
`ReaderDisplayText`. The bug was that `produce()` stored it **without republishing**,
so `ReaderScreen` kept the previously published list (the extracted source) until
some unrelated event republished: the next chunk transition, or an audible-progress
tick. That is why the text appeared to lag the narration by a chunk.

The fix is one guarded republish inside the existing ownership check, using a new
pure-JVM rule `ReaderDisplayText.shouldRepublish(language, chunk, displayedLanguage,
displayedChunk)`. It republishes only when the rendering belongs to **both** the chunk
on screen and the language being displayed, because production runs ahead of playback:
republishing unconditionally would drag the UI onto a prefetched chunk or back onto a
language the reader has already left. Prefetched chunks are still cached, so they are
ready the moment the reader arrives. Language-cache isolation and the canonical
extracted document are untouched.

`publish()` also now preserves `state.error` when it republishes the `ERROR` phase.
Republishing became routine, and dropping the message would have erased the failure
reason the user needs to see; any other phase transition still clears it.

**DONE — 2. The Reader no longer looks locked while a document is extracting.**
Restoring the last PDF and extracting a large one both hold `EXTRACTING` for a while.
During that window the document card claimed "no document loaded" and the reading
mode, narration language and file picker were all disabled — back navigation worked,
but the screen read as frozen.

Every gate now lives in one pure-JVM object, `reader/ReaderGates.kt`, which both
`ReaderScreen` and `ReaderViewModel` call so they cannot disagree. Mode and language
are only preferences and never touch extraction, so they now freeze only while
narration is actually running. Picking another document stays available during
extraction because `ReaderController.startLoad` already serializes loads
(`cancelAndJoin` behind a generation bump) — it is a supported cancellation, not a
race. Playback still refuses to start before the queue is ready, and chunk/segment
navigation still needs a document. The document card now shows an indeterminate bar
with a "reading document" title and body, in English and Persian.

`ReaderPhase`/`ReaderState` moved into their own file (`reader/ReaderState.kt`, no
`android.*`) so `ReaderGates` is testable on a plain JVM. No call sites changed.

**DONE — 3. One language catalog.**
`SettingsScreen.kt` held two hand-typed lists — 14 dubbing languages and 7 app
languages — while `ReaderLanguages.all` already described 99. Both are deleted. The
dubbing dropdown now calls the very same `languageOptions(locale, query)` the Reader's
narration sheet uses, so the two are identical in count, order, labels and flags by
construction. The app-language dropdown reads the new `core/.../i18n/AppLocales.kt`,
which lists the locales actually packaged in the APK and must stay in step with
`resourceConfigurations`; it names each entry in its own language so a user who cannot
read the current UI language can still find theirs. Both resolve names and flags
through `ReaderLanguages`, so a language is never described two ways.

The three preferences stay separate — app UI locale, Reader narration language
(`readerOutputLang`) and Live Dub target language (`targetLanguage`) answer different
questions and have different defaults. Only the catalog was unified, never the
preference. `ReaderLanguages.languageOrNull` was added for callers that must not
invent a language for an unknown code.

**Objective 4 (Live Dub) — untouched and safe.** No file under `app/.../dub/**`,
`GeminiLiveSession.kt` or `GeminiLiveConfig.kt` was modified. `DubService` still reads
`prefs.targetLanguage` and passes it to `translationConfig.targetLanguageCode`. The
only change that touches Dub at all is that its dropdown now offers the full catalog
instead of 14 codes; an unsupported code already surfaces as `GeminiStatus.Error`
through the existing path (`GeminiLiveSession.handleMessage` → `DubService.setErrorAndStop`),
so this cannot break the pipeline.

**Objective 5 (Google Sign-In) — out of scope, untouched.**
**Objective 6 (PDF reading order) — untouched.** `PdfReadingOrder.kt` and
`TextExtractor.kt` were not modified; `PdfReadingOrderTest` and `ReaderPipelineOrderTest`
stay green.

**Tests added (32 new, 134 total).**
`ReaderDisplayRefreshTest` (9) — immediate republish for the displayed chunk and
language, no republish for a prefetched chunk or an abandoned language, no refresh on
a rejected rendering, per-unit appearance with the chunk's shape preserved, language
switching, unchanged canonical source, moving between chunks.
`ReaderStartupGatesTest` (8) — settings and the file picker available during
extraction, playback never before the queue is ready, navigation needs a document,
**no phase is ever fully locked**, configuration freezes only while narrating.
`LanguageCatalogTest` (8) — full-catalog coverage without duplicates, labels/flags from
the catalog, deterministic order, search that filters without reordering, endonym and
secondary-label rules, the app-locale subset, unknown-code normalization.
`AppLocalesTest` (7) — uniqueness, membership in the one catalog, no silently dropped
locale, display name and flag present, strict subset of the Gemini output catalog.

Each new contract was proven to catch its defect by mutation: making `shouldRepublish`
return `true` unconditionally fails 3 tests; restoring the old "EXTRACTING locks the
settings" behaviour fails 3; allowing playback before the queue is ready fails 1;
adding a shipped locale that is not in the catalog fails 4.

**Validation actually performed (no Gradle was run).** Compiled the pure-JVM
Reader/core sources with `kotlinc 2.0.21` against the pinned dependency jars and ran
the JUnit classes directly with `-ea` (matching Gradle's test JVM): **134 tests, OK**
across 12 classes. `SettingsScreen.kt`, `ReaderScreen.kt`, `ReaderViewModel.kt` and the
other Android-dependent files cannot be compiled on the dev server, so the debug-APK
job is their real check.

**CI status: GREEN — VERIFIED.** Pushing `1504669` triggered `Android CI` run
**`35297963994`** (run **#70**, event `push`, branch `feat/reader-segmented-spooling`,
created 2026-09-18T02:05:29Z): `Unit tests` success (including the `Run unit tests`
step, which runs `gradle :core:testDebugUnitTest :app:testDebugUnitTest`) and
`Assemble debug APK` success (including `Assemble debug` and `Upload debug APK`). The
documentation commit on top of it, `14bd894` (the current HEAD), is verified by run
**`35298147134`** (run **#71**, created 2026-09-18T02:13Z): both jobs success, 10 and
14 steps green. The workflow has no `continue-on-error`, so a green `Run unit tests`
step is a genuine pass and a green `Assemble debug` step is a genuine compile check of
the Compose changes.

**IN PROGRESS:** nothing — all three objectives are implemented, tested and
CI-verified on this branch.

**BLOCKED:** nothing.

**NEXT (do not start before the above is read):**
- The Live Dub target-language list is now the full 99-code catalog. Gemini Live
  Translate may support a narrower set. If it does, encode that as a **capability
  filter over `ReaderLanguages.all` with a documented source** — never as a second
  hand-typed list, which is exactly what this session removed.
- Real-device check for Objective 1: Persian PDF + English narration should swap each
  unit's visible text as it is spoken, without waiting for the chunk to end.
- Real-device check for Objective 2: restore a large PDF and confirm the card shows
  the progress bar, mode/language/file picker stay usable, and Play stays disabled
  until the document is ready.
- The Reader still shows the extracted source for units not yet narrated in the
  selected language. That is the documented contract, not a bug — do not "fix" it by
  translating ahead, and do not claim in UI copy that the document is translated up
  front.

### Previous change — Settings/overlay visual pass + display-language sync

Two commits, both pushed to `feat/reader-segmented-spooling`:

- `a9b9336 fix(settings): align settings and overlay UI with reader design`
- `7f47805 feat(reader): synchronize display language with narration language`

**A. Settings now belongs to the same product as the Reader.**
`SettingsScreen` was a flat `verticalScroll` of ungrouped fields with hardcoded
hex colours and raw `sp` sizes. It is rebuilt with the Reader visual system: one
keyed `LazyColumn`, a matching back+title top bar, grouped section headers
(Appearance / Gemini / Live Dub / Account / Overlay / Diagnostics) and rounded,
outlined `surfaceContainerHigh` cards with 44 dp primary-tinted icon badges.
Every control it had before is still present — app language, Gemini API key,
Open AI Studio, dubbing language, Save, account sign-in/out, overlay permission
and logs. The screen is split into a stateful `SettingsScreen` wrapper and a
stateless `SettingsContent` with two `@Preview(uiMode = UI_MODE_NIGHT_YES)`
configurations. The literal green used for "Saved" is gone in favour of the
`primary` token; no hardcoded colour or `sp` size remains in the file.

**B. The overlay UI.** The only overlay UI in the app is the floating Live
bubble, `app/src/main/java/com/voxora/app/dub/FloatingBubbleService.kt`. It is a
plain Android `View` hierarchy, so it cannot use `MaterialTheme`; it now mirrors
the same brand palette instead (gold `#D4AF37`, live green `#3DDC84`, error
`#E85D5D`, `surfaceContainerHigh` fill `#1C1C1F`), with a thinner outline and a
gold→green waveform gradient matching the Live Dub waveform. **Behaviour is
untouched** — drag, tap-to-toggle-Stop, tap-Stop-to-stop, double-tap-to-open,
the 50 ms amplitude poll and the window params are byte-identical. The owner was
asked before this file was edited, because the standing `dub/**` prohibition
covers it; the change is visual-only and confined to paint/colour/geometry
constants.

**C. Reader reading text now follows the selected narration language.**
The reported defect: narration was in the selected language but the reading text
stayed in the document's own language, so a Persian run over an English PDF read
Persian aloud while the on-screen text stayed English.

The only translation mechanism in the product is the Reader Gemini path, and
`GeminiReaderSession.narrate` already returns each unit's transcript in the
selected language — so **no new backend, no external translation API, no device
TTS, no new key destination and no new session type were added**. The fix is a
display layer above extraction:

- New `app/src/main/java/com/voxora/app/reader/ReaderDisplayText.kt` (pure JVM,
  no `android.*`) holds each unit's selected-language rendering, **keyed by
  language as well as chunk and segment**, so a language change can never
  surface text produced for another language and two languages can never share
  an entry. Blank transcripts are rejected rather than stored.
- `ReaderController` records each unit's transcript under the language the run's
  instruction was built with (so the instruction and the reading text can never
  disagree), publishes `ReaderState.segments` through the cache, clears the cache
  when a new document replaces the queue, and gains `setOutputLanguage`, which
  republishes immediately.
- `ReaderViewModel` calls `setOutputLanguage` on init (before
  `restoreLastDocument`) and from `setOutputLang`.
- The canonical extracted document in `ChunkQueue` is **never mutated**;
  `ReaderState.text` still carries it verbatim. A unit not narrated in the
  selected language yet falls back to the extracted source, so the published
  list always keeps the canonical chunk's length, order and boundaries.
- UI: the section header is now `Reading text · <language>` and the hint states
  the fallback honestly. The reading-text card and the live narration transcript
  remain separate surfaces.
- **Unchanged:** audio semantics, the Faithful/Fluent contracts, the narration
  instruction, and `PdfReadingOrder`. Nothing in the protected audio/session
  pipeline was touched.

**Limitation, stated honestly (not a bug).** Display text appears as Gemini
narrates, because the Reader Gemini path is the only translation mechanism. A
chunk that has not been narrated in the selected language still shows the
document's original text for those units. The UI says so; do not claim the whole
document is translated up front.

**Tests added.** `app/src/test/java/com/voxora/app/reader/ReaderDisplayLanguageTest.kt`
— 15 tests covering language/chunk/segment scoping, no cross-language reuse, no
stale text after a language change, blank rejection, trimming,
replace-not-duplicate, clear, and the pipeline contract above the real
`ChunkQueue` (length/order/boundary preservation, every chunk obeying the same
contract, no cross-language mixing, no inheritance across documents, unchanged
canonical source). **Verified to reproduce the defect:** mutating the cache key
to ignore the language makes 4 of the 15 fail.

**Validation actually performed (no Gradle was run).** Compiled the changed
pure-JVM Reader/core sources with `kotlinc 2.0.21` against the pinned dependency
jars and ran the JUnit classes directly with `-ea` (matching Gradle's test JVM):
**102 tests, OK** across `ReaderDisplayLanguageTest` (new), `ReaderPipelineOrderTest`,
`ChunkQueueTest`, `PdfReadingOrderTest`, `ReaderSpoolTest`,
`ReaderNarrationModesTest`, `ReaderLanguageFlagsTest` and
`GeminiReaderSessionTest`. `SettingsScreen.kt`, `ReaderScreen.kt`,
`FloatingBubbleService.kt` and the other Android-dependent files cannot be
compiled on the dev server, so the debug-APK job is their real check.

**CI status: GREEN — VERIFIED.** Pushing `7f47805` triggered `Android CI` run
**`35294468431`** (event `push`, branch `feat/reader-segmented-spooling`, head
`7f478057b412c0ec3c7e65b3ff99f25565b09a09`, created 2026-09-18T01:13:51Z,
https://github.com/mo3iiibest77-hub/Voxora-Android/actions/runs/35294468431).
Both jobs passed: **`Unit tests` = success**, including the `Run unit tests` step
(`gradle :core:testDebugUnitTest :app:testDebugUnitTest`), and **`Assemble debug
APK` = success**, including `Assemble debug` and `Upload debug APK`.
`android-ci.yml` has no `continue-on-error`, so these are genuine passes. The
APK job is the real compile check for the Compose changes.

**Still requires real-device verification — NOT claimed fixed on-device.**
Confirm on the owner's device: the new Settings layout and every control in it,
the restyled bubble over another app, and that selecting Persian over an English
PDF shows the reading text in Persian as narration proceeds.

### Previous change — "chunk 1 is fine, every later chunk is scrambled"

**Reported symptom.** On a large PDF (~210 chunks, ~9 segments each) chunk 1 was
narrated correctly in Fluent, but from chunk 2 onward the **source text shown in
the app was already scrambled**, so the segments were scrambled, Gemini received
bad text, and the narration was scrambled. Jumping manually to chunk 2 / 3 / 27
showed the same. Explicitly *not* an audio-only ordering problem.

**Method.** Walked the whole pipeline (extraction → full text → `documentChunks` →
chunk → `segments` → `ReaderController` → `produce` → `GeminiReaderSession.narrate`
→ PCM/transcript → UI) looking for the **first** corrupted representation rather
than the last. Chunking, segmenting, `segmentCache`, queue replacement and the
chunk transition were ruled out with pure-JVM invariants on the real `ChunkQueue`.
The first corruption is in extraction.

**Exact root cause (verified against the real PDFBox engine).**
`PdfReadingOrder` detected column gutters from a **page-wide union of every
fragment's x-extent**. A single full-width element — a running header or page
footer — therefore covered the gutter for the whole page, the page was classified
as single-column, and the two columns were emitted **row-interleaved**
(`Left one Right one Left two Right two …`). That reproduces the reported
asymmetry exactly: the first page of a section usually has no running header and
read correctly, while every later page carried one and was scrambled.
`sortByPosition = true` was measured again and produces the *same* row-interleaved
output, which is why the naive flip was never a fix.

**Fix, confined to `PdfReadingOrder`.**
- Gutters are now measured **per line**, so a minority of lines (headers/footers)
  may cross a gutter without erasing it.
- The page is read as a stack of **regions**: a gutter-crossing line is emitted in
  place and the runs of ordinary lines between them are read column by column.
- Regions are banded by the **page's** gutters, never by gutters re-derived from
  the region: a short region (two lines under a running header) falls below
  `MIN_LINES_FOR_COLUMNS` and would otherwise silently collapse back to
  row-interleaved order.
- `TextExtractor`, `ChunkQueue`, `ReaderController`, `GeminiReaderSession`, the
  narration modes and the UI were **not** changed. Prefetching was left intact —
  no evidence implicated it. Live Dub untouched.

**Tests added/updated.**
- `PdfReadingOrderTest` — added `aHeaderThatCrossesTheGutterDoesNotEraseTheColumnStructure`,
  `aFullWidthFooterIsReadAfterBothColumns`, `aRunningHeaderOnLaterPagesNoLongerScramblesThosePages`,
  `aHeaderNarrowerThanTheGutterIsReadFirstAndTheColumnsStayDeInterleaved`,
  `aPageWithTooFewLinesHasNoColumnStructureToInfer`; removed
  `aFullWidthHeaderFallsBackToSingleColumnOrdering`, which asserted the buggy
  behaviour as if it were correct.
- `ReaderPipelineOrderTest` (new, 9 tests) — post-extraction contract on the real
  `ChunkQueue`: chunk and segment reconstruction, cross-chunk bleed, cache
  isolation, chunk transition, and that the Faithful/Fluent instruction is
  identical for every chunk and segment.
- The tests were proven to catch the defect: run against the pre-fix
  `PdfReadingOrder` at `707cc3c`, **4 of them fail**; against the fix, all pass.

**Validation actually performed (no Gradle was run).** Compiled the changed sources
with `kotlinc` against the pinned dependency jars and ran the JUnit classes directly
with `-ea` (matching Gradle's test JVM): **47** (core contracts + PDF reading order),
**9** (pipeline order), **43** (chunk/spool/controller) — all OK. The real-engine
end-to-end probe also passes: 40-page single-column document (4800 words → 10
chunks, chunk contiguity, `join(segments(chunk)) == chunk`), and the exact reported
layout (page 1 without a running header, pages 2+ with one) now reads
column-by-column on every page.

**Known limitation, recorded honestly.** A gap that is *inside* a line but large
and repeated across most lines (a tab-aligned label, a table-of-contents leader, a
widely letter-spaced heading) can still be mistaken for a column gutter. That
layout is genuinely ambiguous from geometry alone and was already mis-ordered
before this rework. No threshold was tuned against a synthetic page for it, because
that would trade a verified fix for a speculative one. Documented in `AGENTS.md` §5.

**UI / stale-APK question, answered.** Verified rather than assumed: the redesign is
on `feat/reader-segmented-spooling` (`925ee1d` is an ancestor of HEAD), HEAD is 15
commits ahead of `main`, and CI run **#66** (`35287232792`, head `707cc3c`)
succeeded including the `Assemble debug` and `Upload debug APK` steps. So the
modern UI is both in the branch and in the published artifact; an installed app
that "still looks old" is an APK from an earlier run, not missing code. No UI change
was needed, and none was made.

**Still requires real-device verification — NOT claimed fixed on-device.** Install
the APK built from the commit above and confirm on the owner's actual large PDF
that the source text for chunk 2 and later is in document order and that the
narration follows it.

### Previous change — Reader quality pass (three goals)

**A. Faithful vs Fluent are now an explicit, tested contract.**
- New `core/src/main/java/com/voxora/core/gemini/ReaderNarrationModes.kt` owns the
  mode constants (`faithful` default, `fluent`), normalization (unknown →
  `faithful`), validation, and the single generated instruction. `ReaderController`
  and `ReaderViewModel` delegate to it; no mode list or prompt text is hard-coded
  anywhere else, and nothing lives in the UI layer.
- Faithful = preserve the source wording as much as possible, minimum structural
  adjustment, priority order words → sentences → terminology → ordering → facts →
  meaning; only extraction artifacts / whitespace / wrapping / OCR noise /
  punctuation may change; free paraphrasing is forbidden.
- Fluent = understand first, then rewrite the same content into the clearest natural
  spoken form without changing meaning, facts, intent, or important details; may
  restructure sentences; summarizing, inventing, adding, changing claims,
  translating, or adding commentary are forbidden.
- Pinned by `ReaderNarrationModesTest` (8 tests).

**B. PDF reading order — reproduced, root-caused, fixed, regression-tested.**
- The scrambled output was reproduced with the real PDFBox engine under the
  production configuration. Responsible layer: `TextExtractor`, not the UI.
  Root cause: PDFBox collects glyphs in content-stream order and only sorts when
  `sortByPosition` is on, and the default is `false`.
- `sortByPosition = true` was measured and **rejected**: it fixes a reversed
  single-column page but row-interleaves a column-major two-column page.
- Fix: keep `sortByPosition = false` and override `PDFTextStripper.writePage()` to
  re-order each article with `PdfReadingOrder.order(...)`. `PdfReadingOrder` is a
  pure-geometry, pure-JVM `internal object` (fragment-level gutter detection, line
  grouping, column ordering, stable permutation). 13 tests in
  `PdfReadingOrderTest` cover the reversed case, already-ordered pages,
  multi-column de-interleaving, column-major preservation, that a word gap is not a
  gutter, and permutation/empty/single/determinism. The full-width-header case in
  this first pass asserted the *wrong* behaviour (single-column fallback) and was
  replaced by the running-header regression tests in the next change.
- Limits documented in `AGENTS.md` §5: tables, sidebars, marginalia, rotated text,
  footnotes and overlapping columns may still be imperfect, and scanned PDFs cannot
  be recovered. No UI copy claims otherwise.

**C. Reader screen redesigned.**
- `ReaderScreen` is now a single keyed `LazyColumn` following the listening
  workflow: document identity → reading mode (cards with visible explanations) →
  narration language → playback/progress → error → source segments (current one
  highlighted and tappable) → Gemini narration preview → privacy note.
- Source text and AI narration are separate cards and are never merged.
- Language is a tappable row opening a searchable `ModalBottomSheet` with flags.
- `Theme.kt` gained the Material 3 container/outline roles so grouped surfaces are
  not tinted purple; the existing brand colours were not changed.
- New strings have Persian translations; five now-unused strings were removed from
  both locales.

**Language flags.** New `ReaderLanguageFlags` maps every catalog code to one
explicit representative ISO 3166-1 alpha-2 country and encodes the emoji from the
Unicode regional-indicator range (never hand-typed, never derived from the subtag).
`en → GB`, `fa → IR`, `ar → SA`, `pt-BR → BR` vs `pt-PT → PT`,
`zh-Hans → CN` vs `zh-Hant → TW`; `eu`/`ca`/`ku`/`qu` use the neutral globe.
9 tests in `ReaderLanguageFlagsTest`.

**Validation actually performed (no Gradle was run):** compiled the changed sources
with `kotlinc` against the pinned dependency jars and ran the JUnit classes directly
with `-ea` (matching Gradle's test JVM). `ReaderNarrationModesTest`,
`ReaderLanguageFlagsTest`, `GeminiReaderSessionTest` and `PdfReadingOrderTest`
together: **43 tests, OK**. `ChunkQueueTest` + `ReaderSpoolTest` +
`ReaderControllerRegressionTest`: **43 tests, OK**. All non-Compose Reader and core
sources compile cleanly. `ReaderScreen.kt` and `Theme.kt` are Compose files and
could not be compiled locally, so the debug-APK job is their real check.

**CI status: GREEN — VERIFIED for this pass.** Pushing `35120bf` triggered
`Android CI` run **#65** (run id `35287036680`, event `push`, branch
`feat/reader-segmented-spooling`, 2026-09-17T23:29:03Z,
https://github.com/mo3iiibest77-hub/Voxora-Android/actions/runs/35287036680).
Both jobs passed: **`Unit tests` = success** — including the `Run unit tests` step
that runs `gradle :core:testDebugUnitTest :app:testDebugUnitTest` — and
**`Assemble debug APK` = success** — including `Assemble debug` and
`Upload debug APK`. The APK job is the real compile check for the redesigned
Compose UI (`ReaderScreen.kt`, `Theme.kt`), which cannot be compiled on the dev
server. `android-ci.yml` has no `continue-on-error`, so these are genuine passes.
The same caveat as before applies: job/step conclusions come from the REST API and
raw logs need admin rights, but a green `Run unit tests` step cannot hide a failing
`testDebugUnitTest` task.

### Previous change — sink exception propagation fix
- **Exact failing test (CI):**
  `GeminiReaderSessionTest.sinkFailurePreservesOriginalExceptionAndSanitizesStatus`.
  `:core` reported "13 tests completed, 1 failed"; the `Assemble debug APK` job
  passed.
- **Root cause (proven, not guessed):** `assertSame(failure, …)` failed because
  kotlinx-coroutines **stack-trace recovery** substitutes a *copy* of an exception
  that crosses a `Deferred.await()` boundary. Recovery is gated by JVM assertions
  and Gradle's `Test` task runs with `-ea` by default — so the test passes under a
  plain `java -cp` run and fails under Gradle. `abortLocked` was a red herring:
  `CompletableDeferred.completeExceptionally` is already a no-op on an
  already-completed deferred, so it never replaced the original exception.
- **Fix:** `Turn` records `sinkFailure` (the caller's exact exception) when the
  PCM sink throws, and `narrate` rethrows that object instead of the awaited
  (possibly recovered) throwable. Nothing else changed — stop / close /
  connection replacement / cancellation / transport failure / setup timeout /
  turn timeout / stale callbacks still throw exactly what they threw before.
- **Files changed:** `core/src/main/java/com/voxora/core/gemini/GeminiReaderSession.kt`
  and `core/src/test/java/com/voxora/core/gemini/GeminiReaderSessionTest.kt`
  (assertions added — none removed or weakened) plus `AGENTS.md` and this file.
- **Validation actually performed (no Gradle):** compiled the exact sources with
  kotlinc 2.0.21 and the pinned dependency versions, then ran the suite directly
  under JUnit. Reproduced the CI failure exactly with `-ea`
  (`Tests run: 13, Failures: 1`); after the fix, 13/13 pass both **with and
  without** `-ea`, plus 300 iterations of the sink test and 20 full-suite runs
  pinned to one CPU under load. **No local Gradle task was run.**
- **GitHub Actions result: GREEN — VERIFIED.** Pushing `4e4e17a` triggered
  `Android CI` run **#63** (run id `35282478465`, event `push`, branch
  `feat/reader-segmented-spooling`, 2026-09-17T22:31:51Z → 22:33:47Z,
  https://github.com/mo3iiibest77-hub/Voxora-Android/actions/runs/35282478465).
  Both jobs passed: **`Unit tests` = success** — including the `Run unit tests`
  step that runs `gradle :core:testDebugUnitTest :app:testDebugUnitTest` — and
  **`Assemble debug APK` = success**. `android-ci.yml` has no `continue-on-error`,
  so a green `unit-tests` job is a real pass, not a masked failure.
- **Evidence caveat:** job and step conclusions were read from the GitHub REST
  API. Raw step logs are not retrievable without admin rights (the run-logs
  endpoint returns 403 "Must have admin rights to Repository"), so the literal
  `13 tests completed, 0 failed` line was not read verbatim. The green
  `Run unit tests` step is still conclusive, because a failing
  `testDebugUnitTest` task fails that step and therefore the job.
- **Status: the sink exception propagation fix is DONE and CI-verified.**
- **Next action:** real-device confirmation (owner action) of the Home chooser,
  the back loop, and Reader narration surviving navigation. Do not merge PR #2
  without an explicit owner decision.

### Previous change — Milestone 1 slice: Voxora Home
- `feat(home)`: `HomeScreen` is a neutral Voxora product chooser — header plus two
  equally weighted cards (Live Dub, Voxora Reader) and a Settings entry. The Live
  Dub working surface moved into `app/.../ui/DubScreen.kt`. `VoxoraNav` gained a
  `DUB` destination via a private `VoxoraScreen` enum plus a nav-level
  `BackHandler` where Home is the root. No Navigation Compose graph was added.
- Live Dub engine and Reader engine were **not modified**; navigating away from
  Reader does not stop narration (`ReaderViewModel` has no `onCleared` stop).
- Not yet verified on a device: the Home → product → back loop, and that Reader
  narration survives leaving the Reader destination.

### Next milestone (roadmap order):
- **CI is green** at the current HEAD `7f47805` (run `35294468431`): `Unit tests`
  and `Assemble debug APK` both succeeded, so the Settings/overlay redesign and
  the display-language sync are compile-verified. The remaining step is owner-side
  real-device confirmation: the Home chooser, the back loop, Reader narration
  surviving navigation, the redesigned Reader screen, the new Settings layout,
  the restyled bubble, whether the owner's large PDF now shows chunk 2 and later
  in document order, and whether the reading text follows the selected narration
  language as narration proceeds. **Nothing here is claimed fixed on-device.**
- Roadmap/order mismatch persists: the repo is ahead on Reader (5–8) and behind on
  Home (1), Product Navigation (2) and the Design System (3). `Theme.kt` still uses
  gold `#D4AF37` and near-black `#0A0A0B`, not the `#FFD700` / `#0A0A0F` in
  `AGENTS.md` §3 — the Reader redesign, the Settings rebuild and the bubble
  restyle all deliberately reused the existing brand colours rather than silently
  changing the app palette. `AGENTS.md` §5A now records Reader as the visual
  reference and the Settings/overlay rules that follow from it.
- Google Sign-In was explicitly **out of scope** for this cycle. The existing
  Settings sign-in control was preserved as-is, not reworked.

### Pending items (do NOT start before the above):
1. PDF/chunk caching beyond the last-document URI
2. ~~Reader UI redesign to match the Live Dub start screen style~~ — **done**;
   Settings and the overlay bubble were brought to the same visual language in
   `a9b9336`; all of it needs device confirmation, not more design work
3. PDF viewer alongside audio
4. Pre-translating a whole document for the reading text — deliberately **not**
   done: the Reader Gemini path only produces selected-language text as it
   narrates, and adding an up-front translation pass would change cost and
   latency and risk the audio semantics. Revisit only with an explicit owner
   decision.

"Reader/Dub entry chooser at app launch" and "Navigate back while audio plays" are
implemented in code but still need real-device confirmation. DO NOT start the
pending items above until the audio pipeline is confirmed stable on a real device.

---

## HOW TO RESPOND AS THE NEW CLAUDE INSTANCE

Normal response format (in Persian):

1. وضعیت ریپو — factual summary of current GitHub state
2. ارزیابی — Good / Risk / Bug (only relevant items)
3. پیشنهاد بعدی — ONE clear priority
4. پرامپت OpenCode — complete English prompt ready to paste
5. سؤال از من — ONLY if a real product decision is needed

Rules:
- Always verify GitHub before making claims about the codebase
- Never trust OpenCode's own claims without reading the actual commit
- One priority at a time — no lists of 20 ideas
- Prompts must tell OpenCode to read AGENTS.md first
- Prompts must include: DO NOT touch dub/**, DO NOT run local Gradle
- Use conventional commits: fix(reader):, feat(reader):, docs(agents):
- Update AGENTS.md when architecture changes

---

## SECURITY NOTES

API keys in UserPrefs (not committed to Git — safe).
VoxoraLog is used for all logging (never logs API keys or WS URLs).

---

## FIRST ACTION FOR NEW CLAUDE INSTANCE

1. Use GitHub connector to read:
   - Latest commits on main (git log)
   - AGENTS.md
   - CLAUDE.md (this file — for context)
   - Any files changed in the last 1-3 commits

2. Compare current state with "CURRENT STATE" section above

3. If OpenCode finished a commit since this file was last updated,
   review what actually changed

4. Report the real current state to owner in Persian

5. Determine the single most important next step

6. Produce the OpenCode prompt

Do not just acknowledge this file. Start the GitHub review immediately.
