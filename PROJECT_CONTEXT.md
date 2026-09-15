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
| Architecture | Multi-module: `app`, `core` (more feature modules in Phase 1+) |
| DI | Hilt |
| Async | Coroutines + Flow |
| Preferences | DataStore |
| Auth | Credential Manager + Google Sign-In (Phase 2) |
| Audio in | MediaProjection + AudioRecord (Phase 1) |
| Audio out | AudioTrack (Phase 1) |
| Gemini | WebSocket BidiGenerateContent · model `gemini-3.5-live-translate-preview` |
| i18n | `values` / `values-xx` + `localeConfig` |

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
```

---

## 5. Auth & API key (policy)

- **Google Sign-In** for account identity (Phase 2).
- **Gemini API key:** v1 = user pastes from Google AI Studio. Do not scrape AI Studio.
- Later: optional backend proxy for commercial keys.

YouTube login is **not** required to capture YouTube audio.

---

## 6. App UI languages (i18n)

**Shipped strings so far:** English (`values`), Persian (`values-fa`).

**localeConfig registered:** en, fa, ar, es, fr, de, pt, tr, ru, zh-CN, ja, ko, hi, id.

Add remaining `values-xx/strings.xml` in Phase 2–3. App UI language is independent of dubbing target language.

---

## 7. Roadmap

| Phase | Scope | Status |
|-------|--------|--------|
| **0** | Gradle + Compose shell + gold theme + i18n base | **Done** |
| **1** | MediaProjection + Gemini Live WS + playback + notification | Next |
| **2** | Google Sign-In + key storage + onboarding + more locales | |
| **3** | Polish UI, floating controls, error UX | |
| **4** | Monetization | Later |
| **5** | Advanced A/V sync | Later |

---

## 8. Sister extension lessons

- Live Translate payload shape matters.
- Always clean up audio on stop.
- Avoid naive per-chunk pitch shift (noise).
- Clear permission / quota errors.

---

## 9. Current status (Phase 0 complete)

**Works in repo:**
- Multi-module Gradle (`app`, `core`)
- Compose home UI (dark gold theme)
- EN + FA strings; locales_config for 14 languages
- `GeminiLiveConfig` constants aligned with extension
- Manifest permissions for future capture service

**Not yet:** Gradle Wrapper (open in Android Studio once to generate), Phase 1 capture/Gemini, real Start button logic.

**Next task:** Phase 1 — `DubForegroundService` + AudioPlaybackCapture + WebSocket client + AudioTrack playback.

---

## 10. AI continuation

- Brand is **Voxora** only (global).
- Extension repo stays separate.
- After every change: update this file + commit + push `main`.
