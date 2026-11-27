# Outer Voice 1.6.2 validation

The user reports successful physical pairing with 1.5.0 and continued discovery failure with 1.6.1. This release uses 1.5.0 commit `01ca59e` as the compatibility baseline. Source comparison confirms `ButtonLink.java` is unchanged; the scanner overload, name filter, settings, main-looper execution and target-28 location prerequisites are restored.

`PairingDiscoveryTest` covers the restored permission/target policy and retained registration cleanup, explicit-pairing wake behavior, throttling and shutdown. Existing WAV/import, protocol/gain, stale-binder and capture-policy suites run in `build.ps1`.

The Android 13 `PairingProbe` now injects native ScanResult packets into the actual registered ScanCallback. It checks single and batched delivery, the main looper, wrong-name rejection, long-press GATT initiation, no premature saved binding, duplicate-link prevention, visible callback failures, cancellation/late callbacks and parked pairing. A legacy PendingIntent broadcast must not feed or replace callback scanning. The test runs with location permission/services enabled and microphone permission denied.

Reports under `qa/v162-native` distinguish these callback-injection checks from over-the-air reception. Existing native recording/settings/recovery tests and the floating Live animation probe check retained functionality. No production test endpoint or fallback speaker route is added.

The tests cannot reproduce the car's Bluetooth/VLite wrapper or complete a physical GATT notification subscription. Actual discovery, pairing, sleep recovery and audible BUS12 output require car retesting. An emulator scan request or injected callback alone is not evidence of a working physical pairing.
