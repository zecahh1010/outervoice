package com.zecadev.outervoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Handler;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;

/** One app-wide speaking session, shared by the screen, overlay and BLE foreground service.
 * All entry points and observer callbacks run on the main looper. */
final class VoiceSession {
    private static VoiceSession instance;
    static VoiceSession get(Context c) { if (instance == null) instance = new VoiceSession(c.getApplicationContext()); return instance; }
    final ArrayList<Runnable> observers = new ArrayList<>();
    private final Context app;
    private final Handler main = new Handler(android.os.Looper.getMainLooper());
    private final WavRecorder recorder;
    private final WavPlayer player;
    private final LiveMicPassthrough live;
    private final AudioManager audio;
    private final AudioFocusRequest focus;
    private Object owner;
    private volatile int epoch;
    private int activeMode;
    private boolean focused, capturing, finishing, liveRequested;
    private File temporary;
    double level;
    long milliseconds;
    String message = "";
    private VoiceSession(Context c) {
        app = c; recorder = new WavRecorder(c); player = new WavPlayer(c);
        File[] stale = app.getCacheDir().listFiles((dir,name) -> name.startsWith("session-voice-"));
        if (stale != null) for (File file : stale) file.delete();
        audio = (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener(v -> { if (v < 0) { cancel(); status("Audio interrupted"); } }, main).build();
        live = new LiveMicPassthrough(c, new LiveMicPassthrough.Listener() {
            public void onLevel(double rms) { final int token = epoch; main.post(() -> { if (token == epoch && liveRequested) { level = rms; changed(); } }); }
            public void onStatus(String s) { final int token = epoch; main.post(() -> {
                if (token != epoch || !liveRequested) return;
                if (s.contains("failed") || s.contains("stopped")) { liveRequested = false; release(); }
                status(s);
            }); }
        });
    }
    boolean recording() { return capturing; }
    int recordMode() { return activeMode; }
    boolean live() { return liveRequested; }
    boolean playing() { return player.active(); }
    boolean busy() { return capturing || finishing || player.active() || live.isActive(); }
    boolean owned(Object o) { return owner == o; }
    void changed() { for (Runnable r : new ArrayList<>(observers)) r.run(); }
    private void status(String s) { message = s == null ? "Audio failed" : s; changed(); }
    void down(Object o, int mode) { if (mode == SpeakingOptions.HOLD && !capturing && !liveRequested) startRecord(o, mode); }
    void up(Object o, int mode) {
        // A visible Stop action also stops a session started by a physical button.
        if (capturing && activeMode == SpeakingOptions.RECORD) { finish(); return; }
        if (liveRequested) { cancel(); return; }
        if (mode == SpeakingOptions.HOLD) { if (owner == o && activeMode == mode && capturing) finish(); }
        else toggle(o, mode);
    }
    void toggle(Object o, int mode) {
        if (finishing) return;
        if (activeMode == SpeakingOptions.RECORD && capturing) { finish(); return; }
        if (liveRequested) { cancel(); return; }
        if (mode == SpeakingOptions.LIVE) startLive(o); else startRecord(o, SpeakingOptions.RECORD);
    }
    private boolean begin(Object o, int mode) {
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { status("Open Outer Voice to allow microphone access"); return false; }
        if (recorder.active() || live.isActive()) { status("Microphone is finishing; try again"); return false; }
        cancel(); AudioOwner.claim(this, this::cancel); owner = o; activeMode = mode;
        if (mode == SpeakingOptions.LIVE) return true; // Same no-focus-stealing policy as MiclessKaraoke.
        focused = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!focused) { release(); status("Audio is busy; try again"); }
        return focused;
    }
    private void startRecord(Object o, int mode) {
        if (!begin(o, mode)) return;
        final int token = epoch; milliseconds = 0; capturing = true;
        status("Recording • " + (mode == SpeakingOptions.HOLD ? "release to play" : "tap again to play"));
        File file = new File(app.getCacheDir(), "session-voice-" + UUID.randomUUID() + ".wav");
        recorder.start(file, new WavRecorder.Listener() {
            public void meter(double rms, long ms) { main.post(() -> { if (token == epoch && capturing) { level = rms; milliseconds = ms; changed(); } }); }
            public void done(File completed, String error) { main.post(() -> {
                if (token != epoch) { if (completed != null) completed.delete(); return; }
                capturing = false; finishing = false; level = 0;
                if (completed == null || error != null) { if (completed != null) completed.delete(); release(); status(error == null ? "Recording cancelled" : error); return; }
                temporary = completed; playReady(completed, "recording", token, 0);
            }); }
        });
    }
    private void finish() { capturing = false; finishing = true; recorder.stop(); status("Saving WAV…"); }
    private void startLive(Object o) {
        if (!begin(o, SpeakingOptions.LIVE)) return;
        liveRequested = true; status("Starting Live Speak…");
        try { if (!live.start()) throw new IllegalStateException("Microphone is busy"); }
        catch (RuntimeException e) { liveRequested = false; release(); status(e.getMessage()); }
    }
    void playSaved(Object o, String id, String name) {
        // Saved sound identifiers are local UUIDs; no arbitrary file paths from BLE.
        if (id == null || !id.matches("[a-zA-Z0-9_-]+")) { status("Choose a saved sound first"); return; }
        File file = new File(app.getFilesDir(), id + ".wav");
        if (!file.isFile()) { status("Assigned sound was removed; choose another sound"); return; }
        cancel(); AudioOwner.claim(this, this::cancel); owner = o;
        focused = audio.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!focused) { release(); status("Audio is busy; try again"); return; }
        playReady(file, name, epoch, 0);
    }
    private void playReady(File file, String name, int token, int attempt) {
        if (token != epoch) return;
        if (player.active()) {
            if (attempt < 100) main.postDelayed(() -> playReady(file, name, token, attempt + 1), 20);
            else { release(); deleteTemporary(); status("Playback is busy; try again"); }
            return;
        }
        status("Playing " + name);
        try {
            if (!player.play(file, error -> main.post(() -> { if (token == epoch) { release(); deleteTemporary(); status(error == null ? "Ready to speak" : error); } }))) throw new IllegalStateException("Playback busy");
        } catch (RuntimeException e) { release(); deleteTemporary(); status(e.getMessage()); }
    }
    void cancelIfOwned(Object o) { if (owner == o) cancel(); }
    void cancel() {
        epoch++; capturing = finishing = liveRequested = false; level = 0;
        recorder.cancel(); player.stop(); live.stop(); deleteTemporary(); release(); owner = null;
        message = ""; changed();
    }
    private void deleteTemporary() { if (temporary != null) temporary.delete(); temporary = null; }
    private void release() { if (focused) audio.abandonAudioFocusRequest(focus); focused = false; AudioOwner.release(this); }
}
