package com.zecadev.outervoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** v1.5.0 compatibility permissions for this target-28 application. */
final class SmartButtonPermissions {
    static String[] required(int api){return api>=31?new String[]{Manifest.permission.ACCESS_FINE_LOCATION,"android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"}:new String[]{Manifest.permission.ACCESS_FINE_LOCATION};}
    static boolean granted(Context c){for(String p:required(Build.VERSION.SDK_INT))if(c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;return true;}
    static boolean locationSwitchRequired(int api,int target){return api<=30||target<=30;}
}
