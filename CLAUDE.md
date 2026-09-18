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
- Source text and AI narration are separate UI surfaces and are never merged.

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
- `feat/reader-segmented-spooling` (the implementation branch) carries the Reader
  quality pass — `925ee1d feat(reader): redesign the reader screen`,
  `72527e7 feat(reader): expose the loaded document name`,
  `f644d2f feat(reader): map language catalog to deterministic flags`,
  `b0d556d fix(reader): preserve pdf reading order`,
  `a74db6c fix(reader): define fluent and faithful narration semantics` — plus the
  reading-order fix `6d826e0 fix(reader): de-interleave pdf columns when a page
  carries a running header` and the documentation commits. **15 commits ahead of
  `main` (`1f0a719`)**, pushed to `origin`, and **not merged**. CI is green at
  `707cc3c` (run #66); the reading-order fix lands on top of that and is
  CI-verified separately.
- `feat/ci-feature-branch` = `76f0c96` + `dcbf852 ci: run Android CI on feature
  branch`, with **PR #2 open to `main`** (still open; deliberately NOT merged).
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

### Last change (this session) — "chunk 1 is fine, every later chunk is scrambled"

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
- **CI is green** (run #66, `35287232792`, at `707cc3c`): `Unit tests` and
  `Assemble debug APK` both succeeded. The reading-order fix (`6d826e0`) lands on
  top of that commit and is CI-verified separately. The remaining step is
  owner-side real-device confirmation: the Home chooser, the back loop, Reader
  narration surviving navigation, the redesigned Reader screen on a real device,
  and — most importantly — whether the owner's large PDF now shows chunk 2 and
  later in document order and narrates them correctly. **Nothing here is claimed
  fixed on-device.**
- Roadmap/order mismatch persists: the repo is ahead on Reader (5–8) and behind on
  Home (1), Product Navigation (2) and the Design System (3). `Theme.kt` still uses
  gold `#D4AF37` and near-black `#0A0A0B`, not the `#FFD700` / `#0A0A0F` in
  `AGENTS.md` §3 — the Reader redesign deliberately reused the existing brand
  colours rather than silently changing the app palette.

### Pending items (do NOT start before the above):
1. PDF/chunk caching beyond the last-document URI
2. ~~Reader UI redesign to match the Live Dub start screen style~~ — **done in this
   pass**; it needs device confirmation, not more design work
3. PDF viewer alongside audio

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
