# Native design QA

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
