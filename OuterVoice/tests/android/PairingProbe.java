package com.zecadev.outervoice.probe;

import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Local Android 13 integration tests. Advertisements/live state are injected, never car evidence. */
public final class PairingProbe extends Instrumentation {
    private Context c;private Object scanner,service,voice;private File dir;private boolean animation;
    private final StringBuilder report=new StringBuilder();private final Map<String,Map<String,?>> backup=new HashMap<>();
    private static final String ADDRESS="AA:BB:CC:DD:EE:61";
    interface Task{void run()throws Exception;}
    private Class<?> cls(String s)throws Exception{return Class.forName("com.zecadev.outervoice."+s,true,c.getClassLoader());}
    private Field f(Class<?> k,String n)throws Exception{Field f=k.getDeclaredField(n);f.setAccessible(true);return f;}
    private Object field(Object o,String n)throws Exception{return f(o.getClass(),n).get(o);}
    private Object sf(String k,String n)throws Exception{return f(cls(k),n).get(null);}
    private Object call(Object o,String n,Class<?>[] p,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(o,a);}
    private Object stat(String k,String n,Class<?>[] p,Object...a)throws Exception{Method m=cls(k).getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(null,a);}
    private void check(boolean b,String s){if(!b)throw new AssertionError(s);report.append("PASS ").append(s).append('\n');}
    private SharedPreferences prefs(String n){return c.getSharedPreferences(n,0);}
    private void ui(Task task)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{task.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception(e[0]);}
    private void work(Task task)throws Exception{CountDownLatch done=new CountDownLatch(1);Throwable[] e={null};((Handler)field(scanner,"work")).post(()->{try{task.run();}catch(Throwable t){e[0]=t;}finally{done.countDown();}});if(!done.await(8,TimeUnit.SECONDS))throw new AssertionError("BLE worker timeout");if(e[0]!=null)throw new Exception(e[0]);}
    private void pair()throws Exception{ui(()->stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,"pair"));Thread.sleep(900);scanner=sf("ButtonScanner","current");service=field(field(scanner,"listener"),"this$0");work(()->{});}
    private ScanResult advertisement(int gesture,String name)throws Exception{
        byte[] label=name.getBytes("UTF-8"),raw=new byte[15+label.length];
        byte[] prefix={2,1,6,8,2,(byte)255,0,(byte)gesture,50,1,2,1};System.arraycopy(prefix,0,raw,0,12);
        raw[12]=(byte)(label.length+1);raw[13]=9;System.arraycopy(label,0,raw,14,label.length);
        Method parse=ScanRecord.class.getDeclaredMethod("parseFromBytes",byte[].class);parse.setAccessible(true);
        ScanRecord record=(ScanRecord)parse.invoke(null,(Object)raw);
        if(!name.equals(record.getDeviceName()))throw new AssertionError("invalid native scan fixture");
        BluetoothAdapter adapter=((BluetoothManager)c.getSystemService(Context.BLUETOOTH_SERVICE)).getAdapter();
        return new ScanResult(adapter.getRemoteDevice(ADDRESS),record,-40,SystemClock.elapsedRealtimeNanos());
    }
    private void deliver(ScanResult result)throws Exception{
        ScanCallback callback=(ScanCallback)field(scanner,"callback");if(callback==null)throw new AssertionError("callback is not registered");
        callback.onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES,result);work(()->{});
    }
    private void frames(String name)throws Exception{
        final View[] button={null};ui(()->button[0]=(View)field(overlay(),"mic"));View b=button[0];Rect r=new Rect();ui(()->{int[] at=new int[2];b.getLocationOnScreen(at);r.set(at[0],at[1],at[0]+b.getWidth(),at[1]+b.getHeight());});
        Bitmap one=getUiAutomation().takeScreenshot();Thread.sleep(270);Bitmap two=getUiAutomation().takeScreenshot();int changed=0,red=0;
        for(int y=Math.max(0,r.top);y<Math.min(r.bottom,one.getHeight());y++)for(int x=Math.max(0,r.left);x<Math.min(r.right,one.getWidth());x++){
            int p=one.getPixel(x,y);if(Color.red(p)>Color.green(p)*1.4&&Color.red(p)>Color.blue(p)*1.4)red++;if(p!=two.getPixel(x,y))changed++;
        }
        check(red>100,name+" Live Speak button is red");check(changed>20,name+" Live Speak button visibly pulses between native frames");
        try(FileOutputStream out=new FileOutputStream(new File(dir,name+"-live.png"))){two.compress(Bitmap.CompressFormat.PNG,100,out);}one.recycle();two.recycle();
        check(b.getContentDescription().toString().equals("Live Speak active; tap to stop"),name+" has an explicit live Stop description");
    }
    private Object overlay()throws Exception{
        // Observer is a bound Runnable. Resolve its captured FloatingPanelService, without a release test hook.
        for(Object observer:(List<?>)field(voice,"observers"))for(Field capture:observer.getClass().getDeclaredFields()){
            capture.setAccessible(true);Object target=capture.get(observer);if(target!=null&&target.getClass().getName().endsWith("FloatingPanelService"))return target;
        }throw new AssertionError("overlay observer missing");
    }
    private void restore(String key){SharedPreferences.Editor e=prefs(key).edit().clear();for(Map.Entry<String,?> item:backup.get(key).entrySet()){
        Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);
    }e.commit();}
    private void animation()throws Exception{
        prefs("speaking").edit().putInt("screen_mode",2).commit();
        Activity activity=startActivitySync(new Intent().setClassName(c,"com.zecadev.outervoice.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(700);
        ui(()->voice=stat("VoiceSession","get",new Class<?>[]{Context.class},c));
        for(boolean minimized:new boolean[]{false,true}){
            prefs("floating").edit().putBoolean("enabled",true).putBoolean("minimized",minimized).putBoolean("auto_minimize",false).putInt("x",640).putInt("y",100).putInt("bubble_x",840).putInt("bubble_y",180).putString("items","[{\"id\":\"live-speak\",\"selected\":true}]").commit();
            ui(()->stat("FloatingPanelService","sync",new Class<?>[]{Context.class,boolean.class},c,true));Thread.sleep(650);
            // Emulator has no BUS12. Inject only session state to test the actual release overlay renderer.
            ui(()->{f(voice.getClass(),"liveRequested").setBoolean(voice,true);f(voice.getClass(),"activeMode").setInt(voice,2);f(voice.getClass(),"owner").set(voice,this);call(voice,"changed",new Class<?>[0]);});Thread.sleep(220);
            frames(minimized?"minimized":"expanded");
            Object panel=overlay();View mic=(View)field(panel,"mic");ui(()->call(panel,"finishRecording",new Class<?>[0]));Thread.sleep(300);
            check(!(Boolean)call(voice,"live",new Class<?>[0]),"floating Stop ends a Live session owned by another controller");
            check(!(Boolean)field(panel,"speechAnimating")&&mic.getForeground()==null&&Math.abs(mic.getScaleX()-1)<.01f,"stopping Live clears pulse and restores button scale");
        }
        prefs("smart_buttons").edit().putBoolean("enabled",true).putBoolean("listening_session",true).putString("devices","[]").commit();
        ui(()->{call(activity,"showSmartButtons",new Class<?>[0]);stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,"pair");});Thread.sleep(1800);
        Bitmap page=getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(dir,"pairing-page.png"))){page.compress(Bitmap.CompressFormat.PNG,100,out);}page.recycle();
        report.append("BOUNDARY: native overlay windows and rendered animation with injected live session state; emulator has no exterior BUS12, so this does not validate live car audio.\n");
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);animation=b!=null&&"true".equals(b.getString("animation"));start();}
    @Override public void onStart(){new Thread(()->{Bundle result=new Bundle();try{
        c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"v162-qa");dir.mkdirs();
        for(String key:new String[]{"smart_buttons","floating","speaking"})backup.put(key,new HashMap<>(prefs(key).getAll()));
        if(animation){animation();}else{
        prefs("floating").edit().putBoolean("enabled",false).commit();
        prefs("smart_buttons").edit().clear().putBoolean("enabled",true).putBoolean("listening_session",true).putString("devices","[]").commit();
        check(((android.location.LocationManager)c.getSystemService(Context.LOCATION_SERVICE)).isLocationEnabled(),"location is ON as required by v1.5.0 target-28 compatibility");
        check(c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==0,"v1.5.0 fine location permission is granted");
        check(c.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)!=0,"BLE pairing does not require microphone permission");
        pair();check(scanner!=null,"foreground callback discovery starts with v1.5.0 permission set");
        Object loop=field(scanner,"loop");
        long deadline=SystemClock.elapsedRealtime()+8000;while(field(scanner,"callback")==null&&SystemClock.elapsedRealtime()<deadline)Thread.sleep(50);work(()->{});
        check((Boolean)field(loop,"scanning")&&field(scanner,"callback")!=null,"Android ScanCallback request starts with the v1.5.0 overload");
        check(((Handler)field(scanner,"work")).getLooper()==Looper.getMainLooper(),"scan registration, delivery and GATT use the v1.5.0 main looper");
        int starts=((Collection<?>)field(loop,"starts")).size();pair();check(((Collection<?>)field(loop,"starts")).size()==starts,"reopening pairing preserves the healthy scanner registration");
        deliver(advertisement(0,"Other_button"));check(field(service,"link")==null,"unrelated advertisements cannot start GATT pairing");
        deliver(advertisement(0,"ZEEKR_button"));check(((Long)sf("SmartButtonsService","buttonPackets"))==1,"registered ScanCallback delivers a named short-press advertisement");
        check(field(service,"link")==null&&sf("SmartButtonsService","status").toString().contains("長按"),"short press is detected and requests long press without binding");
        ScanCallback active=(ScanCallback)field(scanner,"callback");
        active.onBatchScanResults(Arrays.asList(advertisement(0,"ZEEKR_button"),advertisement(0,"ZEEKR_button")));work(()->{});
        check(((Long)sf("SmartButtonsService","buttonPackets"))==3,"batched callback results follow the same discovery path");
        long beforeLegacy=(Long)field(scanner,"packets");
        BroadcastReceiver receiver=(BroadcastReceiver)cls("ButtonScanReceiver").getConstructor().newInstance();
        ui(()->receiver.onReceive(c,new Intent("com.zecadev.outervoice.SMART_BUTTON_SCAN").putParcelableArrayListExtra(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT,new ArrayList<>(Arrays.asList(advertisement(1,"ZEEKR_button"))))));work(()->{});
        check((Long)field(scanner,"packets")==beforeLegacy&&field(scanner,"callback")==active,"old PendingIntent broadcasts cannot feed or replace callback discovery");
        deliver(advertisement(1,"ZEEKR_button"));Object link=field(service,"link");check(link!=null,"long-press advertisement starts the production GATT connection path");
        check(field(link,"bt")==field(scanner,"work"),"GATT and scan operations share the same serialized BLE handler");
        check(!prefs("smart_buttons").getString("devices","").contains(ADDRESS),"discovery alone never saves a binding before notification subscription");
        deliver(advertisement(1,"ZEEKR_button"));check(field(service,"link")==link,"repeated long-press packets do not create duplicate GATT links");
        active.onScanFailed(2);work(()->{});check(sf("SmartButtonsService","status").toString().contains("掃描失敗 2"),"scanner failure remains visible during pairing");
        ui(()->stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,"cancel_pair"));Thread.sleep(300);work(()->{});
        long beforeLate=(Long)field(scanner,"packets");active.onScanResult(1,advertisement(1,"ZEEKR_button"));work(()->{});
        check(field(service,"link")==null&&(Long)field(scanner,"packets")==beforeLate,"late callbacks after stop/Cancel cannot restart pairing");
        work(()->call(scanner,"park",new Class<?>[0]));pair();check(!(Boolean)field(service,"sleeping"),"explicit Pair exits parked service state");
        check(sf("SmartButtonsService","status").toString().contains("BLE" )||sf("SmartButtonsService","status").toString().contains("接收"),"pairing UI retains scanner readiness/retry status");
        c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.SmartButtonsService"));Thread.sleep(500);
        report.append("BOUNDARY: Android callback scan request and injected registered-callback packets; no physical BLE radio delivery, peripheral or CCCD response.\n");
        }
        result.putString("result","PASS");
    }catch(Throwable e){report.append("FAIL ").append(e).append('\n');StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));report.append(sw);result.putString("result","FAIL");}
    finally{try{if(voice!=null)ui(()->call(voice,"cancel",new Class<?>[0]));if(c!=null){c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.SmartButtonsService"));c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.FloatingPanelService"));Thread.sleep(300);for(String key:backup.keySet())restore(key);}if(dir!=null)try(FileOutputStream out=new FileOutputStream(new File(dir,animation?"animation-report.txt":"pairing-report.txt"))){out.write(report.toString().getBytes("UTF-8"));}}catch(Exception e){report.append("cleanup ").append(e);}result.putString("report",report.toString());finish("PASS".equals(result.getString("result"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    },"pairing-probe").start();}
}
