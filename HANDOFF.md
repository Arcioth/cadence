# Cadence HANDOFF

**HALTED 2026-09-20.** User: halt, good work, bye. Do not continue until they say so.

Signed off: Grok, Cadence session. Phone: Samsung SM-G990B2 (r9q, Android 16 / SDK 36). USB `R5CX90B91GD`.

## Last ship

- Git `main` @ `9c17812` — permission-screen fix (Android 16).
- Prior: `6103b0d` v0.3 (search, compact, Phosphor/Montserrat, FGS crashfix).
- App id `io.github.arcioth.cadence`. Repo https://github.com/Arcioth/cadence
- APK: `~/Documents/cadence/cadence-debug.apk` and GitHub releases (`v0.3.0` is the last tagged; 0.3.1 permission fix is on `main` only).
- Installed clean over USB after uninstall.

## What it is

Native Android beat player. Not Capacitor, not autoedit.

| Piece | How |
|---|---|
| Play | Media3 ExoPlayer, process-wide. Leaving an album does not stop music. |
| Map | MediaCodec **software** decoder, 12s PCM windows, 11 kHz mono. 30–40s ahead. |
| Beats | Kotlin flux (mid/high, not sub-bass) + **locked BPM** grid. Close doubles snap to the grid. Skip ~80ms MP3 primer. |
| FX | Punch ~30% at song sat max. Shake decays with time-since-beat. HSB ColorMatrix on the photo. |
| Library | MediaStore albums. Search + compact grid. |
| Visuals | Gallery ≤16 images. Double-tap fullscreen. |
| Chrome | Status bar hidden after grant. Clock + Phosphor battery HUD. Montserrat (italic bold *Cadence*). Phosphor.ttf. |
| Notify | `PlaybackService` MediaSession; `startForeground` immediately (do **not** `startForegroundService` from the activity). |

autoedit (`~/Documents/autoedit`) is the dead WebView experiment. Leave it.

## Why the last bugs happened

1. **Album-open crash (v0.1):** lookahead read ExoPlayer `currentPosition` on `Dispatchers.Default`. Fatal. Position is `AtomicLong`.
2. **Frequent crash + no notification (v0.2):** `startForegroundService` without a notification in 5s → `ForegroundServiceDidNotStartInTimeException`.
3. **Allow button dead (v0.3 on Android 16):** one `RequestMultiplePermissions` for audio+images+notify never showed a dialog. Now **only** `READ_MEDIA_AUDIO`. Re-check on resume. Settings fallback. Do not hide the status bar until music is granted. Notifications asked after.

## Open (when resumed)

- Tag/release 0.3.1 if the permission screen actually works on device.
- Video as edit media (explicitly deferred).
- Stronger instrument-aware beats if mid/high flux is still bass-ish.
- Photo-picker persistable URI / OEM quirks if images crash again (Coil already capped at 1280px).

## Build

```
JAVA_HOME=/mnt/arch/home/arcioth/jdk21
ANDROID_HOME=/mnt/arch/home/arcioth/Android/Sdk
cd ~/Documents/cadence
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Do not start hardware (Metatron/Uriel) or other Documents projects unless asked.
