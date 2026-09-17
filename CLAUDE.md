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
- Working tree was clean before this change. Branch `feat/reader-segmented-spooling`
  was **2 commits ahead of `main`** and is not merged.
  - HEAD before this milestone: `af5ff6d fix(build): restore core unit-test compilation and run tests in CI`
  - `main`: `1f0a719 fix(reader): fix suspend output language persistence`
- Reader is implemented well **beyond** the roadmap's Milestone 5–8 scope:
  `ReaderController` (singleton, generation-guarded), segmented `ReaderSpool`,
  `ChunkQueue` (500-word chunks + 80-word narration units), per-chunk
  `GeminiReaderSession`, language catalog, document persistence, and a
  foreground `mediaPlayback` service. Live Dub is untouched.
- Roadmap Milestone 0 (Foundation & Contracts) was addressed on `af5ff6d`:
  `:core` now declares JUnit + real `org.json` test dependencies, and CI has an
  independent `unit-tests` job. It has **not been observed passing** —
  `android-ci.yml` triggers only on push/PR to `main`, so feature-branch pushes
  produce no CI run at all.

### Last change on this branch (this session) — Milestone 1 slice: Voxora Home
- `feat(home)`: `HomeScreen` is now a neutral Voxora product chooser — Voxora
  header plus two equally weighted product cards (Live Dub, Voxora Reader) and a
  Settings entry. The Live Dub working surface (status, waveform, error banner,
  Start/Stop, hints) moved into a new `app/.../ui/DubScreen.kt`. `VoxoraNav` now
  has a `DUB` destination via a private `VoxoraScreen` enum (`ONBOARDING`,
  `HOME`, `DUB`, `READER`, `SETTINGS`, `LOGS`) plus a nav-level `BackHandler`
  where Home is the root. **No Navigation Compose graph was added.**
- Reader is no longer a `TextButton` on the Live Dub surface.
- Live Dub engine (`dub/**`, `GeminiLiveSession`, `GeminiLiveConfig`) and the
  Reader engine/pipeline were **not modified**. Reader playback is still owned by
  `ReaderController` / `ReaderService`; navigating away from Reader does not stop
  narration (`ReaderViewModel` has no `onCleared` stop).
- New strings: `home_tagline`, `dub_title`, `dub_card_desc`, `dub_card_action`,
  `reader_card_desc`, `reader_card_action` — present in `values/` and `values-fa/`.
- Validation actually performed (no Gradle): repository inspection, XML
  well-formedness of every `values*/strings.xml`, cross-check that all 122
  `R.string.*` references in Kotlin resolve against the default locale,
  unused-import review of the changed files, and verification against the pinned
  `material-icons-extended:1.7.6` AAR that the chosen icons exist
  (`Icons.Filled.Translate`, `Icons.AutoMirrored.Filled.MenuBook`).
  **No local Gradle task was run** (project rule). **No CI result observed.**
- Not yet verified on a device: the Home → product → back loop, and that Reader
  narration survives leaving the Reader destination.

### Next milestone (roadmap order):
- Milestone 1 remainder / Milestone 2 — **Product Navigation**: have the owner
  install the debug APK, confirm the Home chooser and back loop on a real device,
  and confirm Reader narration continues after navigating away from Reader. Then
  continue the roadmap's Product Navigation milestone.
- Note the roadmap/order mismatch: the repo is ahead on Reader (5–8) and behind
  on Home (1), Product Navigation (2) and the Design System (3). `Theme.kt` still
  diverges from the brand palette in `AGENTS.md` §3 — it uses gold `#D4AF37` and
  near-black `#0A0A0B`, not `#FFD700` / `#0A0A0F`.

### Pending items (do NOT start before the above):
1. PDF/chunk caching beyond the last-document URI
2. Reader UI redesign to match the Live Dub start screen style
3. PDF viewer alongside audio

Items previously listed as "Reader/Dub entry chooser at app launch" and
"Navigate back while audio plays" are now **implemented in code** but still need
real-device confirmation before being treated as done. DO NOT start the pending
items above until the audio pipeline is confirmed stable by a real device test.

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
