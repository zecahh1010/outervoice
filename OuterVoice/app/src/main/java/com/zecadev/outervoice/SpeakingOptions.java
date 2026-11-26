package com.zecadev.outervoice;

import android.content.Context;
import android.content.SharedPreferences;

/** Screen mode and each physical button's action deliberately use separate keys. */
final class SpeakingOptions {
    static final int HOLD = 0, RECORD = 1, LIVE = 2;
    static final String[] MODES = {"按住說話，放手播放", "按一下錄音，再按一下停止並播放", "Live Speak · 即時傳聲"};
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("speaking", Context.MODE_PRIVATE); }
    static int mode(Context c) { return Math.max(HOLD, Math.min(LIVE, prefs(c).getInt("screen_mode", HOLD))); }
    static String hint(Context c) { return mode(c) == HOLD ? "Hold to speak · release to play" : mode(c) == RECORD ? "Tap to record · tap again to play" : "Tap to speak live · tap again to stop"; }
}
