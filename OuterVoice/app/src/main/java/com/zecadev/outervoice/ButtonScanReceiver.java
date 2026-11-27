/* Based on Zeekr Shortcut - https://github.com/dts88/zeekr-shortcut-car
 * Copyright (C) 2026 dts88
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional GPL section 7 terms apply; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-27: foreground audio-service delivery. */
package com.zecadev.outervoice;

import android.content.*;

public final class ButtonScanReceiver extends BroadcastReceiver {
    static final String ACTION="com.zecadev.outervoice.SMART_BUTTON_SCAN";
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!ACTION.equals(i.getAction()))return;
        // Migration cleanup only: v1.5.0-style callbacks now deliver scan results directly.
        // A queued broadcast from an older APK cannot restart or feed the new listener.
        ButtonScanner.stopOrphan(c.getApplicationContext());
    }
}
