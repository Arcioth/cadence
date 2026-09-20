# Cadence HANDOFF

Session **2026-09-20**. Native rewrite of the autoedit idea.

## Why not autoedit

WebView + aubiojs + muted scout `<audio>` failed: `aubio failed`, ~10s of FX then silence. WASM and MediaElementSource are not a stable Android engine.

## Cadence

- **Name:** Cadence (`io.github.arcioth.cadence`)
- **Play:** Media3 ExoPlayer
- **Lookahead:** MediaCodec 12s windows, 11 kHz mono, drop PCM after analysis
- **Beats:** flux + BPM grid (verses still tick)
- **Library:** MediaStore albums (no recursive storage walk)
- autoedit tree is left alone

## v0.2

BPM is **locked** after the first good window (tiny EMA only if within 6%). Close double-hits snap to the grid. Flux ignores sub-bass bloom. Saturation min/max per song scales FX to ~30% at the loudest part. Playback is process-wide + MediaSession notification. Gallery images (≤16). Double-tap fullscreen.
