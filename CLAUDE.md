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
PDF/TXT → TextExtractor → ChunkQueue → GeminiReaderSession →
PCM → ReaderPlayback (AudioTrack) → foreground media service

Reader files:
- app/.../reader/ReaderController.kt (singleton, owns generation)
- app/.../reader/ReaderService.kt (foreground media service)
- app/.../reader/ReaderPlayback.kt (AudioTrack, USAGE_MEDIA)
- app/.../reader/ReaderViewModel.kt
- app/.../reader/ReaderScreen.kt
- core/.../gemini/GeminiReaderSession.kt (Gemini WebSocket session)

Key policy:
- Gemini API key from UserPrefs only (no external backend)
- Reader MUST be isolated from Live Dub
- Background playback like Live Dub (foreground service + notification)
- Volume follows hardware media keys (USAGE_MEDIA)

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
- `feat/reader-segmented-spooling` (the implementation branch) is at
  `4e4e17a fix(reader): preserve original sink exception` — **5 commits ahead of
  `main`**, pushed to `origin`, and **not merged**.
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

### Last change (this session) — sink exception propagation fix
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
- Close out Milestone 1 / Milestone 2 — **Product Navigation**: the green
  `unit-tests` job has now been obtained (run #63 on `4e4e17a`). The remaining
  step is owner-side: install the debug APK and confirm the Home chooser, the
  back loop, and Reader narration surviving navigation.
- Roadmap/order mismatch persists: the repo is ahead on Reader (5–8) and behind on
  Home (1), Product Navigation (2) and the Design System (3). `Theme.kt` still
  uses gold `#D4AF37` and near-black `#0A0A0B`, not `#FFD700` / `#0A0A0F`.

### Pending items (do NOT start before the above):
1. PDF/chunk caching beyond the last-document URI
2. Reader UI redesign to match the Live Dub start screen style
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
