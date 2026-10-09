# Emulator evidence

v110-* images and XML are from the final v1.1.0 APK at 1024 × 600. Earlier unprefixed screenshots document v1.0.0. Fixture WAVs are test data, not bundled app sounds.

check-v110.py exercises upgrade retention, recording, WAV metadata, deletion, missing-picker fallback and diagnostics. It expects the local Android 9 x86 emulator-5554, adb in the workspace .build-tools directory, the earlier five imported sound fixtures, and welcome.wav / invalid.wav in the emulator Download folder. It uses emulator root to inspect app-private test recordings. Do not treat it as a physical-head-unit audio test. DocumentsUI and MusicPicker are temporarily disabled and restored in a finally block to reproduce absent file/audio pickers.

Android 9 UIAutomator sometimes returns null roots. The scripts retry; resume-v110.py and finish-v110.py document the resumed checks after transient accessibility failures. lifecycle-v110.py checks background/cancel cleanup, persistent removal, and volume control following the main flow. Pure Java format/writer/normalization tests are in ../tests and run with build.ps1 -TestOnly.

## v1.2.0 floating panel

`check-v120.py` uses the same Android 9 emulator and five fixtures. Initial phases: `setup`, `permission`, `grant`, `other`. The permission phase selects Welcome, Please wait and Thank you, customizes Thank you as Light Red + Angry, and moves it before Please wait. The grant phase uses Android's real Allow display over other apps switch. `suite` repeats the final tests from that persisted configuration: resize to 144 px, inspect the genuine system overlay above Android Settings, drag and restore position, hold/release microphone capture, sound playback request, close, checkbox disable, and reset to 88 px. `cancel` tests gesture cancellation then closes the overlay.

The overlay is non-focusable, so Android 9 UIAutomator dumps usually contain the underlying app rather than the overlay. Overlay checks therefore combine system-window type/geometry, actual screenshots, input events, saved preferences, PCM bytes and application diagnostics. `check-v111.py 1.2.0` runs main-screen regression against the new APK with the overlay disabled. Older scripts are historical and target previous UI versions.

`v120-*` screenshots/XML describe v1.2.0. Test clips remain emulator test data and are not bundled in the app. BUS12 unavailable errors in the emulator validate playback requests and error handling, not audible vehicle output.

## v1.2.1 Edit List

`check-v121.py` installs v1.2.1 on emulator-5554 and creates a disposable recorded sound through Add Sound. It checks that home trash controls are absent, draft rename/reorder/deletion are cancelled together, saved rename/order survive restart without changing UUID/audio bytes, and a selected sound's rename retains its floating selection/color/icon. It then confirms deletion, saves it, checks WAV removal and overlay refresh, and verifies all original five fixtures remain. Screenshots include `v121-home.png`, `v121-editor.png`, `v121-renamed-overlay.png` and `v121-after-delete-overlay.png`. The test closes or hides the overlay while editing and rechecks its system-overlay type above Android Settings afterward.

## v1.2.2 Live Speak ordering and icon size

`check-v122.py` recreates legacy floating preferences on emulator-5554, checks migration to the 50% default, reorders Live Speak among saved sounds, and verifies its fixed microphone icon. It checks invalid and blank percentage rejection, compares actual overlay pixels at 0%, 50% and 100% with unchanged circle geometry and saved-sound pixels, verifies persistence, and holds/releases Live Speak in third position above Android Settings. BUS12 is absent in this emulator.

## v1.3.0 sizing, minimize and audio import

`check-v130.py` upgrades emulator-5554, checks actual Live Speak circle dimensions, edge spacing, proportional icons at maximum base size, persisted settings, minimize/drag/reopen and Exit. It generates disposable audio using portable FFmpeg in the ignored .build-tools folder and imports PCM8/24/float WAV, MP3, AAC/M4A, FLAC, OGG, a WAV exceeding20MB, and MP3 with a nonstandard extension. It verifies converted WAV parameters/duration, Play availability and BUS12 preview requests, damaged-file rejection, draft cleanup and unchanged original saved sounds. Audible BUS12 output needs the head unit.

Set OV_QA_PHASE=import to repeat only import checks; OV_QA_CASES can name a comma-separated subset. The script rejects stale UIAutomator snapshots. Fixture audio stays outside source/release assets.

`check-v130-final-ui.py` checks the final settings helper text and home controls, restarts while minimized across status-bar/fullscreen screens, restores the original full-panel position, and verifies a failed replacement removes the stale preview and disables Play.

## v1.4.0 approved simulator implementation

`check-v140.py` phases `config`, `panel`, `bubble`, and `record` check the two settings tabs, six visible ordering entries, warm-to-cool palette, fixed Live Speak mic/custom color, twelve icons, draft/Cancel persistence, system overlay above Android Settings, automatic/manual minimize, close confirmation, direct Live Speak-only bubble gestures, restart positions and adaptive recording. `config` starts with the original five disposable saved sounds and an enabled/auto-minimize-off baseline; the script's first saved configuration enables the overlay. Android's non-focusable overlay is checked using real system-window geometry and screenshots, rather than expecting it in UIAutomator's app tree.

`check-v140-extra.py` phases `timer`, `order`, `six`, `exit`, `motion`, and `import` add timer safety, maximum sizes/wide horizontal scrolling, six Home/Edit entries with disposable recorded-sound cleanup, Exit shutdown, animated pulse pixel measurements, and audio import regressions. Timer/bubble/motion tests expect a Live Speak-only configuration; motion also expects its Pink color. `order` enables all five saved sounds and moves Live Speak first; `exit` then checks shutdown of that enabled overlay. `six` expects five saved fixtures and creates/removes only its new sixth fixture. Import expects the earlier disposable audio files in `/sdcard/Download/OuterVoice-QA`, temporarily disables absent-picker alternatives, and restores their original enabled state. Motion restores the emulator's animation setting afterward.

Recording status updates continuously; the tests stop recording using the already observed button coordinates before requesting an idle accessibility tree. Android's service shutdown is asynchronous, so Exit checks allow a bounded wait for service removal. Retained `v140-*` PNG/XML files document native screens and system overlays. `package-v140.py` builds the source ZIP and checksums, excludes tools/signing credentials and test WAVs, and includes the approved web simulator. Physical BUS12 audio remains a vehicle check.
