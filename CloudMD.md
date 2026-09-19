# CloudMD.md — Voxora long-term project state

**Branch:** `feat/reader-segmented-spooling`
**Last updated:** 2026-09-19, at commit `cf0dc07` plus the uncommitted two-appearance / library /
chunk-cache cycle described below.

This file is the concise, durable state of the project: what is finished, what is in flight, what is
blocked, and the single next action. It is **not** a plan and it is not a wish list — an item is only
under DONE once the repository and CI prove it. `AGENTS.md` is the architecture record; `AgentMD.md`
holds the standing UI/i18n rules plus the current implementation record.

---

## DONE

- **The experimental light variant is removed; there are two appearances** (this cycle). The three
  variants were read from the code, not assumed: `ORIGINAL_DARK` ("Original Dark"), `LIGHT_TEST_1`
  ("Light Test 1") and `LIGHT_TEST_2` ("Voxora Light" — the appearance the request called "Voxlerai").
  `LIGHT_TEST_1` was removed completely — palette file, `Theme.kt` scheme and semantics blocks,
  `ThemeMode` entry, `SettingsScreen` branch, string in both locales and its test file — while the
  Voxora Light appearance was left untouched. `ThemeMode` now declares exactly `ORIGINAL_DARK` and
  `LIGHT_TEST_2`, and every legacy id (including `"light"` and `"light_test_1"`) migrates onto Voxora
  Light so an existing choice can never leave the app themeless. The two survivors stay independent by
  construction, and `themeguard.py` now enforces two modes and asserts that none of the removed
  candidate's values survives in code **or docs**.
- **The Dark UI's two semantic swaps are applied, in the Dark palette only** (this cycle). The status
  and explanation roles exchanged hues (`Success #7DD3FC`, `Explanation #3DDC84`) and the two text
  roles exchanged values (`OnSurface #C4BBA8`, `OnSurfaceVariant #F5F0E6`). Nothing else moved and the
  light appearance was not touched. The consequence is pinned rather than implied: the primary content
  role is deliberately the dimmer of the two text roles. The decorative `waveGreen #3DDC97` was
  deliberately **not** swept up, and contrast was re-derived — every content/help role still clears AA.
- **"Your Books" is one vertically expandable list** (this cycle). A single card holds every saved
  book, collapsed by default with the selected book's compact summary and Continue; it expands on a
  downward drag or a tap and collapses on an upward drag, with the gesture confined to the header row.
  Selecting a book does not open it: it reveals the real chunk, the state and the last-read line plus
  Continue/Remove, so browsing can never interrupt what is playing. The action's wording follows the
  book's real state, and a book whose copy is gone offers no action that could only fail. No new
  persistence was introduced.
- **Stop → Continue no longer re-synthesises the chunk the reader was on** (this cycle). New
  `ReaderChunkCache` (pure JVM) persists whole chunks — PCM plus every unit's boundary and transcript
  — under `cacheDir/reader-chunks/`, reused only when the whole key matches (book, chunk, chunk count,
  unit count, unit-text hash, language, mode, voice, model) and the unit boundaries validate
  all-or-nothing. Only whole chunks are stored; a restored chunk launches no producer at all and has
  its transcripts seeded before the first-unit gate; a restored chunk is replayed from unit zero or
  not at all; the cache is written in the run's `finally` as well as at promotion, which is what
  covers the run's own first chunk. Bounded (two per book, a byte budget, LRU, the just-written entry
  never evicted) and stored by **rename**, not copy. Nothing in it can fail a run. Resume remains
  keyed by the exact persisted logical chunk — never a text, character, PCM or sample offset.
- **Book Intelligence themes follow the reading language** (this cycle). The metadata-only one-shot
  prompt now also asks for the catalogue's subjects as headings **in the target language**, stored
  inside the per-language overview record so a language switch is structurally unable to show another
  language's themes; the card shows them in place of the catalogue's own headings and falls back with
  the existing source-language note when they are absent. Two latent defects were fixed: the prompt
  `VERSION` was documented as part of the cache key but consulted nowhere, and the view model tested
  "an entry exists" instead of `isUsableFor(...)`. A language-labelled code fence is now recognised
  rather than shown as prose.
- **Persistent book reopen is fixed, and Book Intelligence is localized** (`115d6df`, CI-verified).
  The reported real-device defect — a saved book reopening to `ExtractionException` / "Could not read
  this document…" — was **not** a bad document. `ReaderBook.withMetadata` replaces the display title
  with the catalogue title once identification succeeds, and type resolution was falling back to that
  title's "extension" because Voxora's own `file://` copy carries no MIME type. Type resolution is now
  one rule in `core/.../reader/ReaderDocumentType.kt` with a **known type always winning**: the
  persisted `sourceType` → provider MIME → a *file name*'s extension. A display title is never treated
  as a file name. A book whose copy is gone is now marked the persisted `UNAVAILABLE` state instead of
  being deleted (position and cached Book Intelligence are kept, no Continue is offered, no extraction
  is re-attempted), and reopening the already-open book is a no-op. Book Intelligence now separates
  *source metadata* (the catalogue's own text, shown unedited with its source language) from
  *explanatory content* (a labelled AI-generated overview produced by a separate one-shot
  metadata-only `generateContent` call, cached per output language, never the document and never the
  narration session). 681 pure-JVM tests pass locally (up from 642); the Android-side layers
  type-check; all six guards pass; Live Dub untouched. **Android CI is green** on `115d6df`:
  `Unit tests` and `Assemble debug APK` both `success` on the push run `35443414003` and the PR run
  `35443416114`.
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

## IN PROGRESS

- **The cycle's first CI run failed `Unit tests`; the fix is pushed and its run has not been read
  yet.** Push run `35450489051` (commit `99a54ac`) passed `Assemble debug APK` but failed
  `Unit tests` — **19 of 388**, all in the new `ReaderChunkCacheTest`, all `RuntimeException` at the
  shared `store` helper. The cause was not the cache: the app module's unit tests run against
  Android's **stubbed** `org.json`, whose methods throw, and `ReaderChunkCache` is the first
  app-module code any unit test has reached that writes JSON. `:core` already had
  `testImplementation("org.json:json:20240303")`; `app` did not. That dependency was added to
  `app/build.gradle.kts` (test-only, never shipped) and pushed. The run ids for the fix commit are
  recorded in the follow-up docs commit once they are read from the REST API. **The cycle is not
  CI-verified until that run is green.**
- **The local harness could not see that fault, and a guard now can.** `validate-cloud.sh` compiles
  and runs the same sources with a real `json-20240303.jar`, so the stub only exists under Gradle.
  New `jsonguard.py` fails if any `app/src/main` Kotlin file imports `org.json.*` while
  `app/build.gradle.kts` declares no real `org.json` on the test classpath; it was verified both ways.
  This is the same family as the §5 defects in `AgentMD.md` — a fault that exists only in the
  committed tree under Gradle.

## BLOCKED

- **Real-device verification is unavailable from this environment.** Everything about how the
  features *feel* is an owner-only item: the two-entry theme selector and the legacy-id redirect, the
  drag-to-expand library gesture under RTL, whether the swapped Dark roles read as intended, whether
  Continue actually plays from the cache, and whether the resume lands on the same chunk. CI proves it
  compiles and the pure-JVM suite proves its logic, not how it looks or sounds.
- **The cache's real hit rate is unmeasured.** The tests prove the reuse and eviction rules; they
  cannot prove that a real Stop usually lands on a chunk whose producer had finished. A missed cache
  costs a re-synthesis, never a lost position — `cacheDir` is the OS's to clear, and the cache is
  derived audio by design.
- **Live catalogue behaviour is unverified.** Google Books and Open Library contracts are pinned
  against `MockWebServer`; no real request was made, so live coverage, rate limits and real match
  quality are unknown.
- **The `generateContent` overview path is unverified against the live API.** The transport is pinned
  against `MockWebServer` and `DEFAULT_MODEL` (`models/gemini-2.0-flash`) has not been confirmed with
  a real key. A wrong model name degrades to "no overview" and nothing else; a model that ignores
  `responseMimeType` degrades to prose-without-themes, which the card renders as the catalogue's own
  headings.

## NEXT

Owner device verification of the whole cycle: (1) confirm the Settings theme selector offers exactly
two appearances and that an install whose saved preference was the removed one lands on Voxora Light
rather than failing; (2) open "Your Books", drag the header down and up, select a book and confirm its
real chunk and last-read appear and Continue resumes it; (3) play a chunk, let the next one prefetch,
tap Stop, then Continue, and confirm it resumes at the same place; (4) switch the output language and
confirm the Book Intelligence themes change language without narration being disturbed.

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
