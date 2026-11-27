/* Based on Zeekr Shortcut - https://github.com/dts88/zeekr-shortcut-car
 * Copyright (C) 2026 dts88
 * SPDX-License-Identifier: GPL-3.0-only
 * Additional GPL section 7 terms apply; see NOTICE.md.
 * Modified for Outer Voice, 2026-11-27: PendingIntent scanning and sleep recovery. */
package com.zecadev.outervoice;

import android.app.PendingIntent;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import java.util.*;

final class ButtonScanner implements AutoCloseable {
    interface Listener{void result(ScanResult r);void status(String s);void radioOff();}
    static volatile ButtonScanner current;
    private final Context app; private final Listener listener;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final HandlerThread thread=new HandlerThread("outervoice-ble");
    private final Handler work; private final BleScanLoop loop;
    private final PendingIntent intent;private BluetoothLeScanner scanner;private BluetoothAdapter adapter;
    private volatile boolean closed;private boolean sleeping,linkPaused,wasOn;private int phone=-1;private boolean discovery;
    private static PendingIntent token(Context c){return PendingIntent.getBroadcast(c,471,new Intent(c,ButtonScanReceiver.class).setAction(ButtonScanReceiver.ACTION),PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0));}
    static void stopOrphan(Context c){try{BluetoothManager m=(BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE);BluetoothAdapter a=m==null?null:m.getAdapter();BluetoothLeScanner s=a==null?null:a.getBluetoothLeScanner();if(s!=null)s.stopScan(token(c));}catch(RuntimeException ignored){}}
    ButtonScanner(Context c,Listener l){
        app=c.getApplicationContext();listener=l;intent=token(app);thread.start();work=new Handler(thread.getLooper());
        loop=new BleScanLoop(new BleScanLoop.Host(){
            public long now(){return SystemClock.elapsedRealtime();}
            public void later(Runnable r,long ms){work.postDelayed(r,ms);}
            public void status(String s){main.post(()->{if(!closed)listener.status(s);});}
        },new BleScanLoop.Radio(){
            public boolean enabled(){try{
                BluetoothManager m=(BluetoothManager)app.getSystemService(Context.BLUETOOTH_SERVICE);adapter=m==null?null:m.getAdapter();
                boolean on=adapter!=null&&adapter.isEnabled();
                if(wasOn&&!on)main.post(()->{if(!closed)listener.radioOff();});wasOn=on;
                if(!on||!SmartButtonsService.permitted(app))return false;
                android.location.LocationManager loc=(android.location.LocationManager)app.getSystemService(Context.LOCATION_SERVICE);
                return loc==null||loc.isLocationEnabled();
            }catch(RuntimeException e){return false;}}
            public void stop(){try{if(scanner!=null)scanner.stopScan(intent);stopOrphan(app);}catch(RuntimeException ignored){}scanner=null;}
            public void repair(){if(adapter!=null){StaleGatt.Result r=StaleGatt.replaceIfDead(adapter);if(!"alive".equals(r.text))AudioDiagnostics.log("BLE GATT recovery: "+r.text);}}
            public int start(){try{
                scanner=adapter.getBluetoothLeScanner();if(scanner==null)return 3;
                return scanner.startScan(Collections.singletonList(new ScanFilter.Builder().setDeviceName("ZEEKR_button").build()),new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES).setLegacy(true).setReportDelay(0).build(),intent);
            }catch(RuntimeException e){AudioDiagnostics.log("BLE registration: "+e.getMessage());return 3;}}
        });
        current=this;work.post(watchDiscovery);
    }
    void wanted(boolean on){work.post(()->{if(!closed)loop.wanted(on);});}
    void pauseLink(boolean on){work.post(()->{linkPaused=on;loop.pause(on||sleeping);});}
    void park(){work.post(()->{sleeping=true;loop.pause(true);});}
    void wake(){work.post(()->{sleeping=false;loop.recover(10_000);loop.pause(linkPaused);});}
    void recover(){work.post(()->loop.recover(500));}
    boolean running(){return loop.scanning;}
    void receive(Intent value){
        final Intent copy=new Intent(value);work.post(()->{
            if(closed)return;
            int error=copy.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE,0);
            if(error!=0){loop.failed(error);return;}
            ArrayList<ScanResult> results=copy.getParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT);
            if(results!=null)main.post(()->{if(!closed)for(ScanResult r:results)listener.result(r);});
        });
    }
    private final Runnable watchDiscovery=new Runnable(){public void run(){
        if(closed)return;
        try{if(!sleeping&&adapter!=null&&adapter.isEnabled()){
            boolean d=adapter.isDiscovering();int p=adapter.getProfileConnectionState(11)*4+adapter.getProfileConnectionState(16);
            if((discovery&&!d)||(phone>=0&&p!=phone))loop.recover(200);discovery=d;phone=p;
        }}catch(RuntimeException ignored){}
        work.postDelayed(this,2_000);
    }};
    @Override public void close(){closed=true;if(current==this)current=null;main.removeCallbacksAndMessages(null);work.post(()->{loop.wanted(false);work.removeCallbacksAndMessages(null);thread.quitSafely();});}
}
