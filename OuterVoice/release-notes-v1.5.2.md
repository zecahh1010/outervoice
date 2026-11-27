# Outer Voice 1.5.2

Build 13. This update corrects the Live microphone terminology for Outer Voice's purpose: external speaking, recording and playback.

- **原始／相容**
- **低延遲傳聲** (Android 10+)
- **標準麥克風**

Karaoke labels and the karaoke-app reference are removed from the product's settings and status text. The reference remains in developer documentation as provenance for the capture/routing implementation. No karaoke effects are added.

The existing `compat`, `performance` and `standard` settings keys, microphone source mapping, routing, gains and the five v1.5.1 car-test fixes are preserved. This is a wording update; the physical-car verification boundaries in `validation-v1.5.1.md` still apply. Existing saved sounds and button assignments are retained on update.

The normal build/audio/protocol/recovery checks and signature/alignment verification run for the new APK. Native rendering and physical-car audio are not retested for this wording-only patch; see the v1.5.1 native evidence for the unchanged audio/BLE behavior.
