# Outer Voice 1.5.1 validation

2026-11-27. Android 13/API 33 emulator at 1024 × 600; it has no Zeekr button and no BUS12 device. The real-car report for 1.5.0 supersedes any suggestion that its emulator checks proved car operation.

## Regression checks

`RecoveryPolicyTest` runs eight deterministic error-2/park/wake cycles. It verifies unconditional registration cleanup, fresh-service repair calls, settlement, successful restart without a simulated head-unit reset, three-starts-per-30-second throttling, invalidation of queued starts on pause/stop and no orphan retry/renew after shutdown. It tests the actual upstream wrapper traversal with a cyclic Java object fixture: only the dead reference changes, while live and static references remain. It also checks the MiclessKaraoke source/API policy and duplicate-port identity across boots. These are controller and wrapper-graph tests, not a real VLite binder.

Existing WAV/import/normalization and smart-button advertisement/GATT/gain tests pass. APK signature/alignment and signed update installation pass with the retained certificate `664b72c9d4c6075814ffa1c9b72a2d0c78132238fbed9d6b2265b78a7614a17e`.

The emulator-only `FixProbe` exercises the release classes and native windows:

- Same floating foreground-service instance survives switching to the smart-button tab.
- A simulated bound-button action starts recording. With a saved sound selected and the panel minimized, the bubble is red and its rendered pixels change between animation frames.
- Repeated normal status updates do not emit Toasts.
- Actual Android PendingIntent scan registration succeeds. An injected error 2 triggers cleanup and actual re-registration, and park/wake releases and re-registers the scanner while preserving the binding.
- Live mode does not request audio focus. The release PCM worker is invoked by the test harness with an explicit emulator speaker target and streams real Android AudioRecord/AudioTrack frames in compatibility, performance and standard modes, then releases both resources. This bypasses only the release entry point's absent-BUS12 check; there is no emulator/speaker fallback in the shipped app.
- Explicit Live input selection resolves the chosen port. The configuration dialog is checked as a native window.

Reports and screenshots are under `qa/v151-native`. Test addresses and sounds are fixtures, not evidence of physical pairing. The previous `VoiceProbe` also runs the 22 independent-mode, recording lifetime, rename, sound-selection and gain-slider assertions against this update.

Final results: all 19 targeted `FixProbe` assertions and all 22 existing `VoiceProbe` assertions passed on Android 13. The final worker streamed 67,680 PCM frames in compatibility mode, 64,420 in performance mode and 68,400 in legacy standard mode during the short emulator runs; these counts demonstrate streaming, not measured acoustic latency or car loudness. The final AndroidRuntime error log contained no app crash. Missed Bluetooth-off/on broadcasts are also covered by the deterministic radio-polling tests.

## Car checks still required

Install without uninstalling. Check both the full panel and bubble on the 靈動貼 tab. With the actual button, test single/double presses before and after a complete vehicle sleep/wake cycle, including the 10-second settlement period. Confirm bindings remain and re-pairing works without a vehicle reboot. Error 2 has several platform causes; Android registration and fake dead-reference tests cannot prove the exact OEM/container recovery path.

For Live Speak, first use compatibility mode and select the same working input as MiclessKaraoke when auto is silent. Confirm input RMS/PCM frames and listen at the actual exterior speaker. Compare performance mode if appropriate. Real microphone quality, gain audibility, amplifier behavior, acoustic feedback and end-to-end latency have not been measured by these tests.
