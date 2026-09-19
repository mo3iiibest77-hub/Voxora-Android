# AgentMD — standing rules for future UI work

This file is deliberately short. It holds the rules that **every future UI change must follow**
and that are easy to lose in a long architecture document. `AGENTS.md` remains the canonical
architecture record; this file is the permanent contract for typography and i18n, and it does not
expire with any single feature.

Read this before touching a composable, a string, or a theme.

---

## 1. Typography is a theme decision, never a call-site decision

**The rule.** A screen uses `MaterialTheme.typography.*` and nothing else. No `sp` value, no
`fontFamily`, no `fontWeight` override to fake a size, and no `TextStyle(...)` built inline.

**Why.** Typography has to come from one place, so that a Persian screen and an English screen of the
same role render at the same scale and a screen can never drift from the type scale.

**How it is implemented.**

- `Theme.kt` calls `MaterialTheme(colorScheme = colorScheme, content = content)` and passes **no**
  `typography` argument. The app therefore uses the stock **Material 3 `Typography`** — the platform's
  own type scale and the platform's own font.
- **Voxora does not bundle a font and does not override the font family.** There is no
  `ui/theme/Type.kt`, no `res/font/` directory and no font dependency. The device's system font
  renders the UI, including Persian, which is what the product shipped before and what the owner
  requires.
- Do not add a bundled font, a `FontFamily` override, or a `res/font/` directory "to guarantee
  Persian". This was tried and explicitly rejected: shipping a Persian-only cut forced every Latin
  product name through font fallback and changed the app's appearance on every device.

**Applying it.**

- Adding a new screen: use `MaterialTheme.typography.*`. Nothing else to wire.
- Need a size or weight that the scale does not have? Change the screen's role, or raise it as a
  deliberate design decision — do not reach for a literal `sp` or a `fontFamily`.
- **The one sanctioned exception** is the Logs list, which sets `FontFamily.Monospace` because a
  timestamp and a bracketed tag are technical identifiers, not prose. Do not add a second exception
  without recording the reason here.
- `checkimports.py` is the cheap guard for Compose symbols used without an import; the CI
  `Assemble debug` job is the real compile check for Compose, which the dev server cannot run.

---

## 2. Persian is written, not translated — and RTL is fixed in the layout

> **The canonical Persian standard.** The current Persian UI writing and localization style is the
> canonical Persian standard for Voxora and must be preserved across all future features. Before
> adding or modifying any Persian text, **inspect the existing Persian text first and match it** —
> wording, sentence structure, register, terminology, characters, spacing and نیم‌فاصله (ZWNJ),
> punctuation and RTL/BiDi behaviour. Extend the existing Persian language system; never start a new
> writing style, never machine-translate, never hand-reverse a string, never pad with spaces, and
> never add an LRM/RLM/LTR/RTL hack unless a specific BiDi case technically requires it. This is a
> permanent project-wide Localization and UI Design Principle, not a visual preference.

**The rule.** Every user-visible string exists in `res/values/strings.xml` and
`res/values-fa/strings.xml`. The Persian is authored in one register, uses the product's fixed
terminology, and is never machine-translated or transliterated.

**Why.** Persian is a launch language, not a translation target. A mixed register or a transliterated
product name is the clearest signal that the UI was not written for its user.

**Register and terminology.** One register only — the informal second person (`انتخاب کن`, never
`انتخاب کنید`). Product and technical names stay Latin: Gemini, Google, Google Cloud, Google AI
Studio, API, PDF, TXT, OCR, URL, Reader, Live Dub, Voxora. Terminology is fixed in `AGENTS.md` §10:
`document` → `فایل` (never `سند`), `chunk` → `بخش`, narration `segment` → `قطعه`, `narration` →
`روایت`, `usage` → `مصرف`, `quota` → `سهمیه`, `logs` → `لاگ‌ها`. Two different states never share a
Persian label. Numbers stay Western Arabic digits, matching `%d`.

**RTL is a layout property.** Correct alignment and ordering come from Compose's RTL-aware layout
(`LocalLayoutDirection`, `Icons.AutoMirrored.*`). Never fix a Persian layout by:

- hand-reversing a word or a phrase;
- padding with spaces to push a word to the other side;
- putting `TextAlign.Center` on a whole screen to make a mixed string "look right" (`Center` belongs
  only where the surrounding layout really is centred);
- forcing `LayoutDirection.Ltr` on product UI (the log list is the one technical region where that
  is correct).

**Embedded Latin is isolated.** A Persian sentence containing a Latin run (`Gemini API`,
`Google AI Studio`, `Live Dub`) wraps that run in **FSI (U+2068) … PDI (U+2069)** — the Unicode
Bidirectional Algorithm's own "this run is LTR" mechanism, so the Persian orders correctly and the
Latin keeps its internal order. This is *not* the forbidden direction-mark hack: never use LRM/RLM
(U+200E/U+200F). A pure-Latin string needs no isolate, and a bare `%1$d` / `%1$s` placeholder is left
alone because it is substituted at runtime. `bidi_fa.py` in the local harness applies and verifies
the isolates.

**Every new string ships in both locales in the same commit.** `stringcheck.py` fails on a key that
exists in one locale only; `default_web_client_id` is the single deliberate
`translatable="false"` exception.

---

## 3. The explanation role is semantic, and each theme defines it

**The rule.** Every help sentence, hint, caption and "why this is unavailable" note uses
`VoxoraColors.explanation`. Secondary content — supporting labels, values, subtitles — stays on
`onSurfaceVariant`. Do not repaint every secondary label to "unify" them: the distinction is the
point of the role.

**Why.** Before this role existed, explanatory text was a mix of `onSurfaceVariant`, alphas on
`onSurface` and literal hexes, so the same kind of sentence looked like three different things.

**How it is implemented.**

- `VoxoraColors.explanation` resolves through `VoxoraSemanticColors` (`staticCompositionLocalOf` in
  `Theme.kt`), so a screen asks for the role and never for a number.
- **Each appearance states its own value, and the role is not defined by being dimmer.** Original Dark
  uses the green `#3DDC84` — the hue its status role used to carry, because the two roles exchanged
  places in the redesign and the role each colour *means* did not change; Voxora Light uses the deeper
  navy-blue `#0369A1`, where it is the lighter of the two by luminance and so still reads as the
  quieter role.
- **The role is ordered by purpose, never by brightness.** The same redesign exchanged the two text
  roles' values in Original Dark, so the *primary* content role (`onSurface`) is deliberately the
  dimmer of the two there. A screen must ask for the role it means; picking between them by which one
  looks stronger is the failure this rule exists to prevent.
- Never make it a brand gold or an accent, and never make it an action or a status colour. The
  decorative waveform stop `VoxoraBrand.waveGreen` (`#3DDC97`) is a *different* value from the status
  and explanation roles and is never swept up in a semantic swap.

---

## 4. How these rules are checked

| Check | Where | What it proves |
|---|---|---|
| `themeguard.py` | local harness | exactly two `ThemeMode`s all handled, no palette inheriting another, no removed-light-candidate leftover, no bundled font reintroduced |
| `checkimports.py` | local harness | no Compose/AndroidX symbol used without its import |
| `stringcheck.py` | local harness | every `R.string.*` exists, and `values`/`values-fa` parity holds |
| `bidi_fa.py` | local harness | every Latin run inside Persian is isolated, format specifiers untouched |
| `themecheck.py` | local harness | no colour literal outside `Theme.kt` and the two palette files |
| `dubguard.py` | local harness | the disabled overlay stays unconstructed, no fixed delay or `Thread.sleep` returns to `dub/`, the pure sync core (including the playback timeline and head unwrap) imports no `android.*`, `MediaSessionManager` stays in its adapter, the synchronizer reads only a monotonic clock, `DubService` consults `PlaybackTimeline.onChunkArrived` and branches on `ChunkAction.PLAY`, it feeds the synchronizer the playhead rather than `writtenNanos()`, and `DubPlayback` reads the device playback position |
| `PaletteContrast` + the palette tests | `:app:testDebugUnitTest` | every text role clears WCAG AA on the surface it sits on |
| `ReaderDubIsolationTest` | `:app:testDebugUnitTest` | no file under `app/.../reader/` imports or names anything from Live Dub (comments stripped, imports checked raw) |
| `Android CI` | GitHub Actions | the only real compile check for Compose, which the dev server cannot run |

The local harness cannot compile Compose. A green local run is necessary but not sufficient: the
`Assemble debug` CI job is what proves the UI actually builds, and only a real device proves how it
looks and reads.

---

## 5. Implementation record — Reader voice, library, resume and Book Intelligence

This section is a **record**, not a rule. The standing rules are §1–§4 above; the Reader's
architecture contract is `AGENTS.md` §5.

**Commits.** `de89612` (implementation) and `cb07e2a` (its documentation record), then two build
fixes: `22a1b69` and `8293a98`. Branch `feat/reader-segmented-spooling`. CI is green on `8293a98`
— see "CI result" below.

### What was requested

One coherent Reader upgrade: a narration-voice choice (Female/Male, native Gemini voices, no DSP
pitch shifting), a persistent multi-book library that survives restart, exact chunk-level resume,
per-book progress and reading state, automatic book identification after import, and a detailed
source-backed "About This Book" section — all inside the Reader, with Live Dub untouched, no local
Gradle, and CI as the only Android compile check.

### What was actually implemented

- **Voice.** `core/.../gemini/ReaderVoice.kt` — `FEMALE` → `Aoede`, `MALE` → `Charon`, both verified
  present in the documented 30-voice Gemini prebuilt set. `GeminiReaderSession.connect(...)` now
  takes the voice as a required argument (the private `READER_VOICE = "Kore"` constant is gone), and
  `ReaderController.play` resolves it once per run from `UserPrefs.readerVoice` and passes it to
  every session. **Gemini publishes no gender or pitch field**, so the mapping is documented in code
  as a curated perceptual choice, not an API fact.
- **Persistent library.** `ReaderBookRepository` (DataStore `voxora_reader_library`, one versioned
  JSON document) + `ReaderDocumentStore` (the imported document copied into
  `filesDir/reader/books/<id>.<ext>`). Records hold id, path, title, source type, chunk count,
  current chunk, state, timestamps, cached metadata, lookup state and signals. The pure rules live in
  `core/.../reader/ReaderLibrary.kt` and the codec in `ReaderBookCodec.kt`.
- **Resume.** Chunk-level, saved at import / chunk-becomes-current / pause / stop / navigation /
  completion, fire-and-forget on the IO scope so it can never stall audio. `stop()` no longer resets
  the queue. Opening a book re-extracts Voxora's copy and jumps to the saved chunk before publishing
  `READY`. The legacy `last_doc_uri` is migrated once, through the ordinary import path.
- **Book identification.** `BookSignalsReader` (ISBN with check-digit validation, conservative
  title/author heuristics) → `GoogleBooksSource` (primary) + `OpenLibrarySource` (fallback) →
  `BookMatch` (ISBN exact > title+author > unique near-exact title; ties refused) → cached on the
  book. `MetadataHttp` classifies offline/timeout/rate-limit/server/parse distinctly and sends no
  credential.
- **Book Intelligence.** `BookIntel` produces source-backed facts only, plus a fiction/non-fiction
  read from subject headings alone. `BookIntelCard` renders identified / not-found / ambiguous /
  unavailable states. Covers use a minimal hand-written OkHttp + `BitmapFactory` loader.
- **UI.** `ReaderLibrarySection` (continue card + book list + import), `ReaderVoiceSection`,
  `BookIntelCard`, `ReaderCoverImage`. `ReaderScreen` remains a stateless `ReaderContent`. All new
  strings exist in `values` and `values-fa`.
- **No AI-generated overview was added.** Doing it safely would need a metadata-only path; the
  source-backed section is the required baseline and the optional part was deliberately skipped.

### Files and components changed

`core`: new `reader/` package (11 files) + `gemini/ReaderVoice.kt`; modified
`gemini/GeminiReaderSession.kt`, `prefs/UserPrefs.kt`.
`app`: new `reader/library/` (2 files) + `reader/{ReaderVoiceSection, ReaderLibrarySection,
BookIntelCard, ReaderCoverImage, ReaderRecency}.kt`; modified `reader/{ReaderController,
ReaderViewModel, ReaderScreen, TextExtractor}.kt`, `res/values*/strings.xml`.
Docs: `AGENTS.md` §5/§13/§14.

### Build defects found after the first push, and how they were fixed

The first push (`cb07e2a`) failed **both** CI jobs. Three defects, all in the committed tree only —
the local harness had passed against a working tree that already carried the first fix:

| # | Defect | Fix | Commit |
|---|---|---|---|
| 1 | `TextExtractor.kt` had two imports collapsed onto one line (`…PDFBoxResourceLoaderimport com.tom_roush…PDDocument`), an invalid Kotlin statement | split back into two imports | `22a1b69` |
| 2 | `ReaderVoiceSection.kt:68` used `VoxoraColors.explanation` with no import → `Unresolved reference 'VoxoraColors'` | added `import com.voxora.app.ui.theme.VoxoraColors` | `8293a98` |
| 3 | `TextExtractor.kt:41` typed `ExtractedDocument.signals` as `BookSignals` with no import → `Unresolved reference 'BookSignals'` | added `import com.voxora.core.reader.BookSignals` | `8293a98` |

Defects 2 and 3 are why **both** jobs failed: `Assemble debug APK` compiles `app` main sources, and
`Unit tests` compiles them too, so one unresolved reference fails both. The exact errors were read
from the job logs (`:app:compileDebugKotlin FAILED`), not guessed.

**Why the local harness missed them.** Two structural gaps, now understood and recorded rather than
assumed away:

- `validate-reader-android.sh` type-checks the Android Reader layers against a **`TextExtractor`
  stub** (`stub2/TextExtractorStub.kt`), because the harness has no PDFBox jar. The real
  `TextExtractor.kt` was therefore never compiled locally — defect 3 lived in exactly that blind
  spot.
- The script does not cover the Compose files at all, so defect 2 was outside it too.
- `checkimports.py` watched only Compose/AndroidX symbols. A **project** symbol such as
  `VoxoraColors` was invisible to it, even though a project symbol is never resolved by a language
  default.

`checkimports.py` was extended in the local harness with a `WATCHED_PROJECT` list (`VoxoraColors`,
`VoxoraTheme`, `VoxoraLog`, and the `core.reader`/`ReaderVoice` types), checked on **any** bare
mention — member access included — after stripping comments and string literals so a KDoc `[VoxoraLog]`
is not misread as a use. It was verified both ways: it now reports clean on the fixed tree and
reports exactly `VoxoraColors` + `BookSignals` when run against the `de89612` files, i.e. it would
have caught both defects before the first push.

### Tests actually run

| What | How | Result |
|---|---|---|
| Pure-JVM contract suite (55 classes) | `/tmp/rr/validate-cloud.sh` (kotlinc 2.0.21 + JUnit, `-ea`) | **642 tests OK** |
| Android-side Reader layers | `/tmp/rr/validate-reader-android.sh` (android.jar + AndroidX/Hilt/DataStore stubs) | **OK** |
| Guards | `themecheck`, `themeguard`, `checkimports`, `stringcheck`, `bidi_fa`, `dubguard` | all **OK** |
| Live Dub untouched | `git status` filtered for `dub/`, `GeminiLive*`, `SystemCapture`, `DubPlayback`, `FloatingBubble*`, `DelayedScreenOverlay` | **no changes** |
| Extended import guard | `checkimports.py` with `WATCHED_PROJECT`, run against the `de89612` files | **caught both missing imports** |
| Android CI, push run `35439870596` on `8293a98` | GitHub Actions, read via the REST API | `Unit tests` **success**, `Assemble debug APK` **success** |
| Android CI, PR run `35439873171` on `8293a98` | GitHub Actions, read via the REST API | `Unit tests` **success**, `Assemble debug APK` **success** |

New test classes: `ReaderVoiceTest`, `ReaderBookTest`, `ReaderLibraryTest`, `ReaderBookCodecTest`,
`BookSignalsReaderTest`, `BookMatchTest`, `BookIntelTest`, `GoogleBooksSourceTest`,
`OpenLibrarySourceTest`, `BookMetadataLookupTest` (core); `ReaderRecencyTest`,
`ReaderDubIsolationTest` (app); plus two voice setup-payload cases added to
`GeminiReaderSessionTest`.

The harness caught one real defect before push: `library.remove(...)` was being called inside
`synchronized(lock)`, which is a suspension point inside a critical section.

### CI result

**Green.** Commit `8293a98` (the two import fixes on top of `de89612` / `cb07e2a`) passes both jobs
in both runs:

| Run | Event | `Unit tests` | `Assemble debug APK` |
|---|---|---|---|
| `35439870596` | push | success | success |
| `35439873171` | pull_request | success | success |

Read from the GitHub REST API (`/actions/runs/{id}/jobs`), not inferred. `Assemble debug APK` is the
only proof that the new Compose UI compiles; it now does. The three earlier failures
(`35417714110`/`35417711245` on `cb07e2a`, `35439144679`/`35439146941` on `22a1b69`) were the
defects recorded above, and their job logs were read to obtain the exact compiler errors.

### Unresolved limitations

- **Compose is still not compiled locally.** `ReaderScreen.kt`, `ReaderVoiceSection.kt`,
  `BookIntelCard.kt`, `ReaderLibrarySection.kt` and `ReaderCoverImage.kt` are only compiled by the
  CI `Assemble debug` job — proven green for `8293a98`, but every future Compose edit still costs a
  CI round trip. The extended `checkimports.py` closes the missing-import case specifically, not
  Compose compilation in general.
- **The real `TextExtractor.kt` is still not type-checked locally**, because
  `validate-reader-android.sh` substitutes a stub for it (no PDFBox jar in the harness). The extended
  guard covers its missing-import case only. Any other compile error in that file will again be
  found by CI first.
- **No real-device testing was performed.** The voice's perceived character, the cover loader, the
  library layout under RTL, and the resume behaviour after a real process death are all
  device-verification items.
- **Google Books and Open Library were never contacted for real.** Their contracts are pinned
  against `MockWebServer` with recorded response shapes; live coverage, rate limits and the exact
  match quality on real documents are unverified.
- **Matching quality on real title pages is unmeasured.** The heuristics are deliberately
  conservative (a rejection is preferred to a wrong attachment), so real-world identification may
  often end in `NOT_FOUND`.
- **The library is one DataStore document**, bounded in practice by the capped description length
  (4 000 chars per book) and by realistic library sizes. A very large library was not exercised.
- **The legacy migration runs once.** If it fails for a transient reason, the old document is not
  retried.

### Exact next action

The Reader cycle is complete and CI-verified. The next action is **owner device verification**:
install the `8293a98` debug APK and confirm, on a real device, the four things no automated check can
reach — that the Female/Male narrator choice is audibly distinct and survives a restart, that a
cover loads, that the library reads correctly under RTL, and that a book reopens at its saved chunk
after a real process death.

---

## 6. Implementation record — persistent book reopen and localized Book Intelligence

A **record**, not a rule. The standing rules are §1–§4; the Reader's architecture contract is
`AGENTS.md` §5. This section covers the work that followed §5: a real-device defect in reopening a
saved book, and the language rule for the Book Intelligence section.

**Commit.** `115d6df` — `fix(reader): restore persisted books and localize book intelligence`. The
implementation, its tests and this record are all in that one commit. Branch
`feat/reader-segmented-spooling`.

### What was reported

On a real device, importing a PDF or TXT worked and narration started, the book appeared in the
library, but **tapping the saved card to reopen it** logged
`WARN [Reader] Document extraction failed: ExtractionException` and showed the
"Could not read this document…" error. The report was explicit that the source document must **not**
be assumed invalid — the suspicion was the persisted reference and its restoration path.

### Root cause — the display title was being used as a file name

The defect was in **type resolution on reopen**, not in the file, the copy, or the codec.

A reopen reads Voxora's own copy through `Uri.fromFile(...)`. That is a `file://` URI, so
`ContentResolver.getType(...)` returns **null**: there is no MIME type to go on. The only remaining
evidence was the name, and the name the Reader was handed was the record's `title`.

That is the trap. `ReaderBook.withMetadata` replaces the display title with the **catalogue title**
once automatic identification succeeds:

```kotlin
fun withMetadata(metadata: BookMetadata): ReaderBook = copy(
    metadata = metadata,
    lookup = MetadataLookupState.FOUND,
    title = metadata.title.trim().ifEmpty { title },
)
```

So a book imported as `selfish-gene.pdf` and successfully identified became `"The Selfish Gene"` —
a title with no extension. On reopen the old code did:

```kotlin
val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
val isPdf = when {
    mime == "application/pdf" -> true
    mime == "text/plain" -> false
    extension == "pdf" -> true
    extension == "txt" -> false
    else -> throw ExtractionException("Unsupported file type. …")
}
```

`mime` was null and `extension` was `""`, so a **perfectly readable PDF** fell to `else` and threw.
The failure therefore appeared *only after* identification had succeeded — which is why the first
import always worked and every later reopen did not. The persisted `sourceType` that already held the
answer was never consulted.

The codec was not at fault: it round-trips `localPath` and `sourceType` correctly, and no other code
path treats `title` as a file name.

### The fix — one resolution rule, with a known type always winning

Type resolution was extracted into a pure, unit-testable core object, `ReaderDocumentType.resolve`,
whose order of evidence is the rule:

1. **a known type** — the record's persisted `sourceType`. It is a fact the import that actually read
   the document established, and nothing observed later may contradict it;
2. the provider MIME type, when there is one;
3. the extension of a **file name** — never a display title.

`TextExtractor.identify` and `extract` take a new `knownType: ReaderSourceType?` parameter, and
`ReaderController.startLoad` passes `identity.type` from the record. Nothing is re-derived from the
title. The import path is unchanged in behaviour: a fresh import has no known type, so it resolves
from MIME and extension exactly as before.

### Making an unrestorable book a precise, recoverable state

The previous behaviour deleted the record when its file was gone. That destroyed the reader's saved
position and cached Book Intelligence over a file that may come back, and made the failure
indistinguishable from a book that was never imported. It was replaced with a persisted state:

- **`ReaderBookState.UNAVAILABLE`** (id `"unavailable"`), with `ReaderBook.isUnavailable` and an
  idempotent `unavailable()` mutator that changes **only** the state — position, title, metadata and
  overviews are all kept. `hasResumePoint` excludes it, so the library stops offering a Continue that
  cannot work.
- **`ReaderLibrary.markedUnavailable(books, id)`** and **`ReaderBookRepository.markUnavailable(id)`**.
- `ReaderController.openStoredBook` marks rather than deletes, and `reportUnavailable(restoring)`
  publishes the precise outcome: an automatic restore stays quiet and opens the Reader idle, while an
  explicit open says exactly what is wrong (`reader_book_unavailable`). The exception is never
  suppressed — a book that cannot be read is never shown as available.
- **Repeated extraction is impossible.** An `UNAVAILABLE` book returns before any extraction is
  attempted, and `openBook` is now a **no-op when that book is already loaded** — previously a tap
  cancelled running narration and re-extracted a document already in the queue, which is what turned
  one failure into a repeated error line per tap.
- The library row for an unavailable book shows the "Not available" state and is **not clickable**.

### Book Intelligence — explanatory content in the reader's output language

**The distinction that governs this:** *source metadata* is the catalogue's, and *explanatory
content* is the reader's. They are shown as two different things and never conflated.

- The catalogue's own description, subjects, publisher and date are still shown **unedited and
  untranslated**, with the source language named when it differs from the reading language
  (`reader_book_info_source_language`). Voxora never presents its own translation as the publisher's
  text.
- No public catalogue carries a Persian description for most titles, so the reader's language cannot
  come from the provider. `langRestrict` is deliberately **not** used on the query: it filters
  *books* by language and would exclude the correct edition rather than translate anything.
- Instead the record is turned into a short overview by a **separate, one-shot text call** —
  `BookIntelOverviewGenerator` over `GeminiHttpTextTransport` (core) — that sends **the metadata
  only: never the document, never an excerpt of it, and never anything about the reader's position**.
  It is not the narration session and does not touch the audio path. The key travels in the
  `x-goog-api-key` header, never as a `?key=` query parameter.
- The prompt (`BookIntelOverviewPrompt.build`) forbids adding any fact not present in the supplied
  record, and the card labels the result as AI-generated with a note that it is not a catalogue fact
  (`reader_book_info_generated`, `reader_book_info_generated_note`).
- Results are **cached per language on the record** (`ReaderBook.overviews`, `withOverview`,
  `overviewFor(language)`), so switching languages back and forth is instant and works offline. The
  cache is pruned to the newest few (`MAX_CACHED = 3`). The persisted field is **omitted entirely when
  empty**, so a record that never generated an overview encodes byte-identically to before.
- `BookIntelOverviewPrompt.VERSION` is part of what makes a cached text reusable: a text written by an
  older prompt is regenerated rather than displayed as current, and a record written before the field
  existed decodes to `0` and is regenerated once. (This was found while reviewing the change: the
  constant was documented as a cache key but was not consulted anywhere, so improving the prompt would
  have been silently masked by a cached answer. It is now implemented and tested.)
- **Failure is expected and harmless.** A missing key, an offline device, a rate limit, a rejected
  model and a parse failure all end in "no overview": the book is unaffected and the source-backed
  facts stay on screen. Generation is attempted **once per book per language** (`overviewStarted`,
  keyed by `"id|language"`), so a failure costs one call rather than one per recomposition or per
  language-list emission.

### Files and components changed

- **core, new:** `reader/ReaderDocumentType.kt` (the resolution rule), `reader/BookIntelOverview.kt`
  (`BookIntelOverview` + `BookIntelOverviewPrompt`), `reader/GeminiTextClient.kt`
  (`GeminiTextResult`, `GeminiTextTransport`, `BookIntelOverviewGenerator`, `GeminiHttpTextTransport`).
- **core, modified:** `ReaderBook.kt` (`UNAVAILABLE`, `isUnavailable`, `unavailable()`, `overviews`,
  `overviewFor`, `withOverview`), `ReaderLibrary.kt` (`markedUnavailable`, `withOverview`),
  `ReaderBookCodec.kt` (encode/decode overviews incl. `promptVersion`, tolerant of a missing field),
  `BookMetadata.kt` (documentation of the source-vs-generated distinction).
- **app, modified:** `TextExtractor.kt` (`knownType` on `identify`/`extract`, delegated to
  `ReaderDocumentType`), `ReaderController.kt` (mark-not-delete, `reportUnavailable`, no-op reopen,
  pass `knownType`), `library/ReaderBookRepository.kt` (`markUnavailable`, `ensureOverview`,
  `overviewGenerator`, `overviewMutex`), `ReaderViewModel.kt` (`observeLibraryForOverviews`,
  `runOverview`, `overviewStarted`), `BookIntelCard.kt` (generated-overview block, source-language
  note), `ReaderScreen.kt` / `ReaderLibrarySection.kt` (`outputLang` threading, `UNAVAILABLE`
  handling, non-clickable unavailable rows), `res/values/strings.xml` + `values-fa/strings.xml`.
- **docs:** `AGENTS.md` §5 (type-resolution rule, `UNAVAILABLE`, no-op reopen, overview language and
  caching), this section, `CloudMD.md`.
- **new tests:** `ReaderDocumentTypeTest`, `BookIntelOverviewTest`, `ReaderBookOverviewTest` (core).
- **Live Dub:** untouched. `git status` shows no change under `dub/`, `GeminiLive*`,
  `SystemAudioCapture`, `DubPlayback`, `FloatingBubble*` or `DelayedScreenOverlay`.

### Tests actually run

| What | How | Result |
|---|---|---|
| Pure-JVM contract suite | `/tmp/rr/validate-cloud.sh` (kotlinc + JUnit, `-ea`) | **681 tests OK**, 58 classes |
| Android-side Reader layers | `/tmp/rr/validate-reader-android.sh` | **OK** |
| Guards | `themecheck`, `themeguard`, `checkimports`, `stringcheck`, `bidi_fa`, `dubguard` | all **OK** |
| Live Dub untouched | `git status` filtered for `dub/`, `GeminiLive*`, `SystemAudioCapture`, `DubPlayback`, `FloatingBubble*`, `DelayedScreenOverlay` | **no changes** |

The suite grew from 642 to 681 tests. `ReaderDocumentTypeTest` pins the resolution order and the
regression itself — that a **catalogue title with no extension does not decide the type**, and that a
known type wins over a contradicting name. `ReaderBookOverviewTest` pins the `UNAVAILABLE` state
(nothing else is lost, no resume offered, survives the codec) and the overview cache (round trip,
per-language replacement, bounded size, an entry with no `promptVersion` treated as stale).
`BookIntelOverviewTest` pins what is sent and what is accepted back.

### CI result

**Green.** Commit `115d6df` passes both jobs in both runs, read from the GitHub REST API
(`/actions/runs/{id}/jobs`), not inferred:

| Run | Event | `Unit tests` | `Assemble debug APK` |
|---|---|---|---|
| `35443414003` | push | success | success |
| `35443416114` | pull_request | success | success |

`Assemble debug APK` is the only proof that the Compose changes here (`BookIntelCard`,
`ReaderScreen`, `ReaderLibrarySection`) compile; the local harness cannot compile Compose.

### Unresolved limitations

- **No real-device testing was performed.** The reopen fix is proven by unit tests and by tracing the
  lifecycle, not by reopening a book on a device. This is the one item that closes the reported bug.
- **The Gemini text model name is unverified against the live API.** `DEFAULT_MODEL` is
  `models/gemini-2.0-flash`, chosen because the narration model is a Live model and unsuitable for
  `generateContent`. A wrong value degrades to "no overview" and nothing else, but it has not been
  confirmed on a real key.
- **No live call to `generateContent` was made.** The transport is pinned against `MockWebServer`;
  real latency, quota and output quality in Persian are unmeasured.
- **Compose is still not compiled locally** (§5), so the UI changes here carry the same CI-round-trip
  cost and the same blind spot.
- **The prompt version invalidates every previously cached overview once.** Existing installs have no
  `promptVersion` on disk, so their first overview after this change is regenerated. That is the
  intended semantics, but it is a one-time cost per book.

### Exact next action

**Owner device verification of the reopen fix.** Install the debug APK built from this commit and
confirm the reported flow end to end: import a PDF, let narration start, let identification succeed
so the title becomes the catalogue title, then tap the saved card and confirm the book reopens at its
saved chunk instead of showing "Could not read this document…". Then confirm the Book Intelligence
section shows the catalogue's own description with its source language, and a labelled AI-generated
overview in the selected output language, and that switching the output language does not disturb
narration.

---

## 7. Implementation record — two appearances, the Dark semantic swaps, a redesigned library, and a persisted chunk cache

### What was requested

Four connected areas, in the owner's order:

- **A. Consolidate the UI variants.** Keep only the main Dark UI and the Voxora Light UI; remove the
  experimental light variant completely — implementation, navigation/selection path, theme
  references, resources and preview/demo entry points — while leaving the surviving light UI
  untouched and the two survivors architecturally isolated.
- **B. Redesign the Dark UI's colours only**, as two **semantic swaps** rather than a
  search-and-replace: `#3DDC84` ↔ `#7DD3FC` (green/status ↔ ice-blue/explanation) and `#F5F0E6` ↔
  `#C4BBA8` (primary headings ↔ supporting text). Unrelated greens such as `#3DDC97` were to be left
  alone unless proven to be the same semantic role, and nothing was allowed to leak into the light UI.
- **C. "Your Books" as one unified vertically expandable/collapsible list** — swipe down to expand, up
  to collapse, never horizontal. Collapsed must stay compact and useful. Selecting a book makes it
  active and exposes its real saved chunk and last-read; Continue resumes that book, reusing the
  existing persistence model.
- **D–F. Exact chunk-level resume, a two-chunk active cache with rolling prefetch, and a Stop →
  Continue that is not a fresh session.** Resume must land on the exact persisted logical chunk — not
  an approximate text/character/PCM/sample offset and not transient memory — and must play from local
  cache immediately when it is available.

### What the repository actually contained (checked, not assumed)

- The three variants were `ThemeMode.ORIGINAL_DARK` ("Original Dark"), `LIGHT_TEST_1` ("Light Test 1",
  a Nova-inspired comparison candidate) and `LIGHT_TEST_2` ("Voxora Light", the final light
  appearance — the string the request called "Voxlerai"). The names were read from the code, not
  taken from the prompt.
- **Chunk-level resume already existed** (`queue.jumpTo(book.currentChunk)` on open, position saved at
  safe transitions) and a **rolling current+next prefetch already existed** (`runPipeline` prepares
  the current chunk and prefetches the next).
- The genuine gap for D–F was that `ReaderSpool` deleted its temp file at the end of every run, so
  Stop → Continue re-synthesised the chunk the reader was already on.

### What was actually implemented

**A. One light appearance removed.**

- `ThemeMode` now declares exactly `ORIGINAL_DARK` and `LIGHT_TEST_2`; `LEGACY_IDS` redirects every
  old id (`"system"`/`"dark"` → dark, `"light"`/`"light_test_1"` → **Voxora Light**) so an existing
  choice can never leave the app themeless.
- `LightTest1Palette.kt` deleted, along with its scheme/semantics blocks in `Theme.kt`, its
  `ThemeMode` entry, its `SettingsScreen` branch and its `settings_theme_light_test_1` string in both
  locales. `LightTestPalettesTest` was replaced by a Voxora-Light-only `LightPaletteTest`.
- The two survivors remain independent by construction: separate palette files, no shared constant or
  mutable state, and no base-plus-overrides relationship. `themeguard.py` now enforces exactly two
  modes and asserts that none of the removed candidate's values survives in code **or docs**.

**B. Two semantic swaps, in the Dark palette only.**

- `OriginalDarkPalette`: `Success` `#7DD3FC` and `Explanation` `#3DDC84`; `OnSurface` `#C4BBA8` and
  `OnSurfaceVariant` `#F5F0E6`. Nothing else moved; the light palette was not touched.
- The documented consequence is stated in the palette's KDoc and pinned by a test: after the text
  swap the **primary content role is the dimmer of the two**, because the roles are ordered by purpose,
  not by brightness.
- `VoxoraBrand.waveGreen #3DDC97` was deliberately *not* swept up: it is a decorative waveform stop,
  not the status role. `OriginalDarkPaletteTest` pins the swap as a swap and asserts neither role
  equals `#3DDC97`.
- Contrast was re-derived rather than assumed: every content/help role still clears AA on all three
  dark surfaces.

**C. "Your Books" is one expandable list.**

- `ReaderLibrarySection` renders a single card holding every saved book. It is collapsed by default
  and shows the selected book's compact summary (cover, title, real chunk position, state) plus its
  Continue; it expands on a **downward vertical drag** or a tap and collapses on an upward drag.
  The drag is confined to the header row so the page still scrolls normally elsewhere.
- Selecting a book does **not** open it: it reveals the progress bar, "Chunk 12 of 210", the state and
  "Last read …", together with Continue and Remove. Nothing loads and nothing makes sound until
  Continue is pressed.
- The action's wording follows the book's real state ("Start" / "Continue" / "Listen again"), and a
  book whose copy is gone offers no action that could only fail.
- No new persistence was introduced: the section reads the same `ReaderBook` records and calls the
  same `onOpen`/`onRemove`/`onImport` callbacks. Three strings were added in both locales
  (`reader_library_count`, `reader_library_active`, plus `expand`/`collapse`/`start`/`listen_again`).

**D–F. The persisted chunk cache.**

- New `ReaderChunkCache` (app, `reader/`, pure JVM): one directory per chunk under
  `cacheDir/reader-chunks/`, holding the PCM and a record of the key plus every unit's boundary and
  transcript.
- **Reuse requires the whole key**: book, chunk index, chunk count, unit count, a hash of the units'
  own text, output language, narration mode, voice and model. The unit-text hash is what makes a
  re-extraction with different words a different chunk at the same index; the language/mode/voice/
  model parts are what stop audio produced for one selection being replayed under another.
- **All-or-nothing validation.** Every unit must be present once, in order, each ending further into
  the file than the last, and the last exactly at the end of the audio. Anything else — truncated
  audio, inconsistent boundaries, a blank transcript, an unreadable record — is deleted and treated
  as a miss, never partially restored.
- **Only a whole chunk is stored**, so a producer cancelled mid-unit cannot make a later Continue play
  a truncated chunk as if it were complete.
- **A restored chunk launches no producer at all**, and its transcripts are seeded into the display
  cache *before* the slot is published. That ordering is load-bearing: a restored spool is complete
  from the moment it exists, and a first-unit gate that found no rendering would report a perfectly
  good chunk as *failed*.
- **A restored chunk is replayed from unit zero or not at all**, so a mid-chunk resume re-synthesises
  rather than playing unit zero's audio under a later unit's text.
- **Stop → Continue is the case it exists for**, so the cache is written in the run's `finally` block
  as well as at promotion — the run's own first chunk is only reachable there, and that is exactly
  the chunk a reader who stops mid-listen is on.
- **Bounded and deterministic**: two entries per book and a total byte budget, LRU eviction, and the
  entry just written is never evicted. A store **renames** the spool's existing file instead of
  copying it, so a ~10 MB chunk costs a directory entry rather than a disk write that would stall the
  next chunk.
- **Nothing here can fail a run**: a cache that cannot be read is a chunk that has to be synthesised,
  never an error. Removing a book drops its entries with it.

**G. Book Intelligence themes follow the reading language.**

- The one-shot metadata-only prompt now asks for the record's subjects as short headings **in the
  target language**, answered as `{"overview": …, "themes": […]}` with `responseMimeType: JSON`.
- The themes are stored **inside** the per-language `BookIntelOverview`, which is what makes a
  language switch structurally unable to show another language's themes. A plain-prose answer is
  still accepted; a JSON answer with no usable `overview` is rejected rather than shown as prose.
- `BookIntelCard` shows the generated headings in place of the catalogue's own when they exist, and
  falls back to the catalogue's headings with the existing source-language note when they do not.
- **Two latent defects were fixed on the way**: `BookIntelOverviewPrompt.VERSION` was documented as
  part of the cache key but consulted nowhere (a prompt change would have been silently masked), and
  `ReaderViewModel.observeLibraryForOverviews` tested "an entry exists" instead of
  `isUsableFor(...)`, which would have skipped regeneration before the repository's own check ran.
  A language-labelled code fence (` ```json `) was also not recognised, which would have shown the
  raw fenced JSON on the card as prose.

### Files and components changed

- **Core:** `prefs/ThemeMode.kt`; `reader/BookIntelOverview.kt` (`themes`, `GeneratedOverview`,
  `VERSION = 2`, JSON build/parse, theme sanitising, fence stripping); `reader/GeminiTextClient.kt`;
  `reader/ReaderBookCodec.kt`.
- **App:** `ui/theme/OriginalDarkPalette.kt`, `ui/theme/LightTest2Palette.kt`, `ui/theme/Theme.kt`
  (and `LightTest1Palette.kt` deleted); `ui/SettingsScreen.kt`; `reader/ReaderController.kt`,
  `reader/ReaderSpool.kt`, `reader/ReaderChunkCache.kt` (new), `reader/ReaderLibrarySection.kt`,
  `reader/ReaderViewModel.kt`, `reader/BookIntelCard.kt`,
  `reader/library/ReaderBookRepository.kt`; both `strings.xml` files.
- **Tests:** `ThemeModeTest`, `OriginalDarkPaletteTest`, `LightPaletteTest` (new, replacing
  `LightTestPalettesTest`), `BookIntelOverviewTest`, `ReaderBookOverviewTest`, `ReaderChunkCacheTest`
  (new), `ReaderSpoolTest`.

### Build defects found after the first push, and how they were fixed

The first push (`99a54ac`) passed `Assemble debug APK` but failed `Unit tests`: **19 of 388 tests**,
all in the new `ReaderChunkCacheTest`, all `java.lang.RuntimeException` at the shared `store` helper.

| # | Defect | Fix | Commit |
|---|---|---|---|
| 1 | The app module's unit tests run against Android's **stubbed** `org.json`, whose methods throw `RuntimeException("Stub!")`. `ReaderChunkCache` is app-module code that writes `meta.json` with `JSONObject`/`JSONArray`, so every test that stored an entry threw. `:core` already declared `testImplementation("org.json:json:20240303")`; `app` did not, because no app-module unit test had ever reached `org.json` before this cycle. | added the same `testImplementation("org.json:json:20240303")` to `app/build.gradle.kts` | this commit |

`Assemble debug APK` passing is what isolated this: the app main sources compiled and the JSON was
fine at runtime on Android — it is only the **unit-test** runtime that substitutes a stub. The exact
failure (`ReaderChunkCacheTest > … FAILED … RuntimeException at ReaderChunkCacheTest.kt:73`, `388
tests completed, 19 failed`) was read from the job log, not guessed.

**Why the local harness missed it.** `validate-cloud.sh` compiles and runs the same sources with a
real `json-20240303.jar` on its classpath, so `JSONObject` works locally and the stub only appears in
the Android unit-test runtime. The harness's `validate-reader-android.sh` type-checks the Reader
layers but does not run the app-module tests. This is a new member of the same family as §5's defects:
a fault that exists only in the committed tree under Gradle.

`jsonguard.py` was added to the local harness to close it: if any `app/src/main` Kotlin file imports
`org.json.*`, `app/build.gradle.kts` must declare a `testImplementation` on a real `org.json`. It was
verified both ways — it reports clean on the fixed tree and reports exactly
`app/src/main/java/com/voxora/app/reader/ReaderChunkCache.kt` when the dependency is removed, i.e. it
would have caught this before the first push.

### Tests actually run

`validate-cloud.sh` (pure JVM, kotlinc 2.0.21 + JUnit 4.13.2, `-ea`): **715 tests pass across 59
classes** (up from 688 after Part G, and from 681 before this cycle). `validate-reader-android.sh`
type-checks the Android-side Reader layers against `android.jar` with stubs: **OK**. All seven guards
pass: `themecheck.py`, `themeguard.py` (now two modes), `checkimports.py`, `stringcheck.py`
(`values` 360 / `values-fa` 359 with only the `translatable="false"` key differing), `bidi_fa.py`,
`dubguard.py`, `jsonguard.py` (new this cycle — see the defect above). Live Dub is untouched.

### CI result

The first push (`99a54ac`) **failed** `Unit tests` (19/388 — see the defect above) while
`Assemble debug APK` passed. The `org.json` test dependency was added and pushed; the run ids for the
fix commit are recorded in `CloudMD.md` rather than duplicated here. **The cycle is not CI-verified
until that run is green.**

### Unresolved limitations

- **No real-device testing was performed.** Every claim about how the new library feels, how the
  drag-to-expand behaves under RTL, whether the Dark swap reads as intended, and whether Continue
  actually plays from the cache on a device is a **DEVICE VERIFICATION PENDING** item.
- **The cache's hit rate in real use is unmeasured.** The unit tests prove the reuse and eviction
  rules; they cannot prove that a real Stop usually lands on a chunk whose producer had finished.
- **`cacheDir` is the OS's to clear.** The cache is derived audio and is treated as such; a cleared
  cache costs a re-synthesis, never a lost position.
- **The themes are still unverified against a live model.** A model that ignores `responseMimeType`
  still degrades to prose-without-themes, which the card renders as the catalogue's own headings.

### Exact next action

**Owner device verification of the whole cycle.** On a device: (1) confirm the Settings theme
selector offers exactly two appearances and that an install whose saved preference was the removed
one lands on Voxora Light rather than failing; (2) open "Your Books", drag the header down and up,
select a book and confirm its real chunk and last-read appear and Continue resumes it; (3) play a
chunk, let the next one prefetch, tap Stop, then Continue, and confirm the chunk resumes at the same
place; (4) switch the output language and confirm the Book Intelligence themes change language
without narration being disturbed.
