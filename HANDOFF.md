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
