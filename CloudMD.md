# CloudMD.md — Voxora long-term project state

**Branch:** `feat/reader-segmented-spooling`
**Last updated:** 2026-09-19, at commit `de8961294090842e5758bdc715e2e607ba0350ce` (`de89612`).

This file is the concise, durable state of the project: what is finished, what is in flight, what is
blocked, and the single next action. It is **not** a plan and it is not a wish list — an item is only
under DONE once the repository and CI prove it. `AGENTS.md` is the architecture record; `AgentMD.md`
holds the standing UI/i18n rules plus the current implementation record. This file did not exist on
this branch before this commit, so nothing here is a claim about earlier state.

---

## DONE

- **Voxora Light is an independent appearance** (`d037a20`). Three palettes (`OriginalDarkPalette`,
  `LightTest1Palette`, `LightTest2Palette`), the owner's supplied Light values verbatim, WCAG AA
  measured and documented, `glow` promoted to a semantic role. CI-verified.
- **Live Dub synchronization** (`8d4397f`). The 4 s lag was diagnosed as a measured-latency model
  that had no floor, a fabricated source clock, an uncounted hand-off backlog and avoidable local
  buffering. Replaced with a sliding-window latency floor, a real capture clock, accounted hand-off,
  a bounded playback-rate trim and a `[DUB_SYNC]` diagnostic line. CI-verified.
- **The canonical Persian standard** is a permanent, project-wide rule (`AGENTS.md` §10,
  `AgentMD.md` §2), enforced by `bidi_fa.py` and `stringcheck.py`.
- **Reader voice selection, persistent library, chunk-level resume and Book Intelligence**
  (`de89612`, this commit). Two semantic voices mapped to Gemini prebuilt voices with the voice
  threaded through every session; imported books persisted as records plus an app-owned copy of the
  document; multi-book library with independent progress; resume saved at safe transitions and Stop
  no longer resetting the queue; automatic identification against Google Books and Open Library with
  a conservative matcher and a source-backed "About This Book" section. 642 pure-JVM tests pass
  locally; the Android-side layers type-check against `android.jar` with stubs; all six guards pass;
  Live Dub untouched. **CI verification of this commit is the immediate next action and is not yet
  recorded — see BLOCKED.**

## IN PROGRESS

- Nothing. The Reader cycle above is implemented and locally validated; it is waiting only on its CI
  result.

## BLOCKED

- **CI result for `de89612` is not yet read.** The commit is committed locally; the push and the
  Android CI run for it are the next action. Until that run reports `Unit tests` and
  `Assemble debug APK` both green, the Reader UI's compilation is unproven — the local harness cannot
  compile Compose.
- **Real-device verification is unavailable from this environment.** Everything about how the
  features *feel* (voice character, cover loading, RTL library layout, resume across a real process
  death, the rate trim's audibility) is an owner-only item.
- **Live catalogue behaviour is unverified.** Google Books and Open Library contracts are pinned
  against `MockWebServer`; no real request was made, so live coverage, rate limits and real match
  quality are unknown.

## NEXT

Read the Android CI run for `de89612` via the GitHub REST API and confirm both jobs are `success`.
If `Assemble debug` fails, the fault is in a Compose file the local harness cannot compile
(`ReaderScreen.kt`, `ReaderVoiceSection.kt`, `BookIntelCard.kt`, `ReaderLibrarySection.kt`,
`ReaderCoverImage.kt`) — fix it before starting anything else.

---

## Standing facts worth not rediscovering

- **No local Gradle, ever.** 4 GB / 2 cores; GitHub Actions is the only Android build. Pure-JVM
  validation lives in `/tmp/rr` (`validate-cloud.sh` for the full contract suite,
  `validate-reader-android.sh` for the Android Reader layers, `validate-sync.sh`/`validate-dubservice.sh`
  for Live Dub, plus the six guard scripts).
- **The harness cannot compile Compose.** A green local run is necessary, not sufficient.
- **Live Dub and the Reader share nothing but the API key and the language catalog.** Enforced by
  `dubguard.py` and `ReaderDubIsolationTest`.
- **`PROJECT_CONTEXT.md` is stale** (it still describes the v0.6.1 experimental lipsync overlay, which
  is dead code). It was left untouched deliberately — it is a version history note, not a claim about
  current state — but it should not be trusted as a description of the app.
