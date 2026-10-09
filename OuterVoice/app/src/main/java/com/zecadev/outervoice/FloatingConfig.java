package com.zecadev.outervoice;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import org.json.JSONArray;
import org.json.JSONObject;

final class FloatingConfig {
    static final int TEAL = 0xff00c6c7;
    static final int[] COLORS = {0xffa9d8ff, 0xffb7e8cf, 0xffffe69a, 0xffffcbaa,
        0xfff49a97, 0xffd6c4f4, 0xffb8e8ea, 0xffd5efb1, 0xfff6c1df, 0xffbcc7f2};
    static final String[] COLOR_NAMES = {"Sky", "Mint", "Yellow", "Peach", "Red", "Lavender", "Aqua", "Lime", "Pink", "Indigo"};
    static final int[] COLOR_ORDER = {4, 8, 3, 2, 7, 1, 6, 0, 9, 5};
    // The first three retain their old indices. Further icons use local vector drawing.
    static final String[] ICONS = {"\ue814", "\ue87d", "\ue7f4", "", "", "", "", "", "", "", "", ""};
    static final String[] ICON_NAMES = {"Angry", "Thank You", "Warmly Remind", "Funny", "Extreme Angry", "Happy", "Friendly", "Sorry", "Surprised", "Calm", "Urgent", "Celebration"};
    static final int DEFAULT_SIZE = 88, MIN_SIZE = 64, MAX_SIZE = 144;
    static final String LIVE_ID = "live-speak", MIC_ICON = "\ue029";
    static final int DEFAULT_MIC_ENLARGEMENT = 50, MAX_MIC_ENLARGEMENT = 100;
    boolean enabled;
    int size = DEFAULT_SIZE;
    int micEnlargement = DEFAULT_MIC_ENLARGEMENT;
    int spacing = 12;
    int minimizedSize = DEFAULT_SIZE;
    boolean autoMinimize;
    int autoSeconds = 30;
    boolean liveOnly() { int count = 0; for (Item item : items) if (item.selected) count++; return count == 1; }
    Item live() { for (Item item : items) if (item.isLive()) return item; throw new IllegalStateException("Missing Live Speak"); }
    int diameter(boolean live) { return live ? Math.round(size * (1 + micEnlargement / 100f)) : size; }
    final ArrayList<Item> items = new ArrayList<>();
    static final class Item {
        final String id, name;
        boolean selected;
        int color, icon = 1;
        // -1 preserves app blue for existing installations; saved sound indices stay stable.
        int liveColor = -1;
        Item(String id, String name) { this.id = id; this.name = name; }
        boolean isLive() { return LIVE_ID.equals(id); }
        String glyph() { return isLive() ? MIC_ICON : ICONS[icon]; }
        int buttonColor() { return isLive() ? (liveColor < 0 ? TEAL : COLORS[liveColor]) : COLORS[color]; }
        int foreground() { return isLive() && liveColor < 0 ? 0xfff6f9fa : 0xff0f171c; }
    }
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("floating", Context.MODE_PRIVATE);
    }
    static FloatingConfig load(Context context) {
        FloatingConfig config = new FloatingConfig();
        SharedPreferences prefs = prefs(context);
        config.enabled = prefs.getBoolean("enabled", false);
        config.size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, prefs.getInt("size", DEFAULT_SIZE)));
        config.spacing = Math.max(0, Math.min(40, prefs.getInt("spacing", 12)));
        config.minimizedSize = Math.max(MIN_SIZE, Math.min(MAX_SIZE, prefs.getInt("minimized_size", DEFAULT_SIZE)));
        config.autoMinimize = prefs.getBoolean("auto_minimize", false);
        config.autoSeconds = Math.max(1, Math.min(3600, prefs.getInt("auto_seconds", 30)));
        config.micEnlargement = Math.max(0, Math.min(MAX_MIC_ENLARGEMENT, prefs.getInt("mic_enlargement", DEFAULT_MIC_ENLARGEMENT)));
        ArrayList<Item> available = new ArrayList<>();
        Item live = new Item(LIVE_ID, "Live Speak"); live.selected = true;
        available.add(live);
        try {
            JSONArray saved = new JSONArray(context.getSharedPreferences("sounds", Context.MODE_PRIVATE).getString("items", "[]"));
            HashSet<String> seen = new HashSet<>();
            for (int i = 0; i < saved.length(); i++) {
                JSONObject value = saved.getJSONObject(i);
                String id = value.getString("id");
                if (!id.matches("[0-9a-f-]{36}") || !new File(context.getFilesDir(), id + ".wav").isFile() || !seen.add(id)) continue;
                Item item = new Item(id, value.getString("name"));
                item.color = (available.size() - 1) % COLORS.length; available.add(item);
            }
            JSONArray ordered = new JSONArray(prefs.getString("items", "[]"));
            for (int i = 0; i < ordered.length(); i++) {
                JSONObject value = ordered.getJSONObject(i);
                for (int j = 0; j < available.size(); j++) {
                    Item item = available.get(j);
                    if (!item.id.equals(value.optString("id"))) continue;
                    item.selected = item.isLive() || value.optBoolean("selected", false);
                    item.color = Math.max(0, Math.min(COLORS.length - 1, value.optInt("color", 0)));
                    item.icon = Math.max(0, Math.min(ICONS.length - 1, value.optInt("icon", 1)));
                    item.liveColor = Math.max(-1, Math.min(COLORS.length - 1, value.optInt("live_color", -1)));
                    config.items.add(item); available.remove(j); break;
                }
            }
        } catch (Exception e) { AudioDiagnostics.log("Floating settings: " + e.getMessage()); }
        boolean orderedLive = config.items.contains(live);
        config.items.addAll(available);
        // Migrate previous releases, which stored Live Speak outside the ordered list.
        if (!orderedLive) { config.items.remove(live); config.items.add(0, live); }
        return config;
    }
    boolean save(Context context) {
        JSONArray array = new JSONArray();
        try {
            for (Item item : items) {
                JSONObject value = new JSONObject(); value.put("id", item.id);
                value.put("selected", item.selected); value.put("color", item.color); value.put("icon", item.icon);
                value.put("live_color", item.liveColor);
                array.put(value);
            }
            return prefs(context).edit().putBoolean("enabled", enabled).putInt("size", size)
                .putInt("mic_enlargement", micEnlargement)
                .putInt("spacing", spacing)
                .putInt("minimized_size", minimizedSize).putBoolean("auto_minimize", autoMinimize).putInt("auto_seconds", autoSeconds)
                .putString("items", array.toString()).commit();
        } catch (Exception e) { return false; }
    }
}
