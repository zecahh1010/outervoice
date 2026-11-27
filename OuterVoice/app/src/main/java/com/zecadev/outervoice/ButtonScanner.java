/* Based on Zeekr Shortcut - https://github.com/dts88/zeekr-shortcut-car
 * Copyright (C) 2026 dts88; GPL-3.0-only with applicable section 7 terms; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-27: selectable transports and receiver/main-thread decoding. */
package com.zecadev.outervoice;

import android.app.PendingIntent;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import java.util.*;

final class ButtonScanner implements AutoCloseable {
    interface Listener{void result(ScanResult r);void status(String s);void radioOff();}
    static volatile ButtonScanner current;
    private static final ArrayDeque<Long> startsBudget=new ArrayDeque<>();
    final String method;
    private final Context app;private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper()),work;
    private final HandlerThread thread;
    private final BleScanLoop loop;
    private final PendingIntent intent;
    private final String probe=UUID.randomUUID().toString();
    private ScanCallback callback;private BluetoothLeScanner scanner;private BluetoothAdapter adapter;
    private volatile boolean closed;private boolean sleeping,linkPaused,wasOn;private int phone=-1;private boolean discovery;
    volatile String readiness="準備 BLE 掃描",lastDecode="尚未收到系統 BLE 廣播";
    final long createdAt=SystemClock.elapsedRealtime();
    volatile long stopCompleted;
    volatile long legacyIntents;
    volatile long packets,receiverIntents,piPackets,callbackPackets,emptyIntents,decodeErrors,lastStart,lastReception;
    volatile int requestResult=-1;volatile boolean receiverVerified,decodedOnMain;
    static PendingIntent token(Context c){return PendingIntent.getBroadcast(c,472,new Intent(c,ButtonScanReceiver.class).setAction(ButtonScanReceiver.ACTION).setData(Uri.parse("outervoice://smart-button/shortcut")),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));}
    private static PendingIntent legacyToken(Context c){return PendingIntent.getBroadcast(c,471,new Intent(c,ButtonScanReceiver.class).setAction(ButtonScanReceiver.ACTION),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));}
    static void stopLegacy(Context c){try{BluetoothManager m=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);BluetoothAdapter a=m==null?null:m.getAdapter();BluetoothLeScanner s=a==null?null:a.getBluetoothLeScanner();if(s!=null)s.stopScan(legacyToken(c));}catch(RuntimeException ignored){}}
    static void stopOrphan(Context c){try{
        BluetoothManager m=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);BluetoothAdapter a=m==null?null:m.getAdapter();BluetoothLeScanner s=a==null?null:a.getBluetoothLeScanner();
        if(s!=null){s.stopScan(legacyToken(c));s.stopScan(token(c));}
    }catch(RuntimeException ignored){}}
    ButtonScanner(Context c,Listener l){
        app=c.getApplicationContext();listener=l;method=PairingMethod.get(app);
        if(PairingMethod.background(method)){thread=new HandlerThread("outervoice-ble");thread.start();work=new Handler(thread.getLooper());intent=token(app);}
        else{thread=null;work=new Handler(Looper.getMainLooper());intent=null;}
        loop=new BleScanLoop(new BleScanLoop.Host(){
            public long now(){return SystemClock.elapsedRealtime();}
            public void later(Runnable r,long ms){work.postDelayed(r,ms);}
            public void status(String s){readiness=s;AudioDiagnostics.log(s);if(!closed)listener.status(s);}
        },new BleScanLoop.Radio(){
            public boolean enabled(){try{
                BluetoothManager m=(BluetoothManager)app.getSystemService(Context.BLUETOOTH_SERVICE);adapter=m==null?null:m.getAdapter();
                boolean on=adapter!=null&&adapter.isEnabled();if(wasOn&&!on&&!closed)listener.radioOff();wasOn=on;
                if(!on){readiness="藍牙未開啟 · 請到系統藍牙設定開啟";return false;}
                if(!SmartButtonsService.permitted(app)){readiness="未允許"+SmartButtonPermissions.words(app)+"權限 · 請開啟權限";return false;}
                if(SmartButtonPermissions.locationSwitchRequired(Build.VERSION.SDK_INT,app.getApplicationInfo().targetSdkVersion,method)){
                    android.location.LocationManager loc=(android.location.LocationManager)app.getSystemService(Context.LOCATION_SERVICE);
                    if(loc!=null&&!loc.isLocationEnabled()){readiness="車機相容設定需要位置服務 · 請開啟位置服務";return false;}
                }
                return true;
            }catch(RuntimeException e){readiness="藍牙服務未就緒："+e.getMessage();return false;}}
            public String unavailable(){return readiness;}
            public void stop(){
                ScanCallback old=callback;callback=null;
                try{if(scanner!=null&&old!=null)scanner.stopScan(old);}catch(RuntimeException ignored){}
                stopOrphan(app);scanner=null;
            }
            public void repair(){if(adapter!=null){StaleGatt.Result r=StaleGatt.replaceIfDead(adapter);if(!"alive".equals(r.text))AudioDiagnostics.log("BLE GATT recovery: "+r.text);}}
            public int start(){try{
                scanner=adapter.getBluetoothLeScanner();if(scanner==null)return requestResult=3;
                lastStart=SystemClock.elapsedRealtime();List<ScanFilter> filters=Collections.singletonList(new ScanFilter.Builder().setDeviceName("ZEEKR_button").build());
                ScanSettings.Builder settings=new ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(0);
                if(PairingMethod.background(method)){
                    // Exact upstream scan settings and mutable, explicit receiver delivery.
                    settings.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE).setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setLegacy(true);
                    requestResult=scanner.startScan(filters,settings.build(),intent);
                    if(requestResult==0)main.post(()->probeReceiver());return requestResult;
                }
                callback=new ScanCallback(){
                    @Override public void onScanResult(int type,ScanResult r){execute(()->deliver(this,r));}
                    @Override public void onBatchScanResults(List<ScanResult> results){execute(()->{for(ScanResult r:results)deliver(this,r);});}
                    @Override public void onScanFailed(int error){execute(()->{if(!closed&&callback==this){requestResult=error;loop.failed(error);}});}
                };
                scanner.startScan(filters,settings.build(),callback);return requestResult=0;
            }catch(RuntimeException e){lastDecode="掃描請求異常："+e.getMessage();return requestResult=3;}}
        },startsBudget);
        current=this;work.post(watchDiscovery);
    }
    void execute(Runnable r){if(closed)return;if(Looper.myLooper()==work.getLooper())r.run();else work.post(()->{if(!closed)r.run();});}
    Handler handler(){return work;}
    void wanted(boolean on){execute(()->loop.wanted(on));}
    void pauseLink(boolean on){execute(()->{linkPaused=on;loop.pause(on||sleeping);});}
    void park(){execute(()->{sleeping=true;loop.pause(true);});}
    void wake(){execute(()->{sleeping=false;loop.recover(10_000);loop.pause(linkPaused);});}
    void recover(){execute(()->loop.recover(500));}
    void binding(boolean on){execute(()->{if(on){sleeping=false;linkPaused=false;}loop.binding(on);});}
    boolean running(){return loop.scanning;}
    private boolean accepts(){return !closed&&method.equals(PairingMethod.get(app))&&SmartButtonStore.prefs(app).getBoolean("enabled",false)&&SmartButtonStore.prefs(app).getBoolean("listening_session",false);}
    private void deliver(ScanCallback source,ScanResult result){
        if(source!=callback||!accepts())return;callbackPackets++;packets++;lastReception=SystemClock.elapsedRealtime();lastDecode="已收到 callback 廣播";listener.result(result);
    }
    private void probeReceiver(){
        if(!accepts()||intent==null)return;
        try{intent.send(app,0,new Intent().putExtra("ov_receiver_probe",probe));}
        catch(PendingIntent.CanceledException|RuntimeException e){lastDecode="接收通道自檢失敗："+e.getMessage();}
    }
    void receive(Intent value){
        if(Looper.myLooper()!=Looper.getMainLooper()){main.post(()->receive(value));return;}
        if(!PairingMethod.background(method)||!accepts()){stopOrphan(app);return;}
        try{String tag=value.getStringExtra("ov_receiver_probe");if(tag!=null){if(probe.equals(tag))receiverVerified=true;return;}}
        catch(RuntimeException e){receiverIntents++;decodeErrors++;lastDecode="系統廣播無法讀取："+e.getClass().getSimpleName();return;}
        receiverIntents++;decodedOnMain=true;
        final int error;final ArrayList<ScanResult> results;
        try{
            error=value.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE,0);
            ArrayList<ScanResult> list;
            if(Build.VERSION.SDK_INT>=33){
                try{list=value.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,ScanResult.class);if(list==null)list=value.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);}
                catch(RuntimeException typed){list=value.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);}
            }else list=value.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
            results=new ArrayList<>();if(list!=null)for(Object item:list){if(item instanceof ScanResult)results.add((ScanResult)item);else{decodeErrors++;lastDecode="系統廣播包含無效掃描結果";}}
            if(error==0&&results.isEmpty()){emptyIntents++;lastDecode="收到系統廣播，但沒有掃描結果";}
        }catch(RuntimeException e){decodeErrors++;lastDecode="系統廣播讀取失敗："+e.getClass().getSimpleName();SmartButtonsController.note(lastDecode);return;}
        execute(()->{
            if(!accepts())return;
            if(error!=0){if(loop.scanning){requestResult=error;loop.failed(error);}else lastDecode="已忽略停止掃描後送達的錯誤 "+error;return;}
            for(ScanResult result:results){if(result==null)continue;piPackets++;packets++;lastReception=SystemClock.elapsedRealtime();lastDecode="已讀取系統 BLE 廣播";listener.result(result);}
        });
    }
    String facts(){
        return "方式："+PairingMethod.label(method)+"\n執行緒："+(thread==null?"main":"outervoice-ble")+
            "\nAndroid / target："+Build.VERSION.SDK_INT+" / "+app.getApplicationInfo().targetSdkVersion+
            "\n掃描請求結果："+requestResult+"（0 只代表已接受請求）\n接收通道自檢："+(intent==null?"callback 直接接收":receiverVerified?"已通過（不算按鍵廣播）":"等待回覆")+
            "\n系統 BLE 廣播："+receiverIntents+"\nPendingIntent / callback 結果："+piPackets+" / "+callbackPackets+
            "\n已忽略舊版廣播："+legacyIntents+"\n空廣播 / 讀取失敗："+emptyIntents+" / "+decodeErrors+"\n最近接收："+lastDecode;
    }
    private final Runnable watchDiscovery=new Runnable(){public void run(){
        if(closed)return;
        try{if(accepts()&&!sleeping&&adapter!=null&&adapter.isEnabled()){
            boolean d=adapter.isDiscovering();int p=adapter.getProfileConnectionState(11)*4+adapter.getProfileConnectionState(16);
            if((discovery&&!d)||(phone>=0&&p!=phone))loop.recover(200);discovery=d;phone=p;
        }}catch(RuntimeException ignored){}
        work.postDelayed(this,2_000);
    }};
    void close(Runnable cleanup){close(cleanup,null);}
    void close(Runnable cleanup,Runnable finished){
        closed=true;if(current==this)current=null;main.removeCallbacksAndMessages(null);
        work.post(()->{try{cleanup.run();}finally{
            loop.wanted(false);stopCompleted=SystemClock.elapsedRealtime();work.removeCallbacksAndMessages(null);
            if(thread!=null)thread.quitSafely();if(finished!=null)main.post(finished);
        }});
    }
    @Override public void close(){close(()->{});}
}