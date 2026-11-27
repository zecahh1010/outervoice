# Outer Voice 1.6.3 validation

Environment: Android 13/API 33 emulator, 1024 × 600, isolated netsimd instance 63 / gRPC 8877. A Bumble 0.0.236 virtual peripheral advertises the observed ZEEKR_button packet format and exposes service 1820, notification characteristic 2A60, command 2A70 and battery service. The HTTP test-control listener binds 127.0.0.1 only. No Internet permission or test endpoint is added to the release app.

The unchanged signed 1.6.1 control APK completed actual Android scan delivery, GATT service discovery and CCCD subscription in 1,346 ms. Stopping its foreground service also closes the BLE scanner. See `qa/v163-native/baseline-radio.txt` and [investigation](pairing-investigation-v1.6.3.md). This is not a physical Zeekr/VLite test.

`RadioProbe` and `tests/ble/run_radio_probe.py` use the real Android Bluetooth stack and a virtual peripheral, rather than calling the app's scan callback or receiver with synthetic ScanResult objects. They exercise both selectable backends, native method selection/persistence, real advertisement parsing and binding after real notification subscription. Real characteristic notifications exercise single/double actions and foreground-service restart. Switching verifies the old native scan finishes before the new transport is installed and retains binding, action settings and ongoing recording.

Shortcut mode is tested with fine location denied and location services OFF; the host grants location/enables it before the callback compatibility run. Existing native callback-injection, WAV/import/protocol/gain/recovery, recording/settings and Live animation tests provide complementary fault and regression coverage. Final reports and screenshots are under `qa/v163-native`.

Remaining limits: a virtual controller/peripheral cannot reproduce the vehicle's VLite context wrappers, OEM process management, actual button firmware timing, real sleep/wake or exterior BUS12 acoustics. The original car failure has no captured trace and cannot be uniquely reconstructed from the 配對中 text. Receiver self-check success is deliberately separate from actual BLE input and is not treated as over-the-air success. Physical-car confirmation remains required.

Final results: all 128 release checks passed on the final signed build, including 26 real-radio/dual-mode, 7 cold-process, 20 callback-fault, 22 recording/action, 24 Settings, 19 recovery/audio and 10 Live-animation assertions. The three original-1.6.1 control assertions are recorded separately. No AndroidRuntime application crash was observed.
