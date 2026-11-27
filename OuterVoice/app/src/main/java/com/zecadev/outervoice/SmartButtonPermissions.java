package com.zecadev.outervoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** Same API split as Zeekr Shortcut: Nearby Devices on 12+, location on older Android. */
final class SmartButtonPermissions {
    static String[] required(int api){return api>=31?new String[]{"android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION};}
    static boolean granted(Context c){for(String p:required(Build.VERSION.SDK_INT))if(c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;return true;}
    static boolean locationSwitchRequired(int api){return api<31;}
}
