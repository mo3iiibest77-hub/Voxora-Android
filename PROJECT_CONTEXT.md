# PROJECT_CONTEXT.md — Voxora (Android)

**Version:** 0.6.1-dev (versionCode 21)

## v0.6.1
- Duck STREAM_MUSIC only to **~28%** of current (softer start, ASSISTANT dub stays full)
- Circular brand bubble + gold waveform logo; drag; expand Stop; minimize to edge
- **Experimental lipsync:** DelayedScreenOverlay buffers low-res frames ~**2200ms** and shows them full-screen so picture tracks Gemini delay
- Stronger Gemini reconnect (8 attempts, longer backoff, ping 12s)

## Notes on lipsync
- Requires overlay permission (same as bubble)
- Covers the real player with delayed frames — if heavy/black/fail, overlay stops and picture stays live
- Lag is fixed 2.2s for now; tunable later in Settings

## Audio model
- Dub: USAGE_ASSISTANT
- Source apps: STREAM_MUSIC ducked
- Cannot force per-app volume without root
