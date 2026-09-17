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

### Last known good commit on main:
c05ff63 — feat(reader): move narration off main into media playback service

### What this commit did:
- Moved narration pipeline off main thread into ReaderService foreground
- Added background playback with notification Stop button
- Fixed volume to use USAGE_MEDIA (hardware volume keys work)
- Updated AGENTS.md and EN/FA strings
- CI: PASSED

### What OpenCode is working on RIGHT NOW:
Fixing the audio pipeline bug confirmed by device test:

Bug 1 — "Narration audio buffer is full." (Fluent mode)
Root cause: GeminiReaderSession uses Channel<FloatArray>(512) and
calls trySend() from OkHttp callback thread. Gemini produces audio
faster than realtime. trySend() fails immediately when channel full.

Bug 2 — "sent ping but didn't receive pong within 12000ms"
Root cause: Network instability (VPN + 4G in Iran) + pingInterval
of 12s is too tight.

Bug 3 — Simple mode: choppy/fragmented audio
Likely related to Bug 1 and AudioTrack buffer pressure.

The fix being implemented:
- Decouple OkHttp callback from audio pipeline using a raw bytes
  Channel(UNLIMITED) + a decode coroutine that does send() with
  proper backpressure into bounded audioChannel
- Increase pingInterval from 12s to 25s
- Remove fail("Narration audio buffer is full.") fatal error

### Pending items AFTER audio pipeline is stable:
1. PDF/chunk caching (user must re-import book every session)
2. Reader/Dub entry chooser at app launch (instead of bottom tab)
3. Reader UI redesign to match Live Dub start screen style
4. Navigate back while audio plays (currently blocks navigation)
5. PDF viewer alongside audio (read visually while listening)

DO NOT implement items 1-5 until audio pipeline is confirmed stable
by real device test.

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
