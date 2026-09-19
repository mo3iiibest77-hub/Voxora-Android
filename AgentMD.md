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
- **Each theme states its own value, and the role is not defined by being dimmer.** Original Dark
  uses the owner's dedicated icy/electric blue `#7DD3FC`, which is *brighter* than its warm
  secondary text and is kept distinct by hue and dedicated use; Light Test 1 uses `#64748B` and
  Voxora Light the deeper navy-blue `#0369A1`, where it is the lighter of the two by luminance and
  so still reads as the quieter role.
- Never make it a brand gold or an accent, and never make it an action or a status colour.

---

## 4. How these rules are checked

| Check | Where | What it proves |
|---|---|---|
| `themeguard.py` | local harness | three `ThemeMode`s all handled, no palette inheriting another, no deleted-Light-Test-2 leftover, no bundled font reintroduced |
| `checkimports.py` | local harness | no Compose/AndroidX symbol used without its import |
| `stringcheck.py` | local harness | every `R.string.*` exists, and `values`/`values-fa` parity holds |
| `bidi_fa.py` | local harness | every Latin run inside Persian is isolated, format specifiers untouched |
| `themecheck.py` | local harness | no colour literal outside `Theme.kt` and the three palette files |
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
