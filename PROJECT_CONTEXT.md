# PROJECT_CONTEXT.md — Voxora (Android)

**Standing rule:** After any material change, update this file in the same session.

## 1. Product

| Field | Value |
|-------|--------|
| **Brand** | **Voxora** |
| **Tagline** | Live AI dubbing for every language |
| **Type** | Native Android app |
| **Repo** | https://github.com/mo3iiibest77-hub/Voxora-Android |
| **Sister** | https://github.com/mo3iiibest77-hub/ParsLiveDub |
| **Version** | **0.5.0-dev** |

## 2. Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · minSdk 29 · OkHttp WebSocket · DataStore · MediaProjection + AudioPlaybackCapture · AudioTrack · Credential Manager · SYSTEM_ALERT_WINDOW

## 3. Phase status

| Phase | Status |
|-------|--------|
| 0–3 Capture, Gemini, UI, bubble, notif | Done |
| **3.5 Production hardening (0.5.0)** | **Done** |
| CI assembleDebug | Strings + icon aligned for green build |
| 4 Monetization | Later |

## 4. 0.5.0 fixes

- All R.string keys for DubService present in en/fa/fr/ar/es/de/tr; FR apostrophes escaped
- Notification icon `ic_stat_notify` (white)
- Error status no longer wiped by Gemini Idle
- Playback `ALLOW_CAPTURE_BY_NONE` (no self-echo) + audio focus ducking
- versionCode 6 / versionName 0.5.0-dev
- allowBackup=false

## 5. AI continuation

Brand **Voxora** only. After changes: update this file + commit + push `main`.
