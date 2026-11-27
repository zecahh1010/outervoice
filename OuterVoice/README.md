# Outer Voice

Native Android app for a 1024 × 600 head unit. Version 1.6.2 (build 16), package `com.zecadev.outervoice`, Android 9/API 28 or later. Signed with the existing update certificate.

See [1.5.1 car-test fixes](release-notes-v1.5.1.md) for independent 靈動貼 actions, BLE pairing and 0..+24 dB exterior digital gain. See [validation](validation-v1.5.1.md) for emulator evidence and car-only checks.

Version 1.5.1 keeps the overlay on the 靈動貼 tab, repairs BLE sleep/wake registration, removes repeated status Toasts and animates the multi-sound minimized bubble. Live Speak follows MiclessKaraoke capture/rate/buffer/focus behavior; select its microphone in 設定 → Live 麥克風.

Version 1.5.2 uses speaking labels: 原始／相容, 低延遲傳聲 and 標準麥克風. This is an external-speaking app; MiclessKaraoke is a technical capture/routing reference, not a karaoke feature. See [1.5.2 notes](release-notes-v1.5.2.md).

Version 1.6.2 restores the 1.5.0 direct callback scan/main-looper pairing path after real-car discovery failures in 1.6.1. Allow location and Bluetooth permissions, enable location services, then long-press the button while pairing. Later recovery, diagnostics and Live animation remain. See [1.6.2 notes](release-notes-v1.6.2.md) and [validation](validation-v1.6.2.md).

## Settings navigation

Open **設定** for **傳聲方式**, **Live 麥克風**, **音量及增益**, **懸浮面板**, **靈動貼** or **測試及關於**. The list displays current settings and the smart-button page has a return-to-Settings control. The top-level speaking page keeps speaking and saved-sound operations. Its mode label is read-only; screen/floating mode changes remain independent of button assignments. See [1.6.0 notes](release-notes-v1.6.0.md) and [native validation](validation-v1.6.0.md).

## Operation

- Speaking mode: choose hold/release/play (default), tap/record/tap/stop/play, or Live Speak (tap to start/stop realtime microphone output). Screen and floating controls use this choice. Recording and Live Speak show a red floating microphone with motion. Physical smart button actions have independent settings; neither selection forces the other to change. All audio uses BUS12_OUTER_NOTIFY.
- Recording selects a microphone format supported by Android, trying the device default and available sample rates, stereo/mono and float/PCM16 capture. The captured sample rate and channel count are retained; float samples are converted to PCM16 WAV storage for the existing player. There is no fixed 44.1 kHz mono capture requirement. The 180-second maximum remains. Temporary Activity-owned recordings are discarded when leaving the app and are not added to Saved sounds. Background overlay and remote sessions have separate foreground-service lifetimes.
- Cancelled gestures, navigation/backgrounding discard Activity-owned recordings. Overlay and smart-button recording continue under their foreground services when the Activity is backgrounded. Audio focus loss cancels the shared session. Toggle recording and Live Speak can be stopped from a visible microphone control regardless of which control started them.
- Add Sound: use Import Sound to select WAV, MP3 or other audio, or record the microphone. Successful imports enable Play/Stop to test the outer speaker before Add Sound saves the clip. Import examines file contents rather than enforcing an extension list. Import Sound directly opens Outer Voice's built-in storage browser using the v1.2.2 folder-navigation approach. Grant file access if prompted, choose Internal storage or an accessible mounted storage location, then browse folders and select a file. The browser lists every non-hidden file without an extension restriction and imports based on its contents. It does not depend on an installed Android document picker or file manager. PCM8/16/24/32 and float32/64 WAVs are converted directly; other formats use the Android device's decoders. Unreadable audio is reported. There is no fixed 20 MB input limit; free storage and the WAV container capacity still apply. Imports are streamed into 44.1 kHz mono PCM16 WAVs for the existing BUS12 player. Codec priming/padding is retained rather than applying untrusted gapless trim metadata. Saved sounds persist across restarts.
- Edit List: rename sounds, reorder the home list with up/down controls, or stage a confirmed deletion. Save Changes applies the edits and deletes removed WAV files. Cancel or Back discards the entire draft. The home list has no trash buttons. Renaming retains sound identity and floating-panel selection/color/icon; deleted sounds disappear from the panel. The panel's separately configured order is retained.
- **設定 → 音量及增益** provides media volume and exterior digital gain. **設定 → 測試及關於** contains the speaker test, diagnostics and credits. WAV playback uses the existing peak normalization, capped at ×16. BUS12 playback remains unchanged from v1.1.0; there is no default speaker fallback.

## Floating panel

Open **設定 → 懸浮面板** to edit the floating panel.

1. Check **Enable floating panel**. Select the saved sounds to include; the up/down arrows arrange Live Speak and the selected sounds from left to right.
2. In **Buttons & Order**, tap a name to customize its color. Ten colors run from warm to cool: Red, Pink, Peach, Yellow, Lime, Mint, Aqua, Sky, Indigo, Lavender. Live Speak also has its default **App Blue** option above the palette. Its microphone icon remains fixed. Saved sounds offer Angry, Thank You, Warmly Remind, Funny, Extreme Angry, Happy, Friendly, Sorry, Surprised, Calm, Urgent and Celebration icons. The compact list shows six entries before scrolling.
3. In **Size & Minimize**, adjust **Button size** from 64 to 200 physical pixels in 4-pixel steps; **Reset size** returns to 88 px. Adjust **Button spacing** from 0 to 40 px, default 12 px, measured between circle edges. **Enlarge Live Speak button** uses a 0–100% slider, default 50%. Its diameter is base size × (1 + percentage / 100): 88 px becomes 132 px at 50%. Every icon scales with its circle. The minimized button has a separate 64–200 px size, default 88 px, with a preview. **Auto minimize** is optional and off initially; its timeout starts at 30 seconds and accepts 1–3600 seconds. Panel use resets the inactivity timer, which waits during recording, playback, dragging or a close confirmation. Previews scale to fit; actual overlay circles use physical pixel sizes.
4. Press **Save Settings**. Grant microphone access if prompted. When Android opens **Display over other apps**, enable **Allow display over other apps** for Outer Voice and return. The app checks the permission before showing the panel.

The panel follows CarPiano's floating-panel structure: MainActivity opens the controls, while FloatingPanelService uses the default display's WindowManager to create a `TYPE_APPLICATION_OVERLAY` window maintained by a foreground service with an ongoing notification. It uses the same default head-unit screen as CarPiano, rather than selecting a separate display. It stays above ordinary apps when Outer Voice is backgrounded. The full MainActivity remains a normal app screen, as in CarPiano. Android may hide overlays on protected system screens. On newer Android versions, microphone and foreground-service restrictions can also depend on firmware.

The floating microphone uses the saved screen mode: hold/release/play, tap/record/tap/stop/play, or tap/start/stop Live Speak. Recorded WAVs retain the captured rate/channel count before BUS12 playback. A strong, smooth pulse and inset ring indicate recording. The temporary clip is deleted after playback/error or cancellation. Tap a saved-sound circle to play it; choosing another sound replaces current playback. Activity and floating audio share ownership to prevent simultaneous sessions.

The toolbar places **Move** on the left, **Minimize** in the middle and **Close** on the right. The middle control grows with panel width; a narrow single-button panel uses a minus icon. Drag the dedicated handle to move the full panel. A long sound strip scrolls horizontally while the toolbar stays available. Each manual or automatic minimize centers the bubble on Live Speak's position immediately before collapse, including after reordering, moving or scrolling the panel. It stays within the screen edges. Minimize and reopen animate around Live Speak's center and honor Android's disabled-animation setting. With saved sounds selected, tap the draggable microphone bubble to reopen with Live Speak centered on the bubble, including after dragging the bubble. The sound strip retains its horizontal scroll position. The full panel is kept on screen, so near an edge its position is clamped to the closest usable placement. With only Live Speak selected, the bubble controls speaking directly using the selected mode without expanding; dragging cancels overlay-owned recording and moves it. Panel/bubble positions and minimized state persist across restart; the next minimize anchors to Live Speak again. Saving settings reopens the full panel.

Press **×** and confirm **Close panel**, use **Close panel** in the notification, or save settings with **Enable floating panel** unchecked to stop the overlay. Closing disables it. The home header's **Exit** power icon stops audio, disables the overlay, and removes the app task. Opening the floating-panel editor or Add Sound temporarily hides the panel; the 靈動貼 tab keeps it visible. Cancel restores the saved configuration. Opening Outer Voice after a process stop restores an enabled panel after checking permissions; it does not auto-start after boot.

## Build

The local build uses portable Eclipse Temurin Java 17, official Android platform API 36, Android Build Tools 36, `aapt2`, `javac`, D8, zipalign and apksigner. It does not require Android Studio or Gradle. Tool downloads reside in the workspace's ignored `.build-tools` directory.

From this project's directory:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1
```

Run the pure Java WAV, smart-button protocol and digital-gain tests:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -TestOnly
```

Build output: `dist/OuterVoice-1.6.2.apk`. The script verifies its signature and alignment and writes `dist/SHA256.txt`. Build on the normal Windows host if a restricted sandbox blocks Java SDK access.

Run `./package-source.ps1` after building to package corresponding sources/tests and update the APK/source hashes.

The APK is signed with a locally generated Zeca/Outer Voice key, stored outside the app source in `.build-tools/outervoice-signing.jks`, with its password in `.build-tools/outervoice-signing-password.txt`. Preserve both privately for signed updates; neither is included in the APK, source archive or Git repository.

To rebuild elsewhere, supply Java 17, Android API 36 and Build Tools 36, adjust the tool paths in build.ps1, and use the retained signing key for updates. Version values are in `app/src/main/AndroidManifest.xml`.

## Installation and vehicle check

Copy the APK to the head unit and open it with its package installer. Grant microphone access when prompted, then hold the microphone to speak and release to play. BUS12 must be exposed to this app by the vehicle firmware. Successful package installation does not establish microphone routing, amplifier output or audible sound.

The emulator can check native screens, file import/persistence and rejection when BUS12 is absent. The actual vehicle is required to verify live capture, external sound, route-loss behavior, audio focus interactions, latency and acoustic feedback. See `validation.md` and `design-qa.md` for completed checks and remaining limits.

## Sources and licenses

The combined 1.5.0 release uses GPL-3.0 with upstream section 7 attribution terms. Original Outer Voice MIT notices are retained. See `NOTICE.md`, `GPL-3.0.txt`, `LICENSE-MIT.txt` and `UPSTREAM-NOTICE.md`. The Credits page displays attribution and all bundled license text. The corresponding source ZIP accompanies the APK.

Standard Google Material Icons are bundled under their Apache 2.0 license in `app/src/main/assets/MaterialIcons-LICENSE.txt`. The approved generated launcher artwork is `app-icon-source.png`, exported to Android launcher densities.

Inter typography is bundled under the SIL Open Font License in `app/src/main/assets/Inter-OFL.txt`.
