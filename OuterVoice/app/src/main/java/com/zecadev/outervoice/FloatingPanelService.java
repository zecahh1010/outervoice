package com.zecadev.outervoice;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.UUID;

/** Genuine system overlay. Its audio lifetime is independent of MainActivity.onPause(). */
public final class FloatingPanelService extends Service {
    private static final String CHANNEL = "floating_panel", CLOSE = "close", REFRESH = "refresh";
    private final Handler main = new Handler();
    private final WavRecorder recorder = new WavRecorder();
    private WavPlayer player;
    private AudioManager audio;
    private AudioFocusRequest focus;
    private boolean hasFocus, destroyed, holding;
    private int epoch, screenW, screenH, size;
    private int micEnlargement;
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private View panel;
    private TextView mic, status;
    private File temporary;
    private Typeface icons, font;

    static void sync(Context context, boolean refresh) {
        if (!FloatingConfig.prefs(context).getBoolean("enabled", false)) {
            context.stopService(new Intent(context, FloatingPanelService.class)); return;
        }
        if (!Settings.canDrawOverlays(context) || context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        Intent intent = new Intent(context, FloatingPanelService.class);
        if (refresh) intent.setAction(REFRESH);
        try { context.startForegroundService(intent); }
        catch (RuntimeException e) {
            AudioDiagnostics.log("Floating service start: " + e.getMessage());
            Toast.makeText(context, "Could not open floating panel. Open Floating Buttons to try again.", Toast.LENGTH_LONG).show();
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);
        player = new WavPlayer(this);
        icons = Typeface.createFromAsset(getAssets(), "MaterialIcons-Regular.ttf");
        font = Typeface.createFromAsset(getAssets(), "Inter.ttf");
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(change -> {
                if (change < 0) { cancelAudio(); show("Audio interrupted"); }
            }, main).build();
        // Only overlay-owned stale clips are cleaned; Activity recordings use another prefix.
        File[] stale = getCacheDir().listFiles((dir, name) -> name.startsWith("floating-voice-"));
        if (stale != null) for (File file : stale) file.delete();
    }
    private void foreground() {
        NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Floating sound panel", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, FloatingButtonsActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent close = PendingIntent.getService(this, 1, new Intent(this, FloatingPanelService.class).setAction(CLOSE), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Outer Voice floating panel")
            .setContentText("Live Speak and saved sounds are available over other apps")
            .setContentIntent(open).setOngoing(true).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Close panel", close).build();
        if (Build.VERSION.SDK_INT >= 30) startForeground(40, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(40, notification);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && CLOSE.equals(intent.getAction())) { closePanel(); return START_NOT_STICKY; }
        if (!FloatingConfig.prefs(this).getBoolean("enabled", false) || !Settings.canDrawOverlays(this)
            || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { stopSelf(); return START_NOT_STICKY; }
        try {
            foreground();
            if (panel == null || (intent != null && REFRESH.equals(intent.getAction()))) { cancelAudio(); buildPanel(); }
        } catch (RuntimeException e) {
            AudioDiagnostics.log("Floating window failed: " + e.getMessage());
            Toast.makeText(this, "Could not display floating panel. Check Display over other apps permission.", Toast.LENGTH_LONG).show();
            FloatingConfig.prefs(this).edit().putBoolean("enabled", false).apply(); stopSelf();
        }
        return START_NOT_STICKY;
    }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView text(String label, int pixels, int color) {
        TextView view = new TextView(this); view.setText(label); view.setTextColor(color);
        view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, pixels); view.setTypeface(font);
        view.setGravity(Gravity.CENTER); return view;
    }
    private GradientDrawable shape(int color, boolean circle) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color);
        if (circle) drawable.setShape(GradientDrawable.OVAL); else drawable.setCornerRadius(16);
        return drawable;
    }
    private LinearLayout circle(String symbol, String label, int color, String description, int diameter, int largest) {
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setGravity(Gravity.CENTER);
        int iconSize = Math.round(diameter * .45f);
        TextView button = text(symbol, iconSize, color == FloatingConfig.TEAL ? 0xfff6f9fa : 0xff0f171c);
        button.setIncludeFontPadding(false);
        button.setTypeface(icons); button.setBackground(shape(color, true)); button.setContentDescription(description);
        button.setClickable(true); button.setFocusable(true);
        column.addView(button, new LinearLayout.LayoutParams(diameter, diameter));
        LinearLayout.LayoutParams buttonPosition = (LinearLayout.LayoutParams)button.getLayoutParams();
        buttonPosition.topMargin = (largest - diameter) / 2;
        TextView name = text(label, Math.max(12, Math.min(17, size / 6)), 0xfff6f9fa); name.setMaxLines(2);
        LinearLayout.LayoutParams namePosition = new LinearLayout.LayoutParams(diameter, 34);
        namePosition.topMargin = (largest - diameter) / 2;
        column.addView(name, namePosition);
        return column;
    }
    private void buildPanel() {
        if (panel != null) { windows.removeView(panel); panel = null; }
        mic = null; status = null;
        FloatingConfig config = FloatingConfig.load(this); size = config.size; micEnlargement = config.micEnlargement;
        DisplayMetrics metrics = new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics);
        screenW = metrics.widthPixels; screenH = metrics.heightPixels;
        if (FloatingConfig.prefs(this).getBoolean("minimized", false)) { buildBubble(); return; }
        int largest = config.diameter(true);
        LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(8, 8, 8, 6); content.setBackground(shape(0xff182329, false));
        LinearLayout buttons = row(); content.addView(buttons);
        TextView grip = text("\ue25d", 26, 0xffaebcc7); grip.setTypeface(icons); grip.setContentDescription("Move floating panel");
        buttons.addView(grip, new LinearLayout.LayoutParams(36, largest + 34));
        int count = 0, total = 0;
        for (FloatingConfig.Item item : config.items) if (item.selected) { if (count++ > 0) total += config.spacing; total += config.diameter(item.isLive()); }
        int fixedWidth = 16 + 36 + 88;
        int soundWidth = Math.min(total, Math.max(0, screenW - 16 - fixedWidth));
        if (count > 0) {
            HorizontalScrollView scroll = new HorizontalScrollView(this); scroll.setHorizontalScrollBarEnabled(total > soundWidth);
            LinearLayout saved = row(); scroll.addView(saved);
            for (FloatingConfig.Item item : config.items) if (item.selected) {
                int diameter = config.diameter(item.isLive());
                LinearLayout sound = circle(item.glyph(), item.name, item.buttonColor(), item.isLive() ? "Hold Live Speak to record; release to play" : "Play " + item.name, diameter, largest);
                LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(diameter, largest + 34); layout.leftMargin = saved.getChildCount() > 0 ? config.spacing : 0;
                saved.addView(sound, layout);
                if (item.isLive()) {
                    mic = (TextView) sound.getChildAt(0);
                    mic.setOnTouchListener((view, event) -> {
                        switch (event.getActionMasked()) {
                            case MotionEvent.ACTION_DOWN:
                                view.getParent().requestDisallowInterceptTouchEvent(true); beginRecording(); return true;
                            case MotionEvent.ACTION_UP:
                                view.getParent().requestDisallowInterceptTouchEvent(false);
                                if (holding) { holding = false; recorder.stop(); show("Saving WAV…"); mic.setAlpha(1f); }
                                return true;
                            case MotionEvent.ACTION_CANCEL:
                                view.getParent().requestDisallowInterceptTouchEvent(false); cancelAudio(); show("Recording cancelled"); return true;
                            default: return true;
                        }
                    });
                } else sound.getChildAt(0).setOnClickListener(v -> playSound(item));
            }
            buttons.addView(scroll, new LinearLayout.LayoutParams(soundWidth, largest + 34));
        }
        TextView minimize = text("\ue15b", 26, 0xfff6f9fa); minimize.setTypeface(icons); minimize.setContentDescription("Minimize floating panel");
        minimize.setOnClickListener(v -> { cancelAudio(); FloatingConfig.prefs(this).edit().putBoolean("minimized", true).commit(); buildPanel(); });
        buttons.addView(minimize, new LinearLayout.LayoutParams(44, 48));
        TextView close = text("\ue5cd", 26, 0xfff6f9fa); close.setTypeface(icons); close.setContentDescription("Close floating panel");
        close.setOnClickListener(v -> closePanel()); buttons.addView(close, new LinearLayout.LayoutParams(44, 48));
        status = text("Hold Live Speak; release to play", 12, 0xffaebcc7); status.setMaxLines(1);
        content.addView(status, new LinearLayout.LayoutParams(-1, 20));
        panel = content;
        params = new WindowManager.LayoutParams(fixedWidth + soundWidth, largest + 68,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        // Android 12 permits touches outside a translucent overlay at this opacity.
        params.alpha = .8f;
        params.x = FloatingConfig.prefs(this).getInt("x", 24);
        params.y = FloatingConfig.prefs(this).getInt("y", Math.max(0, screenH - params.height - 24));
        clamp(); windows.addView(panel, params);
        grip.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY;
            @Override public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelAudio(); downX = event.getRawX(); downY = event.getRawY(); startX = params.x; startY = params.y; return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + Math.round(event.getRawX() - downX); params.y = startY + Math.round(event.getRawY() - downY);
                        clamp(); windows.updateViewLayout(panel, params); return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        FloatingConfig.prefs(FloatingPanelService.this).edit().putInt("x", params.x).putInt("y", params.y).apply(); return true;
                    default: return true;
                }
            }
        });
    }
    private void buildBubble() {
        TextView bubble = text(FloatingConfig.MIC_ICON, 30, 0xfff6f9fa); bubble.setTypeface(icons);
        bubble.setIncludeFontPadding(false); bubble.setBackground(shape(FloatingConfig.TEAL, true));
        bubble.setContentDescription("Reopen floating panel; drag to move"); bubble.setClickable(true);
        panel = bubble;
        params = new WindowManager.LayoutParams(60, 60, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT; params.alpha = .8f;
        params.x = FloatingConfig.prefs(this).getInt("bubble_x", FloatingConfig.prefs(this).getInt("x", 24));
        params.y = FloatingConfig.prefs(this).getInt("bubble_y", FloatingConfig.prefs(this).getInt("y", 24));
        clamp(); windows.addView(panel, params);
        bubble.setOnClickListener(v -> { FloatingConfig.prefs(this).edit().putBoolean("minimized", false).commit(); buildPanel(); });
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float x, y; int startX, startY; boolean moved;
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN: x = event.getRawX(); y = event.getRawY(); startX = params.x; startY = params.y; moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - x) > 8 || Math.abs(event.getRawY() - y) > 8) moved = true;
                        if (moved) { params.x = startX + Math.round(event.getRawX() - x); params.y = startY + Math.round(event.getRawY() - y); clamp(); windows.updateViewLayout(panel, params); }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) view.performClick();
                        else FloatingConfig.prefs(FloatingPanelService.this).edit().putInt("bubble_x", params.x).putInt("bubble_y", params.y).apply();
                        return true;
                    case MotionEvent.ACTION_CANCEL: return true;
                    default: return true;
                }
            }
        });
    }
    private void clamp() {
        params.x = Math.max(0, Math.min(params.x, Math.max(0, screenW - params.width)));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, screenH - params.height)));
    }
    private boolean acquireFocus() {
        AudioOwner.claim(this, this::cancelAudio);
        if (!hasFocus) hasFocus = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!hasFocus) { AudioOwner.release(this); show("Audio is busy; try again"); }
        return hasFocus;
    }
    private void releaseFocus() {
        if (hasFocus) audio.abandonAudioFocusRequest(focus);
        hasFocus = false; AudioOwner.release(this);
    }
    private void beginRecording() {
        if (recorder.active()) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            show("Open Outer Voice to allow microphone access"); return;
        }
        cancelAudio();
        int token = epoch;
        if (!acquireFocus()) return;
        holding = true; mic.setAlpha(.65f); show("Recording • release to play");
        File file = new File(getCacheDir(), "floating-voice-" + UUID.randomUUID() + ".wav");
        recorder.start(file, new WavRecorder.Listener() {
            @Override public void meter(double level, long milliseconds) { main.post(() -> {
                if (token == epoch && holding) show("Recording " + milliseconds / 1000 + "s • release to play");
            }); }
            @Override public void done(File completed, String error) { main.post(() -> {
                if (destroyed || token != epoch) { if (completed != null) completed.delete(); return; }
                holding = false; mic.setAlpha(1f);
                if (error != null || completed == null) { releaseFocus(); show(error == null ? "Recording cancelled" : error); return; }
                temporary = completed;
                try {
                    WavFormat format = WavFormat.read(completed);
                    AudioDiagnostics.log("Floating WAV saved: " + format.rate + " Hz channels=" + format.channels + "; PCM16 bytes=" + format.bytes);
                } catch (Exception failure) { releaseFocus(); show("Could not save WAV"); deleteTemporary(); return; }
                // Wait for a stopped previous WAV worker before starting the new clip.
                playWhenReady(completed, "Live Speak", token, 0);
            }); }
        });
    }
    private void playSound(FloatingConfig.Item item) {
        cancelAudio(); int token = epoch;
        File file = new File(getFilesDir(), item.id + ".wav");
        if (!file.isFile()) { show("Sound was removed"); return; }
        if (acquireFocus()) playWhenReady(file, item.name, token, 0);
    }
    private void playWhenReady(File file, String name, int token, int attempt) {
        if (destroyed || token != epoch) return;
        if (player.active()) {
            if (attempt < 100) main.postDelayed(() -> playWhenReady(file, name, token, attempt + 1), 20);
            else { releaseFocus(); show("Playback is busy; try again"); }
            return;
        }
        show("Playing " + name);
        try {
            player.play(file, error -> main.post(() -> {
                if (destroyed || token != epoch) return;
                releaseFocus(); show(error == null ? "Hold Live Speak; release to play" : error);
                deleteTemporary();
            }));
        } catch (RuntimeException e) { releaseFocus(); AudioDiagnostics.log("Floating playback: " + e.getMessage()); show(e.getMessage()); deleteTemporary(); }
    }
    private void deleteTemporary() { if (temporary != null) temporary.delete(); temporary = null; }
    private void cancelAudio() {
        epoch++; holding = false; recorder.cancel(); player.stop(); releaseFocus(); deleteTemporary();
        if (mic != null) mic.setAlpha(1f);
    }
    private void show(String message) { if (status != null) status.setText(message == null ? "Audio failed" : message); }
    private void closePanel() {
        FloatingConfig.prefs(this).edit().putBoolean("enabled", false).putBoolean("minimized", false).apply(); stopSelf();
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); cancelAudio(); buildPanel();
    }
    @Override public void onDestroy() {
        destroyed = true; cancelAudio();
        if (panel != null) { try { windows.removeView(panel); } catch (RuntimeException ignored) { } panel = null; }
        stopForeground(true); super.onDestroy();
    }
}
