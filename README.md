# Outer Voice

Native Android app for a 1024 × 600 landscape vehicle head unit. Developer: Zeca. Tester credit: SL. Contributors: Chris and j.Lun.

[Download v1.5.0 APK](OuterVoice/dist/OuterVoice-1.5.0.apk) · [Corresponding source ZIP](OuterVoice/dist/OuterVoice-1.5.0-source.zip) · [Release notes](OuterVoice/release-notes-v1.5.0.md) · [App documentation](OuterVoice/README.md) · [Validation](OuterVoice/validation-v1.5.0.md)

Android 9/API 28 or later; package `com.zecadev.outervoice`; version **1.5.0 (build 11)**. The APK uses the retained signing certificate. Install it over the existing app to preserve saved sounds and settings.

## What's new in v1.5.0

- **Three speaking modes:** hold/release/play, tap/record/tap/stop/play, or Live Speak for real-time microphone output. Screen and floating controls share this setting.
- **Zeekr 靈動貼:** a dedicated tab for pairing, naming and independent per-device single-press assignments. Single press defaults to record/stop/play; alternatives include Live Speak, a selected saved sound, stop audio and no action. Double press plays a selected saved sound. Physical-button assignments never force the screen setting to change.
- **Background feedback:** a shared audio session keeps the floating microphone red with a recording pulse, including recording started remotely. The foreground BLE listener handles advertisements and GATT notifications with deduplication. Long press remains pairing-only.
- **Exterior digital gain:** 0 to +24 dB for recorded/saved sounds and Live Speak, separate from Android media volume, with bounded output limiting.
- **Validation:** offline parser/audio tests, signed update installation and 22 Android 13 native assertions passed. Physical Zeekr button pairing, actual BUS12 sound, acoustics and live latency still require the car.

## Features

- **Speaking:** choose hold/release/play, tap/record/tap/stop/play or Live Speak. All output uses `BUS12_OUTER_NOTIFY`; recording shows a red animated microphone. Capture uses a microphone format supported by the device, without a fixed 44.1kHz mono restriction; recordings are limited to 180 seconds.
- **Floating panel:** use Live Speak and selected saved sounds above other apps. Settings are grouped into **Buttons & Order** and **Size & Minimize**. Arrange buttons, choose from ten colors ordered warm to cool and twelve icons, and customize Live Speak's color while retaining its microphone icon.
- **Size and spacing:** independently adjust base and minimized button sizes from 64–200px. Enlarge the entire Live Speak button by 0–100% (50% default), with proportional icons. Configure spacing between circle edges from 0–40px (12px default).
- **Minimize and restore:** move the panel using its dedicated handle, or drag the minimized microphone bubble. Optional auto-minimize uses an inactivity timeout of 1–3600 seconds, initially 30 seconds when enabled, and waits during recording or playback. With only the microphone selected, the minimized bubble operates directly using the chosen speaking mode. Layout, position and minimized state persist across restarts.
- **Saved sounds:** import WAV, MP3 or other device-decodable audio, or record the microphone. Play/Stop lets you preview an imported sound through the outer speaker before saving. Imports convert to the existing player's PCM WAV format; sounds persist across restarts.
- **Edit List:** stage rename, reorder and confirmed deletion, then Save Changes to apply them. Cancel or Back discards the draft. Renaming preserves floating-panel customization; deleted sounds are removed from the panel.
- **App controls:** the touch/ripple icon opens floating settings. Exit stops audio, disables the floating panel and closes the app.
- Independent 靈動貼 assignments, Android media-volume controls, exterior digital gain, speaker test and copyable audio-routing diagnostics.

## Installation

1. Download the APK above and copy it to the head unit.
2. Install over the existing Outer Voice app to retain saved sounds and settings.
3. Grant microphone access. To use the floating panel, enable **Display over other apps** when prompted.
4. Run the speaker test on the actual head unit and choose the screen speaking mode.
5. For 靈動貼, open its tab, allow Bluetooth/location permissions as requested, enable listening and start pairing. Long-press the actual Zeekr button for about three seconds until its light flashes, then name the device and assign its actions. See the release notes for details.

The built-in browser requires file-access permission and can show storage readable by the app. Audio codec support, access to USB/external storage and the BUS12 speaker route depend on the head unit's Android version and firmware.

## Validation and hardware status

v1.5.0 retains strict BUS12 routing. Completed checks and verification boundaries are recorded in [v1.5.0 validation](OuterVoice/validation-v1.5.0.md), with the [native regression report](OuterVoice/qa/v150-native/v150-probe.txt). Earlier checks remain in the [historical validation log](OuterVoice/validation.md).

Audible external-speaker output, microphone quality, external-storage access and firmware-specific overlay behavior still require the actual head unit.

## Proposed enhancements

These are discussion proposals, not features included in v1.5.0 or commitments to a release date. Suggested first priorities are backup/restore, a floating Stop control and saved panel profiles.

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

The combined v1.5.0 release is GPL-3.0-only with the applicable upstream GPL section 7 terms. Based on Zeekr Shortcut by dts88 (https://github.com/dts88/zeekr-shortcut-car), pinned at `5d9d8a872e459aed49d657a94c55eaeafbf5155d`. This is a modified Outer Voice application, not the original Zeekr Shortcut.

See [notices](OuterVoice/NOTICE.md), [upstream terms](OuterVoice/UPSTREAM-NOTICE.md) and [GPL-3.0](LICENSE). Original [repository MIT notices](LICENSE-MIT.txt), [Outer Voice MIT notices](OuterVoice/LICENSE-MIT.txt), Material Icons and Inter font licenses are retained. Private signing material is excluded from Git and the corresponding source archive.
