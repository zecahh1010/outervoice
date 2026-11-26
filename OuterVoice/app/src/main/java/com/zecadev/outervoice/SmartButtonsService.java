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
    private BluetoothLeScanner scanner;
    private ButtonLink link;
    private VoiceSession voice;
    private boolean scanning, destroyed, pausedScan;
    private int scanGeneration;
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
        if (STOP.equals(action)) { c.stopService(new Intent(c, SmartButtonsService.class)); status = "已停止靈動貼"; return; }
        try { c.startForegroundService(new Intent(c, SmartButtonsService.class).setAction(action)); }
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
        registerReceiver(bluetooth, changes);
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
        if (STOP.equals(a)) { SmartButtonStore.prefs(this).edit().putBoolean("enabled", false).apply(); stopSelf(); return START_NOT_STICKY; }
        if (!SmartButtonStore.prefs(this).getBoolean("enabled", false) || !permitted(this)) { status = "請先允許藍牙掃描及麥克風權限"; stopSelf(); return START_NOT_STICKY; }
        if (link != null && !SmartButtonStore.devices(this).contains(link.address) && (!link.binding || link.wasReady())) link.close("forgotten");
        if (PAIR.equals(a)) {
            pairing = true; status = "配對中 · 長按靈動貼約 3 秒，等背光閃動";
            main.removeCallbacks(pairTimeout); main.postDelayed(pairTimeout, 90_000);
        } else if (CANCEL_PAIR.equals(a)) endPair("已取消配對");
        startScan(); return START_NOT_STICKY;
    }
    private void endPair(String why) {
        pairing = false; main.removeCallbacks(pairTimeout); status = why;
        if (link != null && link.binding && !link.wasReady()) link.close("pairing ended");
    }
    private void startScan() {
        if (destroyed || scanning || pausedScan) return;
        try {
            if (adapter == null || !getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) { status = "此裝置沒有 BLE 藍牙支援"; return; }
            if (!adapter.isEnabled()) { status = "藍牙未開啟 · 請到系統藍牙設定開啟"; return; }
            if (Build.VERSION.SDK_INT <= 30 || getApplicationInfo().targetSdkVersion <= 30) {
                android.location.LocationManager lm = (android.location.LocationManager)getSystemService(LOCATION_SERVICE);
                if (lm != null && !lm.isLocationEnabled()) { status = "請開啟系統位置服務以容許 BLE 掃描（不會收集位置）"; return; }
            }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) { status = "BLE 掃描器未就緒 · 請重試"; return; }
            ScanFilter f = new ScanFilter.Builder().setDeviceName("ZEEKR_button").build();
            scanning = true;
            scanner.startScan(Collections.singletonList(f), new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0).build(), scan);
            if (!pairing) status = "正在接收靈動貼按鍵 · 閒置斷線時仍接收廣播";
        } catch (RuntimeException e) { scanning = false; status = "BLE 掃描失敗：" + e.getMessage(); }
    }
    private void stopScan() { scanGeneration++; if (scanner != null && scanning) try { scanner.stopScan(scan); } catch (RuntimeException ignored) { } scanning = false; }
    private final ScanCallback scan = new ScanCallback() {
        @Override public void onScanResult(int type, ScanResult r) { main.post(() -> offer(r)); }
        @Override public void onBatchScanResults(List<ScanResult> rs) { main.post(() -> { for (ScanResult r : rs) offer(r); }); }
        @Override public void onScanFailed(int error) { main.post(() -> {
            if (destroyed) return; scanning = false; status = "BLE 掃描失敗 " + error + " · 30 秒後重試";
            int generation = ++scanGeneration;
            main.postDelayed(() -> { if (!destroyed && generation == scanGeneration) startScan(); }, 30_000);
        }); }
    };
    private void offer(ScanResult result) {
        if (destroyed || !scanning) return;
        try {
            ScanRecord record = result.getScanRecord(); if (record == null || !"ZEEKR_button".equals(record.getDeviceName())) return;
            String address = result.getDevice().getAddress();
            SmartButtonAd.Advert ad = SmartButtonAd.parse(record.getBytes(), Accessory.BUTTON); if (ad == null) return;
            boolean bound = SmartButtonStore.devices(this).contains(address);
            if (!bound && !pairing) return;
            if (bound) {
                if (ad.battery >= 0) SmartButtonStore.prefs(this).edit().putInt("battery." + address, ad.battery).apply();
                int previous = counters.containsKey(address) ? counters.get(address) : -1;
                if (!SmartButtonAd.isNew(ad.counter, previous)) return;
                counters.put(address, ad.counter);
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
            counters.remove(l.address); connections.put(l.address, "已連線");
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
            if (l.everConnected) counters.remove(l.address);
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
            if (android.location.LocationManager.MODE_CHANGED_ACTION.equals(i.getAction())) { stopScan(); startScan(); return; }
            if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_ON) { pausedScan = false; startScan(); }
            else if (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) == BluetoothAdapter.STATE_TURNING_OFF) {
                stopScan(); pausedScan = true; if (link != null) link.close("Bluetooth off"); pausedScan = true; voice.cancelIfOwned(SmartButtonsService.this); status = "藍牙已關閉";
            }
        }
    };
    @Override public void onDestroy() {
        destroyed = true; pairing = false; stopScan(); if (link != null) link.close("service stopped");
        main.removeCallbacksAndMessages(null); unregisterReceiver(bluetooth); voice.cancelIfOwned(this); connections.clear();
        status = "已停止靈動貼"; stopForeground(true); super.onDestroy();
    }
}
