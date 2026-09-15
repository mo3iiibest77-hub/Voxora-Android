# Voxora (Android)

**Live AI dubbing for every language**

Native Android app (Kotlin · Jetpack Compose · Material 3) that captures system audio, streams it to Gemini Live Translate, and plays the dubbed audio in real time.

Sister: [ParsLiveDub](https://github.com/mo3iiibest77-hub/ParsLiveDub)

## Build (GitHub Actions)

Push to `main` → artifact **`voxora-debug-apk`**.

## First run

1. Install debug APK (API 29+)
2. Settings → paste Gemini API key → Save
3. Start live dubbing → allow screen capture (audio only)
4. Play any video; stop from app, notification, or bubble

## 0.5.0-dev

- Localized sticky errors · no self-echo on dub · audio focus ducking
- 7 UI locales · 30+ dub languages · optional Google Sign-In
