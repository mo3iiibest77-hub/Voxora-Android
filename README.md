# Voxora

**Live AI dubbing for every language.**

Native Android app that captures system audio (YouTube, podcasts, any player), streams it to **Google Gemini Live Translate**, and plays natural dubbed speech in 70+ languages.

> Sister prototype (Chrome extension): [ParsLiveDub](https://github.com/mo3iiibest77-hub/ParsLiveDub)

## Stack

- Kotlin · Jetpack Compose · Material 3
- MediaProjection + AudioPlaybackCapture (Android 10+)
- Gemini Live Translate (WebSocket)
- Google Sign-In (Credential Manager)
- App UI localized for major world languages

## Status

**0.1.0-dev** — Phase 0 scaffold

See [PROJECT_CONTEXT.md](./PROJECT_CONTEXT.md) for architecture, roadmap, and engineering rules.

## Open in Android Studio

1. Clone this repo
2. Open the project root in Android Studio
3. Sync Gradle · Run on a device/emulator (API 29+)

```bash
./gradlew :app:assembleDebug
```

## License

Proprietary / commercial — all rights reserved unless stated otherwise.
