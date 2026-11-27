package com.zecadev.outervoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

/** v1.5.0 compatibility permissions for this target-28 application. */
final class SmartButtonPermissions {
    static String[] required(int api){return required(api,PairingMethod.CALLBACK);}
    static String[] required(int api,String method){
        if(api<31)return new String[]{Manifest.permission.ACCESS_FINE_LOCATION};
        return PairingMethod.CALLBACK.equals(method)?new String[]{Manifest.permission.ACCESS_FINE_LOCATION,"android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"}:new String[]{"android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"};
    }
    static boolean granted(Context c){for(String p:required(Build.VERSION.SDK_INT,PairingMethod.get(c)))if(c.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)return false;return true;}
    static boolean locationSwitchRequired(int api,int target){return locationSwitchRequired(api,target,PairingMethod.CALLBACK);}
    static boolean locationSwitchRequired(int api,int target,String method){return PairingMethod.CALLBACK.equals(method)?api<=30||target<=30:api<31;}
    static String words(Context c){return Build.VERSION.SDK_INT>=31&&PairingMethod.background(PairingMethod.get(c))?"附近裝置":"藍牙及位置";}
}
