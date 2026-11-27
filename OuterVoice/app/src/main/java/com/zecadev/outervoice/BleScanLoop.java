package com.zecadev.outervoice;

import java.util.ArrayDeque;

/** Serialized scan recovery; clock/radio ports allow deterministic sleep and failure tests. */
final class BleScanLoop {
    interface Host { long now(); void later(Runnable r,long ms); void status(String text); }
    interface Radio { boolean enabled(); void stop(); void repair(); int start(); }
    private final Host host; private final Radio radio;
    private final ArrayDeque<Long> starts=new ArrayDeque<>();
    private int generation, failures; private long settledAt, retryAt;
    private boolean wanted, paused, pending;
    volatile boolean scanning;
    BleScanLoop(Host h,Radio r){host=h;radio=r;}
    void wanted(boolean on){wanted=on;if(!on)stop();else ensure();}
    void pause(boolean on){paused=on;if(on)stop();else ensure();}
    void recover(long settle){stop();settledAt=host.now()+settle;retryAt=0;ensure();}
    void stop(){generation++;pending=false;scanning=false;radio.stop();}
    private void schedule(long delay){if(pending)return;pending=true;int token=generation;host.later(()->{if(token!=generation)return;pending=false;ensure();},Math.max(0,delay));}
    private void ensure(){
        if(!wanted||paused||pending||scanning)return;
        long now=host.now();while(!starts.isEmpty()&&now-starts.peekFirst()>=30_000)starts.removeFirst();
        long at=Math.max(settledAt,retryAt);if(starts.size()>=3)at=Math.max(at,starts.peekFirst()+30_050);
        if(now<at){host.status("BLE 正在恢復 · "+Math.max(1,(at-now+999)/1000)+" 秒後重試");schedule(at-now);return;}
        if(!radio.enabled()){host.status("藍牙未就緒 · 等待系統藍牙服務");schedule(5_000);return;}
        // Always stop the stable PendingIntent, even after registration failure or process restart.
        radio.stop();radio.repair();pending=true;int token=generation;
        host.later(()->{
            if(token!=generation)return;pending=false;if(!wanted||paused)return;
            if(!radio.enabled()){schedule(5_000);return;}
            starts.addLast(host.now());int result=radio.start();
            if(result!=0){failed(result);return;}scanning=true;failures=0;
            host.status("正在接收靈動貼按鍵 · 待機時接收廣播");watch(token);
            host.later(()->{if(token==generation&&scanning)recover(500);},25*60_000L);
        },500);
    }
    private void watch(int token){host.later(()->{
        if(token!=generation||!scanning)return;
        if(!radio.enabled()){stop();radio.repair();schedule(5_000);}else watch(token);
    },2_000);}
    void failed(int error){
        stop();radio.repair();failures++;retryAt=host.now()+5_000;
        host.status("BLE 掃描失敗 "+error+" · 已清理登記，正在恢復（"+failures+"）");schedule(5_000);
    }
}
