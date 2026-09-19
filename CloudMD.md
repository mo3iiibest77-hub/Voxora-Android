# CloudMD.md — Voxora long-term project state

**Branch:** `feat/reader-segmented-spooling`
**Last updated:** 2026-09-19, at commit `2012916` — the exact-segment-resume / Book Intelligence
search-fallback / help-icon-style cycle is implemented, documented and **CI-verified**. It sits on top
of the owner's `32ad653` (`ci: skip Android CI when only documentation files change`).

This file is the concise, durable state of the project: what is finished, what is in flight, what is
blocked, and the single next action. It is **not** a plan and it is not a wish list — an item is only
under DONE once the repository and CI prove it. `AGENTS.md` is the architecture record; `AgentMD.md`
holds the standing UI/i18n rules plus the current implementation record.

---

## DONE

- **Exact segment-level resume, a Book Intelligence search fallback, and the help-icon style are
  CI-verified** at `2012916` (on top of the owner's `32ad653`): **Android CI push run
  `35463634403` (#165) is green — both jobs success**, `Unit tests` 10/10 steps including `Run unit
  tests` and `Assemble debug APK` 14/14 steps including `Assemble debug` and `Upload debug APK`. No
  `pull_request` run existed for this SHA at the time of inspection. Locally, 802 pure-JVM tests pass
  across 66 classes (up from 752 across 63), `validate-reader-android.sh` type-checks the Android-side
  Reader layers, and all seven guards pass. See `AgentMD.md` §9. The three parts:
  - **Stop → Play resumes the exact segment.** The defect was one line — `ReaderController.stop()`
    set `segmentIndex = 0` while keeping the chunk. The segment is now part of the persisted position:
    `ReaderBook.currentSegment` (defaulted, so a legacy record migrates to segment 0), kept by Stop
    and Pause alike, restored before playback and clamped against the real unit count by
    `ReaderPosition.clampSegment`. A monotonic `positionStamp`, captured under the controller's lock
    and enforced in the pure reducer, stops a stale fire-and-forget save from overwriting a newer
    one. `Slot.startUnit` separates the persisted logical segment, the cache entry's first unit and
    the playback cursor, so a chunk restored from the cache plays from the saved segment instead of
    re-narrating — `ReaderSpool.unitStart` is the pure arithmetic behind it.
  - **Book Intelligence falls back to a bounded, grounded search.** When Google Books and Open
    Library produce no confident match, `GeminiGroundedBookSearch` runs one `generateContent` call
    with the `google_search` tool on the **existing** key and endpoint (no new credential or vendor),
    and `BookIntelOverviewPlan` is the one pure decision the UI and repository share — so a book no
    catalogue identified is no longer skipped. `BookSearchPrompt` keeps findings bounded and treats
    `NOT_FOUND` as a negative finding; `buildFromContext` states the findings as second-hand evidence
    and forbids invention; the context fingerprint is part of the per-language cache key and
    `VERSION` was raised 2 → 3. Nothing from the search ever becomes a metadata field. A cover
    fallback acts only on the document's own validated ISBN, only after the image is verified, and
    never displaces a catalogue cover.
  - **The help icon joins the ordinary card icon language.** `HelpIconButton` now uses the same
    circular `VoxoraColors.glow` wash and `colorScheme.primary` glyph as the Reader's other section
    icons instead of its own explanation tint; still clickable, dialog unchanged, no text added, no
    literal colour, content description and RTL preserved.
  - **No real-device testing was performed**, and the grounded search and the cover fallback have
    never contacted their live services; see BLOCKED.
- **The 100 MB / exact-resume / bounded-prefetch / help-affordance / observed-usage cycle is
  CI-verified** at `80fdae2` (Android CI push run `35458802897` #158 and `pull_request` run
  `35458806113` #159): **both jobs success** — `Assemble debug APK` 14/14 steps and `Unit tests`
  10/10 steps. It needed one fix round first: `d1209ba` failed **both** jobs on a single Compose
  compile error (`ReaderScreen.kt:267 No parameter with name 'onOpen' found` — the
  `ReaderLibrarySection` callback had been renamed `onOpen` → `onContinue` and the call site was
  missed), so **no test ran** in that round; `9be68d1` fixed it and was green
  (`35458516239` #156 / `35458518642` #157), and `80fdae2` released a retried chunk from the prefetch
  map. `Assemble debug` is the only real compile check for the Compose files. Locally, 752 pure-JVM
  tests pass across 63 classes (up from 715 across 59), `validate-reader-android.sh` type-checks the
  Android-side Reader layers, and all seven guards pass. **No real-device testing was performed**; see
  BLOCKED.
- **The document limit is 100 MB, from one rule** (this cycle). It had been declared twice,
  independently — `TextExtractor.MAX_BYTES` and `ReaderDocumentStore.MAX_BYTES` — with the number
  also written by hand into the error string and two extraction messages, so raising one would have
  left the other refusing a file the app claimed to accept. New `core/.../reader/ReaderDocumentLimits.kt`
  (`MAX_BYTES = 100 MiB`, inclusive) is now the single source of truth consulted by both boundaries,
  and the extraction message interpolates its label. The streaming read still buffers at most one
  byte past the limit, so nothing is loaded eagerly. Both locales say 100 MB.
- **Continue restores a book's own persisted chunk and starts narration** (this cycle). Chunk-level
  resume was already per book and already persisted; the gap was that the library's Continue only
  opened the book, so "keep listening" was two presses. `ReaderViewModel.continueBook` now starts
  playback once extraction finishes, on its own job rather than inside the serialized command queue —
  holding the queue open for a large document's extraction would have made Stop, the action a reader
  reaches for when a restore is slow, appear dead.
- **The rolling prefetch is bounded, tested and deduplicated** (this cycle). The look-ahead decision
  had lived inline in the Android-bound controller, so its bound and end-of-book behaviour had no
  pure test, and nothing prevented the same index being prepared twice. New pure-JVM
  `ReaderPrefetchWindow` decides the index (one chunk ahead, never past the end, already-prepared
  indices skipped) and the run keeps a `prepared` map so a repeated request coalesces onto the slot
  already producing that artifact. Each slot is released on promotion, released again on the
  empty-chunk retry, and the map is cleared when the run exits. Cancellation is unchanged and already
  complete (`generation` + `navigationRevision` + `checkOwned`, with `cancelOwned` on book switch and
  stop). The persisted chunk cache was **not** redesigned: inspection showed it already met the
  requirement (full key, all-or-nothing boundary validation, corruption-is-a-miss, rename publish,
  two per book plus a byte budget with LRU); it gained hit/miss/store logging.
- **The green informational sections gained an info affordance** (this cycle). New
  `ReaderInfoHint.kt` adds `HelpIconButton` (Material `Icons.Outlined.Info` in the explanation role,
  a content description naming the section, an `AlertDialog` dismiss — the pattern the library's
  removal confirmation already uses) and `ExplanationNote`, which keeps each section's sentence
  exactly as it was and puts the icon beside it in a plain `Row` so RTL mirrors without a hand-set
  direction. Adopted on the reading-page header, the playback hint, the bubble explanation, the voice
  explanation and the Book Intelligence generated-overview note; nine new strings in both locales.
  `reader_pause_hint` was also corrected — it had claimed Stop returns to the first chunk.
- **The usage dashboard reports a real observed window and charts only real data** (this cycle). New
  pure-JVM `UsageSeries` and `GeminiUsageLedger.window(...)` feed `requestsThisWeek` (rolling seven
  UTC days) and `observedDaily`. Two separate flags keep it honest: a ledger with activity outside
  this week reports a real `0` for the week, while a ledger with nothing ever recorded reports
  `UNKNOWN` and produces **no chart at all**. A day with no request is a measured zero, bars are
  scaled against the busiest day, and there is deliberately no quota line. The project/account side
  is unchanged: `projectQuota` and `billing` remain `AUTH_REQUIRED`, because an API key genuinely
  cannot read them.
- **The whole previous cycle is CI-verified** at `1b2da81` (Android CI push run `35451720666` and
  `pull_request` run `35451723199`, created 2026-09-19T15:28Z): **both jobs success in both runs** —
  `Unit tests` 10/10 steps including `Run unit tests`, and `Assemble debug APK` 14/14 steps including
  `Assemble debug` and `Upload debug APK`. `Assemble debug` is the only real compile check for
  `ReaderLibrarySection.kt` and the palette wiring; the dev server cannot compile Compose. The cycle
  needed two fix rounds first — `Unit tests` failed 19/388 on `99a54ac` and 1/388 on `00094aa`, both
  from Android unit-test stubs (`org.json`, then `android.util.Log`) — see `AgentMD.md` §7 for the full
  analysis. **No real-device testing was performed**; every
  appearance, gesture and cache-hit claim is a **DEVICE VERIFICATION PENDING** item.
- **Two CI rounds, both real faults the local harness structurally cannot see** (this cycle).
  `Assemble debug APK` passed throughout. (1) `99a54ac` failed `Unit tests` **19 of 388**, all in the
  new `ReaderChunkCacheTest` at its `store` helper: the app module's unit tests run against Android's
  **stubbed** `org.json`, and `ReaderChunkCache` is the first app-module code any unit test has reached
  that writes JSON. `:core` already had `testImplementation("org.json:json:20240303")`; `app` did not.
  Fixed in `00094aa` (test-only). (2) `00094aa` then failed **1 of 388** —
  `anUnreadableEntryIsAMissRatherThanAFailure` at `ReaderChunkCacheTest.kt:217` — because with JSON
  fixed the test reached the cache's error path, which logs through `VoxoraLog` → `android.util.Log`,
  itself a stub that throws. `testOptions { unitTests.isReturnDefaultValues = true }` was added in
  `1b2da81`. `validate-cloud.sh` sees neither stub (it supplies a real `json-20240303.jar` and a
  plain-JVM `VoxoraLog` stub), so new `apptestguard.py` now fails statically on either omission. Note
  `isReturnDefaultValues` does **not** replace the real `org.json`: defaulted JSON accessors return null
  and would break the cache, not fix it. Same family as the §5 defects in `AgentMD.md` — faults that
  exist only in the committed tree under Gradle.
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

- Nothing. The exact-segment-resume / search-fallback / help-icon cycle is implemented, documented and
  pushed at `2012916`; its CI run is being observed and its result recorded above. The only open items
  are the device-verification and live-API checks under BLOCKED.

## BLOCKED

- **Real-device verification is unavailable from this environment.** Everything about how the
  features *feel* is an owner-only item: the two-entry theme selector and the legacy-id redirect, the
  drag-to-expand library gesture under RTL, whether the swapped Dark roles read as intended, whether
  Continue actually plays from the cache, and whether the resume lands on the same chunk. CI proves it
  compiles and the pure-JVM suite proves its logic, not how it looks or sounds. This cycle adds the
  headline item the fix exists for: **whether Stop → Play audibly resumes at the exact segment**,
  whether a cached chunk plays from the saved segment without re-narrating, whether two books keep
  independent positions across a real process death, and whether the help icon now reads as part of
  the card.
- **The grounded search path has never run against the live API.** The `google_search` request shape
  and the `groundingMetadata` parsing are pinned against `MockWebServer`; whether the configured model
  accepts the tool, real latency, quota cost and the quality of real search findings are unverified.
  A failure degrades to "no overview" and nothing else.
- **The cover fallback has never contacted Open Library.** Its URL shape, `image/*` content-type and
  minimum-size rules are pinned by tests; a live 404, redirect or rate limit is unexercised. It can
  only ever add a cover to a book that had none.
- **The cache's real hit rate is unmeasured.** The tests prove the reuse and eviction rules; they
  cannot prove that a real Stop usually lands on a chunk whose producer had finished. A missed cache
  costs a re-synthesis, never a lost position — `cacheDir` is the OS's to clear, and the cache is
  derived audio by design. The prefetch's coalescing and cancellation are likewise reasoned from the
  code plus the pure `ReaderPrefetchWindow` test; the controller's orchestration of them is only
  type-checked, never executed locally.
- **Live catalogue behaviour is unverified.** Google Books and Open Library contracts are pinned
  against `MockWebServer`; no real request was made, so live coverage, rate limits and real match
  quality are unknown.
- **The `generateContent` overview path is unverified against the live API.** The transport is pinned
  against `MockWebServer` and `DEFAULT_MODEL` (`models/gemini-2.0-flash`) has not been confirmed with
  a real key. A wrong model name degrades to "no overview" and nothing else; a model that ignores
  `responseMimeType` degrades to prose-without-themes, which the card renders as the catalogue's own
  headings.

## NEXT

Owner device verification, this cycle first: (1) play into Chunk 3 / Segment 3, tap Stop, then Play,
and confirm it resumes at that segment rather than restarting the chunk — then repeat for two
different books and confirm each keeps its own position, and confirm it survives killing the app;
(2) import a book no catalogue identifies, wait for the Book Intelligence card, and confirm it shows a
generated paragraph labelled as generated with no invented author, year or publisher, and that a book
with a real catalogue cover is not given a different one; (3) confirm the help icon beside each
explanation sentence matches the card's other icons and still opens the dialog in both English and
Persian. Then the earlier cycles' items: (4) confirm the Settings theme selector offers exactly two
appearances and that an install whose saved preference was the removed one lands on Voxora Light;
(5) open "Your Books", drag the header down and up, select a book and confirm its real chunk and
last-read appear and Continue resumes it; (6) switch the output language and confirm the Book
Intelligence themes change language without narration being disturbed; (7) import a file just under
100 MB and confirm it is accepted, then confirm a file over 100 MB is refused with the 100 MB
message; (8) tap the info icon beside each green section and confirm the dialog opens, reads correctly
and dismisses, in both English and Persian; (9) confirm the usage chart appears only after a request
has been made and that no figure implies a quota.

---

## Standing facts worth not rediscovering

- **No local Gradle, ever.** 4 GB / 2 cores; GitHub Actions is the only Android build. Pure-JVM
  validation lives in `/tmp/rr` (`validate-cloud.sh` for the full contract suite,
  `validate-reader-android.sh` for the Android Reader layers, `validate-sync.sh`/`validate-dubservice.sh`
  for Live Dub, plus the seven guard scripts). `validate-cloud.sh` and `validate-reader-android.sh`
  both needed their source lists extended for each new file this cycle; a green local run is necessary,
  not sufficient.
- **A documentation-only commit does not start CI.** The owner's `32ad653` added `paths-ignore`
  (`**/*.md`, `docs/**`, `LICENSE`, `.gitignore`, `.editorconfig`) to both the `push` and
  `pull_request` triggers of `Android CI`, so a commit that changes only `.md` files produces **no
  run at all** — not a skipped job. A code commit still triggers both jobs. Do not read "no run" as
  "green": verify the commit that actually contains the source changes.
- **The harness cannot compile Compose.** A green local run is necessary, not sufficient.
- **The harness has two known blind spots**, both of which have let a real compile error reach CI:
  `validate-reader-android.sh` substitutes a stub for `TextExtractor.kt` (no PDFBox jar), and it does
  not cover the Compose files at all. `checkimports.py` was extended with a `WATCHED_PROJECT` list to
  catch the missing-project-import case in both blind spots, but nothing local compiles Compose — and
  the second blind spot struck again at `d1209ba`, where a renamed Compose parameter
  (`ReaderLibrarySection`'s `onOpen` → `onContinue`) left a call site in `ReaderScreen.kt` stale and
  failed both CI jobs. `checkimports.py` covers missing imports in those files, not renamed
  parameters, so it passed on the broken tree. CI remains the only defence for Compose.
- **CI job logs need a token.** The REST job-log endpoint returns 403 unauthenticated; `gh` is not
  logged in. `~/.git-credentials` holds a token that works for both the run/job API and the log
  download (follow the 302 to the signed URL, without the `Authorization` header).
- **Live Dub and the Reader share nothing but the API key and the language catalog.** Enforced by
  `dubguard.py` and `ReaderDubIsolationTest`.
- **`PROJECT_CONTEXT.md` is stale** (it still describes the v0.6.1 experimental lipsync overlay, which
  is dead code). It was left untouched deliberately — it is a version history note, not a claim about
  current state — but it should not be trusted as a description of the app.
