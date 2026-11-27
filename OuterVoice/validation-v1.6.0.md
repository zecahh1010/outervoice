# Outer Voice 1.6.0 validation

Device: local Android 13/API 33 emulator, 1024 × 600. This release reorganizes native configuration and navigation. Audio capture, DSP, player, BLE scanner/recovery and preference-store implementations retain the 1.5.2 code; this is not a new physical-car audio test.

`SettingsProbe` verifies the actual native activity and controls:

- Two primary tabs: speaking and Settings; the old top configuration/info shortcuts and home mode dropdown are removed.
- Settings is marked selected and all six groups are present with current-value summaries.
- A mode choice saves and returns to Settings without changing a smart-button assignment.
- Live input configuration and audio level controls return to the hub; independent microphone/output gains remain intact.
- Pairing/actions are reachable as a Settings child with a working return action.
- Speaker test, diagnostics and About are grouped; About/Back returns to the tools parent and then Settings.
- The same floating foreground service survives navigation into smart-button settings.
- A remotely owned recording survives a screen-mode change from Settings.
- Cancelling the panel editor returns to Settings with its enabled state retained; the speaking tab returns to home.

Reports and screenshots are under `qa/v160-native`. Test bindings shown as 模擬 are fixtures, not evidence of physical pairing. Settings/native checks restore their original preferences and do not retain a test sound/device. The existing independent-action/rename/gain suite is also run as a regression check.

Final results: all 24 SettingsProbe assertions and all 22 existing VoiceProbe assertions passed on the signed 1.6.0 APK. No application crash appeared in the AndroidRuntime error log. A source comparison against the 1.5.2 source archive confirmed MainActivity.java is the only changed production Java file; the audio, BLE and persistent-store classes are byte-for-byte identical. Screenshots use test gain values and a simulated device; real installations retain their existing preferences.

The normal WAV/import, button-protocol/gain and recovery/capture-policy tests run during build. Signature and alignment are verified, and the APK updates the installed app using the retained certificate. Native screenshots are inspected in one batch, with only a bounded confirmation after correcting header spacing.

The vehicle is still required to confirm OEM sleep recovery, actual microphone/exterior-speaker output, acoustic quality and latency; see `validation-v1.5.1.md` for those boundaries.
