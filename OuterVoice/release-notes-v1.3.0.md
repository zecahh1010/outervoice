Outer Voice v1.3.0 implements the eight approved floating-panel and sound-import changes.

- Live Speak enlargement now changes the whole button diameter, with its microphone scaling proportionally. Default +50% makes an 88 px base button 132 px. Live Speak remains reorderable with its fixed blue microphone.
- Every floating sound icon scales proportionally with the global button size (64–144 px base, 88 px default).
- The home Exit power button stops audio, disables the floating panel, and closes the app task.
- WAV imports accept additional PCM and floating-point formats, sample rates and channel counts through conversion. The old PCM16-only and 20 MB input gates are removed.
- Configure spacing between button edges from 0 to 40 px, default 12 px.
- A touch/ripple icon opens floating-panel settings.
- Minimize collapses the panel into a draggable 60 px microphone bubble. Tap it to reopen the original full-panel layout; the bubble never records. Close disables the overlay.
- Import Sound accepts WAV, MP3 and other decodable audio files. Successful imports enable Play/Stop to test the BUS12 outer speaker before saving.

Imports are streamed into 44.1 kHz mono PCM16 WAVs for the existing BUS12_OUTER_NOTIFY player. Unsupported or damaged files report an error; codec availability depends on the device. Free storage and WAV container capacity still apply. Audible BUS12 output requires the head unit.

The APK uses the existing update certificate and retains saved sounds. Release assets include the signed APK, source archive, and APK SHA256 checksum. See validation.md for completed checks and hardware limits.

Validation: Java WAV/conversion tests and native build/signature/alignment checks passed. Android9 emulator checks passed whole-button enlargement, proportional icons, spacing, minimize/drag/reopen, Exit and WAV/MP3/AAC/FLAC/OGG imports, including a WAV above20MB and unknown-extension audio. Original saved sounds were preserved.
