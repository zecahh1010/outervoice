# Native design QA

Current native UI: **v1.4.0** implements the approved interactive web simulator revisions. The older mockup comparisons below describe their respective releases; current evidence uses the `qa/v140-*` prefix.

Final result: passed for the implemented native screens. Vehicle audio validation remains outstanding.

Reference: approved home, Add and Credits mockups in `design-reference/`. Each reference was normalized from 1638 × 960 to 1024 × 600 and placed beside its corresponding Android screenshot. The emulator viewport is 1024 × 600 at density 160, landscape and immersive fullscreen.

Evidence: `qa/home-comparison.png`, `qa/add-comparison.png`, `qa/credits-comparison.png`. Reference is on the left; final native implementation is on the right. Text and controls were inspected in these full-size comparisons. Earlier comparisons are retained with the `-initial` suffix.

The initial comparison identified typography, the Credits title treatment, and the Add-screen import layout as fidelity issues. The final build bundles Inter, uses the large teal Voice title, and provides a left-aligned import row with a file icon. Field sizes and action spacing were corrected and rechecked.

The home screen has the circular microphone as its sole speaking toggle, with Start Speaking underneath while idle. The implementation switches that label to Stop Speaking during capture. Saved sounds occupy a vertically scrollable list; Add opens a separate naming/import screen. The info icon opens actual package version 1.0.0 (1), developer Zeca, tester SL, and contributors Chris and j.Lun, with no Based on row. The approved microphone/broadcast launcher icon is included at Android launcher densities.

Intentional differences: the emulator reports Speaker unavailable because it has no BUS12 device; the reference depicts an available speaker. The Add screenshot uses a test fixture named welcome.wav. The installed version replaces the reference's placeholder. The idle input meter remains inactive until capture starts. Android document selection and permission dialogs use native system UI.

Minor P3 differences remain in the solid Material icon styling, flat charcoal background versus the reference's subtle gradient, and a few spacing details. No actionable P0, P1 or P2 visual issue remained in the inspected final screens. The active speaking state and its live input meter require the vehicle route and were not visually exercised on the emulator.

## v1.1.0 requested changes

The user's new requirements intentionally extend the approved v1.0.0 reference: the left panel now has Live Speaking / Record & Play selectors, a smaller microphone circle to fit a temporary Play action, and recording duration/status. Add is renamed Add Sound; its screen offers Import WAV and Record microphone side by side plus a local-browser action. Each saved row has a trash icon with a confirmation dialog. The header speaker icon opens volume controls, test tone and diagnostics.

Rendered at 1024 × 600: `qa/v110-home.png`, `qa/v110-record-ready.png`, `qa/v110-add.png`, and `qa/v110-add-ready.png`. These native screenshots were inspected alongside the original visual references: dark palette, teal state treatment, Inter typography, list structure and Credits are retained; the new controls are purposeful requirement changes. The Add screen and recording controls fit the viewport without clipping. Active recording and the finished temporary-clip state were exercised. Actual live BUS12 output remains a hardware-only check.

## v1.4.0 approved simulator revisions

Native screenshots at 1024 × 600 were inspected for the wider centered Home controls, compact six-row Home/Edit lists, clear Add Sound Play action, warm-to-cool color palette, default App Blue row, twelve icons and two Floating Panel tabs. `v140-live-colors.png`, `v140-icons.png`, `v140-final-size.png`, `v140-recorded.png`, `v140-home-six.png` and `v140-edit-six.png` show these layouts. The main settings controls and previews fit without scrolling; the ordering and sound lists retain their own scrollbar.

Emulator inspection corrected a wrapping Home Edit List label and clipped timeout input padding before the final build. The native toolbar separates Move, a flexible-width Minimize control and Close; a single-button panel uses an icon-only center control. `v140-single-panel.png`, `v140-manual-minimized.png` and `v140-single-bubble-restart.png` document the compact states. The microphone scales with its entire button and uses a bounded halo; animated pixel measurements verify 132 → 116 → 132 px motion. Palette/geometry checks preserve the selected color and fixed microphone. `v140-timer-during-hold.png` shows recording continuing beyond the configured inactivity timeout.

Previews scale down to fit; configured sizes apply as physical pixels in the system overlay. The emulator's Speaker unavailable messages reflect absent BUS12 hardware, and Android permission/confirmation dialogs retain native system styling. Vehicle audio remains unverified.
