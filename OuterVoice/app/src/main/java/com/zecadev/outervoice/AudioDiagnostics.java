package com.zecadev.outervoice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class AudioDiagnostics {
    private static final StringBuilder events = new StringBuilder();
    static synchronized void log(String message) {
        events.append(new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date())).append(" ").append(message).append('\n');
        if (events.length() > 16000) events.delete(0, events.length() - 12000);
    }
    static synchronized String report(Context context) {
        StringBuilder out = new StringBuilder("Outer Voice audio report\n");
        try { out.append("Version: ").append(context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName).append('\n'); } catch (Exception ignored) { }
        out.append(Build.MODEL).append(" Android ").append(Build.VERSION.RELEASE).append('\n');
        out.append("Requested: ").append(AudioRoutes.OUTER).append("; usage=MEDIA, content=MUSIC\n");
        AudioManager manager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (manager != null) {
            out.append("Media volume: ").append(manager.getStreamVolume(AudioManager.STREAM_MUSIC)).append('/').append(manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                .append("; muted=").append(manager.isStreamMute(AudioManager.STREAM_MUSIC)).append("; fixed=").append(manager.isVolumeFixed()).append('\n');
            out.append("Microphone muted: ").append(manager.isMicrophoneMute()).append('\n');
            for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) out.append("Output: ").append(AudioRoutes.describe(device)).append('\n');
        }
        out.append("Selected: ").append(AudioRoutes.describe(AudioRoutes.outer(context))).append('\n');
        out.append("Preferred-device acceptance and routing do not prove amplifier output.\n\nSession events:\n").append(events);
        return out.toString();
    }
}
