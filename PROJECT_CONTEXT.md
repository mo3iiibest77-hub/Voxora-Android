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
| **Version** | **0.5.2-dev** (versionCode 8) |

## 2. Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · minSdk 29 · OkHttp WebSocket · DataStore · MediaProjection + AudioPlaybackCapture · AudioTrack · Credential Manager · SYSTEM_ALERT_WINDOW

## 3. Phase status

| Phase | Status |
|-------|--------|
| 0–3 Capture, Gemini, UI, bubble, notif | Done |
| **3.5 Production hardening** | **In progress → 0.5.2** |
| CI assembleDebug | Green expected after this push |
| 4 Monetization | Later (no wallet/ads yet) |

## 4. 0.5.2 fixes (current)

### P0 ANR
- `DubService` rewritten: `Dispatchers.Default` for lifecycle, dedicated `Dispatchers.IO` audioScope for every `AudioTrack.write`.
- `mainHandler` only for notification + bubble updates.
- Sticky Error status (Idle from Gemini no longer wipes a visible error).
- `ic_stat_notify` used for notification icon.

### Audio conflict / ducking
- `DubPlayback(context)` always receives Application Context → `AudioManager` available.
- `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` + explicit `STREAM_MUSIC` volume lowered to ~5% while live; restored on stop.
- `ALLOW_CAPTURE_BY_NONE` to prevent self-echo.
- Larger AudioTrack buffer (4× min) to reduce write blocking.

### i18n
- All critical R.string keys present in en / fa / fr / ar / es / de / tr.
- FR apostrophes escaped; app language vs dubbing language remain separate.

### Other
- OkHttp exposed as `api` from core (compile fix).
- allowBackup=false, ACCESS_NETWORK_STATE present.

## 5. Known remaining (user-reported)

| Issue | Status / notes |
|-------|----------------|
| Latency 2–3 s | Model-side (Gemini Live). A/V sync / WSOLA later (Phase 5). |
| UI language only English | System locale + app language setting; strings now complete for 7 locales. User can change via Settings → App language. |
| Google Sign-In fails | Placeholder `default_web_client_id`; intentional until real Web client ID is supplied. Guest + API key fully works. |

## 6. AI continuation rules

- Brand **Voxora** only.
- After material change: update this file + bump versionCode/versionName + commit + push `main`.
- No ZIP uploads. Prefer GitHub Actions APK.
- User manages own API keys / tokens; do not lecture on security.
- minSdk 29, no monetization yet.
