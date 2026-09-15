# PROJECT_CONTEXT.md — Voxora (Android)

**Version:** 0.6.0-dev (versionCode 20)

## v0.6.0 — four critical fixes

### 1) Audio dominance (separate stream)
- Dub plays on `USAGE_ASSISTANT` + `CONTENT_TYPE_SPEECH` (not STREAM_MUSIC)
- Only `STREAM_MUSIC` is ducked (~4% of current) → YouTube/Instagram quieter
- Gemini stays full volume on assistant path
- `ALLOW_CAPTURE_BY_NONE` prevents self-echo into capture
- Larger AudioTrack buffer to reduce choppy playback

### 2) Floating bubble UX
- Much smaller pill UI
- Drag anywhere on screen
- Minimize to edge tab (`–`), tap tab to restore
- Stop still available when expanded

### 3) Install / override
- CI caches `~/.android/debug.keystore` with fixed key `voxora-debug-keystore-v1`
- Same signature across CI builds → new APK can update over previous without uninstall
- `applicationId` remains `com.voxora.app`

### 4) Stability / size
- Release build enables minify + shrinkResources (smaller APK when release is used)
- Playback / focus logging for crash diagnosis via in-app logs
- Audio write stays on IO dispatcher (ANR fix from 0.5.x kept)

## Known platform limits
- Cannot force per-app volume of third-party players without root
- Gemini Live latency ~2–3s is model-side
- Some apps ignore audio focus ducking; STREAM_MUSIC lower still helps most media players

## Rules
Brand **Voxora** only. After material changes: bump versionCode/versionName, update this file, push main.
