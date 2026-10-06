# Build and validation

Deliverable: OuterVoice-1.1.0.apk, version 1.1.0 (2), package com.zecadev.outervoice, Android 9/API 28 minimum. APK size: 738441 bytes.

SHA-256: `f3ee54d1c0ead97d5f6a697bcdfa30516fe58c33033ef023af23e644baf70d60`.

The final APK passed apksigner verification with a v3 signature and zipalign verification. Signing certificate SHA-256: `664b72c9d4c6075814ffa1c9b72a2d0c78132238fbed9d6b2265b78a7614a17e`.

Pure Java WAV parser tests passed for valid mono/stereo PCM16 and padded ancillary chunks. Tests rejected float, 24-bit, unsupported channel counts, incorrect WAVE signatures, truncated or overflowing chunks, unaligned PCM and invalid byte rates.

Additional pure Java tests passed for recorded WAV headers and metadata, silent PCM, WAV normalization's ×16 ceiling, full-scale signed samples, clipping bounds and invalid PCM byte lengths.

Android 9 checks retained from v1.0.0 include actual Android document-picker import, scrolling, persistence, and microphone permission handling. The final v1.1.0 APK was installed over the earlier version; saved sounds remained. Both Record & Play and Add Sound recording created WAVs verified by reading actual app files: 44100 Hz, one channel, 16-bit samples and nonzero frame counts. This verifies format and capture operation; the emulator microphone data is not proof of real speech quality. Temporary Play rejected missing BUS12, and changing modes removed its cache WAV. Add recording was named and saved. Remove was tested with Cancel and then confirmation; both the saved index and WAV were removed.

With DocumentsUI and MusicPicker disabled, Import WAV automatically opened the local browser after runtime storage permission. The browser rejected invalid.wav and imported welcome.wav. The disabled test-emulator packages were restored. The volume panel, default 100% live level, test-tone missing-route rejection, diagnostic report/copy action and updated Credits version 1.1.0 (2) passed UI checks. No AndroidRuntime fatal crash was recorded. UIAutomator intermittently returned null accessibility roots; those checks were retried/resumed and successful screens were captured. This was a test-service issue, not an observed app crash. Evidence and check scripts are in `qa/`.

Lifecycle checks also passed: leaving the foreground removed the temporary clip and disabled its Play button; Back from an active Add recording discarded that capture; removal stayed effective after restart while earlier saved sounds remained. The media slider changed the emulator's system volume from 5/15 to 15/15 and then restored 5/15, with readback reflected in the panel.

The public wording cleanup was rebuilt with the same version and signing certificate. Focused checks passed for APK update installation, retained sounds, neutral audio-panel wording, media-volume readback and the requested Credits. The APK hash above and affected audio-panel evidence were refreshed. No audio-routing behaviour changed in this cleanup.

The final native screens were compared against the approved mockups at 1024 × 600; see design-qa.md. Test WAVs were imported into the emulator and are not bundled in the APK. A fresh installation starts with an empty sound list.

Hardware validation required: microphone selection and capture, exposure of BUS12_OUTER_NOTIFY to this app, audible outer-speaker output for live speech and WAVs, latency, acoustic feedback, route-loss behavior and active audio-focus interactions. The emulator cannot establish these results. The code requests only BUS12_OUTER_NOTIFY and rejects an unavailable route rather than intentionally falling back to another speaker.

On the head unit, install over v1.0.0, open the header speaker icon, check media volume/live level, and try Test speaker, a WAV and live speech. If still silent, use Diagnostics / Copy report to capture actual device routing and levels. The system media slider is not a verified vendor BUS12 amplifier control. Retain the local signing key and password privately for future app updates. See routing-investigation.md for output settings and the remaining uncertainty.

## v1.1.1 — hold and release speaking

- Removed the Record & Play mode and separate Play control. Live Speaking records while held, finalizes its WAV on release and immediately requests playback using the existing BUS12 WAV player.
- Renamed the Add Sound form submission button to Add Sound. Removed the obsolete live passthrough gain slider.
- Pure Java WAV validation/writer/normalization tests pass. Android Java/DEX/resource compilation, signing verification and ZIP alignment pass, using the retained signing certificate.
- Android 9 emulator check-v111.py verifies the single Live Speaking control, a non-empty 44.1 kHz mono PCM16 WAV after a 2.2-second hold, automatic playback request (BUS12 absent in emulator), Add Sound form recording and label, and temporary-file removal on backgrounding.
- Audible outer-speaker playback remains a vehicle validation step. Historical v1.1.0 emulator scripts and evidence describe the former UI.

## v1.2.0 — floating sound panel (2026-10-06)

Implemented the approved revised first mockup as a native Android settings Activity and a separate foreground overlay Service. The home header's layered-squares icon opens Floating Buttons.

### Passed checks

- Java WAV validation, writer and normalization tests; native resource, Java and DEX compilation; APK signature verification and alignment. Same signer certificate as previous updates: SHA-256 `664b72c9d4c6075814ffa1c9b72a2d0c78132238fbed9d6b2265b78a7614a17e`.
- Upgrade retained all five existing emulator sound fixtures. Default size is 88 physical pixels. Seven named light colors include Light Red; three icon choices represent Angry, Thank You and Warmly Remind. Final 1024 × 600 settings screenshot inspected; icon labels and color choices are visible.
- Real Android permission flow: app showed the Display over other apps explanation, opened the package's Android permission screen, and showed the overlay after Allow display over other apps was enabled and the user returned.
- System `dumpsys window` reports `ty=APPLICATION_OVERLAY`. Actual screenshots show the panel above the separate Android Settings app while Outer Voice's Activity is backgrounded. Foreground service notification is present.
- Color, icon, selection and order persistence. Test order: Welcome, Thank you, Please wait; Thank you configured Light Red + Angry. Only selected sounds are included after the fixed-left Live Speak button.
- Size slider changed actual circle diameter to 144 px (panel measured 712 × 212 px with three selected sounds). Reset restored 88 px (panel 488 × 156 px). Preview is scaled; the overlay uses physical pixels.
- Dragging the dedicated handle moved the system window. Saved coordinates were restored after close/re-enable. Close persisted disabled state, removed the overlay and stopped its Service. The Enable floating panel checkbox also removed it after Save Settings.
- Floating Live Speak captured microphone PCM during a hold, finalized a valid 44.1 kHz mono PCM16 WAV on release, and immediately requested playback via the existing BUS12 WAV player. The emulator lacks BUS12, so the unavailable-route result and temporary-clip cleanup were verified. Saved-sound tap requests playback through the same route.
- Cancelling a held gesture discarded the unfinished WAV and suppressed playback; a normal close click then disabled the panel. Main-screen hold/release and Add Sound recording/cancel regression checks also ran on v1.2.0.
- No AndroidRuntime application crash in the final suite. UIAutomator's intermittent null-root errors were retried; they are automation failures rather than app crashes.

### Evidence and limits

`qa/check-v120.py` contains the initial permission phases and repeatable final suite. `qa/check-v111.py 1.2.0` runs the existing main-screen regression on the new APK. Screenshots include `v120-final-settings.png`, `v120-android-overlay-permission.png`, `v120-panel-144-over-settings.png`, `v120-panel-moved.png`, `v120-panel-live-release.png`, and `v120-final-panel-over-settings.png`.

These checks use the local Android 9 emulator, not the vehicle. Audible BUS12 output, vendor overlay policy and newer-Android foreground microphone behavior need device validation. Android can hide overlays over protected system screens. An enabled panel is restored when Outer Voice is opened, not automatically at device boot.

## v1.2.1 — safer saved-sound editing (2026-10-06)

- Confirmed CarPiano's architecture is preserved: MainActivity opens the floating controls; FloatingPanelService creates a genuine TYPE_APPLICATION_OVERLAY on the default head-unit display. Outer Voice declares SYSTEM_ALERT_WINDOW and checks Display over other apps authorization. MainActivity itself is a normal application window, as in CarPiano.
- Removed home-list trash icons and their deletion handlers. Added Edit List with Rename, up/down ordering controls and text Delete buttons. Deletion requires confirmation and remains staged until Save Changes. Cancel and Back discard the draft. Edit operations preserve the scroll position.
- Pure Java WAV tests, native compilation, signature and alignment verification pass with the existing certificate. v1.2.1 (versionCode 5) retains the saved sounds across upgrade.
- Android 9 emulator regression `qa/check-v121.py` passed: no home trash controls; draft rename/reorder/deletion plus Cancel retains the original names/order/WAV; saved rename/order survive process restart; sound UUID and SHA-256 of recorded audio remain unchanged after rename.
- A selected disposable sound's rename retained its floating selection, color and icon. Confirmed deletion plus Save Changes removed its WAV and removed it from the overlay. All five original emulator fixtures were retained.
- Final screenshot and system-window inspection again confirmed the panel above Android Settings in v1.2.1. No AndroidRuntime application crash was reported. Intermittent UIAutomator null-root responses were retried.
- Visual evidence: `qa/v121-home.png`, `qa/v121-editor.png`, `qa/v121-renamed-overlay.png` and `qa/v121-after-delete-overlay.png`. Audible BUS12 output and vehicle-specific display policy remain hardware checks.

## v1.2.2 — reorderable Live Speak (2026-10-06)

- Pure Java WAV tests, native compilation, APK signature and alignment checks pass, retaining the existing update certificate (versionCode 6).
- Android 9 emulator check-v122.py passes legacy preference migration, default 50% enlargement, locked microphone icon, ordering among saved sounds, and preserved saved-sound settings.
- Empty and out-of-range percentage values block saving. Actual overlay glyph heights are 30px at 0% and 46px at 50%; 100% is approximately twice the baseline. Circle geometry and saved-sound pixels remain unchanged.
- 100% and position persist after restart. Final configuration restores 50% with Live Speak in third position. Holding/releasing it above Android Settings finalizes a 44.1kHz mono PCM16 WAV, requests BUS12 playback, and cleans the temporary clip. No AndroidRuntime crash was reported.
- Audible BUS12 output still requires the vehicle; emulator verifies recording and routing requests only.
