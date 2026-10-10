# Outer Voice

Native Android app for a 1024 × 600 landscape vehicle head unit. Developer: Zeca. Tester credit: SL. Contributors: Chris and j.Lun.

[Download v1.4.1 APK](https://github.com/zecahh1010/outervoice/releases/download/v1.4.1/OuterVoice-1.4.1.apk) · [Release notes and source archive](https://github.com/zecahh1010/outervoice/releases/tag/v1.4.1) · [App documentation](OuterVoice/README.md) · [Validation](OuterVoice/validation.md)

Android 9/API 28 or later; package `com.zecadev.outervoice`; version **1.4.1 (build 9)**. The APK uses the retained signing certificate. Install it over the existing app to preserve saved sounds and settings.

## What's new in v1.4.1

- **Larger buttons:** both Button size and Minimized button size support **64–200 physical pixels**, in 4px steps, with an 88px default. Settings previews scale to fit.
- **Correct reopen position:** minimizing anchors the bubble to Live Speak. Reopening anchors Live Speak to the bubble's current position, including after dragging it, reordering buttons or scrolling the sound strip. Both transitions animate around the microphone. Near a screen edge, placement is adjusted to keep the full panel accessible.
- **Android file picker:** Import Sound opens Android's document picker first, with a compatible arbitrary-file GET_CONTENT picker as a fallback. Images-only galleries are excluded. If no compatible picker is available, the app offers Android Settings or an explicit built-in Browse files option.

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

Android's file picker requires an enabled compatible picker on the head unit. Audio codec support and the BUS12 speaker route depend on its firmware.

## Validation and hardware status

v1.4.1 retains the existing BUS12 playback route. Audio-format/conversion tests, native compilation, APK signature/alignment and update installation pass. Android 9 emulator checks cover both size limits and persistence, anchored minimize/reopen after movement/reordering/scrolling, screen-edge placement, Android's document picker, compatible file-manager import and explicit missing-picker behavior. Saved-sound fixtures remain unchanged.

Audible external-speaker output, microphone quality, firmware-specific picker availability and overlay behavior still require the actual head unit. See [validation](OuterVoice/validation.md) for evidence and remaining hardware checks.

## Proposed enhancements

These are discussion proposals, not features included in v1.4.1 or commitments to a release date. Suggested first priorities are backup/restore, a floating Stop control and saved panel profiles.

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
