# Outer Voice v1.4.1

Fixes two reported v1.4.0 issues and increases the button-size range. Android 9/API 28 minimum; version code 9. Install over the existing app to preserve saved sounds and configuration.

- **Reopen at Live Speak:** both minimize and expand animate around the microphone. After moving the minimized bubble, reopening places Live Speak at that bubble's current center. Reordered buttons and horizontally scrolled sound strips retain their anchor. The full panel stays on screen; near an edge it uses the closest placement that keeps the controls accessible.
- **Larger buttons:** Button size and Minimized button size both support 64–200 physical pixels in 4px steps, retaining the 88px default. Live Speak enlargement still grows the entire button and icon proportionally. Settings previews scale to fit.
- **Android file picker:** Import Sound opens Android's document picker first. Head units that expose a file manager through GET_CONTENT can also use a compatible arbitrary-file picker. Images-only galleries are excluded. If no working picker is available, the app explains this and offers Android Settings or an explicit Browse files fallback instead of silently opening its own browser. Any extension remains selectable; undecodable audio still reports an error.

Signed with the retained update certificate. Build/audio tests, signature/alignment, update installation, native anchored reopen/reorder/drag/restart/scroll/edge checks, Android DocumentsUI, legacy picker URI import and missing-picker behavior pass on Android 9. The simulator has the same positioning correction. Saved-sound fixtures remain unchanged; test package and Android picker state are restored afterward.

Head-unit firmware must provide an enabled compatible file picker to use a native picker. Vehicle-specific picker availability and audible BUS12 output still need testing on the actual head unit.
