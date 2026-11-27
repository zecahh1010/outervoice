# Outer Voice 1.6.3

Build 17. Install over the existing app using the retained signing certificate.

**設定 → 靈動貼 → 配對方式** now offers both:

- **Zeekr Shortcut 方式**: system PendingIntent receiver, dedicated BLE worker and reference scan settings. Android 12+ requests Nearby Devices permissions.
- **v1.5.0 相容方式**: direct ScanCallback and main-looper pairing, retaining the location permissions/services used by the car-tested 1.5.0 path.

The selection persists and applies to pairing and subsequent button listening. Changing it preserves saved device names/bindings/actions, screen speaking mode and an ongoing speaking session. The old native scan finishes stopping before the new transport is created; methods do not run simultaneously or silently switch.

BLE initialization now belongs to the Application/process, as in Zeekr Shortcut. The receiver reads results on the main looper before dispatching to the BLE worker and no longer relies on starting an audio service just to accept an advertisement. Audio button actions ensure a foreground service is ready before recording/playback. Old broadcasts and stopped-scan errors are isolated from the current listener.

配對診斷 separates scan-request results, receiver self-checks, actual BLE results, decode errors, long-press recognition and GATT subscription. A scan request returning zero does not claim that a button was received. The floating Live Speak animation and independent sound/button settings remain.

[Investigation](pairing-investigation-v1.6.3.md) records the confirmed differences and the original 1.6.1 virtual-radio control. It **does not identify a uniquely proven cause for the original car failure**. See [validation](validation-v1.6.3.md) for completed checks and the remaining physical-car boundary.
