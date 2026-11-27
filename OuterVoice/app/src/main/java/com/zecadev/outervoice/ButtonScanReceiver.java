/* Based on Zeekr Shortcut by dts88; GPL-3.0-only with section 7 terms; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-27: application-owned, immediate receiver dispatch. */
package com.zecadev.outervoice;
import android.content.*;
public final class ButtonScanReceiver extends BroadcastReceiver {
    static final String ACTION="com.zecadev.outervoice.SMART_BUTTON_SCAN";
    @Override public void onReceive(Context c,Intent i){
        if(i==null||!ACTION.equals(i.getAction()))return;
        Context app=c.getApplicationContext();
        if(!"outervoice://smart-button/shortcut".equals(String.valueOf(i.getData()))){if(ButtonScanner.current!=null)ButtonScanner.current.legacyIntents++;ButtonScanner.stopLegacy(app);return;}
        SmartButtonsService.remember(app);SmartButtonsController.install(app).receive(i);
    }
}