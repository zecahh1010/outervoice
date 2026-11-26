package com.zecadev.outervoice;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayList;

/** Native settings tab. Assignments save immediately and never write screen_mode. */
final class SmartButtonsPage extends LinearLayout {
    private static final int WHITE=0xfff6f9fa, MUTED=0xffaebcc7, TEAL=0xff00c6c7, LINE=0xff30414a;
    private final Activity activity;
    private final float scale;
    private final Handler main = new Handler();
    private final Typeface font;
    private final LinearLayout devices;
    private final TextView state, audioState, enable, pair;
    private String signature = "";
    private final Runnable tick = new Runnable() { public void run() { refresh(); main.postDelayed(this, 500); } };
    SmartButtonsPage(Activity a) {
        super(a); activity = a; scale = Math.min(a.getResources().getDisplayMetrics().widthPixels/1024f, a.getResources().getDisplayMetrics().heightPixels/600f);
        font = Typeface.createFromAsset(a.getAssets(), "Inter.ttf"); setOrientation(HORIZONTAL); setPadding(px(24),px(18),px(24),px(20));
        LinearLayout left = col(); left.setPadding(0,0,px(24),0); addView(left, new LayoutParams(px(356),-1));
        left.addView(label("Zeekr 靈動貼",26,WHITE,true),new LayoutParams(-1,px(44)));
        left.addView(label("獨立設定單擊及雙擊動作\n不會改變畫面或懸浮按鈕的收音模式",17,MUTED,false),new LayoutParams(-1,px(68)));
        enable = button(""); left.addView(enable,new LayoutParams(-1,px(52)));
        enable.setOnClickListener(v -> {
            if (SmartButtonStore.prefs(a).getBoolean("enabled",false)) { SmartButtonStore.prefs(a).edit().putBoolean("enabled",false).apply(); SmartButtonsService.command(a,SmartButtonsService.STOP); }
            else activate(SmartButtonsService.SYNC);
            refresh();
        });
        space(left,10); pair = button("＋ 配對靈動貼"); left.addView(pair,new LayoutParams(-1,px(52)));
        pair.setOnClickListener(v -> { if (SmartButtonsService.pairing) SmartButtonsService.command(a,SmartButtonsService.CANCEL_PAIR); else activate(SmartButtonsService.PAIR); refresh(); });
        space(left,12); state = label("",17,TEAL,false); left.addView(state,new LayoutParams(-1,px(75)));
        audioState = label("",16,MUTED,false); left.addView(audioState,new LayoutParams(-1,px(50)));
        LinearLayout system = new LinearLayout(a);
        TextView bt=button("系統藍牙"),location=button("位置服務"),permissions=button("權限");
        for (TextView b : new TextView[]{bt,location,permissions}) { b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(16)); system.addView(b,new LayoutParams(0,px(48),1)); }
        left.addView(system);
        bt.setOnClickListener(v->open(Settings.ACTION_BLUETOOTH_SETTINGS)); location.setOnClickListener(v->open(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        permissions.setOnClickListener(v->{ try { a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:"+a.getPackageName()))); } catch (RuntimeException e) { toast("無法開啟權限設定"); } });
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true); addView(scroll,new LayoutParams(0,-1,1));
        devices=col(); scroll.addView(devices); refresh();
    }
    private int px(float n){return Math.round(n*scale);}
    private LinearLayout col(){LinearLayout l=new LinearLayout(activity);l.setOrientation(VERTICAL);return l;}
    private void space(LinearLayout l,int h){l.addView(new android.view.View(activity),new LayoutParams(1,px(h)));}
    private TextView label(String s,int size,int color,boolean bold){TextView v=new TextView(activity);v.setText(s);v.setTextColor(color);v.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,px(size));v.setTypeface(Typeface.create(font,bold?Typeface.BOLD:Typeface.NORMAL));v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    private TextView button(String s){TextView v=label(s,18,WHITE,true);v.setGravity(Gravity.CENTER);v.setPadding(px(12),0,px(12),0);GradientDrawable d=new GradientDrawable();d.setColor(0xff182329);d.setCornerRadius(px(10));d.setStroke(px(1),LINE);v.setBackground(d);v.setClickable(true);v.setFocusable(true);return v;}
    private void toast(String s){Toast.makeText(activity,s,Toast.LENGTH_LONG).show();}
    private void open(String action){try{activity.startActivity(new Intent(action));}catch(RuntimeException e){toast("此車機未提供這個系統設定頁");}}
    private void activate(String action){
        ArrayList<String> missing=new ArrayList<>();
        for(String p:new String[]{Manifest.permission.RECORD_AUDIO,Manifest.permission.ACCESS_FINE_LOCATION})if(activity.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        if(Build.VERSION.SDK_INT>=31)for(String p:new String[]{"android.permission.BLUETOOTH_SCAN","android.permission.BLUETOOTH_CONNECT"})if(activity.checkSelfPermission(p)!=PackageManager.PERMISSION_GRANTED)missing.add(p);
        if(!missing.isEmpty()){activity.requestPermissions(missing.toArray(new String[0]),77);toast("允許權限後，請再按啟用或配對");return;}
        SmartButtonStore.prefs(activity).edit().putBoolean("enabled",true).apply();SmartButtonsService.command(activity,action);
    }
    private void refresh(){
        boolean on=SmartButtonStore.prefs(activity).getBoolean("enabled",false);
        enable.setText(on?"已啟用 · 點按停止接收":"啟用靈動貼");enable.setTextColor(on?TEAL:WHITE);
        pair.setText(SmartButtonsService.pairing?"取消配對":"＋ 配對靈動貼");
        state.setText(SmartButtonsService.status);audioState.setText(VoiceSession.get(activity).message);
        StringBuilder key=new StringBuilder(SmartButtonStore.devices(activity).toString());
        for(String a:SmartButtonStore.devices(activity))key.append(SmartButtonStore.prefs(activity).getAll().toString()).append(SmartButtonsService.connections.get(a));
        key.append(activity.getSharedPreferences("sounds",0).getString("items","[]"));
        if(key.toString().equals(signature))return;signature=key.toString();devices.removeAllViews();
        if(SmartButtonStore.devices(activity).isEmpty()){
            devices.addView(label("尚未配對靈動貼",25,WHITE,true),new LayoutParams(-1,px(64)));
            devices.addView(label("1  開啟車機藍牙\n2  點按「配對靈動貼」\n3  長按靈動貼約 3 秒，等背光閃動\n4  連線成功後設定名稱和按鍵動作",20,MUTED,false),new LayoutParams(-1,px(200)));
            devices.addView(label("配對期間不執行按鍵動作。\n靈動貼長按用於配對，不用作按住收音。",17,MUTED,false));return;
        }
        for(String a:SmartButtonStore.devices(activity))card(a,on);
    }
    private void card(String a,boolean on){
        LinearLayout card=col();card.setPadding(px(18),px(10),px(18),px(16));GradientDrawable d=new GradientDrawable();d.setColor(0xff182329);d.setCornerRadius(px(12));d.setStroke(px(1),LINE);card.setBackground(d);
        LayoutParams bounds=new LayoutParams(-1,-2);bounds.bottomMargin=px(14);devices.addView(card,bounds);
        LinearLayout title=new LinearLayout(activity);title.setGravity(Gravity.CENTER_VERTICAL);
        title.addView(label(SmartButtonStore.name(activity,a),24,WHITE,true),new LayoutParams(0,px(50),1));
        TextView rename=button("改名"),forget=button("移除");title.addView(rename,new LayoutParams(px(78),px(48)));title.addView(forget,new LayoutParams(px(78),px(48)));card.addView(title);
        int battery=SmartButtonStore.prefs(activity).getInt("battery."+a,-1);
        String connection=on?SmartButtonsService.connections.get(a):"已停用";
        card.addView(label((connection==null?"待機 · 等待按鍵廣播":connection)+(battery<0?"":" · 電量 "+battery+"%")+"\n"+a,16,MUTED,false),new LayoutParams(-1,px(48)));
        rename.setOnClickListener(v->{EditText input=new EditText(activity);input.setSingleLine(true);input.setText(SmartButtonStore.name(activity,a));input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});new AlertDialog.Builder(activity).setTitle("靈動貼名稱").setView(input).setNegativeButton("取消",null).setPositiveButton("儲存",(x,w)->{String name=input.getText().toString().trim();if(!name.isEmpty())SmartButtonStore.prefs(activity).edit().putString("name."+a,name).apply();refresh();}).show();});
        forget.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("移除 "+SmartButtonStore.name(activity,a)+"？").setNegativeButton("取消",null).setPositiveButton("移除",(x,w)->{SmartButtonStore.forget(activity,a);if(on)SmartButtonsService.command(activity,SmartButtonsService.SYNC);refresh();}).show());
        int action=SmartButtonStore.action(activity,a);
        TextView single=button("單擊  ·  "+SmartButtonStore.ACTION_NAMES[action]);single.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);card.addView(single,new LayoutParams(-1,px(52)));
        single.setOnClickListener(v->new AlertDialog.Builder(activity).setTitle("靈動貼單擊動作").setSingleChoiceItems(SmartButtonStore.ACTION_NAMES,SmartButtonStore.action(activity,a),(x,w)->{SmartButtonStore.prefs(activity).edit().putInt("single."+a,w).apply();x.dismiss();refresh();}).setNegativeButton("取消",null).show());
        if(action==2)soundButton(card,a,"single_sound.","單擊聲音");
        space(card,8);soundButton(card,a,"double_sound.","雙擊播放");
    }
    private void soundButton(LinearLayout card,String a,String prefix,String title){
        String id=SmartButtonStore.prefs(activity).getString(prefix+a,"");TextView pick=button(title+"  ·  "+SmartButtonStore.soundName(activity,id));pick.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);pick.setTextColor(TEAL);card.addView(pick,new LayoutParams(-1,px(52)));
        pick.setOnClickListener(v->{
            ArrayList<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add("");names.add("不播放聲音");
            for(FloatingConfig.Item item:FloatingConfig.load(activity).items)if(!item.isLive()){ids.add(item.id);names.add(item.name);}
            if(ids.size()==1)toast("先到「收音／聲音」加入或錄製聲音，再選擇");
            new AlertDialog.Builder(activity).setTitle(title+" · 選擇已儲存聲音").setSingleChoiceItems(names.toArray(new String[0]),ids.indexOf(id),(x,w)->{SmartButtonStore.prefs(activity).edit().putString(prefix+a,ids.get(w)).apply();x.dismiss();refresh();}).setNegativeButton("取消",null).show();
        });
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();main.post(tick);}
    @Override protected void onDetachedFromWindow(){main.removeCallbacksAndMessages(null);super.onDetachedFromWindow();}
}
