# Outer Voice 1.6.1 validation

Environment: local Android 13/API 33 emulator, 1024 × 600; signed 1.6.1 build 15. No physical Zeekr button or BUS12 exterior speaker is connected to the emulator.

## New discovery regression tests

`PairingDiscoveryTest` verifies the Android permission split, retaining a healthy scan, explicit pairing after park/wake settlement, the 500 ms cleanup gap, the three-registration/30-second budget and shutdown invalidation. It runs with the existing WAV/import, protocol/gain and BLE recovery/capture suites in `build.ps1`.

`PairingProbe` passed all 17 discovery assertions on the signed APK with the location switch OFF and microphone permission denied. The actual Android BLE PendingIntent registration succeeds using Nearby Devices permissions. Synthetic native `ScanResult` packets are sent through the production mutable PendingIntent and manifest receiver, including application-context resolution with a receiver wrapper that uses a different preference namespace.

It checks wrong-name rejection, short-press discovery without GATT, long-press GATT initiation, a shared scan/GATT worker, no binding saved before subscription, duplicate-link prevention, visible scanner errors during pairing, Cancel rejecting late advertisements, and explicit Pair leaving the parked state. A fake peripheral address is used; no physical connection or CCCD response is asserted.

## Floating Live Speak

The animation run of `PairingProbe` passed all 10 assertions. It injects Live session state because the emulator lacks BUS12, then compares actual screenshot pixels in the expanded and minimized native overlay windows. Both render a red microphone with a visible pulse and an explicit Stop description. The real floating Stop handler cancels a session owned by another controller and clears its animation/scale. Screenshots and reports are under `qa/v161-native`.

The tests use no production test endpoints or fallback audio route. They restore their saved preference values and stop test services. Existing native regression reports and the final verification summary accompany this file in `qa/v161-native`.

## Verification limits

Registering a scan and injecting an Android advertisement prove app delivery and state handling, not over-the-air reception, physical GATT subscription or pairing on Zeekr firmware. Actual smart-button discovery, VLite attribution, OEM sleep/binder recovery, microphone routing, exterior playback, acoustic quality and latency require a car retest. Normal source/protocol and emulator checks cannot substitute for that test.
