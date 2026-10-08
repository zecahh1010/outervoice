# Approved on 2026-10-08; implemented in v1.3.0

# Proposed changes - awaiting user approval
Generated with built-in imagegen, ui-mockup prompt: Three dark navy and teal landscape Outer Voice screens showing floating configuration, home with touch/ripple settings icon and Exit control, and broad WAV import. Show reorderable Live Speak with fixed mic, 50% larger circle and proportional icon (88 -> 132px), global button size, equal edge-to-edge spacing default12px, and preview. Keep saved color/icon/order controls.

Implementation specification takes precedence over illustrative details: retain current global64-144px range (mockup labels48-160 are illustrative); Live Speak diameter=global diameter*(1+enlargement/100); all glyphs scale with their circle. Spacing0-40px default12, measured between circle edges. Exit stops audio, closes floating panel, disables it, then closes activity. WAV import removes fixed PCM16/sample-rate/channel and20MB gates, attempts decoding/conversion, reports corrupt/unsupported inputs honestly. No app code changed for this preview.

Verified current import restrictions: PCM16 mono/stereo,8-192kHz,20MB. Device-specific BUS12 capabilities cannot be established from repository alone. Proposed import flexibility requires decoding/conversion rather than feeding arbitrary WAV bytes to current PCM16 AudioTrack.

## Minimize and reopen mockup
Generated with built-in imagegen: approval board with three map-backed headunit states, expanded panel with minus and close controls, minimized draggable60px teal microphone shortcut, and restored panel retaining buttons/order/position. Fixed white mic; Live Speak132px vs88px saved buttons, proportional icons and12px gaps. Minimize retains enable state; Close disables. Bubble tap only restores, does not record. Preview only, no implementation changes.

## Item 8 - broader audio import and test playback (pending approval)
Rename Import WAV to Import Sound. Allow WAV, MP3 and other audio selections, including M4A/AAC, FLAC, OGG and AMR, attempting decoding based on file content and available decoders rather than a PCM16-only extension gate. Decode/convert successful imports to the app playback format for BUS12 routing. Do not claim every codec is supported; report corrupt or undecodable files clearly. After successful import, enable Play next to imported filename; while playing show Stop. Preview must use the same BUS12 outer-speaker route as saved-sound playback before Add Sound saves it. Keep Record microphone and Add Sound. These are proposed changes; no app source modified.
Android officially supports MP3 decoding (mono/stereo 8-320kbps CBR/VBR): https://developer.android.com/media/platform/supported-formats
