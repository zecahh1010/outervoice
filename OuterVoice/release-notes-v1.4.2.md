# Outer Voice v1.4.2

Import Sound now opens Outer Voice's built-in storage browser directly, using the folder-navigation approach available in v1.2.2. This avoids the Android picker launch failure reported on the head unit.

- Grant file access when prompted, select Internal storage or accessible mounted storage, browse folders and select a sound. Up returns to the parent folder; Cancel returns to Add Sound.
- Import no longer depends on Android DocumentsUI, a file-manager application or the v1.4.1 arbitrary-file compatibility filter.
- WAV, MP3 and other device-decodable sounds remain supported. The browser lists all non-hidden files; content validation reports unreadable audio. Successful imports still enable Play/Stop preview before saving.
- The v1.4.1 panel-position fixes and 64–200px size ranges remain unchanged. Existing saved sounds and settings are retained when updating.

Version 1.4.2 (build 10), Android 9/API 28 minimum, signed with the existing update certificate. Storage accessibility, audio codec support and audible BUS12 output still depend on the actual head unit. The browser can show only storage readable by the app.

Build/audio tests, signature/alignment and update installation pass. Android 9 emulator checks pass for direct browsing with or without DocumentsUI, permission denial/retry/grant, folder navigation, cancellation, WAV/MP3/content-based import, invalid audio rejection and pending-file cleanup. Saved-sound preferences remain unchanged.
