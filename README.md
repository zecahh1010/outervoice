# Outer Voice

Native Android app for a 1024 × 600 landscape vehicle head unit. Developer: Zeca. Tester credit: SL. Contributors: Chris and j.Lun.

[Download v1.4.2 APK](https://github.com/zecahh1010/outervoice/releases/download/v1.4.2/OuterVoice-1.4.2.apk) · [Release notes and source archive](https://github.com/zecahh1010/outervoice/releases/tag/v1.4.2) · [App documentation](OuterVoice/README.md) · [Validation](OuterVoice/validation.md)

Android 9/API 28 or later; package `com.zecadev.outervoice`; version **1.4.2 (build 10)**. The APK uses the retained signing certificate. Install it over the existing app to preserve saved sounds and settings.

## What's new in v1.4.2

- **Built-in sound browser:** Import Sound opens Outer Voice's own storage/folder browser directly, using the approach available in v1.2.2. Grant file access if prompted, choose internal or accessible mounted storage, and browse to a sound. An installed Android document picker or file manager is no longer required.
- **Existing sound support retained:** WAV, MP3 and other device-decodable audio remain importable. Files are checked by contents; selecting an unsupported or unreadable file reports an error. Play/Stop preview remains available after a successful import.
- The v1.4.1 improvements remain: 64–200px button and minimized-button sizes, proportional icons, and Live Speak positioning when minimizing/reopening or moving the bubble.

## Features

- **Live Speaking:** hold to record; release to finalize a temporary WAV and immediately play through `BUS12_OUTER_NOTIFY`. A strong, smooth pulse indicates recording. Capture uses a microphone format supported by the device, without a fixed 44.1kHz mono restriction; recordings are limited to 180 seconds.
- **Floating panel:** use Live Speak and selected saved sounds above other apps. Settings are grouped into **Buttons & Order** and **Size & Minimize**. Arrange buttons, choose from ten colors ordered warm to cool and twelve icons, and customize Live Speak's color while retaining its microphone icon.
- **Size and spacing:** independently adjust base and minimized button sizes from 64–200px. Enlarge the entire Live Speak button by 0–100% (50% default), with proportional icons. Configure spacing between circle edges from 0–40px (12px default).
- **Minimize and restore:** move the panel using its dedicated handle, or drag the minimized microphone bubble. Optional auto-minimize uses an inactivity timeout of 1–3600 seconds, initially 30 seconds when enabled, and waits during recording or playback. With only Live Speak selected, hold the minimized bubble directly to speak. Layout, position and minimized state persist across restarts.
- **Saved sounds:** import WAV, MP3 or other device-decodable audio, or record the microphone. Play/Stop lets you preview an imported sound through the outer speaker before saving. Imports convert to the existing player's PCM WAV format; sounds persist across restarts.
- **Edit List:** stage rename, reorder and confirmed deletion, then Save Changes to apply them. Cancel or Back discards the draft. Renaming preserves floating-panel customization; deleted sounds are removed from the panel.
- **App controls:** the touch/ripple icon opens floating settings. Exit stops audio, disables the floating panel and closes the app.
- Media-volume controls, speaker test and copyable audio-routing diagnostics.

## Installation

1. Download the APK above and copy it to the head unit.
2. Install over the existing Outer Voice app to retain saved sounds and settings.
3. Grant microphone access. To use the floating panel, enable **Display over other apps** when prompted.
4. Run the speaker test on the actual head unit, then hold Live Speak and release to play.

The built-in browser requires file-access permission and can show storage readable by the app. Audio codec support, access to USB/external storage and the BUS12 speaker route depend on the head unit's Android version and firmware.

## Validation and hardware status

v1.4.2 retains the existing BUS12 playback route. Completed checks and evidence for this release are recorded in [validation](OuterVoice/validation.md). The v1.4.1 checks for size limits and anchored panel transitions remain documented there as well.

Audible external-speaker output, microphone quality, external-storage access and firmware-specific overlay behavior still require the actual head unit.

## Proposed enhancements

These are discussion proposals, not features included in v1.4.2 or commitments to a release date. Suggested first priorities are backup/restore, a floating Stop control and saved panel profiles.

| Proposal | Intended behavior |
|---|---|
| Backup and restore | Export sounds and settings together for recovery after reinstalling, resetting or changing devices. |
| Floating Stop control | Show a prominent Stop button during playback, with the sound name and progress. |
| Panel profiles | Save and switch arrangements of selected sounds, order, colors, sizes and spacing. |
| Per-sound volume | Adjust each saved sound independently of the overall media-volume control. |
| Sound trimming | Preview and remove unwanted sections or silence, saving a copy while retaining the original. |
| Search and categories | Find sounds by name and organize larger libraries. |
| Optional startup restoration | Restore the panel when the head unit starts, subject to firmware and background-app restrictions. |

## Source and build

The application and build script are in [OuterVoice](OuterVoice/README.md). Build with the documented local Java/Android toolchain; portable tool downloads and private signing material are excluded from Git. The retained signing key is required to produce compatible future updates.

The MIT license notice is retained. Material Icons and Inter font licenses are included with the app assets.
