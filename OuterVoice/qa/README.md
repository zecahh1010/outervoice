# Emulator evidence

v110-* images and XML are from the final v1.1.0 APK at 1024 × 600. Earlier unprefixed screenshots document v1.0.0. Fixture WAVs are test data, not bundled app sounds.

check-v110.py exercises upgrade retention, recording, WAV metadata, deletion, missing-picker fallback and diagnostics. It expects the local Android 9 x86 emulator-5554, adb in the workspace .build-tools directory, the earlier five imported sound fixtures, and welcome.wav / invalid.wav in the emulator Download folder. It uses emulator root to inspect app-private test recordings. Do not treat it as a physical-head-unit audio test. DocumentsUI and MusicPicker are temporarily disabled and restored in a finally block to reproduce absent file/audio pickers.

Android 9 UIAutomator sometimes returns null roots. The scripts retry; resume-v110.py and finish-v110.py document the resumed checks after transient accessibility failures. lifecycle-v110.py checks background/cancel cleanup, persistent removal, and volume control following the main flow. Pure Java format/writer/normalization tests are in ../tests and run with build.ps1 -TestOnly.
