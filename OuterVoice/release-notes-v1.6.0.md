# Outer Voice 1.6.0

Build 14. All configuration is reached from the **設定** tab; **傳聲／聲音** remains the operational home for speaking and the saved-sound library.

The settings list shows current values and groups the controls into:

1. **傳聲方式** — hold/release/play, tap/record/tap/stop/play or Live Speak for the screen and floating microphone.
2. **Live 麥克風** — source mode, input port and microphone digital gain.
3. **音量及增益** — Android media volume and exterior-speaker digital gain.
4. **懸浮面板** — enable, selected sounds/order, colors/icons, sizes/spacing, minimize and inactivity behavior in the existing panel editor.
5. **靈動貼** — pairing, device names and independent single/double-press actions.
6. **測試及關於** — exterior-speaker test, audio diagnostics, app version and license/credits.

The separate floating/audio/info icons at the top of home are removed. Home displays the current speaking mode as a read-only label; changing it happens under Settings. The power/Exit action and route status remain available. The smart-button page is now a Settings child with **返回設定**; the notification still deep-links to it. About returns to its tools parent, and hardware Back follows the same hierarchy. The floating panel stays active while visiting Settings or smart-button configuration. The panel editor retains its existing temporary-hide/Cancel/Save behavior.

Changing the screen/floating speaking mode still does not overwrite a smart-button assignment or stop a remotely owned recording. Sound files, preference keys, gain values, BLE bindings, routing and capture implementation are retained from 1.5.2. A speaker test asks the user to stop an active audio session first.

Install over the existing app without uninstalling. The retained signing certificate permits a normal update. See `validation-v1.6.0.md` for native navigation checks; the existing physical-car audio/BLE verification boundaries continue to apply.
