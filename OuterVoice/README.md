# Outer Voice

A native Android APK for a 1024 × 600 vehicle head-unit screen. Version 1.1.0 (build 2), package `com.zecadev.outervoice`, Android 9/API 28 or later. Install this signed update over v1.0.0 to retain saved sounds.

Developer: Zeca. Tester credit: SL. Contributors: Chris, j.Lun. Credits read the actual package version at runtime. The tester credit is attribution supplied by the user, not a claim that SL has already tested this build.

## Use

- Tap the microphone circle to start speaking; tap it again to stop. The label changes between Start Speaking and Stop Speaking.
- Tap a saved sound row to play its WAV. Tap the same row while playing to stop.
- Switch the left panel between Live Speaking and Record & Play. In Record & Play, tap the microphone to record, tap again to finish, then Play. This clip is temporary: another recording, mode change or leaving the app discards it. It is never added to Saved sounds.
- Tap Add Sound to open the separate naming screen. Import a WAV or Record microphone, stop recording, enter the sound name, and Save. Saved sounds are stored privately and persist after restart.
- Tap a sound's trash icon and confirm Remove to delete it.
- Tap the speaker icon in the header for Audio settings: system media volume, live microphone level, Test speaker and Diagnostics. Diagnostics show output-device addresses, requested/actual routing, input levels and errors; Copy report places the text on the clipboard.
- Tap the information icon for the version and credits. The License row opens the included source license notice.

Live speech and saved sounds both request `BUS12_OUTER_NOTIFY` using media usage. There is no BUS11 route, route picker, automatic default-speaker fallback, or network permission. Live speaking disables WAV playback. Switching screens, losing audio focus, or leaving the Activity stops audio.

Live input is the Android-selected microphone, 44.1 kHz mono PCM16, processed in 10 ms chunks with default unity gain (100%), adjustable down to 0% in Audio settings. Actual latency and acoustic feedback depend on the vehicle; this is not a 10 ms end-to-end latency claim. Recording also uses 44.1 kHz mono PCM16 and finishes a valid WAV on Stop, with a 180-second limit.

Imports accept PCM16 WAV, mono or stereo, 8–192 kHz, up to 20 MB. Invalid/truncated files are rejected. WAV playback streams from disk and applies peak normalization toward -0.5 dBFS, capped at ×16; source files remain unchanged. The moderate test tone bypasses normalization.

Import first tries OPEN_DOCUMENT, then GET_CONTENT, then the built-in local WAV browser. Browse files on this device opens that browser directly. It displays readable internal and removable storage folders, including Download. The browser requests shared-audio/storage read access when needed; the system picker does not. Record microphone remains available without a file picker. Newer Android scoped-storage rules or vendor mount permissions can still restrict direct browsing.

The media slider changes Android STREAM_MUSIC, not a separately verified BUS12 amplifier control. Firmware may fix or ignore that volume. The app does not automatically raise system volume; it reports mute/fixed state. See routing-investigation.md for the output settings and remaining hardware checks.

## Build

The local build uses portable Eclipse Temurin Java 17, official Android platform API 36, Android Build Tools 36, `aapt2`, `javac`, D8, zipalign and apksigner. It does not require Android Studio or Gradle. Tool downloads reside in the workspace's ignored `.build-tools` directory.

From this project's directory:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

Run only pure Java WAV validation tests:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -TestOnly
```

Build output: `dist/OuterVoice-1.1.0.apk`. The script verifies its signature and alignment and writes `dist/SHA256.txt`. Build on the normal Windows host if a restricted sandbox blocks Java SDK access.

The APK is signed with a locally generated Zeca/Outer Voice key, stored outside the app source in `.build-tools/outervoice-signing.jks`, with its password in `.build-tools/outervoice-signing-password.txt`. Preserve both privately for signed updates; neither is included in the APK, source archive or Git repository.

To rebuild elsewhere, supply Java 17, Android API 36 and Build Tools 36, adjust the tool paths in build.ps1, and use the retained signing key for updates. Version values are in `app/src/main/AndroidManifest.xml`.

## Installation and vehicle check

Copy the APK to the head unit and open it with its package installer. Grant microphone access when prompted, then tap the microphone again to start. BUS12 must be exposed to this app by the vehicle firmware. Successful package installation does not establish microphone routing, amplifier output or audible sound.

The emulator can check native screens, file import/persistence and rejection when BUS12 is absent. The actual vehicle is required to verify live capture, external sound, route-loss behavior, audio focus interactions, latency and acoustic feedback. See `validation.md` and `design-qa.md` for completed checks and remaining limits.

## Sources and licenses

The MIT license notice is bundled in `app/src/main/assets/LICENSE.txt`. The Credits page displays the app version, developer, tester, contributors and license.

Standard Google Material Icons are bundled under their Apache 2.0 license in `app/src/main/assets/MaterialIcons-LICENSE.txt`. The approved generated launcher artwork is `app-icon-source.png`, exported to Android launcher densities.

Inter typography is bundled under the SIL Open Font License in `app/src/main/assets/Inter-OFL.txt`.
