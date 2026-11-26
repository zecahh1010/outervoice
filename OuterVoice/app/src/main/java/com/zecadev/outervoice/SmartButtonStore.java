package com.zecadev.outervoice;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;

final class SmartButtonStore {
    static final String[] ACTION_NAMES = {"錄音／停止後播放", "Live Speak／停止即時傳聲", "播放指定聲音", "停止音訊", "不執行"};
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("smart_buttons", Context.MODE_PRIVATE); }
    static ArrayList<String> devices(Context c) {
        ArrayList<String> values = new ArrayList<>();
        try { JSONArray a = new JSONArray(prefs(c).getString("devices", "[]"));
            for (int i = 0; i < a.length(); i++) { String s = a.getString(i); if (android.bluetooth.BluetoothAdapter.checkBluetoothAddress(s) && !values.contains(s)) values.add(s); }
        } catch (Exception ignored) { }
        return values;
    }
    static void add(Context c, String address) { ArrayList<String> a = devices(c); if (!a.contains(address)) { a.add(address); prefs(c).edit().putString("devices", new JSONArray(a).toString()).apply(); } }
    static void forget(Context c, String address) {
        ArrayList<String> a = devices(c); a.remove(address); SharedPreferences.Editor e = prefs(c).edit().putString("devices", new JSONArray(a).toString());
        for (String key : prefs(c).getAll().keySet()) if (key.endsWith("." + address)) e.remove(key);
        e.apply();
    }
    static String name(Context c, String a) { return prefs(c).getString("name." + a, "靈動貼 " + a.substring(a.length() - 5)); }
    static int action(Context c, String a) { return Math.max(0, Math.min(4, prefs(c).getInt("single." + a, 0))); }
    static String soundName(Context c, String id) {
        for (FloatingConfig.Item item : FloatingConfig.load(c).items) if (!item.isLive() && item.id.equals(id)) return item.name;
        return id.isEmpty() ? "未選擇聲音" : "聲音已移除 · 請重新選擇";
    }
}
