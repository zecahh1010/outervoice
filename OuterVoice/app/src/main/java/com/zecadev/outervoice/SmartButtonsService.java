/* Based on Zeekr Shortcut - https://github.com/dts88/zeekr-shortcut-car
 * Copyright (C) 2026 dts88; GPL-3.0-only with applicable section 7 terms; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-27: process-owned BLE with a foreground audio service. */
package com.zecadev.outervoice;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;

/** Audio/lifetime notification. Bluetooth is installed by Application and the receiver independently. */
public final class SmartButtonsService extends Service {
    static final String PAIR="pair",CANCEL_PAIR="cancel_pair",STOP="stop",SYNC="sync",CHANGE_METHOD="change_method";
    static volatile String status="未啟用靈動貼",scanStatus="尚未開始掃描",pairPhase="";
    static volatile boolean pairing;
    static volatile long packets,buttonPackets,longPresses;
    static final Map<String,String> connections=new java.util.concurrent.ConcurrentHashMap<>();
    private static final ArrayDeque<Runnable> waiting=new ArrayDeque<>();
    private static boolean active;
    static String diagnostics(){return SmartButtonsController.install(currentContext()).diagnostics();}
    private static Context application;
    static void remember(Context c){application=c.getApplicationContext();}
    private static Context currentContext(){if(application==null)throw new IllegalStateException("Application not installed");return application;}
    static boolean permitted(Context c){return SmartButtonPermissions.granted(c);}
    static void command(Context c,String action){
        if(Looper.myLooper()!=Looper.getMainLooper()){new Handler(Looper.getMainLooper()).post(()->command(c,action));return;}
        remember(c);
        if(STOP.equals(action)){
            SmartButtonStore.prefs(c).edit().putBoolean("listening_session",false).apply();waiting.clear();
            SmartButtonsController.install(c).stop();c.stopService(new Intent(c,SmartButtonsService.class));return;
        }
        SmartButtonStore.prefs(c).edit().putBoolean("listening_session",true).apply();
        try{c.startForegroundService(new Intent(c,SmartButtonsService.class).setAction(action));}
        catch(RuntimeException e){waiting.clear();status="無法啟動靈動貼："+e.getMessage();SmartButtonsController.note(status);}
    }
    static void resume(Context c){if(SmartButtonStore.prefs(c).getBoolean("enabled",false)&&permitted(c))command(c,SYNC);}
    static void withForeground(Context c,Runnable action){
        if(active){action.run();return;}
        if(waiting.size()<8)waiting.addLast(action);command(c,SYNC);
    }
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){super.onCreate();remember(this);foreground();active=true;}
    private void foreground(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("smart_buttons","靈動貼",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,71,new Intent(this,MainActivity.class).putExtra("tab","buttons"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,72,new Intent(this,SmartButtonsService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"smart_buttons").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Outer Voice · 靈動貼")
            .setContentText("在背景接收按鍵；點按開啟設定").setOngoing(true).setContentIntent(open).addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止靈動貼",stop).build();
        if(Build.VERSION.SDK_INT>=30){int types=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)types|=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;startForeground(71,n,types);
        }else startForeground(71,n);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        String action=i==null?SYNC:i.getAction();
        if(STOP.equals(action)){SmartButtonStore.prefs(this).edit().putBoolean("enabled",false).apply();command(this,STOP);stopSelf();return START_NOT_STICKY;}
        if(!SmartButtonStore.prefs(this).getBoolean("enabled",false)||!SmartButtonStore.prefs(this).getBoolean("listening_session",false)||!permitted(this)){waiting.clear();status="請允許"+SmartButtonPermissions.words(this)+"權限";stopSelf();return START_NOT_STICKY;}
        foreground();SmartButtonsController.install(this).action(action);
        while(!waiting.isEmpty())waiting.removeFirst().run();return START_NOT_STICKY;
    }
    @Override public void onDestroy(){active=false;waiting.clear();SmartButtonsController.install(this).foregroundLost();stopForeground(true);super.onDestroy();}
}
