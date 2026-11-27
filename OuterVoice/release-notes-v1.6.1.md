# Outer Voice 1.6.1

Build 15, 2026-11-27. Install over the existing app to preserve sounds, settings and button assignments. The update retains the original signing certificate.

## Smart-button discovery

Compared the implementation with [Zeekr Shortcut](https://github.com/dts88/zeekr-shortcut-car) at `e64a4f6034460853e35b8789d2ab21aa12bfb8e1`. Its button subsystem is unchanged from the previously referenced `5d9d8a8` revision.

- Android 12+ discovery uses Nearby Devices scan/connect permissions. Outer Voice no longer requires microphone/location permission or an enabled location switch before starting BLE on these versions. Older Android retains its location requirement. Target SDK 28 is retained for the existing file browser.
- Mutable PendingIntent delivery resolves application context first, with Android 13 typed scan results. Advertisement processing and GATT operations now share the same BLE worker, including stopping scanning before GATT retries.
- Pairing preserves an already healthy scan and exits parked/wake-settlement state when explicitly requested. Registration cleanup, retry throttling and the reference GATT notification-subscription sequence remain.
- The page displays scanner readiness/failures during pairing, received button/long-press counts and **配對診斷**. A short press requests a long press; only successful GATT notification subscription saves a new binding.

Open **設定 → 靈動貼 → 配對靈動貼**, allow Nearby Devices if requested, and long-press the button for about three seconds until its light flashes. Wait for the GATT connection to finish. A persistent zero count means the app has not received the matching advertisement; it does not mean a GATT pairing attempt has failed.

## Floating Live Speak

The floating microphone now turns red and pulses during both recording and Live Speak, in the expanded panel and minimized bubble. The pulse stops when Live Speak stops or the session ends. It indicates an active microphone session rather than detecting individual spoken syllables; the system's reduced-animation setting is respected.

Android 13 native discovery/receiver and animation checks passed. Test advertisements and Live session state were injected; actual Zeekr pairing, sleep recovery and exterior-speaker audio still require a car retest. See [validation](validation-v1.6.1.md).
