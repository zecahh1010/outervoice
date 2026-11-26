/*
 * Based on Zeekr Shortcut - https://github.com/dts88/zeekr-shortcut-car
 * Copyright (C) 2026 dts88
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional terms under GPL-3.0 section 7 apply; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-26: independent per-button audio assignments.
 */
package com.zecadev.outervoice;

import android.content.Context;

/** Main-thread dispatch after protocol validation and cross-channel deduplication. */
final class SmartButtonActions {
    static void run(Context c, Object owner, String address, int gesture) {
        if (!SmartButtonStore.prefs(c).getBoolean("enabled", false) || !SmartButtonStore.devices(c).contains(address)) return;
        if (gesture != SmartButtonAd.SINGLE && gesture != SmartButtonAd.DOUBLE) return;
        VoiceSession voice = VoiceSession.get(c);
        int action = gesture == SmartButtonAd.DOUBLE ? 2 : SmartButtonStore.action(c, address);
        if (action == 0) voice.toggle(owner, SpeakingOptions.RECORD);
        else if (action == 1) voice.toggle(owner, SpeakingOptions.LIVE);
        else if (action == 2) {
            String key = (gesture == SmartButtonAd.DOUBLE ? "double_sound." : "single_sound.") + address;
            String id = SmartButtonStore.prefs(c).getString(key, "");
            if (id.isEmpty()) return;
            voice.playSaved(owner, id, SmartButtonStore.soundName(c, id));
        } else if (action == 3) voice.cancel();
    }
}
