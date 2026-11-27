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
        // Resolve the application context before consulting any virtualized app state.
        // Zeekr Shortcut likewise installs/delivers through its application, not receiver prefs.
        Context app=c.getApplicationContext();
        ButtonScanner scanner=ButtonScanner.current;
        if(scanner!=null){scanner.receive(i);return;}
        try{app.startForegroundService(new Intent(app,SmartButtonsService.class).setAction("scan_result").putExtra("scan_result",i));}
        catch(RuntimeException e){ButtonScanner.stopOrphan(app);AudioDiagnostics.log("BLE wake delivery: "+e.getMessage());}
    }
}
