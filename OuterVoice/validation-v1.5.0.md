# Outer Voice 1.5.0 validation

Date: 2026-11-26. Package `com.zecadev.outervoice`, version 1.5.0 (11). Test device: local Android 13/API 33 emulator at 1024 × 600. It has no Zeekr button and no BUS12 output.

## Build and update

The offline Java/audio tests cover WAV format validation, import conversion/resampling/cancellation and normalization. `SmartButtonAudioTest` adds upstream-format advertisement and ASCII GATT vectors, pairing-only long press, unknown/truncated messages, counter cycles/repeats, GATT/advertisement deduplication, query checksums, gain bounds, ±full-scale 0 dB preservation, little-endian amplification and monotonic bounded output at +24 dB. These are actual parser/DSP tests, not evidence of a radio connection or amplifier sound pressure.

The signed APK installs over the existing 1.4.2 APK using `adb install -r`. The package's update certificate SHA-256 is `664b72c9d4c6075814ffa1c9b72a2d0c78132238fbed9d6b2265b78a7614a17e`. APK signature/alignment are checked by `build.ps1`. The final package digest is in `dist/SHA256.txt`.

## Native UI and background checks

The emulator permission flow was exercised through the actual Android dialogs for location and Nearby devices. BLE scanner registration and a foreground `SmartButtonsService` were observed after Home; Bluetooth diagnostics showed an active registered scan with no suspension at that observation. This does not prove reception of a physical button's advertisements.

The separate instrumentation APK in `tests/android` exercises the release app's actual shared session, dispatch and settings code. It supplies a clearly labelled test address and a generated local WAV; it does not pretend to pair a device. The probe restores preferences and removes its generated sound. Detailed results: `qa/v150-native/v150-probe.txt`.

All 22 final native assertions passed. Checks cover independent screen/button modes; click recording surviving release, Activity pause and Home; second-click stop; screen-mode changes preserving remote recording; hold down/release; screen toggle start/stop; +24 dB persistence; selected sound dispatch; missing assigned sound handling; unassigned double press as a no-op; disabled actions and stop-audio action. UI checks also exercise per-device single-action choice, rename/save, double-press sound selection and actual gain-slider changes saving +12 and +24 dB. No application crash appeared in the AndroidRuntime error log. The emulator's System UI showed a startup ANR before testing; it was restarted before the successful native runs.

Screenshots in `qa/v150-native` show the native red floating recording indicator, its continued presence over the launcher, a simulated-device assignment page, sound picker and gain setting. Test-device screenshots are explicitly labelled 模擬. A single frame shows appearance; the recording pulse is implemented with the existing repeated `ValueAnimator` and respects disabled Android animations.

## Target-car checks still required

1. Update with the APK without uninstalling. Confirm existing saved sounds and floating settings remain.
2. Choose each on-screen speaking mode. Hold/release and tap/stop should record and then play; Live Speak should route ongoing MIC audio to BUS12. Confirm actual microphone input, exterior speaker, timing and focus behavior. There is no cabin fallback when BUS12 is unavailable.
3. In **靈動貼**, allow requested permissions, enable listening, start pairing and long-press the actual Zeekr button approximately three seconds until the light flashes. Verify the device card appears only after notification subscription. Test rename, repeated single/double presses, idle disconnection/advertisement operation, reconnect, forgetting, Bluetooth off/on and multiple saved buttons. Radio, GATT timing, OEM BLE filters and firmware background process policy remain unverified.
4. Set different screen and button modes. Confirm neither forces the other to change. Change the screen setting during a remotely started recording and stop it using the physical button or visible Stop control.
5. Test each double-press sound, sound renaming and deletion. Test the panel outside the app, minimized bubble, drag, close-panel button and notification stop. Main Exit stops both foreground services; reboot/process-kill restoration is not promised.
6. Compare exterior gain at 0, +6, +12 and +24 dB with the same recording, saved sound and live input. These are digital amplitude gains before limiting, separate from Android media volume; they do not control an undocumented amplifier register and do not guarantee a particular measured SPL or latency.

No physical Zeekr button pairing, audible car output, OEM microphone route, gain audibility, acoustic-feedback behavior or live round-trip latency is claimed by this emulator verification.
