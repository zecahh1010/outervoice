package com.zecadev.outervoice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import java.util.Locale;

final class AudioRoutes {
    static final String OUTER = "BUS12_OUTER_NOTIFY";
    static AudioDeviceInfo outer(Context context) {
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager == null) return null;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            String address = device.getAddress();
            if (address != null && address.toUpperCase(Locale.US).contains(OUTER)) return device;
        }
        return null;
    }
    static String describe(AudioDeviceInfo device) {
        return device == null ? "none" : "id=" + device.getId() + " type=" + device.getType() + " address=" + device.getAddress();
    }
}
