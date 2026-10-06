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
