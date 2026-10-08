package com.zecadev.outervoice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.Collections;

/** Native settings screen based on the approved first mockup. */
public final class FloatingButtonsActivity extends Activity {
    private static final int BG = 0xff0f171c, SURFACE = 0xff182329, LINE = 0xff30414a;
    private static final int WHITE = 0xfff6f9fa, MUTED = 0xffaebcc7, TEAL = FloatingConfig.TEAL;
    private static final int MICROPHONE = 20, OVERLAY = 21;
    private FloatingConfig config;
    private FloatingConfig.Item editing;
    private Typeface font, icons;
    private float scale;
    private LinearLayout list, customization, preview;
    private TextView sizeValue;
    private CheckBox enabled;
    private String micPercentDraft;
    private EditText micPercentInput;
    private boolean requestingOverlay, saved;
    private int px(float value) { return Math.round(value * scale); }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(size)); view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create(font, bold ? Typeface.BOLD : Typeface.NORMAL)); return view;
    }
    private TextView symbol(String value, int size, int color) {
        TextView view = text(value, size, color, false); view.setTypeface(icons); view.setGravity(Gravity.CENTER); return view;
    }
    private GradientDrawable shape(int color, int outline, boolean circle) {
        GradientDrawable drawable = new GradientDrawable(); drawable.setColor(color);
        if (circle) drawable.setShape(GradientDrawable.OVAL); else drawable.setCornerRadius(px(10));
        if (outline != 0) drawable.setStroke(px(2), outline); return drawable;
    }
    private TextView button(String value, boolean primary) {
        TextView view = text(value, 22, primary ? BG : WHITE, true); view.setGravity(Gravity.CENTER);
        view.setBackground(shape(primary ? TEAL : SURFACE, primary ? 0 : LINE, false));
        view.setClickable(true); view.setFocusable(true); return view;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        scale = Math.min(getResources().getDisplayMetrics().widthPixels / 1024f, getResources().getDisplayMetrics().heightPixels / 600f);
        font = Typeface.createFromAsset(getAssets(), "Inter.ttf"); icons = Typeface.createFromAsset(getAssets(), "MaterialIcons-Regular.ttf");
        config = FloatingConfig.load(this); if (!config.items.isEmpty()) editing = config.items.get(0);
        micPercentDraft = String.valueOf(config.micEnlargement);
        if (state != null) {
            // Keep draft changes if Android recreates the configuration screen.
            try {
                org.json.JSONArray draft = new org.json.JSONArray(state.getString("draft", "[]"));
                java.util.ArrayList<FloatingConfig.Item> ordered = new java.util.ArrayList<>();
                for (int i = 0; i < draft.length(); i++) {
                    org.json.JSONObject value = draft.getJSONObject(i);
                    for (FloatingConfig.Item item : config.items) if (item.id.equals(value.getString("id"))) {
                        item.selected = item.isLive() || value.getBoolean("selected"); item.color = value.getInt("color"); item.icon = value.getInt("icon"); ordered.add(item);
                    }
                }
                for (FloatingConfig.Item item : config.items) if (!ordered.contains(item)) ordered.add(item);
                config.items.clear(); config.items.addAll(ordered);
                config.enabled = state.getBoolean("enabled"); config.size = state.getInt("size", 88);
                config.micEnlargement = state.getInt("micEnlargement", 50);
                config.spacing = state.getInt("spacing", 12);
                micPercentDraft = state.getString("micPercentDraft", String.valueOf(config.micEnlargement));
                requestingOverlay = state.getBoolean("requestingOverlay");
                for (FloatingConfig.Item item : config.items) if (item.id.equals(state.getString("editing"))) editing = item;
            } catch (Exception ignored) { }
        }
        // Avoid covering the controls while editing; persisted settings are restored on Cancel.
        stopService(new Intent(this, FloatingPanelService.class));
        build();
    }
    private void build() {
        LinearLayout root = column(); root.setBackgroundColor(BG); setContentView(root);
        LinearLayout header = row(); header.setPadding(px(24), 0, px(24), 0); header.setBackgroundColor(SURFACE);
        TextView back = symbol("\ue5c4", 32, WHITE); back.setContentDescription("Back"); back.setOnClickListener(v -> cancel());
        header.addView(back, new LinearLayout.LayoutParams(px(48), px(56)));
        header.addView(text("Floating Buttons", 34, WHITE, true)); root.addView(header, new LinearLayout.LayoutParams(-1, px(68)));
        LinearLayout settings = row(); settings.setPadding(px(24), 0, px(24), 0);
        enabled = new CheckBox(this); enabled.setText("Enable floating panel"); enabled.setTextColor(WHITE);
        enabled.setTypeface(font); enabled.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(19)); enabled.setButtonTintList(ColorStateList.valueOf(TEAL));
        enabled.setChecked(config.enabled); enabled.setOnCheckedChangeListener((view, checked) -> config.enabled = checked);
        settings.addView(enabled, new LinearLayout.LayoutParams(px(288), px(60)));
        settings.addView(text("Button size", 19, WHITE, false), new LinearLayout.LayoutParams(px(110), px(60)));
        SeekBar slider = new SeekBar(this); slider.setMax((FloatingConfig.MAX_SIZE - FloatingConfig.MIN_SIZE) / 4);
        slider.setProgress((config.size - FloatingConfig.MIN_SIZE) / 4); slider.setProgressTintList(ColorStateList.valueOf(TEAL));
        slider.setContentDescription("Floating button size"); settings.addView(slider, new LinearLayout.LayoutParams(0, px(48), 1f));
        sizeValue = text(config.size + " px", 20, WHITE, true); settings.addView(sizeValue, new LinearLayout.LayoutParams(px(76), px(60)));
        TextView defaultSize = text("Default: 88 px", 16, MUTED, false); settings.addView(defaultSize, new LinearLayout.LayoutParams(px(132), px(60)));
        TextView reset = text("Reset", 19, TEAL, true); reset.setGravity(Gravity.CENTER); settings.addView(reset, new LinearLayout.LayoutParams(px(68), px(60)));
        reset.setOnClickListener(v -> slider.setProgress((88 - FloatingConfig.MIN_SIZE) / 4));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar view) { }
            public void onStopTrackingTouch(SeekBar view) { }
            public void onProgressChanged(SeekBar view, int value, boolean user) {
                config.size = FloatingConfig.MIN_SIZE + value * 4; sizeValue.setText(config.size + " px"); buildPreview();
            }
        }); root.addView(settings, new LinearLayout.LayoutParams(-1, px(60)));
        LinearLayout spacing = row(); spacing.setPadding(px(24), 0, px(24), 0);
        spacing.addView(text("Button spacing", 19, WHITE, false), new LinearLayout.LayoutParams(px(164), px(48)));
        SeekBar gap = new SeekBar(this); gap.setMax(40); gap.setProgress(config.spacing);
        gap.setContentDescription("Floating button spacing"); gap.setProgressTintList(ColorStateList.valueOf(TEAL));
        spacing.addView(gap, new LinearLayout.LayoutParams(0, px(48), 1f));
        TextView gapValue = text(config.spacing + " px", 20, WHITE, true); spacing.addView(gapValue, new LinearLayout.LayoutParams(px(76), px(48)));
        spacing.addView(text("Between edges · default 12 px", 16, MUTED, false), new LinearLayout.LayoutParams(px(280), px(48)));
        gap.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStartTrackingTouch(SeekBar view) { }
            public void onStopTrackingTouch(SeekBar view) { }
            public void onProgressChanged(SeekBar view, int value, boolean user) { config.spacing = value; gapValue.setText(value + " px"); buildPreview(); }
        });
        root.addView(spacing, new LinearLayout.LayoutParams(-1, px(48)));
        LinearLayout body = row(); body.setGravity(Gravity.TOP); body.setPadding(px(24), px(4), px(24), 0);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout left = column(); body.addView(left, new LinearLayout.LayoutParams(0, -1, 1.15f));
        left.addView(text("Panel buttons", 26, WHITE, true), new LinearLayout.LayoutParams(-1, px(40)));
        ScrollView scroll = new ScrollView(this); scroll.setScrollbarFadingEnabled(false); list = column(); scroll.addView(list);
        left.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        View divider = new View(this); divider.setBackgroundColor(LINE); LinearLayout.LayoutParams d = new LinearLayout.LayoutParams(px(1), -1); d.setMargins(px(18), 0, px(24), 0); body.addView(divider, d);
        ScrollView detailScroll = new ScrollView(this); customization = column(); detailScroll.addView(customization);
        body.addView(detailScroll, new LinearLayout.LayoutParams(0, -1, 1f));
        LinearLayout footer = row(); footer.setPadding(px(24), px(10), px(24), px(16));
        root.addView(footer, new LinearLayout.LayoutParams(-1, px(194)));
        LinearLayout previewColumn = column(); footer.addView(previewColumn, new LinearLayout.LayoutParams(0, -1, 1.3f));
        previewColumn.addView(text("Floating panel preview", 21, WHITE, true), new LinearLayout.LayoutParams(-1, px(30)));
        HorizontalScrollView previewScroll = new HorizontalScrollView(this); preview = row(); previewScroll.addView(preview);
        previewColumn.addView(previewScroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        previewColumn.addView(text("Hold Live Speak; release to play. Tap sounds to play.", 13, MUTED, false), new LinearLayout.LayoutParams(-1, px(20)));
        LinearLayout actions = row(); actions.setGravity(Gravity.BOTTOM); LinearLayout.LayoutParams actionSize = new LinearLayout.LayoutParams(0, -1, 1f); actionSize.leftMargin = px(28); footer.addView(actions, actionSize);
        TextView cancel = button("Cancel", false), save = button("Save Settings", true);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, px(56), .8f));
        LinearLayout.LayoutParams saveSize = new LinearLayout.LayoutParams(0, px(56), 1.2f); saveSize.leftMargin = px(12); actions.addView(save, saveSize);
        cancel.setOnClickListener(v -> cancel()); save.setOnClickListener(v -> save());
        buildRows(); buildCustomization(); buildPreview();
    }
    private void buildRows() {
        list.removeAllViews();
        if (config.items.isEmpty()) list.addView(text("No saved sounds yet.\nLive Speak is always available.", 20, MUTED, false));
        for (FloatingConfig.Item item : config.items) {
            LinearLayout line = row(); line.setPadding(px(8), 0, px(8), 0);
            line.setBackground(shape(item == editing ? 0xff113039 : SURFACE, item == editing ? TEAL : LINE, false));
            LinearLayout.LayoutParams lineSize = new LinearLayout.LayoutParams(-1, px(54)); lineSize.bottomMargin = px(6); list.addView(line, lineSize);
            CheckBox check = new CheckBox(this); check.setButtonTintList(ColorStateList.valueOf(TEAL)); check.setContentDescription("Show " + item.name + " in floating panel"); check.setChecked(item.selected);
            if (item.isLive()) { check.setChecked(true); check.setEnabled(false); check.setContentDescription("Live Speak is always included"); }
            line.addView(check, new LinearLayout.LayoutParams(px(40), -1));
            if (!item.isLive()) check.setOnCheckedChangeListener((view, selected) -> { item.selected = selected; editing = item; buildRows(); buildCustomization(); buildPreview(); });
            TextView badge = symbol(item.glyph(), 24, item.isLive() ? WHITE : BG); badge.setBackground(shape(item.buttonColor(), 0, true));
            line.addView(badge, new LinearLayout.LayoutParams(px(36), px(36)));
            TextView name = text(item.name, 20, WHITE, true); name.setPadding(px(12), 0, px(4), 0); name.setMaxLines(2);
            line.addView(name, new LinearLayout.LayoutParams(0, -1, 1f));
            View.OnClickListener edit = v -> { editing = item; buildRows(); buildCustomization(); };
            badge.setOnClickListener(edit); name.setOnClickListener(edit); line.setOnClickListener(edit);
            for (int direction : new int[]{-1, 1}) {
                TextView arrow = symbol(direction < 0 ? "\ue5d8" : "\ue5db", 27, WHITE);
                arrow.setContentDescription((direction < 0 ? "Move up " : "Move down ") + item.name);
                int next = selectedNeighbor(item, direction); arrow.setEnabled(item.selected && next >= 0); arrow.setAlpha(arrow.isEnabled() ? 1f : .3f);
                line.addView(arrow, new LinearLayout.LayoutParams(px(42), -1));
                arrow.setOnClickListener(v -> { int target = selectedNeighbor(item, direction); if (target >= 0) { Collections.swap(config.items, config.items.indexOf(item), target); buildRows(); buildPreview(); } });
            }
        }
    }
    private int selectedNeighbor(FloatingConfig.Item item, int direction) {
        for (int i = config.items.indexOf(item) + direction; i >= 0 && i < config.items.size(); i += direction) if (config.items.get(i).selected) return i;
        return -1;
    }
    private void buildCustomization() {
        customization.removeAllViews();
        micPercentInput = null;
        if (editing == null) return;
        TextView heading = text("Customize " + editing.name, 25, WHITE, true); heading.setMaxLines(2);
        customization.addView(heading, new LinearLayout.LayoutParams(-1, px(36)));
        if (editing.isLive()) {
            customization.addView(text("Microphone icon (fixed)", 21, WHITE, true), new LinearLayout.LayoutParams(-1, px(28)));
            TextView fixedMic = symbol(FloatingConfig.MIC_ICON, 32, TEAL);
            fixedMic.setContentDescription("Fixed microphone icon");
            customization.addView(fixedMic, new LinearLayout.LayoutParams(-1, px(36)));
            customization.addView(text("Enlarge Live Speak button by", 20, WHITE, true), new LinearLayout.LayoutParams(-1, px(32)));
            LinearLayout inputRow = row(); customization.addView(inputRow);
            micPercentInput = new EditText(this); micPercentInput.setSingleLine(true); micPercentInput.setTextColor(WHITE);
            micPercentInput.setTypeface(font); micPercentInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(24));
            micPercentInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            micPercentInput.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(3)});
            micPercentInput.setContentDescription("Live Speak button enlargement percent"); micPercentInput.setText(micPercentDraft);
            inputRow.addView(micPercentInput, new LinearLayout.LayoutParams(px(108), px(48)));
            inputRow.addView(text("%   Default: 50%", 20, WHITE, false));
            micPercentInput.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
            micPercentInput.setOnEditorActionListener((view, action, event) -> {
                if (action != android.view.inputmethod.EditorInfo.IME_ACTION_DONE) return false;
                android.view.inputmethod.InputMethodManager keyboard = (android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                if (keyboard != null) keyboard.hideSoftInputFromWindow(view.getWindowToken(), 0); return true;
            });
            micPercentInput.addTextChangedListener(new android.text.TextWatcher() {
                public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                public void onTextChanged(CharSequence value, int start, int before, int count) {
                    micPercentDraft = value.toString();
                    try { int percent = Integer.parseInt(micPercentDraft);
                        if (percent >= 0 && percent <= FloatingConfig.MAX_MIC_ENLARGEMENT) { config.micEnlargement = percent; buildPreview(); }
                    } catch (NumberFormatException ignored) { }
                }
                public void afterTextChanged(android.text.Editable value) { }
            });
            customization.addView(text("0–100%. Button and microphone grow together.\nAll icons scale with their button size.", 16, MUTED, false), new LinearLayout.LayoutParams(-1, px(44)));
            return;
        }
        customization.addView(text("Button color", 21, WHITE, true), new LinearLayout.LayoutParams(-1, px(34)));
        LinearLayout colors = row(); colors.setGravity(Gravity.TOP); customization.addView(colors);
        for (int i = 0; i < FloatingConfig.COLORS.length; i++) {
            final int value = i; LinearLayout swatch = column(); swatch.setGravity(Gravity.CENTER_HORIZONTAL);
            colors.addView(swatch, new LinearLayout.LayoutParams(0, px(72), 1f));
            TextView circle = text("", 16, BG, false); circle.setBackground(shape(FloatingConfig.COLORS[i], editing.color == i ? TEAL : 0, true));
            circle.setContentDescription("Color " + FloatingConfig.COLOR_NAMES[i]);
            swatch.addView(circle, new LinearLayout.LayoutParams(px(43), px(43)));
            TextView label = text(FloatingConfig.COLOR_NAMES[i], 12, WHITE, false); label.setGravity(Gravity.CENTER);
            swatch.addView(label, new LinearLayout.LayoutParams(-1, px(28)));
            View.OnClickListener select = v -> { editing.color = value; buildRows(); buildCustomization(); buildPreview(); };
            swatch.setOnClickListener(select); circle.setOnClickListener(select);
        }
        customization.addView(text("Button icon", 21, WHITE, true), new LinearLayout.LayoutParams(-1, px(30)));
        LinearLayout choices = row(); customization.addView(choices);
        for (int i = 0; i < FloatingConfig.ICONS.length; i++) {
            final int value = i; LinearLayout tile = column(); tile.setGravity(Gravity.CENTER);
            tile.setBackground(shape(SURFACE, editing.icon == i ? TEAL : LINE, false));
            LinearLayout.LayoutParams tileSize = new LinearLayout.LayoutParams(0, px(96), 1f); if (i > 0) tileSize.leftMargin = px(8); choices.addView(tile, tileSize);
            TextView glyph = symbol(FloatingConfig.ICONS[i], 37, editing.icon == i ? FloatingConfig.COLORS[editing.color] : WHITE);
            tile.addView(glyph, new LinearLayout.LayoutParams(-1, px(48)));
            TextView label = text(FloatingConfig.ICON_NAMES[i], 17, WHITE, false); label.setGravity(Gravity.CENTER); label.setMaxLines(2);
            tile.addView(label, new LinearLayout.LayoutParams(-1, px(42)));
            tile.setContentDescription("Icon " + FloatingConfig.ICON_NAMES[i]); tile.setOnClickListener(v -> { editing.icon = value; buildRows(); buildCustomization(); buildPreview(); });
        }
    }
    private void addPreview(String glyph, String label, int color, boolean live) {
        float factor = Math.min(.62f, 88f / config.diameter(true));
        int diameter = px(config.diameter(live) * factor);
        int largest = px(config.diameter(true) * factor);
        LinearLayout item = column(); item.setGravity(Gravity.CENTER);
        int iconSize = Math.round(config.diameter(live) * factor * .45f);
        TextView circle = symbol(glyph, iconSize, color == TEAL ? WHITE : BG); circle.setIncludeFontPadding(false); circle.setBackground(shape(color, 0, true));
        LinearLayout.LayoutParams circlePosition = new LinearLayout.LayoutParams(diameter, diameter);
        circlePosition.topMargin = (largest - diameter) / 2;
        item.addView(circle, circlePosition);
        TextView name = text(label, 12, WHITE, false); name.setGravity(Gravity.CENTER); name.setMaxLines(2);
        LinearLayout.LayoutParams namePosition = new LinearLayout.LayoutParams(diameter, px(32));
        namePosition.topMargin = (largest - diameter) / 2;
        item.addView(name, namePosition);
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(diameter, -2);
        position.leftMargin = preview.getChildCount() > 1 ? px(config.spacing * factor) : 0;
        preview.addView(item, position);
    }
    private void buildPreview() {
        if (preview == null) return; preview.removeAllViews(); preview.setPadding(px(6), 0, px(6), 0); preview.setBackground(shape(SURFACE, LINE, false));
        TextView grip = symbol("\ue25d", 24, MUTED); preview.addView(grip, new LinearLayout.LayoutParams(px(28), px(70)));
        for (FloatingConfig.Item item : config.items) if (item.selected) addPreview(item.glyph(), item.name, item.buttonColor(), item.isLive());
        preview.addView(symbol("\ue15b", 28, WHITE), new LinearLayout.LayoutParams(px(36), px(70)));
        preview.addView(symbol("\ue5cd", 28, WHITE), new LinearLayout.LayoutParams(px(36), px(70)));
    }
    private void save() {
        try {
            int percent = Integer.parseInt(micPercentDraft);
            if (percent < 0 || percent > FloatingConfig.MAX_MIC_ENLARGEMENT) throw new NumberFormatException();
            config.micEnlargement = percent;
        } catch (NumberFormatException error) {
            if (micPercentInput != null) micPercentInput.setError("Enter 0–100");
            toast("Enter a Live Speak button enlargement from 0 to 100%."); return;
        }
        if (config.enabled && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, MICROPHONE); return;
        }
        if (config.enabled && !Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this).setTitle("Display over other apps")
                .setMessage("Allow Outer Voice to display the floating sound panel above other apps. Turn on Allow display over other apps, then return here.")
                .setNegativeButton("Cancel", null).setPositiveButton("Open Settings", (dialog, which) -> {
                    try { requestingOverlay = true; startActivityForResult(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())), OVERLAY); }
                    catch (RuntimeException e) { requestingOverlay = false; toast("Open Android Settings > Apps > Special access > Display over other apps > Outer Voice."); }
                }).show(); return;
        }
        if (!config.save(this)) { toast("Could not save settings"); return; }
        saved = true; FloatingConfig.prefs(this).edit().putBoolean("minimized", false).apply(); FloatingPanelService.sync(this, true); finish();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == OVERLAY) {
            requestingOverlay = false;
            if (Settings.canDrawOverlays(this)) save(); else toast("Floating panel needs Display over other apps permission. Settings have not been saved.");
        }
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == MICROPHONE) {
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) save();
            else toast("Microphone permission is required for floating Live Speak.");
        }
    }
    private void cancel() { FloatingPanelService.sync(this, false); finish(); }
    @Override public void onBackPressed() { cancel(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        org.json.JSONArray array = new org.json.JSONArray();
        try {
            for (FloatingConfig.Item item : config.items) {
                org.json.JSONObject value = new org.json.JSONObject(); value.put("id", item.id); value.put("selected", item.selected); value.put("color", item.color); value.put("icon", item.icon); array.put(value);
            }
        } catch (Exception ignored) { }
        state.putString("draft", array.toString()); state.putBoolean("enabled", config.enabled); state.putInt("size", config.size);
        state.putInt("micEnlargement", config.micEnlargement); state.putString("micPercentDraft", micPercentDraft);
        state.putInt("spacing", config.spacing);
        state.putBoolean("requestingOverlay", requestingOverlay); if (editing != null) state.putString("editing", editing.id);
    }
    @Override protected void onDestroy() {
        if (!saved && !isChangingConfigurations() && !requestingOverlay) FloatingPanelService.sync(this, false);
        super.onDestroy();
    }
}
