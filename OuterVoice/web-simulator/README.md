# Outer Voice UI simulator

Approved interactive reference for the v1.4.0 native Android changes at 1024 × 600. Open `index.html` directly, or serve this folder with `python -m http.server 8788 --bind 127.0.0.1` and visit http://127.0.0.1:8788.

Includes Home, Add Sound, Edit Saved Sounds, the two Floating Panel tabs, ordering, ten colors plus Live Speak's default App Blue, twelve icons, button size, Live Speak enlargement, spacing, minimized size, auto-minimize, draggable panel, minimize/reopen, direct speaking in a Live Speak-only bubble, and Exit. Draft edits apply on Save; Cancel discards them. Demo data and configuration persist in browser local storage. Reset demo restores the samples.

Hold-to-speak and sample playback show simulated states; no microphone is captured. Imported files can be previewed using browser audio decoding during the current session. Imported file bytes are not persisted across reloads. Android system overlay permissions, WAV conversion, and vehicle BUS12 routing cannot be exercised in a webpage. The Other app button demonstrates overlay placement within the simulated screen only. Audio settings are represented by a placeholder notification; native permission and system dialogs are not reproduced.

The app's locally bundled Inter and Material Icons fonts are included with their licenses in assets. No external resources or uploads are used.

Reviewed revisions: centered Home speaking controls, removal of the fixed-microphone explanation and duplicate Browse link, an enlargement slider, a clearly outlined Play button, and the Floating Panel name. A separate minimized-button slider ranges from 64–144px, default 88px. The proposed panel toolbar separates a large labeled Minimize button from Close and the sounds below; Close asks for confirmation. Each manual or automatic minimize centers the bubble on the actual Live Speak button immediately before collapse, including after reordering, dragging or strip scrolling, and clamps it within the simulated screen. Reopen restores the saved full-panel position. Both transitions respect reduced-motion preferences. These revisions were reviewed in the simulator before implementation in native v1.4.0.

The second review widens the Live Speaking column to 400px and orders the panel toolbar as Move, Minimize, Close. The empty right side of the preview now displays the minimized microphone button at a reduced preview scale, with its configured pixel size. Optional auto-minimize defaults to off with a 30-second idle interval; panel interactions restart the timer. The timer waits while recording, dragging, or playing imported audio. The time accepts whole seconds from 1 to 3600. Add Sound no longer displays a fixed recording-format specification.

Floating Panel settings are now grouped into Buttons & Order, Size & Spacing, and Minimize tabs. The enable checkbox and Save/Cancel controls are shared. Draft values survive tab changes. Ordering and customization use the main content area; the full panel preview is on Size & Spacing, while Minimize has a full-size minimized-button preview. The timeout field displays only the seconds unit, without a default caption.

Latest layout: Size & Spacing and Minimize are combined into a single Size & Minimize tab. Compact controls sit beside two scaled previews. All controls and previews fit the 1024 × 600 app canvas without scrolling; preview scaling also fits the selected sounds horizontally. Buttons & Order remains a separate tab with a scrollable sound list.

The live panel width follows its selected buttons, with a 252px minimum and a screen-bounded maximum. Move and Close keep 60px targets, while Minimize fills the remaining middle width. Below 360px panel width its label is hidden, leaving a labeled, accessible minimize icon. Live Speak-only panels center the sound and retain three distinct toolbar controls. Browser checks covered both six selected buttons and Live Speak alone, including minimize/reopen behavior.

The sound icon picker now includes 12 choices: Angry, Thank You, Warmly Remind, Funny, Extreme Angry, Happy, Friendly, Sorry, Surprised, Calm, Urgent, and Celebration. The nine additions are local SVG vectors that inherit icon size and foreground color. Existing saved icon indices are retained. The 4 × 3 grid and seven colors fit in the customization column without scrolling. Live Speak remains a fixed microphone. Browser checks covered new icon selection, preview rendering, and Cancel restoring the saved choice.

The color picker now has ten light colors, adding Lime, Sand, and Indigo after the original seven. Existing saved color indices are retained, and all ten swatches fit in one row.

The ordering list uses 16px labels and 42px rows with 4px gaps, fitting six entries on the app canvas. Home saved sounds use 20px labels, 64px rows, and 44px play buttons, also fitting six. Both lists retain a dedicated vertical scrollbar for additional sounds.

Edit Saved Sounds now uses 18px names, 16px action labels, and 54px rows with 6px gaps, allowing six entries to fit while retaining Rename, Delete, and reorder controls. Its list keeps a vertical scrollbar for additional entries.

Live Speak enlargement is now in Size & Minimize, above the minimized-button size. Buttons & Order instead offers Live Speak color selection: the default App Blue plus all ten light colors. Its microphone icon remains fixed. Selected color applies to the list badge, floating button, minimized bubble, and both previews. Existing configurations retain App Blue. Browser checks verified color preview updates, enlargement changes across tabs, Cancel restoring the saved color, and both settings tabs fitting without scrolling.

App Blue appears in a separate Default color row above Live Speak's ten-color palette. Light Red is now labeled Pink throughout the color picker; the color value and saved indices stay the same. The shorter labels fit on one line, and the fixed-microphone preview remains visible without scrolling.

Latest palette revision: the former Light Red/Pink swatch is labeled Red. Sand is replaced by a distinct light Pink swatch (#f6c1df), retaining its slot in the ten-color palette. This applies to both Live Speak and saved-sound customization.

The palette displays from warm to cool: Red, Pink, Peach, Yellow, Lime, Mint, Aqua, Sky, Indigo, Lavender. Stored color indices are preserved, and Live Speak's App Blue default remains in its separate row above the palette.

When Live Speak is the only selected floating button, its minimized bubble directly supports hold-to-speak and release-to-play without expanding. Moving beyond the drag threshold cancels recording and moves the bubble; pointer cancellation also cancels recording. Space and Enter support the same hold/release interaction. With multiple selected buttons, tapping the minimized bubble still reopens the panel. Browser verification covered direct pointer playback without expansion, drag cancellation, and reopening a two-button panel. Audio recording remains simulated.

Holding Live Speak now gently presses the button inward and shows a breathing inset halo. Releasing or cancelling returns it smoothly to rest. The feedback applies to Home, the floating panel, and the direct-speaking minimized bubble, preserving the selected color and fixed microphone. The halo stays inside the button to avoid clipping in narrow panels; reduced-motion preferences use a static indicator.

The hold motion is now stronger: the entire button and microphone pulse between 88% and 100% scale every second, paired with a brighter 4px inset ring. Releasing or cancelling eases from the current pulse size back to full size over 220ms. Reduced-motion preferences still show a static indicator.

These reviewed revisions are implemented in the native v1.4.0 APK. The entries above retain the design iteration history; browser audio and overlays remain simulations. Native settings, capture, Android overlay permission and lifecycle are verified separately in `../qa/` and `../validation.md`.


v1.4.1 corrects reopen anchoring: both animations pivot around Live Speak, and reopening aligns the microphone to the bubble's current center, including after dragging the bubble. Horizontal sound-strip scrolling is retained. The full panel stays within the simulated screen; edge clamping takes priority when the complete panel cannot fit around the requested anchor. Browser checks matched the moved bubble and expanded Live Speak centers within 0.02 viewport pixels. The temporary test tab preserved the user's settings draft. Import in a desktop browser remains the browser's own file chooser; Android picker compatibility is implemented and tested in the APK.

Both base and minimized button-size sliders now support 64–200px in 4px steps, with 88px defaults. The minimized settings preview scales to fit.
