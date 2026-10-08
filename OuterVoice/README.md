# Outer Voice

Native Android app for a 1024 × 600 head unit. Version 1.3.0 (build 7), package `com.zecadev.outervoice`, Android 9/API 28 or later. Signed with the existing update certificate.

## Operation

- Live Speaking: hold the microphone button to record; release to finalize the WAV and immediately play it through BUS12_OUTER_NOTIFY. Record & Play and its separate Play button have been removed.
- Recording uses 44.1 kHz mono PCM16 with a 180-second maximum. The temporary WAV is replaced by the next recording and deleted when leaving the app. It is not added to Saved sounds.
- Cancelled gestures, navigation, backgrounding and audio focus loss discard unfinished recordings.
- Add Sound: use Import Sound to select WAV, MP3 or other audio, or record the microphone. Successful imports enable Play/Stop to test the outer speaker before Add Sound saves the clip. Import examines file contents rather than enforcing an extension list. PCM8/16/24/32 and float32/64 WAVs are converted directly; other formats use the Android device's decoders. Unreadable audio is reported. There is no fixed 20 MB input limit; free storage and the WAV container capacity still apply. Imports are streamed into 44.1 kHz mono PCM16 WAVs for the existing BUS12 player. Codec priming/padding is retained rather than applying untrusted gapless trim metadata. Saved sounds persist across restarts.
- Edit List: rename sounds, reorder the home list with up/down controls, or stage a confirmed deletion. Save Changes applies the edits and deletes removed WAV files. Cancel or Back discards the entire draft. The home list has no trash buttons. Renaming retains sound identity and floating-panel selection/color/icon; deleted sounds disappear from the panel. The panel's separately configured order is retained.
- Audio settings provide media volume, speaker test and diagnostics. WAV playback uses the existing peak normalization, capped at ×16. BUS12 playback remains unchanged from v1.1.0; there is no default speaker fallback.

## Floating panel

Tap the touch/ripple icon in the home header to open **Floating Buttons**.

1. Check **Enable floating panel**. Select the saved sounds to include; the up/down arrows arrange Live Speak and the selected sounds from left to right.
2. Tap a sound's name to customize it. Choose Sky, Mint, Yellow, Peach, Light Red, Lavender or Aqua and an Angry, Thank You or Warmly Remind icon. Live Speak always stays included with the fixed app blue/teal microphone. Select Live Speak to enter its button enlargement percentage, from 0 to 100%, default 50%. Its diameter is base size × (1 + percentage / 100): 88 px becomes 132 px at 50%. Every icon scales proportionally with its own circle.
3. Adjust **Button size** from 64 to 144 physical pixels in 4-pixel steps; **Reset** returns to 88 px. The configuration preview is scaled to fit the screen; actual floating circles use the chosen pixel diameter.
   Adjust **Button spacing** from 0 to 40 px, default 12 px, measured between adjacent circle edges.
4. Press **Save Settings**. Grant microphone access if prompted. When Android opens **Display over other apps**, enable **Allow display over other apps** for Outer Voice and return. The app checks the permission before showing the panel.

The panel follows CarPiano's floating-panel structure: MainActivity opens the controls, while FloatingPanelService uses the default display's WindowManager to create a `TYPE_APPLICATION_OVERLAY` window maintained by a foreground service with an ongoing notification. It uses the same default head-unit screen as CarPiano, rather than selecting a separate display. It stays above ordinary apps when Outer Voice is backgrounded. The full MainActivity remains a normal app screen, as in CarPiano. Android may hide overlays on protected system screens. On newer Android versions, microphone and foreground-service restrictions can also depend on firmware.

Hold the floating Live Speak microphone to record; release it to finalize a 44.1 kHz mono PCM16 WAV and request immediate BUS12 playback. The temporary clip is deleted after playback/error or cancellation. Tap a saved-sound circle to play it; choosing another sound replaces the current playback. Activity and floating audio share ownership to prevent simultaneous playback/recording sessions.

Drag only the dedicated handle to move the panel. Position, selected sounds, order, colors, icons, size and spacing persist. A long sound strip scrolls horizontally with Live Speak in its chosen position; drag, minimize and close controls remain outside the scrolling strip. Press **−** to minimize to a draggable 60 px microphone bubble. Tap the bubble to reopen the panel at its original full-panel position; tapping it never records. Bubble position is saved separately. Panel and bubble use absolute screen coordinates across fullscreen and status-bar screens. Minimized state survives a service restart. Saving settings reopens the full panel.

Press **×**, **Close panel** in the notification, or save settings with **Enable floating panel** unchecked to stop the overlay. Closing sets it to disabled. The home header's **Exit** power icon stops audio, closes and disables the overlay, and removes the app task. Opening settings or Add Sound temporarily hides the panel; Cancel restores the saved configuration. If Android stops the process, opening Outer Voice again restores an enabled panel after checking permissions; it does not auto-start after boot.

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

Build output: `dist/OuterVoice-1.3.0.apk`. The script verifies its signature and alignment and writes `dist/SHA256.txt`. Build on the normal Windows host if a restricted sandbox blocks Java SDK access.

The APK is signed with a locally generated Zeca/Outer Voice key, stored outside the app source in `.build-tools/outervoice-signing.jks`, with its password in `.build-tools/outervoice-signing-password.txt`. Preserve both privately for signed updates; neither is included in the APK, source archive or Git repository.

To rebuild elsewhere, supply Java 17, Android API 36 and Build Tools 36, adjust the tool paths in build.ps1, and use the retained signing key for updates. Version values are in `app/src/main/AndroidManifest.xml`.

## Installation and vehicle check

Copy the APK to the head unit and open it with its package installer. Grant microphone access when prompted, then hold the microphone to speak and release to play. BUS12 must be exposed to this app by the vehicle firmware. Successful package installation does not establish microphone routing, amplifier output or audible sound.

The emulator can check native screens, file import/persistence and rejection when BUS12 is absent. The actual vehicle is required to verify live capture, external sound, route-loss behavior, audio focus interactions, latency and acoustic feedback. See `validation.md` and `design-qa.md` for completed checks and remaining limits.

## Sources and licenses

The MIT license notice is bundled in `app/src/main/assets/LICENSE.txt`. The Credits page displays the app version, developer, tester, contributors and license.

Standard Google Material Icons are bundled under their Apache 2.0 license in `app/src/main/assets/MaterialIcons-LICENSE.txt`. The approved generated launcher artwork is `app-icon-source.png`, exported to Android launcher densities.

Inter typography is bundled under the SIL Open Font License in `app/src/main/assets/Inter-OFL.txt`.
