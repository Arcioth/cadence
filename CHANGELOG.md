# Changelog

## 2026-09-20

- Crash on album open: lookahead read ExoPlayer from a background thread (`setThrowsWhenUsingWrongThread`). Position is now an AtomicLong; analysis uses a software decoder so it does not steal the playback codec.
- New native app Cadence. Kotlin, Compose, ExoPlayer, MediaCodec lookahead.
- Automatic sway / stretch / rotate on album art from beat envelope.
