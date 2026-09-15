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
| **Version** | **0.3.0-dev** (Phase 2) |

Global product. UI language ≠ dubbing target language.

---

## 2. Tech stack

Kotlin · Jetpack Compose · Material 3 · Hilt · minSdk 29 · OkHttp WebSocket · DataStore · MediaProjection + AudioPlaybackCapture · AudioTrack · Credential Manager (Google Sign-In)

---

## 3. Phase status

| Phase | Status |
|-------|--------|
| 0 Scaffold | Done |
| 1 Capture + Gemini + playback | Done (0.2.0-dev) |
| **2 Google Sign-In + onboarding + locales** | **Done (0.3.0-dev)** |
| 3 Polish UI / floating controls | Next |
| 4 Monetization | Later |
| 5 Advanced A/V sync | Later |

---

## 4. Phase 2 implemented

### Onboarding
- First-run 3-page pager (`OnboardingScreen`)
- Skip / Next / Get started
- Shortcut to Settings for API key
- Flag `onboarding_done` in DataStore

### Auth
- `GoogleAuthHelper` via Android Credential Manager + Google ID token
- Requires `default_web_client_id` (Web client ID from Google Cloud Console)
- If placeholder ID: clear error message; **guest mode** (API key only) still works
- Account email/name stored in DataStore; Sign out clears state

### Settings
- Account section (Sign in / Sign out)
- Link to Google AI Studio
- API key + dubbing language (unchanged core)

### i18n (UI strings)
- Full / primary: **en**, **fa**
- Additional: **ar**, **es**, **de**, **tr**, **fr**
- `localeConfig` already lists more codes for system per-app language

### Navigation
- `VoxoraNav`: onboarding → home | settings

---

## 5. Key files (Phase 2)

- `app/.../ui/OnboardingScreen.kt`
- `app/.../ui/VoxoraNav.kt`
- `app/.../ui/SettingsScreen.kt`
- `app/.../auth/GoogleAuthHelper.kt`
- `core/.../prefs/UserPrefs.kt` — onboarding + account fields
- `res/values*/strings.xml`

---

## 6. How to enable real Google Sign-In

1. Google Cloud Console → OAuth 2.0 **Web** client ID
2. Put it in `app/src/main/res/values/strings.xml` → `default_web_client_id`
3. Add Android OAuth client with app SHA-1 for Play/debug
4. Rebuild

Without this, Sign-In shows a configuration message; dubbing with pasted API key still works.

---

## 7. How to test Phase 2

1. Fresh install (or clear app data) → onboarding appears
2. Complete or skip → Home
3. Settings → optional Sign in (needs client ID) → paste Gemini key → Save
4. Start live dubbing as in Phase 1

---

## 8. Known limits

- Original system audio not fully ducked
- ~2–3s Gemini latency expected
- Google Sign-In inactive until Web client ID is set
- Gradle Wrapper may need generation in Android Studio

---

## 9. AI continuation

Brand **Voxora** only. After changes: update this file + commit + push `main`.
Next suggested: Phase 3 floating controls / notification polish / stronger error UX.
