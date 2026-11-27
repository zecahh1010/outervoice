# Pairing investigation for 1.6.3

## What is established

The user reports that Outer Voice 1.5.0 ScanCallback pairing and Zeekr Shortcut PendingIntent pairing work on the same car. Outer Voice 1.6.1 stays at 配對中. This rules out a blanket claim that the car cannot support PendingIntent scanning.

The unchanged, signed **1.6.1 APK** was run as a control on Android 13/API 33 against a virtual peripheral using Google's Bumble and an isolated Android netsim radio. This did **not** inject ScanResult objects: advertisements travelled through the Android Bluetooth stack and the real manifest receiver, then actual GATT service discovery/CCCD subscription saved a binding. The control completed in **1,346 ms** on this run. This is virtual-radio evidence, not a physical Zeekr/VLite result. See `qa/v163-native/baseline-radio.txt`.

## Source comparison

Reference: Zeekr Shortcut [`ddf1b97e300aac203a3f2bf719e5b1e4381cac83`](https://github.com/dts88/zeekr-shortcut-car/tree/ddf1b97e300aac203a3f2bf719e5b1e4381cac83), fetched 2026-11-27. Its button subsystem is unchanged from the previously examined `e64a4f6`. Outer Voice control: `65f2243` / release 1.6.1.

| Area | Zeekr Shortcut | Outer Voice 1.6.1 | 1.6.3 treatment |
| --- | --- | --- | --- |
| Initialization | Application installs a process singleton; receiver installs it idempotently | Scanner owned and created by the audio foreground service | Application-owned controller, installed before activities/services/receiver |
| Receiver dispatch | Read error/parcelled results on receiver/main looper, then post parsed values to BLE worker | Copy Intent, read its lazy Bundle later on BLE worker | Decode/catch parcel errors on main, post decoded results; report empty/unreadable payloads |
| Service lifetime | BLE manager independent of foreground audio service | Service destruction closes scanner and GATT | BLE runtime survives service destruction; audio actions first ensure foreground service readiness |
| Background scan | Explicit mutable PendingIntent, named filter, low latency/aggressive/all matches/legacy/delay zero | These core parameters already match | Preserve reference parameters in selectable Shortcut transport |
| Permissions | target SDK 36; Nearby Devices on Android 12+ | target SDK 28; Nearby Devices with neverForLocation; fine location capped at API 30 | Keep target 28 for existing file access; Shortcut uses Nearby Devices on 12+, callback preserves v1.5.0 location compatibility |
| Scan result state | Separate scan/listening/binding states and counters | Registration accepted could remain displayed as 配對中 without identifying the failing stage | Distinguish request acceptance, receiver self-check, real BLE broadcast count, decode failures, long press, GATT connection and subscription |
| Old registrations | Stable token, cleanup, generations and state guards | Stable token and cleanup, but no release-specific routing boundary | Distinct data-qualified token; ignore old/no-data broadcasts and stopped-scan errors; finish native stop before installing another transport |

Relevant upstream files: [`SmartButtons.java`](https://github.com/dts88/zeekr-shortcut-car/blob/ddf1b97e300aac203a3f2bf719e5b1e4381cac83/app/src/main/java/com/kooo/evcam/button/SmartButtons.java), [`ScanReceiver.java`](https://github.com/dts88/zeekr-shortcut-car/blob/ddf1b97e300aac203a3f2bf719e5b1e4381cac83/app/src/main/java/com/kooo/evcam/button/ScanReceiver.java), [`ZeekrShortcutApp.java`](https://github.com/dts88/zeekr-shortcut-car/blob/ddf1b97e300aac203a3f2bf719e5b1e4381cac83/app/src/main/java/com/kooo/evcam/ZeekrShortcutApp.java).

The control test also confirms that stopping the 1.6.1 foreground service destroys its BLE listener. This is a reproduced lifetime difference. It can explain loss of background listening **if that lifecycle event occurs**; it is not proof that this event caused the user's initial pairing failure. Reading the Bundle on a different thread and the differing target/container permission environment are confirmed integration differences, not proven physical-car root causes.

## What cannot be claimed

No contemporaneous car receiver/BLE/GATT trace was available for the failed 1.6.1 attempt. The exact original failure cannot be uniquely attributed to service lifetime, container dispatch, permissions or another OEM interaction. The successful virtual control disproves an unconditional defect in the PendingIntent API/path. Do not describe emulator registration, loopback self-checks or virtual-radio success as physical-car validation.

## User-visible recovery

The pairing page offers **Zeekr Shortcut 方式** and **v1.5.0 相容方式**, saved independently from all speaking actions. Switching retains bindings/names/actions and waits for the old native scan to stop before installing the other backend. Application/receiver startup, decoded result handling and GATT order are common and diagnosable. The local receiver loopback self-check is counted separately from real BLE results, so it cannot make an absent peripheral appear detected.
