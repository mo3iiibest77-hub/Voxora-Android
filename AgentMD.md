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

**Why.** Voxora ships to devices whose system fonts differ, and Persian has no system font the
product can rely on. Typography therefore has to come from one place, so a Persian screen and an
English screen of the same role render at the same scale.

**How it is implemented.**

- `app/src/main/java/com/voxora/app/ui/theme/Type.kt` defines `VoxoraTypography`: all **15**
  Material 3 roles, each copied from the Material default so the scale is unchanged, with
  `fontFamily = VazirmatnUiFamily`.
- `VazirmatnUiFamily` loads four bundled weights from `app/src/main/res/font/`
  (`vazirmatn_ui_nl_regular/medium/semibold/bold.ttf`). The font is **Vazirmatn UI, Non-Latin**
  (v33.003), licensed SIL OFL — the licence text is committed at `third_party/vazirmatn/OFL.txt`.
- `Theme.kt` passes `typography = VoxoraTypography` to `MaterialTheme`, so it applies app-wide.
- The *Non-Latin* cut is the correct cut on purpose: it carries the Persian/Arabic glyphs and **no
  Latin ones**, so Persian shapes in Vazirmatn while Latin falls through to the platform's Latin
  font. Do not swap in a full-cut Vazirmatn "to be safe" — that would force product names, URLs and
  model names into a Persian face.

**Applying it.**

- Adding a new screen: use `MaterialTheme.typography.*`. Nothing else to wire.
- Adding a new weight: add the `.ttf` to `res/font/`, add it to `VazirmatnUiFamily`, keep the SIL
  OFL licence file in `third_party/vazirmatn/`.
- **The one sanctioned exception** is the Logs list, which sets `FontFamily.Monospace` because a
  timestamp and a bracketed tag are technical identifiers, not prose. Do not add a second exception
  without recording the reason here.
- `checkimports.py` and the `Type.kt` checks in the local `themeguard.py` are the cheap guards; the
  CI `Assemble debug` job is the real compile check for Compose, which the dev server cannot run.

---

## 2. Persian is written, not translated — and RTL is fixed in the layout

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
  Light Test 2 the cool slate `#5F6775`, where it is genuinely lighter than secondary content.
- Never make it a brand gold or an accent, and never make it an action or a status colour.

---

## 4. How these rules are checked

| Check | Where | What it proves |
|---|---|---|
| `themeguard.py` | local harness | three `ThemeMode`s all handled, no palette inheriting another, no deleted-Light-Test-2 leftover, `Type.kt` + every `R.font.*` + `typography =` wired |
| `checkimports.py` | local harness | no Compose/AndroidX symbol used without its import |
| `stringcheck.py` | local harness | every `R.string.*` exists, and `values`/`values-fa` parity holds |
| `bidi_fa.py` | local harness | every Latin run inside Persian is isolated, format specifiers untouched |
| `themecheck.py` | local harness | no colour literal outside `Theme.kt` and the three palette files |
| `PaletteContrast` + the palette tests | `:app:testDebugUnitTest` | every text role clears WCAG AA on the surface it sits on |
| `Android CI` | GitHub Actions | the only real compile check for Compose, which the dev server cannot run |

The local harness cannot compile Compose. A green local run is necessary but not sufficient: the
`Assemble debug` CI job is what proves the UI actually builds, and only a real device proves how it
looks and reads.
