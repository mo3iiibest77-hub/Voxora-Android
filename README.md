# Voxora (Android)

**Live AI dubbing for every language**

Native Android app (Kotlin · Jetpack Compose · Material 3) that captures system audio, streams it to Gemini Live Translate, and plays the dubbed audio in real time.

Sister project: [ParsLiveDub](https://github.com/mo3iiibest77-hub/ParsLiveDub) (Chrome extension).

## Requirements

- Android Studio Ladybug+ / JDK 17
- minSdk 29 (Android 10+) for AudioPlaybackCapture
- Gemini API key from [Google AI Studio](https://aistudio.google.com/apikey)

## Build (local)

```bash
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Build (GitHub Actions)

Push to `main` (or run **Actions → Android CI → Run workflow**).
The workflow produces a downloadable **voxora-debug-apk** artifact.

## First run

1. Install the debug APK on a real device (API 29+).
2. Complete onboarding → Settings → paste Gemini API key → Save.
3. Optional: enable floating bubble (overlay permission).
4. Press **Start live dubbing** → allow screen capture (audio only).
5. Play any video/podcast; stop from app, notification, or bubble.

## Current version

See `PROJECT_CONTEXT.md` (Phase 3: floating controls + notification + error UX).

## Notes

- ~2–3s model latency is expected.
- Google Sign-In needs a Web client ID in `default_web_client_id` (optional; guest mode works with API key only).
