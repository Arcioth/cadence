# Changelog

## 2026-09-20 — halted

User halted the project. HANDOFF signed. Last code: `9c17812` (permission screen: audio-only request, resume re-check, settings link).

## 2026-09-20 (v0.3.1)

- Permission screen: request only `READ_MEDIA_AUDIO` (Android 16 dropped the bundled audio+images+notify dialog). Re-check on resume. Open settings. Status bar stays until grant.

## 2026-09-20 (v0.3)

- Crash: `startForegroundService` never posted a notification (also why the player notification was missing). Service now `startForeground` immediately + Media3 notification.
- Album search + compact grid toggle. Montserrat + Phosphor icons. Status bar hidden; clock + battery HUD.
- Shake follows time since beat and decays with the envelope. HSB is a real ColorMatrix on the image.
- Coil loads images at 1280px max to stop OOM.

## 2026-09-20 (v0.2)

- Beats: mid/high flux (less bass bloom), locked BPM, snap close doubles to the grid, skip MP3 primer.
- Saturation min/max per song drives ~30% peak FX. Shake (rot L/R, vert, horiz) + zoom in/out.
- Gallery: up to 16 images. Double-tap edit for fullscreen (clock + battery top-right).
- Playback lives in the app process: leaving the album does not stop music. Media notification (asks POST_NOTIFICATIONS).

## 2026-09-20

- Crash on album open: lookahead read ExoPlayer from a background thread (`setThrowsWhenUsingWrongThread`). Position is now an AtomicLong; analysis uses a software decoder so it does not steal the playback codec.
- New native app Cadence. Kotlin, Compose, ExoPlayer, MediaCodec lookahead.
- Automatic sway / stretch / rotate on album art from beat envelope.
