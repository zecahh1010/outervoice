package com.zecadev.outervoice;

import android.Manifest;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.view.animation.DecelerateInterpolator;
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

/** Genuine system overlay. Its audio lifetime is independent of MainActivity.onPause(). */
public final class FloatingPanelService extends Service {
    private static final String CHANNEL = "floating_panel", CLOSE = "close", REFRESH = "refresh";
    private final Handler main = new Handler();
    private VoiceSession speech;
    private boolean speechAnimating;
    private final Runnable speechChanged = this::updateSpeech;

    private boolean destroyed;
    private int screenW, screenH, size;
    private int micEnlargement;
    private WindowManager windows;
    private WindowManager.LayoutParams params;
    private View panel;
    private HorizontalScrollView soundScroll;
    private TextView mic, status;
    private Typeface icons, font;
    private FloatingConfig config;
    private boolean minimized, transitioning, gestureActive, confirmingClose;
    private final Runnable autoMinimize = () -> {
        if (destroyed || minimized || panel == null || !config.autoMinimize) return;
        if (speech.busy() || gestureActive || transitioning || confirmingClose) main.postDelayed(this.autoMinimize, 250);
        else transitionPanel(true);
    };
    private void armAutoMinimize() {
        main.removeCallbacks(autoMinimize);
        if (!destroyed && panel != null && !minimized && config.autoMinimize) main.postDelayed(autoMinimize, config.autoSeconds * 1000L);
    }

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
            Toast.makeText(context, "Could not open floating panel. Open Floating Panel to try again.", Toast.LENGTH_LONG).show();
        }
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onCreate() {
        super.onCreate();
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        speech = VoiceSession.get(this); speech.observers.add(speechChanged);
        icons = Typeface.createFromAsset(getAssets(), "MaterialIcons-Regular.ttf");
        font = Typeface.createFromAsset(getAssets(), "Inter.ttf");
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
    private LinearLayout circle(FloatingConfig.Item item, int diameter, int largest) {
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setGravity(Gravity.CENTER);
        SoundIcon button = new SoundIcon(this, icons, item.icon, item.isLive(), Math.round(diameter * .45f), item.foreground());
        button.setBackground(shape(item.buttonColor(), true)); button.setContentDescription(item.isLive() ? SpeakingOptions.hint(this) : "Play " + item.name);
        button.setClickable(true); button.setFocusable(true);
        LinearLayout.LayoutParams position = new LinearLayout.LayoutParams(diameter, diameter); position.topMargin = (largest - diameter) / 2; column.addView(button, position);
        TextView name = text(item.name, Math.max(12, Math.min(17, size / 6)), 0xfff6f9fa); name.setMaxLines(2);
        LinearLayout.LayoutParams label = new LinearLayout.LayoutParams(diameter, 34); label.topMargin = (largest - diameter) / 2; column.addView(name, label);
        return column;
    }
    private GradientDrawable controlShape(int color, int border) { GradientDrawable d = shape(color, false); d.setCornerRadius(9); d.setStroke(1, border); return d; }
    private void buildPanel() {
        main.removeCallbacks(autoMinimize); transitioning = false; gestureActive = false;
        if (panel != null) { HoldFeedback.stop(mic); speechAnimating = false; panel.animate().cancel(); windows.removeView(panel); panel = null; }
        mic = null; status = null; soundScroll = null;
        config = FloatingConfig.load(this); size = config.size; micEnlargement = config.micEnlargement;
        DisplayMetrics metrics = new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics); screenW = metrics.widthPixels; screenH = metrics.heightPixels;
        minimized = FloatingConfig.prefs(this).getBoolean("minimized", false);
        if (minimized) { buildBubble(); updateSpeech(); return; }
        int largest = config.diameter(true), count = 0, total = 0;
        for (FloatingConfig.Item item : config.items) if (item.selected) { if (count++ > 0) total += config.spacing; total += config.diameter(item.isLive()); }
        final int width = Math.min(screenW - 16, Math.max(252, total + 30));
        LinearLayout content = new LinearLayout(this) {
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                if (transitioning) return true;
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) { gestureActive = true; main.removeCallbacks(autoMinimize); }
                boolean result = super.dispatchTouchEvent(event);
                if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) { gestureActive = false; armAutoMinimize(); }
                return result;
            }
        };
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(14, 10, 14, 8); content.setBackground(controlShape(0xff182329, 0xff30414a));
        LinearLayout toolbar = row(); LinearLayout.LayoutParams toolbarSize = new LinearLayout.LayoutParams(-1,72); toolbarSize.bottomMargin = 14; toolbar.setPadding(0,0,0,10); content.addView(toolbar,toolbarSize);
        TextView grip = text("\ue25d",32,0xffaebcc7); grip.setTypeface(icons); grip.setBackground(controlShape(0xff182329,0xff30414a)); grip.setContentDescription("Move floating panel"); toolbar.addView(grip,new LinearLayout.LayoutParams(60,60));
        LinearLayout minimize = row(); minimize.setGravity(Gravity.CENTER); minimize.setBackground(controlShape(0xff113039,FloatingConfig.TEAL)); minimize.setContentDescription("Minimize floating panel"); minimize.setClickable(true); minimize.setFocusable(true);
        TextView minIcon = text("\ue15b",30,FloatingConfig.TEAL); minIcon.setTypeface(icons); minimize.addView(minIcon,new LinearLayout.LayoutParams(30,60));
        if(width>=360){TextView label=text("Minimize",22,FloatingConfig.TEAL);label.setPadding(12,0,0,0);minimize.addView(label);}
        LinearLayout.LayoutParams middle=new LinearLayout.LayoutParams(0,60,1);middle.setMargins(16,0,16,0);toolbar.addView(minimize,middle);minimize.setOnClickListener(v->transitionPanel(true));
        TextView close=text("\ue5cd",32,0xfff6f9fa);close.setTypeface(icons);close.setBackground(controlShape(0xff182329,0xff30414a));close.setContentDescription("Close floating panel");toolbar.addView(close,new LinearLayout.LayoutParams(60,60));close.setOnClickListener(v->confirmClose());
        HorizontalScrollView scroll=new HorizontalScrollView(this);soundScroll=scroll;scroll.setFillViewport(true);scroll.setHorizontalScrollBarEnabled(total>width-28);LinearLayout strip=row();strip.setGravity(count==1?Gravity.CENTER:Gravity.LEFT|Gravity.CENTER_VERTICAL);scroll.addView(strip);content.addView(scroll,new LinearLayout.LayoutParams(width-28,largest+34));
        for(FloatingConfig.Item item:config.items)if(item.selected){int d=config.diameter(item.isLive());LinearLayout sound=circle(item,d,largest);LinearLayout.LayoutParams pos=new LinearLayout.LayoutParams(d,largest+34);if(strip.getChildCount()>0)pos.leftMargin=config.spacing;strip.addView(sound,pos);
            if(item.isLive()){mic=(TextView)sound.getChildAt(0);mic.setOnTouchListener((view,event)->{switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:view.getParent().requestDisallowInterceptTouchEvent(true);beginRecording();return true;
                case MotionEvent.ACTION_UP:view.getParent().requestDisallowInterceptTouchEvent(false);finishRecording();return true;
                case MotionEvent.ACTION_CANCEL:view.getParent().requestDisallowInterceptTouchEvent(false);cancelAudio();show("Recording cancelled");return true;
                default:return true;}});
            }else sound.getChildAt(0).setOnClickListener(v->playSound(item));
        }
        status=text(SpeakingOptions.hint(this),12,0xffaebcc7);status.setMaxLines(1);content.addView(status,new LinearLayout.LayoutParams(-1,20));panel=content;
        params=new WindowManager.LayoutParams(width,largest+158,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.LEFT;params.alpha=.8f;
        params.x=FloatingConfig.prefs(this).getInt("x",24);params.y=FloatingConfig.prefs(this).getInt("y",Math.max(0,screenH-params.height-24));clamp();windows.addView(panel,params);
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){@Override public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){scroll.removeOnLayoutChangeListener(this);scroll.scrollTo(FloatingConfig.prefs(FloatingPanelService.this).getInt("strip_scroll",0),0);}});
        grip.setOnTouchListener(new View.OnTouchListener(){float x,y;int startX,startY;public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:cancelAudio();x=e.getRawX();y=e.getRawY();startX=params.x;startY=params.y;return true;
            case MotionEvent.ACTION_MOVE:params.x=startX+Math.round(e.getRawX()-x);params.y=startY+Math.round(e.getRawY()-y);clamp();windows.updateViewLayout(panel,params);return true;
            case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:FloatingConfig.prefs(FloatingPanelService.this).edit().putInt("x",params.x).putInt("y",params.y).apply();return true;
            default:return true;}}});updateSpeech();armAutoMinimize();
    }
    private void buildBubble() {
        int d=config.minimizedSize;boolean speak=config.liveOnly();
        SoundIcon bubble=new SoundIcon(this,icons,0,true,Math.round(d*.45f),config.live().foreground());bubble.setBackground(shape(config.live().buttonColor(),true));bubble.setContentDescription(speak?"Hold Live Speak directly; release to play; drag to move":"Reopen floating panel; drag to move");bubble.setClickable(true);bubble.setFocusable(true);panel=bubble;mic=bubble;
        params=new WindowManager.LayoutParams(d,d,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.LEFT;params.alpha=.8f;
        params.x=FloatingConfig.prefs(this).getInt("bubble_x",FloatingConfig.prefs(this).getInt("x",24));params.y=FloatingConfig.prefs(this).getInt("bubble_y",FloatingConfig.prefs(this).getInt("y",24));clamp();windows.addView(panel,params);
        if(!speak)bubble.setOnClickListener(v->transitionPanel(false));
        bubble.setOnTouchListener(new View.OnTouchListener(){float x,y;int startX,startY;boolean moved;public boolean onTouch(View v,MotionEvent e){if(transitioning)return true;switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN:x=e.getRawX();y=e.getRawY();startX=params.x;startY=params.y;moved=false;if(speak)beginRecording();return true;
            case MotionEvent.ACTION_MOVE:if(!moved&&Math.hypot(e.getRawX()-x,e.getRawY()-y)>8){moved=true;if(speak)cancelAudio();}if(moved){params.x=startX+Math.round(e.getRawX()-x);params.y=startY+Math.round(e.getRawY()-y);clamp();windows.updateViewLayout(panel,params);}return true;
            case MotionEvent.ACTION_UP:if(!moved){if(speak)finishRecording();else v.performClick();}else saveBubblePosition();return true;
            case MotionEvent.ACTION_CANCEL:if(speak)cancelAudio();if(moved)saveBubblePosition();return true;
            default:return true;}}});
    }
    private void saveBubblePosition(){FloatingConfig.prefs(this).edit().putInt("bubble_x",params.x).putInt("bubble_y",params.y).apply();}
    private void finishRecording(){speech.up(this,SpeakingOptions.mode(this));armAutoMinimize();}
    private void transitionPanel(boolean minimize){
        if(transitioning||panel==null)return;cancelAudio();main.removeCallbacks(autoMinimize);transitioning=true;
        int d=config.minimizedSize;long duration=ValueAnimator.areAnimatorsEnabled()?320:0;
        if(minimize){
            // Anchor each collapse to the actual microphone, including ordering and strip scrolling.
            int[] location=new int[2];mic.getLocationOnScreen(location);
            int x=Math.max(0,Math.min(screenW-d,Math.round(location[0]+mic.getWidth()*mic.getScaleX()/2f-d/2f)));
            int y=Math.max(0,Math.min(screenH-d,Math.round(location[1]+mic.getHeight()*mic.getScaleY()/2f-d/2f)));
            float cx=location[0]+mic.getWidth()*mic.getScaleX()/2f,cy=location[1]+mic.getHeight()*mic.getScaleY()/2f;
            FloatingConfig.prefs(this).edit().putInt("strip_scroll",soundScroll.getScrollX()).apply();
            panel.setPivotX(cx-params.x);panel.setPivotY(cy-params.y);float scale=d/(float)config.diameter(true);
            panel.animate().scaleX(scale).scaleY(scale).translationX(x+d/2f-cx).translationY(y+d/2f-cy).alpha(0).setDuration(duration).setInterpolator(new DecelerateInterpolator()).withEndAction(()->{if(destroyed)return;FloatingConfig.prefs(this).edit().putBoolean("minimized",true).putInt("bubble_x",x).putInt("bubble_y",y).commit();buildPanel();panel.setAlpha(0);panel.animate().alpha(1).setDuration(ValueAnimator.areAnimatorsEnabled()?140:0).start();}).start();
        }else{
            float cx=params.x+d/2f,cy=params.y+d/2f;
            FloatingConfig.prefs(this).edit().putBoolean("minimized",false).commit();buildPanel();
            View opening=panel;opening.setAlpha(0);transitioning=true;main.removeCallbacks(autoMinimize);
            opening.addOnLayoutChangeListener(new View.OnLayoutChangeListener(){@Override public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
                opening.removeOnLayoutChangeListener(this);
                if(destroyed||panel!=opening||minimized)return;
                int[] location=new int[2];mic.getLocationOnScreen(location);
                float px=location[0]+mic.getWidth()/2f-params.x,py=location[1]+mic.getHeight()/2f-params.y;
                params.x=Math.round(cx-px);params.y=Math.round(cy-py);clamp();windows.updateViewLayout(opening,params);
                FloatingConfig.prefs(FloatingPanelService.this).edit().putInt("x",params.x).putInt("y",params.y).apply();
                float scale=d/(float)config.diameter(true);opening.setPivotX(px);opening.setPivotY(py);opening.setScaleX(scale);opening.setScaleY(scale);opening.setTranslationX(cx-params.x-px);opening.setTranslationY(cy-params.y-py);
                opening.animate().scaleX(1).scaleY(1).translationX(0).translationY(0).alpha(1).setDuration(duration).setInterpolator(new DecelerateInterpolator()).withEndAction(()->{transitioning=false;armAutoMinimize();}).start();
            }});
        }
    }
    private void confirmClose(){
        cancelAudio();main.removeCallbacks(autoMinimize);confirmingClose=true;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Close floating panel?").setMessage("You can enable it again in Floating Panel settings.").setNegativeButton("Cancel",null).setPositiveButton("Close panel",(d,w)->closePanel()).create();
        dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);dialog.setOnDismissListener(d->{confirmingClose=false;armAutoMinimize();});dialog.show();
    }
    private void clamp() {
        params.x = Math.max(0, Math.min(params.x, Math.max(0, screenW - params.width)));
        params.y = Math.max(0, Math.min(params.y, Math.max(0, screenH - params.height)));
    }
    private void beginRecording() { speech.down(this, SpeakingOptions.mode(this)); }
    private void playSound(FloatingConfig.Item item) { speech.playSaved(this,item.id,item.name); }
    private void cancelAudio() { if (speech != null) speech.cancelIfOwned(this); }
    private void updateSpeech() {
        if (destroyed) return;
        boolean recording = speech.recording();
        boolean live = speech.live(), active = recording || live;
        if (mic != null) {
            mic.setBackground(shape(active ? 0xffef5350 : config.live().buttonColor(),true));
            mic.setContentDescription(recording ? "Recording; tap to stop and play" : live ? "Live Speak active; tap to stop" : SpeakingOptions.hint(this));
            if (speechAnimating != active) { speechAnimating = active; if (active) HoldFeedback.start(mic); else HoldFeedback.stop(mic); }
        }
        show(recording ? "Recording " + speech.milliseconds / 1000 + "s / 180s" : speech.message.isEmpty() ? SpeakingOptions.hint(this) : speech.message);
        armAutoMinimize();
    }
    private void show(String message) {
        if (status != null) status.setText(message == null ? "Audio failed" : message);
        // Routine session/meter updates never create Toasts; errors remain in diagnostics.
    }
    private void closePanel() {
        FloatingConfig.prefs(this).edit().putBoolean("enabled", false).putBoolean("minimized", false).apply(); stopSelf();
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); cancelAudio(); buildPanel();
    }
    @Override public void onDestroy() {
        destroyed = true; main.removeCallbacksAndMessages(null); cancelAudio(); speech.observers.remove(speechChanged); HoldFeedback.stop(mic);
        if (panel != null) { try { windows.removeView(panel); } catch (RuntimeException ignored) { } panel = null; }
        stopForeground(true); super.onDestroy();
    }
}
