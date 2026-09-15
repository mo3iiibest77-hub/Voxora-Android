# PROJECT_CONTEXT.md — Voxora (Android)

**Standing rule:** After any material change, update this file in the same session.

## 1. Product

| Field | Value |
|-------|--------|
| **Brand** | **Voxora** |
| **Version** | **0.5.3-dev** (versionCode 9) |
| **Repo** | https://github.com/mo3iiibest77-hub/Voxora-Android |

## 2. 0.5.3 fix (critical)

**Crash on Start Live:**
`Can't create handler inside thread DefaultDispatcher-worker that has not called Looper.prepare()`

Root cause: Handler usage mixed with Default dispatcher.
Fix: Removed all `Handler` / `Looper`. UI updates (notification + bubble) now use `withContext(Dispatchers.Main)`. AudioTrack writes remain on dedicated IO scope.

## 3. Previous (0.5.2)

- ANR: AudioTrack.write on IO
- Ducking: STREAM_MUSIC ~5%
- Sticky Error status
- i18n complete for 7 locales

## 4. Remaining

- In-app logger (next)
- Latency is model-side
- Sign-In needs real Web client ID

## 5. Rules

Brand Voxora only. Bump version + update this file + push main after material changes.
