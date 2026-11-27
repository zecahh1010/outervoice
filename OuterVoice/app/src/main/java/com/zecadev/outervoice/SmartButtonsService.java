/*
 * Based on Zeekr Shortcut - https://github.com/dts88/zeekr-shortcut-car
 * Copyright (C) 2026 dts88
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional terms under GPL-3.0 section 7 apply; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-26: audio-only actions and local pairing UI.
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

/** Foreground BLE listener. Does not depend on a visible activity or overlay. */
public final class SmartButtonsService extends Service {
    static final String PAIR = "pair", CANCEL_PAIR = "cancel_pair", STOP = "stop", SYNC = "sync";
    static volatile String status = "未啟用靈動貼";
    static volatile boolean pairing;
    static final Map<String, String> connections = new java.util.concurrent.ConcurrentHashMap<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Map<String, Integer> counters = new HashMap<>();
    private final Map<String, Last> presses = new HashMap<>();
    private BluetoothAdapter adapter;
    private ButtonScanner scanner;
    private ButtonLink link;
    private VoiceSession voice;
    private boolean destroyed, pausedScan, sleeping;
    private final Runnable pairTimeout = () -> endPair("配對逾時 · 請長按靈動貼約 3 秒，背光閃動時再試");
    private static final class Last { int gesture; boolean notification; long at; Last(int g, boolean n, long t) { gesture = g; notification = n; at = t; } }
    static boolean permitted(Context c) {
        // Keep target 28 for the head unit's existing direct USB/storage browser.
        // Android's legacy Bluetooth permission split also requires location permission.
        if (c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT < 31 || (c.checkSelfPermission("android.permission.BLUETOOTH_SCAN") == PackageManager.PERMISSION_GRANTED
            && c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") == PackageManager.PERMISSION_GRANTED);
    }
    static void command(Context c, String action) {
        if (STOP.equals(action)) { SmartButtonStore.prefs(c).edit().putBoolean("listening_session",false).apply(); c.stopService(new Intent(c, SmartButtonsService.class)); status = "已停止靈動貼"; return; }
        try { SmartButtonStore.prefs(c).edit().putBoolean("listening_session",true).apply(); c.startForegroundService(new Intent(c, SmartButtonsService.class).setAction(action)); }
        catch (RuntimeException e) { status = "無法啟動靈動貼：" + e.getMessage(); AudioDiagnostics.log(status); }
    }
    static void resume(Context c) { if (SmartButtonStore.prefs(c).getBoolean("enabled", false) && permitted(c)) command(c, SYNC); }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public void onCreate() {
        super.onCreate(); voice = VoiceSession.get(this);
        BluetoothManager manager = (BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter = manager == null ? null : manager.getAdapter();
        IntentFilter changes = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        changes.addAction(android.location.LocationManager.MODE_CHANGED_ACTION);
        changes.addAction(Intent.ACTION_SCREEN_OFF); changes.addAction(Intent.ACTION_SCREEN_ON); changes.addAction(Intent.ACTION_USER_PRESENT);
        changes.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(bluetooth, changes);
        scanner = new ButtonScanner(this,new ButtonScanner.Listener(){
            public void result(ScanResult r){offer(r);}
            public void status(String s){if(!pairing)status=s;}
            public void radioOff(){if(link!=null)link.close("Bluetooth off detected by polling");voice.cancelIfOwned(SmartButtonsService.this);}
        });
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("smart_buttons", "靈動貼", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 71, new Intent(this, MainActivity.class).putExtra("tab", "buttons"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 72, new Intent(this, SmartButtonsService.class).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(this, "smart_buttons").setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Outer Voice · 靈動貼").setContentText("在背景接收按鍵；點按開啟設定").setOngoing(true).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止靈動貼", stop).build();
        if (Build.VERSION.SDK_INT >= 30) startForeground(71, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE | android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        else startForeground(71, n);
    }
    @Override public int onStartCommand(Intent i, int flags, int id) {
        String a = i == null ? SYNC : i.getAction();
        if (STOP.equals(a)) { SmartButtonStore.prefs(this).edit().putBoolean("enabled", false).putBoolean("listening_session",false).apply(); stopSelf(); return START_NOT_STICKY; }
        if (!SmartButtonStore.prefs(this).getBoolean("enabled", false) || !permitted(this)) { status = "請先允許藍牙掃描及麥克風權限"; stopSelf(); return START_NOT_STICKY; }
        if (link != null && !SmartButtonStore.devices(this).contains(link.address) && (!link.binding || link.wasReady())) link.close("forgotten");
        if (PAIR.equals(a)) {
            pairing = true; status = "配對中 · 長按靈動貼約 3 秒，等背光閃動";
            main.removeCallbacks(pairTimeout); main.postDelayed(pairTimeout, 90_000);
        } else if (CANCEL_PAIR.equals(a)) endPair("已取消配對");
        startScan();
        if (PAIR.equals(a)) scanner.recover();
        if ("scan_result".equals(a)) { Intent result=i.getParcelableExtra("scan_result"); if(result!=null)scanner.receive(result); }
        return START_NOT_STICKY;
    }
    private void endPair(String why) {
        pairing = false; main.removeCallbacks(pairTimeout); status = why;
        if (link != null && link.binding && !link.wasReady()) link.close("pairing ended");
        startScan();
    }
    private void startScan() {
        if (destroyed || scanner == null) return;
        scanner.pauseLink(pausedScan);
        scanner.wanted(pairing || !SmartButtonStore.devices(this).isEmpty());
        if (!pairing && SmartButtonStore.devices(this).isEmpty()) status="尚未配對靈動貼 · 點按配對開始";
    }
    private void stopScan() { if(scanner!=null)scanner.pauseLink(true); }
    private final Runnable park = () -> {
        android.os.PowerManager power=(android.os.PowerManager)getSystemService(POWER_SERVICE);
        if(power!=null&&!power.isInteractive()){
            sleeping=true;scanner.park();if(link!=null)link.close("head unit sleeping");
            voice.cancelIfOwned(this);status="車機休眠 · 亮屏後自動恢復接收";
        }
    };
    private void offer(ScanResult result) {
        if (destroyed || sleeping || !SmartButtonStore.prefs(this).getBoolean("enabled",false)) return;
        try {
            ScanRecord record = result.getScanRecord(); if (record == null || !"ZEEKR_button".equals(record.getDeviceName())) return;
            String address = result.getDevice().getAddress();
            SmartButtonAd.Advert ad = SmartButtonAd.parse(record.getBytes(), Accessory.BUTTON); if (ad == null) return;
            boolean bound = SmartButtonStore.devices(this).contains(address);
            if (!bound && !pairing) return;
            if (bound) {
                if (ad.battery >= 0) SmartButtonStore.prefs(this).edit().putInt("battery." + address, ad.battery).apply();
                int previous = counters.containsKey(address) ? counters.get(address) : SmartButtonStore.prefs(this).getInt("counter."+address,-1);
                if (!SmartButtonAd.isNew(ad.counter, previous)) return;
                counters.put(address, ad.counter); SmartButtonStore.prefs(this).edit().putInt("counter."+address,ad.counter).apply();
                if (!pairing) press(address, ad.gesture, false);
            }
            if (ad.gesture == SmartButtonAd.LONG && (bound || pairing)) connect(address, !bound);
        } catch (RuntimeException e) { AudioDiagnostics.log("BLE advert: " + e.getMessage()); }
    }
    private void connect(String address, boolean binding) {
        if (link != null && !link.isClosed() && link.address.equals(address)) return;
        if (link != null && !link.isClosed() && !link.isReady()) return;
        if (binding && SmartButtonStore.devices(this).size() >= 5) { endPair("最多可儲存 5 個靈動貼"); return; }
        if (link != null) link.close("switch device");
        link = new ButtonLink(getApplicationContext(), main, adapter, address, Accessory.BUTTON, binding, links);
        link.start();
    }
    private final ButtonLink.Listener links = new ButtonLink.Listener() {
        public void attempt(ButtonLink l, int n) {
            connections.put(l.address, "連線中 · 第 " + n + " 次");
            if (n > 1) { pausedScan = true; stopScan(); }
        }
        public void ready(ButtonLink l) {
            counters.remove(l.address); SmartButtonStore.prefs(SmartButtonsService.this).edit().remove("counter."+l.address).apply(); connections.put(l.address, "已連線");
            if (l.binding) {
                SmartButtonStore.add(SmartButtonsService.this, l.address);
                pairing = false; main.removeCallbacks(pairTimeout); status = "已配對 · 可改名及設定按鍵動作";
            }
            pausedScan = false; startScan();
        }
        public void reply(ButtonLink l, SmartButtonAd.Reply r, byte[] raw) {
            if (!l.isReady() || pairing || !SmartButtonStore.devices(SmartButtonsService.this).contains(l.address)) return;
            if (r.gesture != 0) press(l.address, r.gesture, true);
        }
        public void battery(ButtonLink l, int percent) { if (percent >= 0 && percent <= 100) SmartButtonStore.prefs(SmartButtonsService.this).edit().putInt("battery." + l.address, percent).apply(); }
        public void closed(ButtonLink l, String why) {
            connections.put(l.address, "待機 · 可接收按鍵廣播");
            if (l.everConnected) { counters.remove(l.address); SmartButtonStore.prefs(SmartButtonsService.this).edit().remove("counter."+l.address).apply(); }
            if (link == l) link = null;
            if (l.binding && pairing) status = "未能連線 · 請再次長按靈動貼至背光閃動";
            pausedScan = false; if (!destroyed) startScan();
            AudioDiagnostics.log("Smart button " + l.address + ": " + why);
        }
    };
    private void press(String address, int gesture, boolean notification) {
        if (gesture != SmartButtonAd.SINGLE && gesture != SmartButtonAd.DOUBLE) return; // Long press is pairing only.
        long now = SystemClock.elapsedRealtime(); Last last = presses.get(address);
        if (last != null && SmartButtonAd.samePress(last.gesture, last.notification, last.at, gesture, notification, now)) return;
        presses.put(address, new Last(gesture, notification, now));
        if (!SmartButtonStore.prefs(this).getBoolean("enabled", false) || !SmartButtonStore.devices(this).contains(address)) return;
        status = SmartButtonStore.name(this, address) + (gesture == SmartButtonAd.DOUBLE ? " · 雙擊" : " · 單擊");
        SmartButtonActions.run(this, this, address, gesture);
    }
    private final BroadcastReceiver bluetooth = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String action=i.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)){main.removeCallbacks(park);main.postDelayed(park,40_000);return;}
            if (Intent.ACTION_SCREEN_ON.equals(action)||Intent.ACTION_USER_PRESENT.equals(action)){
                main.removeCallbacks(park);
                if(sleeping||Intent.ACTION_SCREEN_ON.equals(action)){sleeping=false;if(link!=null)link.close("head unit woke");scanner.wake();startScan();}return;
            }
            if (android.location.LocationManager.MODE_CHANGED_ACTION.equals(action)||BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){scanner.recover();startScan();return;}
            if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) { pausedScan = false; scanner.wake(); startScan(); }
            else if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_TURNING_OFF) {
                stopScan(); pausedScan = true; if (link != null) link.close("Bluetooth off"); pausedScan = true; voice.cancelIfOwned(SmartButtonsService.this); status = "藍牙已關閉";
            }
        }
    };
    @Override public void onDestroy() {
        destroyed = true; pairing = false; scanner.close(); if (link != null) link.close("service stopped");
        main.removeCallbacksAndMessages(null); unregisterReceiver(bluetooth); voice.cancelIfOwned(this); connections.clear();
        status = "已停止靈動貼"; stopForeground(true); super.onDestroy();
    }
}
