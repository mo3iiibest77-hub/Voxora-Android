# PROJECT_CONTEXT.md — Voxora (Android)

**Version:** 0.5.4-dev (versionCode 10)

## Latest: In-app logger

- `VoxoraLog` ring-buffer (800 lines) with ERROR/WARN/INFO/DEBUG
- Settings → View logs (copy / share / clear)
- DubService, Playback, Capture instrumented with step-by-step logs
- Use this to diagnose "Could not start dubbing"

## Previous fixes
- 0.5.3: removed Handler (Looper crash)
- 0.5.2: ANR + volume ducking ~5%
- i18n 7 locales

## Rules
Brand Voxora only. Bump version + update this file + push after material changes.
