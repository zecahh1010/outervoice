# Outer-speaker output settings

The user's v1.0.0 head-unit test captured microphone input but produced no audible outer-speaker sound. The exact cause has not been established without the head unit's route/volume report. Selecting BUS12 alone does not prove audible amplifier output.

## v1.1.0 output configuration

| Setting | Behaviour |
| --- | --- |
| Route | BUS12_OUTER_NOTIFY, with case-insensitive address substring matching; no BUS11/default-speaker fallback |
| Audio attributes | USAGE_MEDIA / CONTENT_TYPE_MUSIC for live and WAV AudioTracks |
| Device selection | AudioTrack.setPreferredDevice, muted-silence priming and actual getRoutedDevice verification before voice/WAV samples |
| WAV buffer | At least max(sample rate, 8192) bytes, also respecting Android minimum buffer |
| WAV level | Peak normalization toward -0.5 dBFS, capped at ×16; silent files stay silent and source files remain unchanged |
| Live level | Default 100% instead of v1.0.0's 25%; adjustable 0–100% |
| System volume | Explicit media slider; no automatic volume raise; mute/fixed state shown in diagnostics |

The address matching can help firmware addresses with extra text. Level changes can help weak microphone/WAV audio, but cannot repair a muted amplifier or firmware policy that does not conduct audio to the outer speaker. A separate vendor BUS12 amplifier-volume control has not been verified.

Use the header speaker icon to open Audio settings. Check system media volume and live microphone level, then Test speaker. Diagnostics includes enumerated device addresses, selected device, preferred-route acceptance, actual routed device, track stream, written live frames, microphone RMS and failures. Copy report provides text for diagnosis. Actual route verification is performed while the AudioTrack is playing; see https://developer.android.com/reference/android/media/AudioRouting.

If the test tone is audible but live speech is not, microphone RMS and live frames help distinguish weak/zero captured samples from playback failure. If neither tone nor WAV is audible while actual routing reports BUS12, investigate firmware media mute/volume and amplifier activation. BUS11 is not used by this app.

File selection tries Android OPEN_DOCUMENT and GET_CONTENT before falling back to a local folder/WAV browser. The fallback was tested with system file/audio pickers disabled on Android 9. Vendor USB mount permissions and newer scoped-storage rules can still restrict direct browsing; recording directly in Add Sound provides another source.
