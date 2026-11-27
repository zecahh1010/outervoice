/*
 * Based on Zeekr Shortcut - https://github.com/dts88/zeekr-shortcut-car
 * Copyright (C) 2026 dts88
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional terms under GPL-3.0 section 7 apply; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-27: serialized pairing, visible scan phases and audio actions.
 */
package com.zecadev.outervoice;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import java.util.*;

/** Zeekr Shortcut pairing order, serialized with scan delivery on the BLE worker. */
public final class SmartButtonsService extends Service {
    static final String PAIR="pair",CANCEL_PAIR="cancel_pair",STOP="stop",SYNC="sync";
    static volatile String status="未啟用靈動貼",scanStatus="尚未開始掃描",pairPhase="";
    static volatile boolean pairing;
    static volatile long packets,buttonPackets,longPresses;
    static final Map<String,String> connections=new java.util.concurrent.ConcurrentHashMap<>();
    private static final ArrayDeque<String> trace=new ArrayDeque<>();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<String,Integer> counters=new HashMap<>();
    private final Map<String,Last> presses=new HashMap<>();
    private BluetoothAdapter adapter;private ButtonScanner scanner;private ButtonLink link;private VoiceSession voice;
    private volatile boolean destroyed;private boolean pausedScan,sleeping;
    private static final class Last{int gesture;boolean notification;long at;Last(int g,boolean n,long t){gesture=g;notification=n;at=t;}}
    private static synchronized void note(String message){
        trace.addLast(message);while(trace.size()>35)trace.removeFirst();AudioDiagnostics.log("Smart buttons: "+message);
    }
    static synchronized String diagnostics(){
        StringBuilder out=new StringBuilder(status).append("\n\n掃描：").append(scanStatus).append("\n掃描結果：").append(packets)
            .append("\n靈動貼廣播：").append(buttonPackets).append("\n長按廣播：").append(longPresses).append("\n\n最近步驟：\n");
        for(String row:trace)out.append(row).append('\n');return out.toString();
    }
    static boolean permitted(Context c){return SmartButtonPermissions.granted(c);}
    static void command(Context c,String action){
        if(STOP.equals(action)){SmartButtonStore.prefs(c).edit().putBoolean("listening_session",false).apply();c.stopService(new Intent(c,SmartButtonsService.class));status="已停止靈動貼";return;}
        try{SmartButtonStore.prefs(c).edit().putBoolean("listening_session",true).apply();c.startForegroundService(new Intent(c,SmartButtonsService.class).setAction(action));}
        catch(RuntimeException e){status="無法啟動靈動貼："+e.getMessage();note(status);}
    }
    static void resume(Context c){if(SmartButtonStore.prefs(c).getBoolean("enabled",false)&&permitted(c))command(c,SYNC);}
    @Override public IBinder onBind(Intent i){return null;}
    @Override public void onCreate(){
        super.onCreate();voice=VoiceSession.get(this);BluetoothManager m=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);adapter=m==null?null:m.getAdapter();
        scanner=new ButtonScanner(this,new ButtonScanner.Listener(){
            public void result(ScanResult r){offer(r);}
            public void status(String s){scanStatus=s;refreshStatus();}
            public void radioOff(){if(link!=null)link.close("Bluetooth off detected by polling");main.post(()->voice.cancelIfOwned(SmartButtonsService.this));}
        });
        IntentFilter changes=new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);changes.addAction(android.location.LocationManager.MODE_CHANGED_ACTION);
        changes.addAction(Intent.ACTION_SCREEN_OFF);changes.addAction(Intent.ACTION_SCREEN_ON);changes.addAction(Intent.ACTION_USER_PRESENT);
        changes.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);changes.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(bluetooth,changes,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(bluetooth,changes);
        foreground();
    }
    private void foreground(){
        NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);nm.createNotificationChannel(new NotificationChannel("smart_buttons","靈動貼",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,71,new Intent(this,MainActivity.class).putExtra("tab","buttons"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,72,new Intent(this,SmartButtonsService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification n=new Notification.Builder(this,"smart_buttons").setSmallIcon(android.R.drawable.ic_btn_speak_now).setContentTitle("Outer Voice · 靈動貼")
            .setContentText("在背景接收按鍵；點按開啟設定").setOngoing(true).setContentIntent(open).addAction(android.R.drawable.ic_menu_close_clear_cancel,"停止靈動貼",stop).build();
        if(Build.VERSION.SDK_INT>=30){int types=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;
            if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)types|=android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
            startForeground(71,n,types);
        }else startForeground(71,n);
    }
    @Override public int onStartCommand(Intent i,int flags,int id){
        String action=i==null?SYNC:i.getAction();
        if(STOP.equals(action)){SmartButtonStore.prefs(this).edit().putBoolean("enabled",false).putBoolean("listening_session",false).apply();stopSelf();return START_NOT_STICKY;}
        if(!SmartButtonStore.prefs(this).getBoolean("enabled",false)||!permitted(this)){
            status=permitted(this)?"未啟用靈動貼":"請先允許藍牙掃描及連接權限";ButtonScanner.stopOrphan(getApplicationContext());stopSelf();return START_NOT_STICKY;
        }
        if("scan_result".equals(action)&&!SmartButtonStore.prefs(this).getBoolean("listening_session",false)){ButtonScanner.stopOrphan(this);stopSelf();return START_NOT_STICKY;}
        foreground();Intent scanIntent=i==null?null:i.getParcelableExtra("scan_result");
        scanner.execute(()->handle(action,scanIntent));return START_NOT_STICKY;
    }
    private void handle(String action,Intent scanIntent){
        if(destroyed)return;
        if(link!=null&&!SmartButtonStore.devices(this).contains(link.address)&&(!link.binding||link.wasReady()))link.close("forgotten");
        if(PAIR.equals(action)){
            // A foreground pairing request means the user is present. Match upstream's binding
            // bypass of wake settlement, and do not restart an already healthy named scan.
            scanner.handler().removeCallbacks(park);sleeping=false;pausedScan=false;
            if(link!=null&&link.binding&&!link.wasReady())link.close("new pairing window");
            pairing=true;packets=buttonPackets=longPresses=0;pairPhase="配對中 · 長按靈動貼約 3 秒至閃燈";
            scanner.handler().removeCallbacks(pairTimeout);scanner.handler().postDelayed(pairTimeout,90_000);note("binding started");scanner.binding(true);
        }else if(CANCEL_PAIR.equals(action))endPair("已取消配對");
        startScan();if(scanIntent!=null)scanner.receive(scanIntent);refreshStatus();
    }
    private void refreshStatus(){status=pairing?pairPhase+"\n"+scanStatus:scanStatus;}
    private final Runnable pairTimeout=()->endPair(longPresses>0?"已偵測長按，但連線尚未完成 · 請再次長按重試":buttonPackets>0?"已收到靈動貼廣播 · 請長按至背光閃動後再試":"未收到靈動貼廣播 · 請檢查藍牙及靈動貼狀態");
    private void endPair(String why){
        pairing=false;pairPhase=why;scanner.handler().removeCallbacks(pairTimeout);scanner.binding(false);
        if(link!=null&&link.binding&&!link.wasReady())link.close("pairing ended");startScan();status=why;note(why);
    }
    private void startScan(){
        if(destroyed)return;scanner.pauseLink(pausedScan);scanner.wanted(pairing||!SmartButtonStore.devices(this).isEmpty());
        if(!pairing&&SmartButtonStore.devices(this).isEmpty()){scanStatus="尚未配對靈動貼 · 點按配對開始";status=scanStatus;}
    }
    private final Runnable park=()->{
        PowerManager p=(PowerManager)getSystemService(POWER_SERVICE);
        if(!pairing&&p!=null&&!p.isInteractive()){
            sleeping=true;scanner.park();if(link!=null)link.close("head unit sleeping");main.post(()->voice.cancelIfOwned(SmartButtonsService.this));status="車機休眠 · 亮屏後自動恢復接收";
        }
    };
    private void offer(ScanResult result){
        if(destroyed||sleeping||!SmartButtonStore.prefs(this).getBoolean("enabled",false))return;
        packets++;
        try{
            String address=result.getDevice().getAddress();ScanRecord record=result.getScanRecord();
            boolean bound=SmartButtonStore.devices(this).contains(address);String name=record==null?null:record.getDeviceName();
            if(!bound&&!Accessory.BUTTON.bleName.equals(name))return;
            buttonPackets++;SmartButtonAd.Advert ad=SmartButtonAd.parse(record==null?null:record.getBytes(),Accessory.BUTTON);
            if(ad==null){if(pairing){pairPhase="已偵測靈動貼，但廣播格式未能辨認";refreshStatus();}note("unreadable advertisement "+SmartButtonAd.shortId(address));return;}
            if(ad.gesture==SmartButtonAd.LONG)longPresses++;
            if(pairing){pairPhase=ad.gesture==SmartButtonAd.LONG?"已偵測靈動貼 · 正在連線":"已偵測靈動貼 · 請長按至背光閃動";refreshStatus();}
            if(bound){
                if(ad.battery>=0)SmartButtonStore.prefs(this).edit().putInt("battery."+address,ad.battery).apply();
                int previous=counters.containsKey(address)?counters.get(address):SmartButtonStore.prefs(this).getInt("counter."+address,-1);
                if(!SmartButtonAd.isNew(ad.counter,previous))return;
                counters.put(address,ad.counter);SmartButtonStore.prefs(this).edit().putInt("counter."+address,ad.counter).apply();
                if(pairing){if(ad.gesture==SmartButtonAd.LONG)endPair("這個靈動貼已配對");return;}
                press(address,ad.gesture,false);
            }
            if(ad.gesture==SmartButtonAd.LONG&&(bound||pairing)){note("long press "+SmartButtonAd.shortId(address));connect(address,!bound);}
        }catch(RuntimeException e){note("advertisement: "+e.getMessage());}
    }
    private void connect(String address,boolean binding){
        if(link!=null&&!link.isClosed()&&(link.address.equals(address)||!link.isReady()))return;
        if(binding&&SmartButtonStore.devices(this).size()>=5){endPair("最多可儲存 5 個靈動貼");return;}
        if(link!=null)link.close("switch device");
        link=new ButtonLink(getApplicationContext(),scanner.handler(),adapter,address,Accessory.BUTTON,binding,links);link.start();
    }
    private final ButtonLink.Listener links=new ButtonLink.Listener(){
        public void attempt(ButtonLink l,int n){
            connections.put(l.address,"連線中 · 第 "+n+" 次");if(pairing){pairPhase="已偵測靈動貼 · 連線第 "+n+" 次";refreshStatus();}
            note("GATT attempt "+n+" "+SmartButtonAd.shortId(l.address));
            if(n>1){pausedScan=true;l.pausesScan=true;scanner.pauseLink(true);} // synchronous on same BLE thread, before retry connectGatt
        }
        public void ready(ButtonLink l){
            if(destroyed||link!=l||!l.isReady())return;
            counters.remove(l.address);SmartButtonStore.prefs(SmartButtonsService.this).edit().remove("counter."+l.address).apply();connections.put(l.address,"已連線");
            if(l.binding&&pairing){SmartButtonStore.add(SmartButtonsService.this,l.address);pairing=false;scanner.handler().removeCallbacks(pairTimeout);scanner.binding(false);status="已配對 · 可改名及設定按鍵動作";}
            pausedScan=false;l.pausesScan=false;startScan();note("GATT notify subscribed; binding ready "+SmartButtonAd.shortId(l.address));
        }
        public void reply(ButtonLink l,SmartButtonAd.Reply r,byte[] raw){
            if(!l.isReady()||!SmartButtonStore.devices(SmartButtonsService.this).contains(l.address))return;
            if(pairing){if(r.gesture==SmartButtonAd.LONG)endPair("這個靈動貼已配對");return;}
            if(r.gesture!=0)press(l.address,r.gesture,true);
        }
        public void battery(ButtonLink l,int n){if(n>=0&&n<=100)SmartButtonStore.prefs(SmartButtonsService.this).edit().putInt("battery."+l.address,n).apply();}
        public void closed(ButtonLink l,String why){
            connections.put(l.address,"待機 · 可接收按鍵廣播");
            if(l.everConnected){counters.remove(l.address);SmartButtonStore.prefs(SmartButtonsService.this).edit().remove("counter."+l.address).apply();}
            if(link==l)link=null;
            if(l.binding&&pairing){pairPhase="連線未成功 · 可再次長按重試";refreshStatus();}
            pausedScan=false;if(!destroyed)startScan();note("GATT closed "+SmartButtonAd.shortId(l.address)+": "+why);
        }
    };
    private void press(String address,int gesture,boolean notification){
        if(gesture!=SmartButtonAd.SINGLE&&gesture!=SmartButtonAd.DOUBLE)return;
        long now=SystemClock.elapsedRealtime();Last last=presses.get(address);
        if(last!=null&&SmartButtonAd.samePress(last.gesture,last.notification,last.at,gesture,notification,now))return;
        presses.put(address,new Last(gesture,notification,now));
        if(!SmartButtonStore.prefs(this).getBoolean("enabled",false)||!SmartButtonStore.devices(this).contains(address))return;
        status=SmartButtonStore.name(this,address)+(gesture==SmartButtonAd.DOUBLE?" · 雙擊":" · 單擊");
        main.post(()->{if(!destroyed)SmartButtonActions.run(this,this,address,gesture);});
    }
    private final BroadcastReceiver bluetooth=new BroadcastReceiver(){public void onReceive(Context c,Intent i){scanner.execute(()->{
        String action=i.getAction();
        if(Intent.ACTION_SCREEN_OFF.equals(action)){scanner.handler().removeCallbacks(park);scanner.handler().postDelayed(park,40_000);return;}
        if(Intent.ACTION_SCREEN_ON.equals(action)||Intent.ACTION_USER_PRESENT.equals(action)){
            scanner.handler().removeCallbacks(park);if(sleeping||Intent.ACTION_SCREEN_ON.equals(action)){sleeping=false;if(link!=null)link.close("head unit woke");scanner.wake();startScan();}return;
        }
        if(android.location.LocationManager.MODE_CHANGED_ACTION.equals(action)||BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){scanner.recover();startScan();return;}
        int state=i.getIntExtra(BluetoothAdapter.EXTRA_STATE,-1);
        if(state==BluetoothAdapter.STATE_ON){pausedScan=false;scanner.wake();startScan();}
        else if(state==BluetoothAdapter.STATE_TURNING_OFF){pausedScan=true;scanner.pauseLink(true);if(link!=null)link.close("Bluetooth off");pausedScan=true;main.post(()->voice.cancelIfOwned(SmartButtonsService.this));status="藍牙已關閉";}
    });}};
    @Override public void onDestroy(){
        destroyed=true;pairing=false;main.removeCallbacksAndMessages(null);unregisterReceiver(bluetooth);voice.cancelIfOwned(this);
        scanner.close(()->{scanner.handler().removeCallbacks(pairTimeout);scanner.handler().removeCallbacks(park);if(link!=null)link.close("service stopped");connections.clear();});
        status="已停止靈動貼";stopForeground(true);super.onDestroy();
    }
}
