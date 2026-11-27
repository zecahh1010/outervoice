package com.zecadev.outervoice.probe;

import android.app.*;
import android.content.*;
import android.graphics.Rect;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Real Android BLE-stack test against the isolated netsim/Bumble peripheral. No ScanResult injection. */
public final class RadioProbe extends Instrumentation {
    private Context c;private Object voice,scanner;private boolean baseline;private File dir;
    private final StringBuilder report=new StringBuilder();private final Map<String,Map<String,?>> backup=new HashMap<>();
    private static final String ADDRESS="F0:F1:F2:F3:F4:63";
    interface Task{void run()throws Exception;}interface Condition{boolean ok()throws Exception;}
    private Class<?> cls(String n)throws Exception{return Class.forName("com.zecadev.outervoice."+n,true,c.getClassLoader());}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private Object sf(String cl,String n)throws Exception{Field f=cls(cl).getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    private Object call(Object o,String n,Class<?>[] p,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(o,a);}
    private Object stat(String cl,String n,Class<?>[] p,Object...a)throws Exception{Method m=cls(cl).getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(null,a);}
    private void ui(Task task)throws Exception{Throwable[] error={null};runOnMainSync(()->{try{task.run();}catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw new Exception(error[0]);}
    private SharedPreferences prefs(String n){return c.getSharedPreferences(n,0);}
    private void check(boolean value,String text){if(!value)throw new AssertionError(text);report.append("PASS ").append(text).append('\n');}
    private void await(Condition condition,long timeout,String label)throws Exception{long until=SystemClock.elapsedRealtime()+timeout;while(SystemClock.elapsedRealtime()<until){if(condition.ok())return;Thread.sleep(80);}throw new AssertionError(label+"; "+sf("SmartButtonsService","status"));}
    private void phase(String text)throws Exception{
        try(FileOutputStream out=new FileOutputStream(new File(dir,"phase.txt"))){out.write(text.getBytes("UTF-8"));}
        long until=SystemClock.elapsedRealtime()+15000;
        while(SystemClock.elapsedRealtime()<until){File ack=new File(dir,"ack.txt");if(ack.isFile()){byte[] data=new byte[(int)ack.length()];try(FileInputStream in=new FileInputStream(ack)){in.read(data);}if(text.equals(new String(data,"UTF-8").trim()))return;}Thread.sleep(100);}
        throw new AssertionError("host fixture timeout: "+text);
    }
    private Object findPage(View v){if(v.getClass().getName().endsWith("SmartButtonsPage"))return v;if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){Object found=findPage(((ViewGroup)v).getChildAt(i));if(found!=null)return found;}return null;}
    private void choose(Activity activity,String method)throws Exception{
        ui(()->{call(activity,"showSmartButtons",new Class<?>[0]);Object page=findPage(activity.getWindow().getDecorView());call(page,"chooseMethod",new Class<?>[0]);});
        String text=method.equals("shortcut")?"適合背景按鍵":"已有實車成功記錄";AccessibilityNodeInfo node=null;
        for(int retry=0;retry<50&&node==null;retry++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null){List<AccessibilityNodeInfo> found=root.findAccessibilityNodeInfosByText(text);if(!found.isEmpty())node=found.get(0);}if(node==null)Thread.sleep(100);}
        if(node==null)throw new AssertionError("method picker option not visible: "+text);Rect r=new Rect();node.getBoundsInScreen(r);
        long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,r.centerX(),r.centerY(),0),up=MotionEvent.obtain(now,now+60,MotionEvent.ACTION_UP,r.centerX(),r.centerY(),0);down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        getUiAutomation().injectInputEvent(down,true);getUiAutomation().injectInputEvent(up,true);down.recycle();up.recycle();
        await(()->method.equals(prefs("smart_buttons").getString("pairing_method","")),5000,"method selection persistence");
        await(()->{Object s=sf("ButtonScanner","current");return s!=null&&method.equals(field(s,"method"));},7000,"new transport installation");scanner=sf("ButtonScanner","current");
        check(method.equals(prefs("smart_buttons").getString("pairing_method","")),"native method picker saves "+method);
    }
    private void command(String action)throws Exception{ui(()->stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,action));}
    private void restore(){for(String key:backup.keySet()){SharedPreferences.Editor e=prefs(key).edit().clear();for(Map.Entry<String,?> item:backup.get(key).entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);}e.commit();}}
    @Override public void onCreate(Bundle b){super.onCreate(b);baseline=b!=null&&"true".equals(b.getString("baseline"));start();}
    @Override public void onStart(){new Thread(()->{Bundle result=new Bundle();try{
        c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"v163-radio");dir.mkdirs();
        for(String key:new String[]{"smart_buttons","speaking","floating"})backup.put(key,new HashMap<>(prefs(key).getAll()));
        prefs("floating").edit().putBoolean("enabled",false).commit();prefs("speaking").edit().putInt("screen_mode",0).commit();
        prefs("smart_buttons").edit().clear().putBoolean("enabled",true).putBoolean("listening_session",true).putString("devices","[]").putInt("single."+ADDRESS,0).putString("name."+ADDRESS,"虛擬靈動貼（測試）").commit();
        Activity activity=startActivitySync(new Intent().setClassName(c,"com.zecadev.outervoice.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(700);
        ui(()->voice=stat("VoiceSession","get",new Class<?>[]{Context.class},c));
        for(String method:baseline?new String[]{"baseline"}:new String[]{"shortcut","callback"}){
            if(!baseline){
                if(method.equals("callback"))phase("callback-config");
                Object previous=sf("ButtonScanner","current");boolean hadBinding=prefs("smart_buttons").getString("devices","").contains(ADDRESS);
                if(hadBinding)ui(()->call(voice,"toggle",new Class<?>[]{Object.class,int.class},stat("SmartButtonsController","install",new Class<?>[]{Context.class},c),1));
                choose(activity,method);
                check(method.equals("shortcut")?c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=0:c.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)==0,method+" uses its expected location permission policy");
                if(hadBinding){check(prefs("smart_buttons").getString("devices","").contains(ADDRESS),"switching transport preserves saved binding");check((Boolean)call(voice,"recording",new Class<?>[0]),"switching transport preserves controller-owned recording");ui(()->call(voice,"cancel",new Class<?>[0]));}
                if(previous!=scanner)check((Boolean)field(previous,"closed")&&(Long)field(previous,"stopCompleted")>0&&(Long)field(scanner,"createdAt")>=(Long)field(previous,"stopCompleted"),"old native scan stops completely before new transport installs");
            }
            prefs("smart_buttons").edit().putString("devices","[]").commit();command("pair");Thread.sleep(1000);scanner=sf("ButtonScanner","current");
            long started=SystemClock.elapsedRealtime();phase(method+"-advert");
            await(()->prefs("smart_buttons").getString("devices","").contains(ADDRESS),20000,"physical-stack virtual GATT binding");
            report.append("DETAIL ").append(method).append(" pair_ms=").append(SystemClock.elapsedRealtime()-started).append('\n');
            Object controller=field(field(scanner,"listener"),"this$0"),link=field(controller,"link");
            check(link!=null&&(Boolean)call(link,"isReady",new Class<?>[0]),method+" completes actual Android GATT/CCCD subscription");
            check((Long)sf("SmartButtonsService","longPresses")>0,method+" receives real virtual-radio long-press advertisement");
            if(!baseline){
                boolean shortcut=method.equals("shortcut");check(shortcut?((Handler)field(scanner,"work")).getLooper()!=Looper.getMainLooper():((Handler)field(scanner,"work")).getLooper()==Looper.getMainLooper(),method+" uses its expected delivery looper");
                if(shortcut){check((Boolean)field(scanner,"decodedOnMain"),"system PendingIntent Bundle decoded on receiver/main looper");check((Long)field(scanner,"piPackets")>0,"actual OS BLE PendingIntent results reach controller");}
                else check((Long)field(scanner,"callbackPackets")>0,"actual OS ScanCallback results reach controller");
            }
            c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.SmartButtonsService"));Thread.sleep(500);
            if(baseline){check((Boolean)field(scanner,"closed"),"1.6.1 service destruction also destroys BLE listener");phase(method+"-stop");continue;}
            check(!(Boolean)field(scanner,"closed"),method+" BLE runtime survives foreground-service destruction");
            phase(method+"-press1");await(()->(Boolean)call(voice,"recording",new Class<?>[0]),6000,"real single notification starts recording");check(true,method+" notification restarts foreground service and starts recording");
            phase(method+"-press2");await(()->!(Boolean)call(voice,"recording",new Class<?>[0]),6000,"second notification stops recording");check(true,method+" second single notification stops recording");
            check(prefs("speaking").getInt("screen_mode",-1)==0,method+" remote actions preserve independent screen speaking mode");
            phase(method+"-double");await(()->sf("SmartButtonsService","status").toString().contains("雙擊"),5000,"real double notification dispatch");check(true,method+" real double notification is dispatched");
            phase(method+"-stop");
        }
        report.append("BOUNDARY: actual Android Bluetooth stack and a virtual netsim/Bumble peripheral, not physical Zeekr/VLite firmware.\n");result.putString("result","PASS");
    }catch(Throwable e){report.append("FAIL ").append(e).append('\n');StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));report.append(sw);result.putString("result","FAIL");}
    finally{try{if(voice!=null)ui(()->call(voice,"cancel",new Class<?>[0]));if(c!=null){command("stop");c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.FloatingPanelService"));Thread.sleep(700);restore();}if(dir!=null){try(FileOutputStream out=new FileOutputStream(new File(dir,baseline?"baseline-radio.txt":"dual-radio.txt"))){out.write(report.toString().getBytes("UTF-8"));}try(FileOutputStream out=new FileOutputStream(new File(dir,"phase.txt"))){out.write("DONE".getBytes("UTF-8"));}}}catch(Exception e){report.append("cleanup ").append(e);}result.putString("report",report.toString());finish("PASS".equals(result.getString("result"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);}
    },"radio-probe").start();}
}
