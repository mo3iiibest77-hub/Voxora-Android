# CloudMD.md — Voxora long-term project state

**Branch:** `feat/reader-segmented-spooling`
**Last updated:** 2026-09-19, at commit `8293a981e67032fb054321838d0f845d64431a67` (`8293a98`).

This file is the concise, durable state of the project: what is finished, what is in flight, what is
blocked, and the single next action. It is **not** a plan and it is not a wish list — an item is only
under DONE once the repository and CI prove it. `AGENTS.md` is the architecture record; `AgentMD.md`
holds the standing UI/i18n rules plus the current implementation record.

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
  (`de89612`, CI-verified at `8293a98`). Two semantic voices mapped to Gemini prebuilt voices with
  the voice threaded through every session; imported books persisted as records plus an app-owned
  copy of the document; multi-book library with independent progress; resume saved at safe
  transitions and Stop no longer resetting the queue; automatic identification against Google Books
  and Open Library with a conservative matcher and a source-backed "About This Book" section. 642
  pure-JVM tests pass locally; the Android-side layers type-check against `android.jar` with stubs;
  all six guards pass; Live Dub untouched. **Android CI is green** on `8293a98`: `Unit tests` and
  `Assemble debug APK` both `success` on the push run `35439870596` and the PR run `35439873171`.
- **The three build defects that first blocked that cycle are fixed** (`22a1b69`, `8293a98`): a
  merged PDFBox import line in `TextExtractor.kt`, plus a missing `VoxoraColors` import and a
  missing `BookSignals` import. All three were committed-tree-only faults that the local harness
  could not see; see `AgentMD.md` §5 for why, and for the `checkimports.py` extension that now
  catches the missing-import class locally.
- **Persistent book reopen is fixed, and Book Intelligence is localized** (commit SHA and CI result
  recorded in the follow-up documentation commit). The reported real-device defect — a saved book
  reopening to `ExtractionException` / "Could not read this document…" — was **not** a bad document.
  `ReaderBook.withMetadata` replaces the display title with the catalogue title once identification
  succeeds, and type resolution was falling back to that title's "extension" because Voxora's own
  `file://` copy carries no MIME type. Type resolution is now one rule in
  `core/.../reader/ReaderDocumentType.kt` with a **known type always winning**: the persisted
  `sourceType` → provider MIME → a *file name*'s extension. A display title is never treated as a
  file name. A book whose copy is gone is now marked the persisted `UNAVAILABLE` state instead of
  being deleted (position and cached Book Intelligence are kept, no Continue is offered, no
  extraction is re-attempted), and reopening the already-open book is a no-op. Book Intelligence now
  separates *source metadata* (the catalogue's own text, shown unedited with its source language)
  from *explanatory content* (a labelled AI-generated overview produced by a separate one-shot
  metadata-only `generateContent` call, cached per output language, never the document and never the
  narration session). 681 pure-JVM tests pass locally (up from 642); the Android-side layers
  type-check; all six guards pass; Live Dub untouched.

## IN PROGRESS

- Nothing. The Reader work above is implemented and locally validated; CI verification and the
  documentation SHA/CI record are the only steps outstanding.

## BLOCKED

- **Real-device verification is unavailable from this environment.** Everything about how the
  features *feel* (voice character, cover loading, RTL library layout, resume across a real process
  death, the rate trim's audibility) is an owner-only item, and the reopen fix itself has not been
  exercised on a device — it is proven by unit tests and by tracing the lifecycle, not by reopening a
  real book. CI proves it compiles and the pure-JVM suite proves its logic, not how it looks or
  sounds.
- **Live catalogue behaviour is unverified.** Google Books and Open Library contracts are pinned
  against `MockWebServer`; no real request was made, so live coverage, rate limits and real match
  quality are unknown.
- **The `generateContent` overview path is unverified against the live API.** The transport is pinned
  against `MockWebServer` and `DEFAULT_MODEL` (`models/gemini-2.0-flash`) has not been confirmed with
  a real key. A wrong model name degrades to "no overview" and nothing else.

## NEXT

Owner device verification: import a PDF, let identification succeed so the title becomes the
catalogue title, then **tap the saved card and confirm the book reopens at its saved chunk** instead
of showing "Could not read this document…". Then confirm the Book Intelligence section shows the
catalogue's description with its source language plus a labelled AI-generated overview in the
selected output language. No further Reader code work is pending.

---

## Standing facts worth not rediscovering

- **No local Gradle, ever.** 4 GB / 2 cores; GitHub Actions is the only Android build. Pure-JVM
  validation lives in `/tmp/rr` (`validate-cloud.sh` for the full contract suite,
  `validate-reader-android.sh` for the Android Reader layers, `validate-sync.sh`/`validate-dubservice.sh`
  for Live Dub, plus the six guard scripts).
- **The harness cannot compile Compose.** A green local run is necessary, not sufficient.
- **The harness has two known blind spots**, both of which let a real compile error reach CI once:
  `validate-reader-android.sh` substitutes a stub for `TextExtractor.kt` (no PDFBox jar), and it does
  not cover the Compose files at all. `checkimports.py` was extended with a `WATCHED_PROJECT` list to
  catch the missing-project-import case in both blind spots, but nothing local compiles Compose.
- **CI job logs need a token.** The REST job-log endpoint returns 403 unauthenticated; `gh` is not
  logged in. `~/.git-credentials` holds a token that works for both the run/job API and the log
  download (follow the 302 to the signed URL, without the `Authorization` header).
- **Live Dub and the Reader share nothing but the API key and the language catalog.** Enforced by
  `dubguard.py` and `ReaderDubIsolationTest`.
- **`PROJECT_CONTEXT.md` is stale** (it still describes the v0.6.1 experimental lipsync overlay, which
  is dead code). It was left untouched deliberately — it is a version history note, not a claim about
  current state — but it should not be trusted as a description of the app.
