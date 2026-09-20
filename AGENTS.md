# Cadence (Grok)

**HALTED 2026-09-20.** Read `HANDOFF.md` before touching anything.

Native Android beat player. Not Capacitor. Not autoedit.

- Folder/album of MP3s is a project (MediaStore).
- ExoPlayer plays. MediaCodec decodes **12s PCM windows** ahead of the playhead (30–45s mapped, never the whole file).
- Beat tracker is pure Kotlin (spectral flux + tempo grid) on 11 kHz mono.
- UI: Compose. No tolerance/settings dump. FX are automatic.

Build:

```
JAVA_HOME=/mnt/arch/home/arcioth/jdk21
ANDROID_HOME=/mnt/arch/home/arcioth/Android/Sdk
./gradlew :app:assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`
