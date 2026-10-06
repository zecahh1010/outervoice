# Outer Voice

Native Android app for a 1024 × 600 landscape vehicle head unit. Developer: Zeca. Tester credit: SL. Contributors: Chris and j.Lun.

[Download v1.1.0 APK](OuterVoice/dist/OuterVoice-1.1.0.apk) · [App documentation](OuterVoice/README.md) · [Validation](OuterVoice/validation.md)

## Features

- Live microphone speaking through BUS12_OUTER_NOTIFY.
- Record & Play: temporary 44.1 kHz mono PCM16 WAV capture and playback.
- Scrollable saved sounds, with Add Sound importing WAVs or recording the microphone.
- A local file browser for head units without a system file picker, and confirmed sound removal.
- Media-volume and live-level controls, speaker test and copyable audio-routing diagnostics.

Android 9/API 28 minimum; package com.zecadev.outervoice; version 1.1.0 (2). The APK is signed with the same certificate as v1.0.0 and can update it while retaining saved sounds.

## Validation and hardware status

The signed APK, native screens, recording/WAV format, import fallback, deletion, persistence and media-volume controls passed local Android 9 emulator checks. Audible BUS12 output still requires the actual head unit. The user's v1.0.0 head-unit test captured microphone input but produced no audible outer-speaker output; v1.1.0 improves BUS12 address matching and WAV normalization, restores live level to 100%, and adds diagnostics. See [routing investigation](OuterVoice/routing-investigation.md).

## Source and build

The application and build script are in [OuterVoice](OuterVoice/README.md). Build with the documented local Java/Android toolchain; portable tool downloads and private signing material are excluded from Git. The retained signing key is required to produce compatible future updates.

The MIT license notice is retained. Material Icons and Inter font licenses are included with the app assets.
