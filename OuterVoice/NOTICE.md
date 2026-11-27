# Outer Voice 1.6.1 notices

This is Outer Voice, a modified combined application, not the original Zeekr Shortcut and not an official Zeekr product.

Based on Zeekr Shortcut by dts88 (https://github.com/dts88/zeekr-shortcut-car)

Smart button protocol and GATT implementation originate from upstream commit `5d9d8a872e459aed49d657a94c55eaeafbf5155d`, retrieved 2026-11-26. `Accessory.java`, `SmartButtonAd.java` and `ButtonLink.java` retain the upstream copyright headers. The package is changed to Outer Voice; AndroidX annotations and vehicle/knob lighting controls are removed. `SmartButtonsService.java` and `SmartButtonActions.java` adapt the upstream event handling for independent audio actions, local pairing and a foreground listener. These modifications are dated 2026-11-26.

The combined release is distributed under GPL-3.0-only with dts88's applicable GPL section 7 attribution and modified-version terms. See `GPL-3.0.txt` and section 8 of `UPSTREAM-NOTICE.md`. The complete upstream notice is retained for provenance; unrelated camera, ntfy and vehicle-control components were not incorporated.

Original Outer Voice MIT notices are retained in `LICENSE-MIT.txt`. Font and other bundled asset notices remain under `app/src/main/assets`. The About/Credits page displays the required attribution and bundled license text.

Corresponding source, assets, native tests and build scripts accompany the APK in `OuterVoice-1.6.1-source.zip`. Private signing keys/passwords are not included. A new builder supplies their own signing key, JDK and Android SDK; replacing an installed APK using its existing identity requires the original signing certificate.

## 1.5.1 changes, 2026-11-27

`StaleGatt.java` is ported from the same pinned upstream commit, retaining its header and dead-wrapper-only replacement logic (package changed). `ButtonScanner`, `ButtonScanReceiver` and `BleScanLoop` adapt the upstream PendingIntent, cleanup/settlement/rate-limit and sleep recovery design to Outer Voice. The APK credits and GPL/section 7 terms continue to apply. Live capture policy and port identity follow the local MiclessKaraoke implementation; no MiclessKaraoke files are modified.

## 1.6.1 changes, 2026-11-27

Rechecked the upstream button subsystem at `e64a4f6034460853e35b8789d2ab21aa12bfb8e1`; these upstream files match the previously pinned revision. Outer Voice adapts its API-specific scan permissions, application-context PendingIntent delivery, serialized scan/GATT thread and explicit-pairing wake behavior. Local diagnostics, tests and Live Speak overlay animation were added. Upstream headers and applicable GPL section 7 terms are retained.
