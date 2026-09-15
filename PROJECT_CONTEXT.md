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
| **Version** | **0.4.0-dev** (Phase 3) |

---

## 2. Phase status

| Phase | Status |
|-------|--------|
| 0 Scaffold | Done |
| 1 Capture + Gemini + playback | Done |
| 2 Google Sign-In + onboarding + locales | Done |
| **3 Floating controls + notification + error UX** | **Done (0.4.0-dev)** |
| 4 Monetization | Later |
| 5 Advanced A/V sync | Later |

---

## 3. Phase 3 implemented

### Floating bubble
- `FloatingBubbleService` — overlay with Live label + Stop
- Requires `SYSTEM_ALERT_WINDOW` (Settings → Enable floating bubble)
- Shown while Connecting/Live; hidden on stop/error

### Notification polish
- Channel name/description
- Titles: Connecting / Live / Error
- BigText body, Stop action, onlyAlertOnce

### Stronger error UX
- Pre-start API key check (no projection prompt if missing)
- Permission / projection denial mapped to clear messages
- Gemini 401 / 429 / network mapped in `DubService.mapError`
- Home error banner: message + Settings + Retry
- `DubService.postError` / `clearError` for UI-driven errors

### Settings
- Overlay permission entry point

---

## 4. Key files (Phase 3)

- `app/.../dub/FloatingBubbleService.kt`
- `app/.../dub/DubService.kt`
- `app/.../ui/HomeScreen.kt`
- `app/.../MainActivity.kt`
- `app/.../ui/VoxoraNav.kt`
- `app/.../ui/SettingsScreen.kt`
- `AndroidManifest.xml` — SYSTEM_ALERT_WINDOW + FloatingBubbleService

---

## 5. How to test Phase 3

1. Pull `main`, run on device API 29+
2. Start without API key → error banner, no capture dialog
3. Deny mic → permission error banner
4. Cancel capture → projection denied message
5. Live session → richer notification + optional floating bubble (after overlay grant)
6. Stop from bubble, notification, or Home

---

## 6. Known limits

- Overlay needs manual user grant
- System audio ducking still imperfect
- ~2–3s Gemini latency expected
- Google Sign-In needs Web client ID (Phase 2)

---

## 7. AI continuation

Brand **Voxora** only. After changes: update this file + push `main`.
Next: Phase 4 monetization planning / entitlement design.
