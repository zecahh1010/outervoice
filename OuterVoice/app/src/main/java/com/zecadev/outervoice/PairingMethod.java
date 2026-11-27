package com.zecadev.outervoice;

import android.content.Context;

/** One persisted BLE transport, shared by pairing and subsequent button listening. */
final class PairingMethod {
    static final String SHORTCUT="shortcut", CALLBACK="callback", KEY="pairing_method";
    static String valid(String value){return CALLBACK.equals(value)?CALLBACK:SHORTCUT;}
    static String get(Context c){return valid(c.getSharedPreferences("smart_buttons",Context.MODE_PRIVATE).getString(KEY,SHORTCUT));}
    static String label(String value){return CALLBACK.equals(value)?"v1.5.0 相容方式":"Zeekr Shortcut 方式";}
    static boolean background(String value){return SHORTCUT.equals(value);}
    static void select(Context c,String value){android.content.SharedPreferences.Editor edit=c.getSharedPreferences("smart_buttons",Context.MODE_PRIVATE).edit().putString(KEY,valid(value));if(!get(c).equals(valid(value)))edit.remove("pair_until");edit.commit();}
}
