# Outer Voice 1.6.2

Build 16, 2026-11-27. This update addresses the vehicle report that 1.6.1 remains at 配對中 while 1.5.0 can discover and pair the same button.

Discovery returns to the car-tested 1.5.0 implementation from commit `01ca59e`:

- Direct `BluetoothLeScanner.startScan(..., ScanCallback)` with the `ZEEKR_button` filter, low-latency mode, zero report delay and original default matching settings.
- Scan registration, advertisement processing and GATT pairing run on the main looper, as in 1.5.0. `ButtonLink.java` is unchanged from 1.5.0.
- The original target-28 compatibility requirements return: fine location permission and enabled location services, plus Nearby Devices scan/connect permission on Android 12+. Microphone permission is needed for speaking, not BLE discovery. 配對診斷 includes a shortcut to location services.
- Old PendingIntent registrations are stopped during migration. Queued broadcasts from the previous APK cannot feed or recreate the new callback listener. Late callbacks from stopped scan registrations are rejected.

Later fixes remain: scan cleanup/throttling and sleep/wake recovery, pairing diagnostics/counts, independent screen/button assignments, consolidated Settings, and red pulsing floating Live Speak controls.

Install `OuterVoice-1.6.2.apk` over the existing app. Open **設定 → 靈動貼**, allow Bluetooth/location permissions and enable location services, then tap **配對靈動貼** and hold the button for about three seconds until its backlight flashes. A binding is saved only after GATT notification subscription succeeds.

Local callback, recovery and native checks are recorded in [validation](validation-v1.6.2.md). Synthetic Android callbacks do not prove physical BLE reception or pairing on the car; the restored path requires a vehicle retest.
