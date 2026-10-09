# Outer Voice v1.4.0

Implements the approved interactive UI revisions in the native Android app. Android 9/API 28 minimum; version code 8. Updates use the existing signing certificate and preserve saved sounds and existing panel selections, colors, icons and order.

- Wider, vertically centered Live Speaking section; compact saved-sound and Edit List rows show more items with scrolling available.
- Add Sound has one Import Sound action and a clear Play button after import/recording. Microphone capture selects a supported rate, stereo/mono and float/PCM16 format instead of requiring 44.1 kHz mono. Recorded WAV storage remains PCM16 for the existing player and retains captured rate/channel count. Supported audio imports continue to use Android decoders and conversion to the playback format.
- If Android's file picker is missing, Import Sound opens the built-in file browser directly; it no longer falls back to an images-only gallery. Any file extension can be selected, with clear errors for undecodable audio.
- Floating Panel settings use **Buttons & Order** and **Size & Minimize** tabs. Six ordering entries fit; size, spacing, Live Speak enlargement, minimized size and timeout fit together with previews.
- Ten colors arranged warm to cool, plus the default App Blue option for Live Speak. Its microphone stays fixed and its color can change. Twelve sound icons include Funny, Extreme Angry, Happy, Friendly, Sorry, Surprised, Calm, Urgent and Celebration.
- Move / Minimize / Close toolbar with separated controls and a middle button that expands with panel width. Narrow single-button panels remain usable. Close asks for confirmation.
- Smooth minimize/reopen and stronger recording pulse. Separate minimized size defaults to 88 px. Optional inactivity auto-minimize defaults to 30 seconds and waits during recording/playback/dragging.
- A minimized Live Speak-only bubble records directly while held; release plays without expanding. Dragging cancels and moves it. Positions, settings and minimized state persist.
- Native foreground-service overlay continues to display over ordinary apps using Android's **Display over other apps** permission. Exit stops audio, disables the overlay and closes the task.

Release assets: signed APK, source archive including the approved web simulator, and SHA256 checksums. Install the APK over the existing app to retain saved sounds. Keep the signing key private for future builds.

Build, signature/alignment, Java audio tests and Android 9 emulator UI/overlay checks are documented in `validation.md` and `qa/`. Audible BUS12 outer-speaker output, head-unit microphone quality and firmware behavior still require vehicle testing.
