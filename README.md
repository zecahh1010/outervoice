# Outer Voice

Native Android app for a 1024 × 600 landscape vehicle head unit. Developer: Zeca. Tester credit: SL. Contributors: Chris and j.Lun.

[Download v1.3.0 APK](OuterVoice/dist/OuterVoice-1.3.0.apk) · [App documentation](OuterVoice/README.md) · [Validation](OuterVoice/validation.md)

## Features

- Live Speaking: hold to record a WAV; release to save and immediately play through BUS12_OUTER_NOTIFY.
- A floating panel above other apps: reorderable Live Speak plus selected saved sounds, seven light colors, three icons and 64–144 px base circles (88 px default). Live Speak's button is enlarged by 0–100% (50% default), with proportional icons on every button. Configure edge spacing (0–40 px, default 12). Minimize to a draggable mic bubble and tap to reopen; saved layout persists.
- Scrollable saved sounds, with Import Sound accepting WAV, MP3 and other decodable audio, plus Play/Stop preview through the outer speaker before saving. Imports convert to the existing player's PCM WAV format. Record microphone remains available.
- Touch/ripple icon opens floating settings; Exit stops audio, disables the floating panel and closes the app.
- A local file browser for head units without a system file picker, and a dedicated Edit List screen for staged rename, reorder and confirmed deletion. Home-screen trash buttons are removed.
- Media-volume controls, speaker test and copyable audio-routing diagnostics.

Android 9/API 28 minimum; package com.zecadev.outervoice; version 1.3.0 (7). The APK is signed with the same certificate as v1.0.0 and can update it while retaining saved sounds.

## Validation and hardware status

v1.3.0 retains the existing BUS12 recording and playback route and converts imported audio into its playback format. Local WAV/conversion tests, compilation, APK signature and alignment checks pass. Actual codec support depends on the device. Audible BUS12 output and vendor-specific overlay behavior require the head unit. See [validation](OuterVoice/validation.md).

## Source and build

The application and build script are in [OuterVoice](OuterVoice/README.md). Build with the documented local Java/Android toolchain; portable tool downloads and private signing material are excluded from Git. The retained signing key is required to produce compatible future updates.

The MIT license notice is retained. Material Icons and Inter font licenses are included with the app assets.
