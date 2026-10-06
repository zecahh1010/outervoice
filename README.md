# Outer Voice

Native Android app for a 1024 × 600 landscape vehicle head unit. Developer: Zeca. Tester credit: SL. Contributors: Chris and j.Lun.

[Download v1.2.2 APK](OuterVoice/dist/OuterVoice-1.2.2.apk) · [App documentation](OuterVoice/README.md) · [Validation](OuterVoice/validation.md)

## Features

- Live Speaking: hold to record a WAV; release to save and immediately play through BUS12_OUTER_NOTIFY.
- A floating panel above other apps: reorderable Live Speak plus selected saved sounds, configurable order, seven light colors, three icons and 64–144 px circles (88 px default). Live Speak has a fixed mic icon with configurable 0–100% enlargement (50% default). Drag position and configuration persist; the enable checkbox and × control stop the overlay.
- Scrollable saved sounds, with Add Sound importing WAVs or recording the microphone.
- A local file browser for head units without a system file picker, and a dedicated Edit List screen for staged rename, reorder and confirmed deletion. Home-screen trash buttons are removed.
- Media-volume controls, speaker test and copyable audio-routing diagnostics.

Android 9/API 28 minimum; package com.zecadev.outervoice; version 1.2.2 (6). The APK is signed with the same certificate as v1.0.0 and can update it while retaining saved sounds.

## Validation and hardware status

v1.2.2 retains the WAV recording and playback path that the user verified on the head unit. Local WAV tests, compilation, APK signature and alignment checks pass. The floating panel's system permission flow and display over Android Settings were verified in the Android 9 emulator. Audible BUS12 output and vendor-specific overlay behavior require the actual head unit. See [validation](OuterVoice/validation.md).

## Source and build

The application and build script are in [OuterVoice](OuterVoice/README.md). Build with the documented local Java/Android toolchain; portable tool downloads and private signing material are excluded from Git. The retained signing key is required to produce compatible future updates.

The MIT license notice is retained. Material Icons and Inter font licenses are included with the app assets.
