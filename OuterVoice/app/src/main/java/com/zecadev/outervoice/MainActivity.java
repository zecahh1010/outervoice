package com.zecadev.outervoice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(15, 23, 28), SURFACE = Color.rgb(24, 35, 41);
    private static final int TEAL = Color.rgb(0, 198, 199), MUTED = Color.rgb(174, 188, 199);
    private static final int LINE = Color.rgb(48, 65, 74), WHITE = Color.rgb(246, 249, 250);
    private static final int MIC_REQUEST = 10, WAV_REQUEST = 11, STORAGE_REQUEST = 12;
    private final Handler main = new Handler();
    private final ArrayList<Sound> sounds = new ArrayList<>();
    private final ArrayList<TextView> rows = new ArrayList<>();
    private final ArrayList<View> bars = new ArrayList<>();
    private Typeface icons;
    private Typeface bodyFont;
    private LiveMicPassthrough live;
    private WavPlayer player;
    private final WavRecorder recorder = new WavRecorder();
    private boolean recordMode;
    private File temporaryRecording;
    private int recordEpoch;
    private long recordMilliseconds;
    private TextView tempPlay, addRecord, recordInfo;
    private File browseDirectory;
    private AudioManager audioManager;
    private AudioFocusRequest focus;
    private boolean hasFocus, paused, importing;
    private String page = "home", playingId = "", feedback = "";
    private String lastHomeState = "";
    private LinearLayout root;
    private TextView micButton, micLabel, micStatus, routeStatus, importLabel, saveButton;
    private EditText nameInput;
    private File pendingFile;
    private double level;
    private float scale = 1f;
    private int importEpoch;
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            updateHome();
            if (!live.isActive() && !player.active() && !recorder.active()) abandonFocus();
            if (!paused) main.postDelayed(this, 120);
        }
    };
    private static final class Sound {
        final String id, name;
        Sound(String id, String name) { this.id = id; this.name = name; }
    }

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        scale = Math.min(getResources().getDisplayMetrics().widthPixels / 1024f,
                getResources().getDisplayMetrics().heightPixels / 600f);
        icons = Typeface.createFromAsset(getAssets(), "MaterialIcons-Regular.ttf");
        bodyFont = Typeface.createFromAsset(getAssets(), "Inter.ttf");
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        focus = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(change -> {
                    if (change < 0) { stopAudio(); feedback = "Audio interrupted"; updateHome(); }
                }, main).build();
        live = new LiveMicPassthrough(this, new LiveMicPassthrough.Listener() {
            @Override public void onStatus(String message) {
                main.post(() -> {
                    if (message.contains("failed")) feedback = message;
                    else if (message.contains("Starting")) feedback = "Starting…";
                    else feedback = "";
                    updateHome();
                });
            }
            @Override public void onLevel(double rms) { main.post(() -> level = rms); }
        });
        player = new WavPlayer(this);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        live.setGain(preferences().getInt("mic_gain", 100) / 100f);
        cleanupTemporary();
        loadSounds();
        showHome();
    }

    private int px(float value) { return Math.round(value * scale); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.HORIZONTAL); v.setGravity(Gravity.CENTER_VERTICAL); return v; }
    private TextView text(String value, float size, int color, boolean bold) {
        TextView v = new TextView(this); v.setText(value); v.setTextColor(color);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size));
        v.setTypeface(Typeface.create(bodyFont, bold ? Typeface.BOLD : Typeface.NORMAL));
        v.setGravity(Gravity.CENTER_VERTICAL); return v;
    }
    private TextView icon(String code, float size, int color) {
        TextView v = text(code, size, color, false); v.setTypeface(icons); v.setGravity(Gravity.CENTER); return v;
    }
    private GradientDrawable shape(int fill, int stroke, float radius, boolean circle) {
        GradientDrawable d = new GradientDrawable(); d.setColor(fill);
        if (circle) d.setShape(GradientDrawable.OVAL); else d.setCornerRadius(px(radius));
        if (stroke != 0) d.setStroke(px(circle ? 5 : 1), stroke); return d;
    }
    private void clickable(View view, int fill, int stroke, float radius, boolean circle) {
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(Color.argb(70, 255, 255, 255)),
                shape(fill, stroke, radius, circle), null));
        view.setClickable(true); view.setFocusable(true);
    }
    private TextView button(String label, boolean primary) {
        TextView v = text(label, 22, WHITE, true); v.setGravity(Gravity.CENTER);
        clickable(v, primary ? TEAL : SURFACE, primary ? 0 : LINE, 10, false);
        v.setPadding(px(20), 0, px(20), 0); return v;
    }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, px(height))); }
    private LinearLayout startPage(String title, boolean back) {
        root = column(); root.setBackgroundColor(BG); setContentView(root);
        LinearLayout header = row(); header.setPadding(px(26), 0, px(26), 0);
        header.setBackgroundColor(SURFACE);
        root.addView(header, new LinearLayout.LayoutParams(-1, px(68)));
        if (back) {
            TextView arrow = icon("\ue5c4", 32, WHITE); arrow.setContentDescription("Back");
            clickable(arrow, Color.TRANSPARENT, 0, 8, false);
            header.addView(arrow, new LinearLayout.LayoutParams(px(56), px(56)));
            arrow.setOnClickListener(v -> cancelAdd());
        }
        TextView heading = text(title, 34, WHITE, true);
        if (!back) {
            SpannableString styled = new SpannableString(title);
            styled.setSpan(new ForegroundColorSpan(TEAL), 6, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            heading.setText(styled);
        }
        header.addView(heading, new LinearLayout.LayoutParams(0, -1, 1f));
        return header;
    }
    private void showHome() {
        page = "home"; rows.clear(); bars.clear(); lastHomeState = "";
        LinearLayout header = startPage("Outer Voice", false);
        TextView audioSettings = icon("\ue050", 30, TEAL);
        audioSettings.setContentDescription("Audio settings and diagnostics");
        clickable(audioSettings, Color.TRANSPARENT, 0, 8, false);
        header.addView(audioSettings, new LinearLayout.LayoutParams(px(48), px(48)));
        audioSettings.setOnClickListener(v -> showAudioSettings());
        routeStatus = text("Outer speaker", 18, MUTED, true);
        header.addView(routeStatus, new LinearLayout.LayoutParams(px(170), px(48)));
        TextView info = icon("\ue88e", 32, WHITE); info.setContentDescription("App information and credits");
        clickable(info, Color.TRANSPARENT, 0, 8, false);
        header.addView(info, new LinearLayout.LayoutParams(px(56), px(56)));
        info.setOnClickListener(v -> { stopAudio(); showCredits(); });
        LinearLayout body = row(); body.setGravity(Gravity.TOP);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout left = column(); left.setGravity(Gravity.CENTER_HORIZONTAL);
        left.setPadding(px(24), px(22), px(24), px(22));
        body.addView(left, new LinearLayout.LayoutParams(px(352), -1));
        LinearLayout modes = row();
        TextView speakingMode = text("Live Speaking", 17, WHITE, true);
        TextView recordingMode = text("Record & Play", 17, WHITE, true);
        for (TextView mode : new TextView[]{speakingMode, recordingMode}) {
            mode.setGravity(Gravity.CENTER); modes.addView(mode, new LinearLayout.LayoutParams(0, px(48), 1f));
        }
        clickable(speakingMode, !recordMode ? TEAL : SURFACE, 0, 8, false);
        clickable(recordingMode, recordMode ? TEAL : SURFACE, 0, 8, false);
        speakingMode.setOnClickListener(v -> switchMode(false)); recordingMode.setOnClickListener(v -> switchMode(true));
        left.addView(modes, new LinearLayout.LayoutParams(-1, px(48))); space(left, 16);
        micButton = icon("\ue029", 100, WHITE);
        clickable(micButton, SURFACE, TEAL, 0, true);
        left.addView(micButton, new LinearLayout.LayoutParams(px(160), px(160)));
        micButton.setOnClickListener(v -> { if (recordMode) toggleRecording(false); else toggleMic(); });
        space(left, 6);
        micLabel = text("Start Speaking", 25, WHITE, true); micLabel.setGravity(Gravity.CENTER);
        left.addView(micLabel, new LinearLayout.LayoutParams(-1, px(45)));
        space(left, 5);
        tempPlay = button("Play", false);
        tempPlay.setVisibility(recordMode ? View.VISIBLE : View.GONE);
        left.addView(tempPlay, new LinearLayout.LayoutParams(-1, px(44)));
        tempPlay.setOnClickListener(v -> { if (temporaryRecording != null) playFile(temporaryRecording, "temporary"); });
        space(left, 5);
        LinearLayout meter = row(); meter.setGravity(Gravity.CENTER);
        for (int i = 0; i < 11; i++) {
            View bar = new View(this); bar.setBackground(shape(LINE, 0, 4, false));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(px(10), px(35));
            params.setMargins(px(4), 0, px(4), 0); meter.addView(bar, params); bars.add(bar);
        }
        left.addView(meter, new LinearLayout.LayoutParams(-1, px(42)));
        micStatus = text("Ready to speak", 18, MUTED, false); micStatus.setGravity(Gravity.CENTER);
        left.addView(micStatus, new LinearLayout.LayoutParams(-1, px(45)));
        View divider = new View(this); divider.setBackgroundColor(LINE);
        body.addView(divider, new LinearLayout.LayoutParams(px(1), -1));
        LinearLayout right = column(); right.setPadding(px(28), px(20), px(24), px(24));
        body.addView(right, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout listHeader = row();
        listHeader.addView(text("Saved sounds", 28, WHITE, true), new LinearLayout.LayoutParams(0, px(52), 1f));
        TextView add = button("+ Add Sound", true);
        listHeader.addView(add, new LinearLayout.LayoutParams(px(184), px(48)));
        add.setOnClickListener(v -> { stopAudio(); showAdd(); });
        right.addView(listHeader); space(right, 18);
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(false); scroll.setVerticalScrollBarEnabled(true); scroll.setScrollbarFadingEnabled(false);
        right.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout entries = column(); scroll.addView(entries);
        if (sounds.isEmpty()) {
            TextView empty = text("No sounds yet\nTap Add Sound to import or record.", 20, MUTED, false);
            entries.addView(empty, new LinearLayout.LayoutParams(-1, px(140)));
        }
        for (Sound sound : sounds) {
            LinearLayout line = row(); line.setPadding(px(20), 0, px(20), 0);
            clickable(line, SURFACE, LINE, 12, false);
            TextView play = icon("\ue037", 36, TEAL);
            play.setBackground(shape(Color.TRANSPARENT, TEAL, 0, true));
            line.addView(play, new LinearLayout.LayoutParams(px(56), px(56)));
            TextView label = text(sound.name, 24, WHITE, true);
            label.setPadding(px(30), 0, 0, 0); label.setMaxLines(2);
            line.addView(label, new LinearLayout.LayoutParams(0, -1, 1f));
            TextView remove = icon("\ue872", 28, MUTED);
            remove.setContentDescription("Remove " + sound.name);
            clickable(remove, Color.TRANSPARENT, 0, 8, false);
            line.addView(remove, new LinearLayout.LayoutParams(px(52), px(60)));
            remove.setOnClickListener(v -> removeSound(sound));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, px(86)); params.bottomMargin = px(10);
            entries.addView(line, params); rows.add(label);
            line.setContentDescription("Play " + sound.name); line.setOnClickListener(v -> playSound(sound));
        }
        updateHome();
    }

    private void updateHome() {
        if (!"home".equals(page) || micButton == null) return;
        boolean speaking = live.isActive(), playing = player.active(), recording = recorder.active();
        boolean available = AudioRoutes.outer(this) != null;
        int lit = (speaking || recording) ? Math.min(11, (int) Math.round(level * 66)) : 0;
        String state = recordMode + "|" + (temporaryRecording != null) + "|" + recording + "|" + recordMilliseconds / 1000 + "|" + speaking + "|" + playing + "|" + available + "|" + playingId + "|" + feedback + "|" + lit;
        if (state.equals(lastHomeState)) return;
        lastHomeState = state;
        String labelText = recordMode ? (recording ? "Stop Recording" : "Start Recording") : (speaking ? "Stop Speaking" : "Start Speaking");
        micLabel.setText(labelText); micButton.setContentDescription(labelText);
        if (recordMode) {
            tempPlay.setText(playing && "temporary".equals(playingId) ? "Stop" : "Play");
            tempPlay.setEnabled(temporaryRecording != null && !recording && !speaking);
            tempPlay.setAlpha(tempPlay.isEnabled() ? 1f : .4f);
        }
        clickable(micButton, (speaking || recording) ? TEAL : SURFACE, TEAL, 0, true);
        micStatus.setText(!feedback.isEmpty() ? feedback : recording ? "Recording " + recordMilliseconds / 1000 + "s / 180s" : speaking ? "Speaking now" : playing ? "Playing sound…" : recordMode ? (temporaryRecording == null ? "Record a temporary clip" : "Clip ready • temporary") : "Ready to speak");
        routeStatus.setText(available ? "Outer speaker" : "Speaker unavailable");
        routeStatus.setTextColor(available ? WHITE : MUTED);
        for (int i = 0; i < bars.size(); i++) bars.get(i).setBackground(shape(i < lit ? TEAL : LINE, 0, 4, false));
        for (int i = 0; i < rows.size(); i++) {
            TextView label = rows.get(i); View line = (View) label.getParent();
            boolean enabled = !speaking && !recording && (!playing || sounds.get(i).id.equals(playingId));
            line.setEnabled(enabled); line.setAlpha(enabled ? 1f : 0.4f);
            ((TextView) ((LinearLayout) line).getChildAt(0)).setText(playing && sounds.get(i).id.equals(playingId) ? "\ue047" : "\ue037");
        }
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (speaking || playing || recording) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    private boolean acquireFocus() {
        if (hasFocus) return true;
        hasFocus = audioManager != null && audioManager.requestAudioFocus(focus) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        if (!hasFocus) toast("Audio is busy; try again"); return hasFocus;
    }
    private void abandonFocus() {
        if (hasFocus && audioManager != null) audioManager.abandonAudioFocusRequest(focus);
        hasFocus = false;
    }
    private void toggleMic() {
        if (live.isActive()) { live.stop(); feedback = "Stopping…"; updateHome(); return; }
        if (player.active() || recorder.active()) { toast("Stop the current audio first"); return; }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST); return;
        }
        if (!acquireFocus()) return;
        try { feedback = ""; live.start(); } catch (RuntimeException e) { abandonFocus(); feedback = e.getMessage(); AudioDiagnostics.log(feedback); toast(feedback); }
        updateHome();
    }
    private void playSound(Sound sound) { playFile(new File(getFilesDir(), sound.id + ".wav"), sound.id); }
    private void playFile(File file, String id) {
        if (live.isActive() || recorder.active()) return;
        if (player.active()) { if (id.equals(playingId)) player.stop(); return; }
        if (!acquireFocus()) return;
        try {
            playingId = id; feedback = "";
            player.play(file, error -> main.post(() -> {
                playingId = ""; if (error != null) { feedback = error; toast(error); } updateHome();
            }));
        } catch (RuntimeException e) { playingId = ""; abandonFocus(); feedback = e.getMessage(); AudioDiagnostics.log(feedback); toast(feedback); }
        updateHome();
    }
    private void stopAudio() {
        live.stop(); player.stop(); recorder.cancel(); recordEpoch++; abandonFocus(); level = 0;
        if ("add".equals(page) && addRecord != null) {
            addRecord.setText("Record microphone"); saveButton.setEnabled(!importing); saveButton.setAlpha(importing ? .4f : 1f);
            recordInfo.setText("44.1 kHz mono PCM16 • up to 180 seconds");
        }
    }
    private boolean microphoneAllowed() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) return true;
        requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MIC_REQUEST); return false;
    }
    private void switchMode(boolean next) {
        if (recordMode == next) return;
        stopAudio(); deleteTemporary(); recordMode = next; feedback = ""; showHome();
    }
    private void deleteTemporary() { if (temporaryRecording != null) temporaryRecording.delete(); temporaryRecording = null; }
    private void cleanupTemporary() {
        File[] files = getCacheDir().listFiles((d, name) -> name.startsWith("voice-") || name.equals("outer-test.wav"));
        if (files != null) for (File file : files) file.delete();
    }
    private void toggleRecording(boolean forSound) {
        if (recorder.active()) { recorder.stop(); if (forSound) addRecord.setText("Finishing…"); return; }
        if (live.isActive() || player.active()) { toast("Stop playback first"); return; }
        if (!microphoneAllowed() || !acquireFocus()) return;
        if (!forSound) deleteTemporary();
        int epoch = ++recordEpoch; recordMilliseconds = 0; feedback = "";
        File file = new File(getCacheDir(), "voice-" + UUID.randomUUID() + ".wav");
        recorder.start(file, new WavRecorder.Listener() {
            public void meter(double rms, long milliseconds) { main.post(() -> {
                if (epoch != recordEpoch) return;
                level = rms; recordMilliseconds = milliseconds;
                if (forSound && "add".equals(page)) recordInfo.setText("Recording " + milliseconds / 1000 + "s / 180s");
                updateHome();
            }); }
            public void done(File completed, String error) { main.post(() -> {
                if (epoch != recordEpoch || isFinishing()) { if (completed != null) completed.delete(); return; }
                abandonFocus(); level = 0;
                if (error != null) { feedback = error; toast(error); }
                if (forSound && "add".equals(page)) {
                    addRecord.setText("Record microphone"); saveButton.setEnabled(true); saveButton.setAlpha(1f);
                    recordInfo.setText(error != null ? error : completed == null ? "Recording cancelled" : "Recorded " + recordMilliseconds / 1000 + "s • 44.1 kHz mono PCM16");
                    if (completed != null) { if (pendingFile != null) pendingFile.delete(); pendingFile = completed; importLabel.setText("Microphone recording.wav"); }
                } else if (completed != null) temporaryRecording = completed;
                updateHome();
            }); }
        });
        if (forSound) { addRecord.setText("Stop Recording"); saveButton.setEnabled(false); saveButton.setAlpha(.4f); }
        updateHome();
    }
    private void removeSound(Sound sound) {
        new AlertDialog.Builder(this).setTitle("Remove sound?").setMessage(sound.name)
            .setNegativeButton("Cancel", null).setPositiveButton("Remove", (dialog, which) -> {
                stopAudio(); int index = sounds.indexOf(sound); sounds.remove(sound);
                if (!persistSounds()) { sounds.add(index, sound); toast("Could not remove sound"); return; }
                new File(getFilesDir(), sound.id + ".wav").delete(); showHome();
            }).show();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(request, permissions, result);
        boolean granted = result.length > 0 && result[0] == PackageManager.PERMISSION_GRANTED;
        if (request == MIC_REQUEST) toast(granted ? "Microphone allowed. Tap again to start." : "Microphone permission is required");
        if (request == STORAGE_REQUEST) { if (granted) browseFiles(null); else toast("File access denied. You can still record a sound."); }
    }

    private void showAdd() {
        page = "add"; feedback = ""; startPage("Add Sound", true);
        LinearLayout form = column(); form.setPadding(px(40), px(20), px(40), px(20));
        root.addView(form, new LinearLayout.LayoutParams(-1, 0, 1f));
        form.addView(text("Button name", 26, WHITE, true)); space(form, 10);
        nameInput = new EditText(this); nameInput.setSingleLine(true); nameInput.setTextColor(WHITE);
        nameInput.setHintTextColor(MUTED); nameInput.setHint("Enter a sound name");
        nameInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24));
        nameInput.setTypeface(Typeface.create(bodyFont, Typeface.BOLD));
        nameInput.setPadding(px(20), 0, px(20), 0); nameInput.setBackground(shape(SURFACE, LINE, 10, false));
        nameInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(60)});
        nameInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        nameInput.setOnEditorActionListener((view, action, event) -> {
            if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
            android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            if (keyboard != null) keyboard.hideSoftInputFromWindow(nameInput.getWindowToken(), 0);
            return true;
        });
        form.addView(nameInput, new LinearLayout.LayoutParams(-1, px(62))); space(form, 20);
        form.addView(text("Import or record", 26, WHITE, true)); space(form, 10);
        LinearLayout sources = row();
        TextView importButton = button("Import WAV", false);
        addRecord = button("Record microphone", false);
        sources.addView(importButton, new LinearLayout.LayoutParams(0, px(64), 1f));
        LinearLayout.LayoutParams recordParams = new LinearLayout.LayoutParams(0, px(64), 1f); recordParams.leftMargin = px(16);
        sources.addView(addRecord, recordParams); form.addView(sources);
        importButton.setOnClickListener(v -> chooseWav()); addRecord.setOnClickListener(v -> {
            if (importing) { toast("Wait for import to finish"); return; } toggleRecording(true);
        });
        importLabel = text("PCM16 WAV • up to 20 MB", 19, MUTED, false);
        form.addView(importLabel, new LinearLayout.LayoutParams(-1, px(40)));
        recordInfo = text("Recording: 44.1 kHz mono PCM16 • up to 180 seconds", 18, MUTED, false);
        form.addView(recordInfo, new LinearLayout.LayoutParams(-1, px(36)));
        TextView browse = text("Browse files on this device", 19, TEAL, true);
        browse.setOnClickListener(v -> browseFiles(null)); form.addView(browse, new LinearLayout.LayoutParams(-1, px(40)));
        form.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout actions = row(); actions.setGravity(Gravity.RIGHT);
        TextView cancel = button("Cancel", false); saveButton = button("Save", true);
        actions.addView(cancel, new LinearLayout.LayoutParams(px(238), px(74)));
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(px(224), px(74)); saveParams.leftMargin = px(20);
        actions.addView(saveButton, saveParams); form.addView(actions);
        cancel.setOnClickListener(v -> cancelAdd()); saveButton.setOnClickListener(v -> saveSound());
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != WAV_REQUEST || result != RESULT_OK || data == null || data.getData() == null || !"add".equals(page)) return;
        importWav(data.getData());
    }
    private void importWav(final Uri uri) {
        if (!"add".equals(page) || recorder.active()) return;
        final int epoch = ++importEpoch;
        importing = true; saveButton.setEnabled(false); saveButton.setAlpha(0.4f); importLabel.setText("Importing and checking WAV…");
        new Thread(() -> {
            File copied = new File(getFilesDir(), "pending-" + UUID.randomUUID() + ".wav");
            String error = null;
            try (InputStream input = openWav(uri); FileOutputStream output = new FileOutputStream(copied)) {
                if (input == null) throw new java.io.IOException("Cannot open the selected file");
                byte[] bytes = new byte[8192]; long total = 0; int count;
                while ((count = input.read(bytes)) != -1) {
                    total += count; if (total > 20L * 1024 * 1024) throw new java.io.IOException("WAV must be 20 MB or smaller");
                    output.write(bytes, 0, count);
                }
            } catch (Exception e) { error = e.getMessage(); }
            if (error == null) try { WavFormat.read(copied); } catch (Exception e) { error = e.getMessage(); }
            final String failure = error;
            main.post(() -> {
                if (epoch != importEpoch || !"add".equals(page) || isFinishing()) { copied.delete(); return; }
                importing = false; saveButton.setEnabled(true); saveButton.setAlpha(1f);
                if (failure != null) { copied.delete(); importLabel.setText(failure); return; }
                if (pendingFile != null) pendingFile.delete(); pendingFile = copied;
                String filename = "file".equals(uri.getScheme()) ? new File(uri.getPath()).getName() : "Imported WAV";
                try (android.database.Cursor cursor = getContentResolver().query(uri, new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) filename = cursor.getString(0);
                } catch (Exception ignored) { }
                importLabel.setText(filename);
                if (nameInput.getText().toString().trim().isEmpty()) nameInput.setText(filename.replaceFirst("(?i)\\.wav$", ""));
            });
        }, "wav-import").start();
    }
    private void saveSound() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) { nameInput.setError("Enter a button name"); return; }
        if (pendingFile == null || importing || recorder.active()) { toast("Import or record a WAV first"); return; }
        String id = UUID.randomUUID().toString(); File target = new File(getFilesDir(), id + ".wav");
        if (!pendingFile.renameTo(target)) { toast("Could not save sound"); return; }
        Sound sound = new Sound(id, name); sounds.add(sound);
        if (!persistSounds()) { sounds.remove(sound); target.renameTo(pendingFile); toast("Could not save sound list"); return; }
        pendingFile = null; showHome();
    }
    private void cancelAdd() {
        stopAudio(); importEpoch++; importing = false;
        if (pendingFile != null) pendingFile.delete(); pendingFile = null;
        showHome();
    }
    private SharedPreferences preferences() { return getSharedPreferences("sounds", MODE_PRIVATE); }
    private void loadSounds() {
        try {
            JSONArray array = new JSONArray(preferences().getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i); String id = item.getString("id");
                if (id.matches("[0-9a-f-]{36}") && new File(getFilesDir(), id + ".wav").isFile()) sounds.add(new Sound(id, item.getString("name")));
            }
        } catch (Exception e) { toast("Could not read saved sounds"); }
        File[] leftovers = getFilesDir().listFiles((directory, name) -> name.startsWith("pending-"));
        if (leftovers != null) for (File file : leftovers) file.delete();
    }
    private boolean persistSounds() {
        JSONArray array = new JSONArray();
        try {
            for (Sound sound : sounds) { JSONObject item = new JSONObject(); item.put("id", sound.id); item.put("name", sound.name); array.put(item); }
            return preferences().edit().putString("items", array.toString()).commit();
        } catch (Exception e) { return false; }
    }
    private void showCredits() {
        page = "credits"; startPage("Credits", true);
        LinearLayout content = column(); content.setPadding(px(180), px(25), px(180), px(24));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        TextView title = text("Outer Voice", 72, WHITE, true); title.setGravity(Gravity.CENTER);
        SpannableString styledTitle = new SpannableString("Outer Voice");
        styledTitle.setSpan(new ForegroundColorSpan(TEAL), 6, styledTitle.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(styledTitle);
        content.addView(title, new LinearLayout.LayoutParams(-1, px(120)));
        String version = "";
        try { android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0); version = info.versionName + " (" + info.getLongVersionCode() + ")"; } catch (Exception ignored) { }
        String[][] credits = {{"App version", version}, {"Developer", "Zeca"}, {"Tester", "SL"}, {"Contributors", "Chris, j.Lun"}, {"License", "MIT"}};
        for (String[] credit : credits) {
            LinearLayout line = row();
            line.addView(text(credit[0], 24, WHITE, true), new LinearLayout.LayoutParams(0, px(56), 1f));
            TextView value = text(credit[1], 23, WHITE, false); value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            line.addView(value, new LinearLayout.LayoutParams(0, px(56), 1f)); content.addView(line);
            View border = new View(this); border.setBackgroundColor(LINE); content.addView(border, new LinearLayout.LayoutParams(-1, px(1)));
            if ("License".equals(credit[0])) { line.setOnClickListener(v -> showLicense()); line.setClickable(true); }
        }
        space(content, 22);
        TextView note = text("Microphone and WAV playback to the outer speaker", 20, MUTED, false); note.setGravity(Gravity.CENTER); content.addView(note);
    }
    private void showLicense() {
        try (InputStream input = getAssets().open("LICENSE.txt")) {
            java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream(); byte[] buffer = new byte[4096]; int count;
            while ((count = input.read(buffer)) > 0) bytes.write(buffer, 0, count);
            new AlertDialog.Builder(this).setTitle("Open-source license").setMessage(bytes.toString("UTF-8")).setPositiveButton("Close", null).show();
        } catch (Exception e) { toast("License unavailable"); }
    }
    private InputStream openWav(Uri uri) throws Exception {
        return "file".equals(uri.getScheme()) ? new java.io.FileInputStream(new File(uri.getPath())) : getContentResolver().openInputStream(uri);
    }
    private void chooseWav() {
        if (recorder.active() || importing) { toast("Stop recording or wait for import first"); return; }
        for (String action : new String[]{Intent.ACTION_OPEN_DOCUMENT, Intent.ACTION_GET_CONTENT}) {
            Intent intent = new Intent(action).setType(Intent.ACTION_GET_CONTENT.equals(action) ? "audio/wav" : "*/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try { startActivityForResult(intent, WAV_REQUEST); return; }
            catch (android.content.ActivityNotFoundException e) { AudioDiagnostics.log("Picker absent: " + action); }
            catch (RuntimeException e) { AudioDiagnostics.log("Picker failed: " + e.getMessage()); }
        }
        browseFiles(null);
    }
    private void browseFiles(File directory) {
        if (recorder.active() || importing || !"add".equals(page)) { toast("Stop recording or wait for import first"); return; }
        String permission = android.os.Build.VERSION.SDK_INT >= 33 ? "android.permission.READ_MEDIA_AUDIO" : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{permission}, STORAGE_REQUEST); return;
        }
        ArrayList<File> entries = new ArrayList<>(); ArrayList<String> labels = new ArrayList<>();
        if (directory == null) {
            addLocation(entries, labels, android.os.Environment.getExternalStorageDirectory(), "Internal storage");
            File[] appLocations = getExternalFilesDirs(null);
            if (appLocations != null) for (File appLocation : appLocations) {
                if (appLocation == null) continue;
                String path = appLocation.getAbsolutePath(); int androidPart = path.indexOf("/Android/");
                if (androidPart > 0) addLocation(entries, labels, new File(path.substring(0, androidPart)), "Storage");
            }
            File[] mounts = new File("/storage").listFiles();
            if (mounts != null) for (File mount : mounts) {
                if (!mount.getName().equals("emulated") && !mount.getName().equals("self")) addLocation(entries, labels, mount, "Storage");
            }
        } else {
            File[] children = directory.listFiles(file -> file.isDirectory() || file.getName().toLowerCase(java.util.Locale.US).endsWith(".wav"));
            if (children != null) {
                java.util.Arrays.sort(children, (a, b) -> a.isDirectory() != b.isDirectory() ? (a.isDirectory() ? -1 : 1) : a.getName().compareToIgnoreCase(b.getName()));
                for (File child : children) { entries.add(child); labels.add((child.isDirectory() ? "Folder: " : "WAV: ") + child.getName()); }
            }
        }
        browseDirectory = directory;
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(directory == null ? "Choose storage" : directory.getAbsolutePath())
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                File selected = entries.get(which);
                if (selected.isDirectory()) browseFiles(selected); else importWav(Uri.fromFile(selected));
            }).setNegativeButton("Cancel", null).setNeutralButton(directory == null ? "Help" : "Up", (d, which) -> {
                if (directory == null) toast("Copy a PCM16 WAV to Download or USB storage. Record microphone is also available.");
                else browseFiles(directory.getParentFile() != null && directory.getParentFile().canRead() ? directory.getParentFile() : null);
            }).create();
        dialog.show();
        if (entries.isEmpty()) toast(directory == null ? "No readable storage found. You can record a sound instead." : "No folders or WAV files here; use Up to choose another location.");
    }
    private void addLocation(ArrayList<File> files, ArrayList<String> names, File file, String name) {
        if (!file.isDirectory() || !file.canRead()) return;
        for (File existing : files) try { if (existing.getCanonicalPath().equals(file.getCanonicalPath())) return; } catch (Exception ignored) { }
        files.add(file); names.add(name + ": " + file.getAbsolutePath());
    }
    private void showAudioSettings() {
        LinearLayout panel = column(); panel.setPadding(px(24), px(8), px(24), px(8));
        TextView volumeLabel = text("", 20, WHITE, true);
        panel.addView(volumeLabel);
        android.widget.SeekBar volume = new android.widget.SeekBar(this);
        volume.setMax(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        volume.setProgress(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC));
        volumeLabel.setText("System media volume: " + volume.getProgress() + "/" + volume.getMax());
        volume.setEnabled(!audioManager.isVolumeFixed());
        panel.addView(volume, new LinearLayout.LayoutParams(-1, px(44)));
        volume.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar v) { }
            public void onStopTrackingTouch(android.widget.SeekBar v) { AudioDiagnostics.log("Media volume requested=" + v.getProgress()); }
            public void onProgressChanged(android.widget.SeekBar v, int value, boolean user) {
                if (user) try { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0); } catch (RuntimeException e) { toast("Firmware rejected volume change"); }
                volumeLabel.setText("System media volume: " + audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + v.getMax());
            }
        });
        int initialGain = preferences().getInt("mic_gain", 100);
        TextView gainLabel = text("Live microphone level: " + initialGain + "%", 20, WHITE, true); panel.addView(gainLabel);
        android.widget.SeekBar gain = new android.widget.SeekBar(this); gain.setMax(100); gain.setProgress(initialGain);
        panel.addView(gain, new LinearLayout.LayoutParams(-1, px(44)));
        gain.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(android.widget.SeekBar v) { }
            public void onStopTrackingTouch(android.widget.SeekBar v) { preferences().edit().putInt("mic_gain", v.getProgress()).apply(); }
            public void onProgressChanged(android.widget.SeekBar v, int value, boolean user) { live.setGain(value / 100f); gainLabel.setText("Live microphone level: " + value + "%"); }
        });
        TextView note = text("BUS12 only • firmware controls amplifier volume\nWAVs use peak normalization (up to ×16).", 17, MUTED, false);
        panel.addView(note, new LinearLayout.LayoutParams(-1, px(60)));
        LinearLayout actions = row();
        TextView test = button("Test speaker", false), report = button("Diagnostics", false);
        actions.addView(test, new LinearLayout.LayoutParams(0, px(54), 1f)); actions.addView(report, new LinearLayout.LayoutParams(0, px(54), 1f)); panel.addView(actions);
        test.setOnClickListener(v -> {
            if (live.isActive() || recorder.active() || player.active()) { toast("Stop current audio first"); return; }
            try { playFile(WavTools.testTone(getCacheDir()), "test"); } catch (Exception e) { toast(e.getMessage()); }
        });
        report.setOnClickListener(v -> showDiagnostics());
        new AlertDialog.Builder(this).setTitle("Audio settings").setView(panel).setPositiveButton("Close", null).show();
    }
    private void showDiagnostics() {
        String report = AudioDiagnostics.report(this);
        ScrollView scroll = new ScrollView(this); TextView content = text(report, 16, WHITE, false);
        content.setTextIsSelectable(true); content.setPadding(px(16), px(8), px(16), px(8)); scroll.addView(content);
        new AlertDialog.Builder(this).setTitle("Audio diagnostics").setView(scroll).setPositiveButton("Close", null)
            .setNeutralButton("Copy report", (dialog, which) -> {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Outer Voice audio report", report)); toast("Report copied");
            }).show();
    }
    private void toast(String message) { Toast.makeText(this, message == null ? "Operation failed" : message, Toast.LENGTH_LONG).show(); }
    @Override public void onBackPressed() { if (!"home".equals(page)) cancelAdd(); else { stopAudio(); super.onBackPressed(); } }
    @Override protected void onResume() { super.onResume(); paused = false; main.removeCallbacks(refresh); main.post(refresh); }
    @Override protected void onPause() { paused = true; main.removeCallbacks(refresh); if (live != null) { stopAudio(); deleteTemporary(); if ("add".equals(page) && addRecord != null) { addRecord.setText("Record microphone"); saveButton.setEnabled(!importing); saveButton.setAlpha(importing ? .4f : 1f); recordInfo.setText("44.1 kHz mono PCM16 • up to 180 seconds"); } } super.onPause(); }
    @Override protected void onDestroy() { importEpoch++; if (live != null) stopAudio(); deleteTemporary(); main.removeCallbacksAndMessages(null); if (pendingFile != null) pendingFile.delete(); super.onDestroy(); }
}
