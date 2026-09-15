# PROJECT_CONTEXT.md — Voxora (Android)

**Standing rule:** After any material change, update this file in the same session.

---

## 1. Product

| Field | Value |
|-------|--------|
| **Brand** | **Voxora** |
| **Tagline** | Live AI dubbing for every language |
| **Type** | Native Android app |
| **Repo** | https://github.com/mo3iiibest77-hub/Voxora-Android |
| **Sister** | https://github.com/mo3iiibest77-hub/ParsLiveDub |
| **Version** | **0.2.0-dev** (Phase 1) |

Global product. UI language ≠ dubbing target language.

---

## 2. Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · minSdk 29 · OkHttp WebSocket · DataStore · MediaProjection + AudioPlaybackCapture · AudioTrack

---

## 3. Phase 1 architecture (implemented)

```
MainActivity → permissions → MediaProjection intent
        → DubService (FGS mediaProjection)
        → SystemAudioCapture (AudioRecord + AudioPlaybackCapture)
        → PCM 16 kHz chunks
        → GeminiLiveSession (WebSocket Live Translate)
        → AudioTrack 24 kHz playback
        → Notification Start/Stop
```

Key files:
- `core/.../gemini/GeminiLiveSession.kt` — protocol aligned with ParsLiveDub
- `core/.../audio/PcmUtils.kt`
- `core/.../prefs/UserPrefs.kt`
- `app/.../dub/SystemAudioCapture.kt`
- `app/.../dub/DubPlayback.kt`
- `app/.../dub/DubService.kt`
- `app/.../ui/HomeScreen.kt`, `SettingsScreen.kt`, `VoxoraNav.kt`

---

## 4. How to test

1. Open in Android Studio · Sync Gradle (generate wrapper if needed)
2. Run on device API 29+
3. Settings → paste Gemini API key → Save → choose dubbing language
4. Play YouTube (or any media)
5. Start live dubbing → allow screen capture (audio only)
6. Hear dubbed audio; Stop from app or notification

---

## 5. Roadmap status

| Phase | Status |
|-------|--------|
| 0 Scaffold | Done |
| **1 Capture + Gemini + playback** | **Done (0.2.0-dev)** |
| 2 Google Sign-In + onboarding + more locales | Next |
| 3 Polish UI / floating controls | |
| 4 Monetization | Later |
| 5 Advanced A/V sync | Later |

---

## 6. Known limits (Phase 1)

- Original app audio still plays (no perfect system-wide duck yet)
- ~2–3s Gemini model latency is expected
- Some OEMs restrict AudioPlaybackCapture for certain apps
- Gradle Wrapper not committed — Android Studio can add it on first open
- Old `VoxoraAppRoot.kt` may still exist unused; HomeScreen is the active UI

---

## 7. AI continuation

Brand **Voxora** only. After changes: update this file + commit + push `main`.
