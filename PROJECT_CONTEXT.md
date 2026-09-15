# PROJECT_CONTEXT.md — Voxora (Android)

**Standing rule:** After any material change, update this file in the same session (version, works, issues, next task).

---

## 1. Product

| Field | Value |
|-------|--------|
| **Brand** | **Voxora** |
| **Tagline** | Live AI dubbing for every language |
| **Type** | Native Android app (not a Chrome extension) |
| **Repo** | https://github.com/mo3iiibest77-hub/Voxora-Android |
| **Sister project** | https://github.com/mo3iiibest77-hub/ParsLiveDub (MV3 extension prototype) |
| **Version** | 0.1.0-dev |

### Positioning
Global product — **not** Persian-only. Gemini Live Translate supports 70+ dubbing target languages. The **app UI** is localized for major world languages (see §6).

### Core value
Capture system audio from any app (YouTube, podcasts, etc.) → stream to Gemini Live Translate → play natural dubbed speech in the user’s target language, with low client overhead and clear onboarding.

---

## 2. Why native (vs extension)

- Extension on Lemur cannot reliably lipsync video frames (black / blink).
- Android **MediaProjection + AudioPlaybackCapture** captures internal audio cleanly.
- Foreground service + AudioTrack give better control of latency buffers and ducking.
- Path to monetization, Google Sign-In, and Play distribution.

Logic is **ported from** ParsLiveDub (protocol, chunking, ducking ideas) — **rewritten in Kotlin**, not copy-paste JS.

---

## 3. Tech stack (locked)

| Layer | Choice |
|-------|--------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Min SDK | 29 (Android 10) — required for AudioPlaybackCapture |
| Target / Compile SDK | 35+ |
| Architecture | Multi-module: `app`, `core`, `feature-dub`, `feature-auth`, `feature-settings` |
| DI | Hilt |
| Async | Coroutines + Flow |
| Preferences | DataStore |
| Auth | Credential Manager + Google Sign-In |
| Audio in | MediaProjection + AudioRecord (AudioPlaybackCaptureConfiguration) |
| Audio out | AudioTrack (PCM 24 kHz from model) |
| Gemini | WebSocket BidiGenerateContent · model `gemini-3.5-live-translate-preview` |
| i18n | Android resources `values` / `values-xx` + per-app language picker |

No Flutter / React Native for v1.

---

## 4. Architecture (target)

```
User plays YouTube (or any media)
        │
MediaProjection permission (one-time prompt)
        │
Foreground Service (mediaProjection type)
        │
AudioRecord ← system playback PCM
        │
Resample / chunk (~60ms) → 16 kHz mono
        │
WebSocket → Gemini Live Translate
        │
PCM 24 kHz response → AudioTrack
        │
Optional: duck original via session volume / usage (best-effort)
```

UI modules:
- Onboarding (permissions, how it works)
- Home / Live session controls
- Settings (API key, target language, app language)
- Account (Google Sign-In)

---

## 5. Auth & API key (policy)

- **Google Sign-In** for account identity and future cloud settings.
- **Gemini API key:** v1 = user pastes key from Google AI Studio (guided in-app). Do **not** automate scraping AI Studio.
- Later: optional backend proxy so keys stay server-side (commercial).

YouTube login is **not** required to capture YouTube audio; system capture hears whatever is playing.

---

## 6. App UI languages (i18n)

Ship string resources for at least:

English, Persian (fa), Arabic (ar), Spanish (es), French (fr), German (de), Portuguese (pt), Turkish (tr), Russian (ru), Chinese Simplified (zh-rCN), Japanese (ja), Korean (ko), Hindi (hi), Indonesian (id).

User can change **app UI language** independently from **dubbing target language**.

---

## 7. Roadmap

| Phase | Scope |
|-------|--------|
| **0** | Scaffold Gradle + Compose + theme + PROJECT_CONTEXT (current) |
| **1** | MediaProjection capture + Gemini Live WS + playback + notification controls |
| **2** | Google Sign-In + secure key storage + full onboarding |
| **3** | Polish UI, floating controls, error UX |
| **4** | Monetization (subscriptions / ads / region-aware payments — separate design) |
| **5** | Advanced A/V sync experiments |

---

## 8. Sister extension lessons (do not regress)

- Gemini payload: transcription fields **outside** `generationConfig` when required by Live Translate.
- Never leave audio routes stuck after stop.
- Gender pitch-shift on short realtime chunks caused noise — prefer clean pass-through unless streaming phase-vocoder exists.
- Clear errors for quota / permission / capture conflict.

---

## 9. Current status

- Repo created; scaffold in progress.
- Next: Gradle modules, Compose shell, dark gold theme, baseline `values` + `values-fa` strings.

---

## 10. AI continuation

- Product name is **Voxora** (global). Do not rename to Pars/Persian.
- Extension repo stays separate for desktop experiments.
- After every change: bump version if releasing, update this file, commit + push `main`.
