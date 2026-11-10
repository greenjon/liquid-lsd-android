# Liquid LSD for Android

An audio-reactive VJ app: the [Liquid LSD](https://liquid-lsd.greenjon.com) visuals (the same engine as the web TV at
spaz.org/tv and the picture in the Spaz Radio app), driven by your phone's microphone or by whatever the phone is
playing, with control over scenes, effects and backgrounds. It follows live broadcasts from the Liquid LSD desktop
app. Think of it as the desktop app's player: it shows what the desktop makes, it doesn't edit it.

App ID `lsd.liquid`, Kotlin + Jetpack Compose, Android 10 (API 29) and later.

## How it is put together

- `lsd-visuals/` is a committed copy of liquid-lsd's `android/lsd-visuals/` module: the web engine (in its
  `?embed` mode) as assets, the WebView that shows it (`LsdVisuals`), its controls (`LsdControl`, `LsdState`,
  `LsdCatalog`), signed content updates from spaz.org/tv, and the audio feed (`PcmTap`). `lsd-visuals/VERSION`
  names the liquid-lsd commit. **Don't edit it here:** change it in liquid-lsd, then run
  `scripts/sync_visuals.sh [path/to/liquid-lsd]` and commit.
- `app/` is the VJ app: `audio/AudioService` (a foreground service running the microphone or the system-audio
  capture, `audio/AudioCapture`), `VjViewModel` (settings, the picture's state, saved control settings) and
  `ui/` (the picture full screen, a tap shows the controls).

## Audio

- **Microphone**: raw (`UNPROCESSED`) where the phone offers it, else `VOICE_RECOGNITION`.
- **Phone audio**: `AudioPlaybackCapture` through a MediaProjection the user grants each time. Apps that opt out of
  capture (many streaming apps) come through as silence; the level meter shows it.
- The sound is analysed on the phone only, never recorded or sent.

## Build

`./gradlew assembleDebug`, tests `./gradlew testDebugUnitTest`. The plan this app follows is in liquid-lsd's
`.planning/vj-app-plan.md`.

## Licence

GPL-3.0 (see `LICENSE`), like Liquid LSD.
