package com.zecadev.outervoice;

import java.util.*;

public final class RecoveryPolicyTest {
    static void check(boolean good,String what){if(!good)throw new AssertionError(what);}
    static final class Task implements Comparable<Task>{long at,order;Runnable run;public int compareTo(Task t){int c=Long.compare(at,t.at);return c==0?Long.compare(order,t.order):c;}}
    static final class Clock implements BleScanLoop.Host{
        long time,sequence;PriorityQueue<Task> tasks=new PriorityQueue<>();public long now(){return time;}
        public void later(Runnable r,long ms){Task t=new Task();t.at=time+ms;t.order=sequence++;t.run=r;tasks.add(t);}
        public void status(String text){}
        void advance(long delta){long end=time+delta;int guard=0;while(!tasks.isEmpty()&&tasks.peek().at<=end){check(++guard<10000,"no retry spin");Task t=tasks.remove();time=t.at;t.run.run();}time=end;}
    }
    static final class Radio implements BleScanLoop.Radio{
        final Clock clock;boolean on=true,dead,registered;int stops,repairs,started;List<Long> times=new ArrayList<>();
        Radio(Clock c){clock=c;}public boolean enabled(){return on;}
        public void stop(){stops++;registered=false;}public void repair(){repairs++;dead=false;}
        public int start(){started++;times.add(clock.time);if(dead)return 2;check(!registered,"cleanup before registration");registered=true;return 0;}
    }
    static class Dead{boolean alive;Dead(boolean a){alive=a;}}
    static class Holder{Object stale,live,cycle;static Object shared=new Dead(false);}
    public static void main(String[] args){
        Clock c=new Clock();Radio r=new Radio(c);BleScanLoop loop=new BleScanLoop(c,r);
        loop.wanted(true);check(!loop.scanning,"start waits for cleanup gap");c.advance(500);check(loop.scanning&&r.started==1,"first registration");
        for(int i=0;i<8;i++){
            r.dead=true;int stops=r.stops;loop.failed(2);check(r.stops>stops&&!r.dead,"error2 cleans registration and repairs stale service");
            c.advance(35000);check(loop.scanning,"registration recovers without head unit reboot");
            loop.pause(true);int before=r.started;c.advance(60000);check(r.started==before&&!loop.scanning,"park holds radio resources closed");
            loop.recover(10000);loop.pause(false);c.advance(9999);check(!loop.scanning,"wake settlement");c.advance(501);check(loop.scanning,"wake re-registers after settlement");
        }
        for(int i=0;i<12;i++){loop.recover(0);c.advance(600);}
        c.advance(60000);
        for(long t:r.times){int count=0;for(long other:r.times)if(other>=t&&other<t+30000)count++;check(count<=3,"at most three registrations per 30 seconds");}
        loop.wanted(false);int before=r.started;c.advance(1800000);check(r.started==before&&!r.registered,"no orphan renew/retry after stop");
        loop.wanted(true);loop.pause(true);c.advance(1000);check(r.started==before,"pending start invalidated by pause");loop.pause(false);c.advance(500);check(loop.scanning,"resume after pending cancellation");
        r.on=false;c.advance(2500);check(!loop.scanning&&!r.registered,"missed Bluetooth off broadcast is detected by polling");r.on=true;c.advance(6000);check(loop.scanning,"Bluetooth on recovers without a state broadcast");
        Holder holder=new Holder();Dead stale=new Dead(false),live=new Dead(true),fresh=new Dead(true);holder.stale=stale;holder.live=live;holder.cycle=holder;Object shared=Holder.shared;
        check(StaleGatt.swap(holder,o->o instanceof Dead&&!((Dead)o).alive,fresh)==1,"only stale instance replaced in wrapper graph");
        check(holder.stale==fresh&&holder.live==live&&Holder.shared==shared,"live binder/static fields unchanged; cycles bounded");
        check(LiveCaptureMode.source("compat",33,false)==6&&LiveCaptureMode.source("compat",33,true)==9,"Micless compatible sources");
        check(LiveCaptureMode.source("performance",33,false)==10&&LiveCaptureMode.source("standard",28,false)==1,"performance and legacy modes");
        try{LiveCaptureMode.source("performance",28,false);throw new AssertionError("API guard");}catch(IllegalArgumentException expected){}
        List<EndpointInfo> ports=Arrays.asList(new EndpointInfo(7,15,"","Pixel 3a"),new EndpointInfo(8,15,"","Pixel 3a"));
        check(EndpointInfo.resolve(ports,ports.get(0).key(),true).id==7,"same-boot duplicate microphones retain explicit selection");
        check(EndpointInfo.resolve(ports,ports.get(0).key(),false)==null,"rebooted ambiguous microphone requires selection");
        System.out.println("PASS eight BLE error2/park/wake cycles, cleanup, retry throttling, stale-wrapper swap, shutdown, Micless capture policy and duplicate mic identity");
    }
}
