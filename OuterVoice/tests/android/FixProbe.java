package com.zecadev.outervoice.probe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.os.*;
import android.view.accessibility.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Emulator-only regression harness for the five car-reported v1.5.0 faults. */
public final class FixProbe extends Instrumentation {
    private Context c;private Object voice;private File dir,fixture;private final StringBuilder report=new StringBuilder();
    private final Map<String,Map<String,?>> backup=new HashMap<>();private static final String ID="22345678-1234-1234-1234-123456789abc",ADDRESS="AA:BB:CC:DD:EE:02";
    private interface Task{void run()throws Exception;}
    private Class<?> cls(String n)throws Exception{return Class.forName("com.zecadev.outervoice."+n,true,c.getClassLoader());}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    private Object statField(String cl,String n)throws Exception{Field f=cls(cl).getDeclaredField(n);f.setAccessible(true);return f.get(null);}
    private Object call(Object o,String n,Class<?>[] p,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(o,a);}
    private Object stat(String cl,String n,Class<?>[] p,Object...a)throws Exception{Method m=cls(cl).getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(null,a);}
    private void ui(Task r)throws Exception{Throwable[] e={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){e[0]=t;}});if(e[0]!=null)throw new Exception(e[0]);}
    private SharedPreferences prefs(String n){return c.getSharedPreferences(n,0);}
    private void check(boolean b,String s){if(!b)throw new AssertionError(s);report.append("PASS ").append(s).append('\n');}
    private void shot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private Rect bubble()throws Exception{
        for(int retry=0;retry<30;retry++){
            for(AccessibilityWindowInfo w:getUiAutomation().getWindows()){
                AccessibilityNodeInfo root=w.getRoot();if(root==null)continue;List<AccessibilityNodeInfo> nodes=new ArrayList<>();nodes.add(root);
                for(int i=0;i<nodes.size();i++){AccessibilityNodeInfo n=nodes.get(i);CharSequence d=n.getContentDescription();Rect r=new Rect();n.getBoundsInScreen(r);
                    if(d!=null&&d.toString().contains("Recording")&&r.width()>40&&r.width()<150)return r;
                    for(int j=0;j<n.getChildCount();j++){AccessibilityNodeInfo child=n.getChild(j);if(child!=null)nodes.add(child);}
                }
            }Thread.sleep(100);
        }throw new AssertionError("recording bubble missing from actual accessibility windows");
    }
    private long overlaySince(){ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);for(ActivityManager.RunningServiceInfo s:am.getRunningServices(100))if(s.service.getClassName().endsWith("FloatingPanelService"))return s.activeSince;return -1;}
    private boolean windowText(String text)throws Exception{for(int retry=0;retry<50;retry++){for(AccessibilityWindowInfo w:getUiAutomation().getWindows()){AccessibilityNodeInfo root=w.getRoot();if(root!=null&&!root.findAccessibilityNodeInfosByText(text).isEmpty())return true;}Thread.sleep(100);}return false;}
    private void worker(Object scanner,Task r)throws Exception{
        Handler handler=(Handler)field(scanner,"work");java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);Throwable[] e={null};handler.post(()->{try{r.run();}catch(Throwable t){e[0]=t;}finally{done.countDown();}});if(!done.await(8,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("BLE worker timeout");if(e[0]!=null)throw new Exception(e[0]);
    }
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();AtomicInteger toasts=new AtomicInteger();
        try{
            c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"v151-qa");dir.mkdirs();
            for(String key:new String[]{"sounds","floating","speaking","smart_buttons"})backup.put(key,new HashMap<>(prefs(key).getAll()));
            android.accessibilityservice.AccessibilityServiceInfo si=getUiAutomation().getServiceInfo();si.flags|=android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(si);
            getUiAutomation().setOnAccessibilityEventListener(e->{if(e.getEventType()==AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED&&"com.zecadev.outervoice".contentEquals(e.getPackageName())&&e.getClassName()!=null&&e.getClassName().toString().contains("Toast"))toasts.incrementAndGet();});
            prefs("smart_buttons").edit().putBoolean("enabled",true).putBoolean("listening_session",true).putString("devices","[\""+ADDRESS+"\"]").putString("name."+ADDRESS,"測試靈動貼（模擬）").commit();
            prefs("speaking").edit().putInt("screen_mode",1).putString("live_capture_mode","compat").putString("live_input","").commit();
            fixture=new File(c.getFilesDir(),ID+".wav");try(RandomAccessFile f=new RandomAccessFile(fixture,"rw")){stat("WavTools","header",new Class<?>[]{RandomAccessFile.class,long.class},f,2L);f.writeShort(0);}
            prefs("sounds").edit().putString("items","[{\"id\":\""+ID+"\",\"name\":\"測試音效（模擬）\"}]").commit();
            prefs("floating").edit().putBoolean("enabled",true).putBoolean("minimized",true).putBoolean("auto_minimize",false).putInt("bubble_x",850).putInt("bubble_y",180).putString("items","[{\"id\":\"live-speak\",\"selected\":true},{\"id\":\""+ID+"\",\"selected\":true}]").commit();
            Activity activity=startActivitySync(new Intent().setClassName(c,"com.zecadev.outervoice.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(1000);
            ui(()->voice=stat("VoiceSession","get",new Class<?>[]{Context.class},c));
            long since=overlaySince();check(since>0,"floating foreground service is running");
            ui(()->call(activity,"showSmartButtons",new Class<?>[0]));Thread.sleep(600);
            check(overlaySince()==since,"Smart Buttons tab preserves the same floating service");shot("v151-buttons-with-bubble.png");
            ui(()->stat("SmartButtonActions","run",new Class<?>[]{Context.class,Object.class,String.class,int.class},c,this,ADDRESS,1));Thread.sleep(800);
            check((Boolean)call(voice,"recording",new Class<?>[0]),"smart-button dispatch starts recording");
            Rect rect=bubble();Bitmap one=getUiAutomation().takeScreenshot();Thread.sleep(230);Bitmap two=getUiAutomation().takeScreenshot();int changed=0,red=0;
            for(int y=Math.max(0,rect.top);y<Math.min(rect.bottom,one.getHeight());y++)for(int x=Math.max(0,rect.left);x<Math.min(rect.right,one.getWidth());x++){int p=one.getPixel(x,y);if(Color.red(p)>Color.green(p)*1.4&&Color.red(p)>Color.blue(p)*1.4)red++;if(p!=two.getPixel(x,y))changed++;}one.recycle();two.recycle();
            check(red>100,"multi-sound minimized bubble turns red while recording");check(changed>20,"multi-sound minimized bubble visibly animates across rendered frames");shot("v151-minimized-recording.png");
            ui(()->call(voice,"cancel",new Class<?>[0]));Thread.sleep(300);toasts.set(0);
            for(int i=0;i<30;i++)ui(()->call(voice,"status",new Class<?>[]{String.class},"Ready to speak"));Thread.sleep(400);
            check(toasts.get()==0,"repeated idle/meter status updates produce no Toasts");
            Object scanner=statField("ButtonScanner","current");check(scanner!=null,"callback BLE scanner is installed");Object loop=field(scanner,"loop");
            check((Boolean)field(loop,"scanning"),"callback BLE registration succeeds on Android 13");
            worker(scanner,()->call(loop,"failed",new Class<?>[]{int.class},2));Thread.sleep(5900);
            check((Boolean)field(loop,"scanning"),"injected error2 cleans up and re-registers without restarting the app");
            worker(scanner,()->call(scanner,"park",new Class<?>[0]));Thread.sleep(400);check(!(Boolean)field(loop,"scanning"),"park releases BLE registration");
            call(scanner,"wake",new Class<?>[0]);Thread.sleep(10700);check((Boolean)field(loop,"scanning"),"wake re-registers after service settlement");
            check(prefs("smart_buttons").getString("devices","").contains(ADDRESS),"wake recovery preserves the paired device binding");
            ui(()->call(voice,"begin",new Class<?>[]{Object.class,int.class},this,2));
            check(!(Boolean)field(voice,"focused"),"Live Speak does not request or steal Android audio focus");ui(()->call(voice,"cancel",new Class<?>[0]));
            AudioManager am=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);AudioDeviceInfo output=null;
            for(AudioDeviceInfo d:am.getDevices(AudioManager.GET_DEVICES_OUTPUTS))if(d.getType()==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER){output=d;break;}
            check(output!=null,"emulator-only PCM backend target is available");
            for(String mode:new String[]{"compat","performance","standard"}){
                prefs("speaking").edit().putString("live_capture_mode",mode).putString("live_input","").commit();
                Class<?> listener=cls("LiveMicPassthrough$Listener");AtomicInteger meters=new AtomicInteger();List<String> statuses=Collections.synchronizedList(new ArrayList<>());
                Object callbacks=java.lang.reflect.Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(o,m,a)->{if(m.getName().equals("onLevel"))meters.incrementAndGet();else if(m.getName().equals("onStatus"))statuses.add(String.valueOf(a[0]));return null;});
                Object engine=cls("LiveMicPassthrough").getConstructor(Context.class,listener).newInstance(c,callbacks);
                Field requested=engine.getClass().getDeclaredField("requested");requested.setAccessible(true);requested.setBoolean(engine,true);
                AudioDeviceInfo target=output;Throwable[] failure={null};Thread pump=new Thread(()->{try{call(engine,"pump",new Class<?>[]{AudioDeviceInfo.class},target);}catch(Throwable e){failure[0]=e;}});pump.start();Thread.sleep(1500);call(engine,"stop",new Class<?>[0]);pump.join(3000);
                String details=String.valueOf(statField("LiveMicPassthrough","lastReport"));java.util.regex.Matcher frames=java.util.regex.Pattern.compile("PCM frames=(\\d+)").matcher(details);
                check(!pump.isAlive()&&failure[0]==null&&frames.find()&&Long.parseLong(frames.group(1))>0&&meters.get()>0,"actual Android PCM capture/write/release streams in "+mode+" mode (emulator speaker only)");
                report.append("DETAIL ").append(details).append('\n');
            }
            prefs("speaking").edit().putString("live_capture_mode","compat").commit();
            @SuppressWarnings("unchecked") List<AudioDeviceInfo> inputs=(List<AudioDeviceInfo>)stat("LiveAudioConfig","inputs",new Class<?>[]{Context.class},c);
            if(!inputs.isEmpty()){
                Object info=stat("LiveAudioConfig","info",new Class<?>[]{AudioDeviceInfo.class},inputs.get(0));String key=(String)call(info,"key",new Class<?>[0]);
                stat("LiveAudioConfig","select",new Class<?>[]{Context.class,String.class},c,key);AudioDeviceInfo chosen=(AudioDeviceInfo)stat("LiveAudioConfig","input",new Class<?>[]{Context.class},c);
                check(chosen.getId()==inputs.get(0).getId(),"explicit Live microphone selection resolves the chosen port");
            }
            ui(()->call(activity,"showLiveSettings",new Class<?>[0]));check(windowText("Live 麥克風設定"),"Live microphone configuration dialog renders as a native window");Thread.sleep(500);shot("v151-live-settings.png");
            report.append("BOUNDARY: native PCM uses an explicit emulator-speaker target via the test harness; the release has no BUS12 fallback. Actual Zeekr sleep, stale VLite binder, acoustics and latency need car retesting.\n");
        }catch(Throwable e){report.append("FAIL ").append(e).append('\n');StringWriter sw=new StringWriter();e.printStackTrace(new PrintWriter(sw));report.append(sw);result.putString("result","FAIL");}
        finally{
            try{if(voice!=null)ui(()->call(voice,"cancel",new Class<?>[0]));if(c!=null){c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.FloatingPanelService"));stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,"stop");Thread.sleep(500);for(String key:backup.keySet()){SharedPreferences.Editor e=prefs(key).edit().clear();for(Map.Entry<String,?> item:backup.get(key).entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);}e.commit();}}if(fixture!=null)fixture.delete();if(dir!=null)try(FileOutputStream out=new FileOutputStream(new File(dir,"v151-probe.txt"))){out.write(report.toString().getBytes("UTF-8"));}}catch(Throwable ignored){}
        }
        if(!"FAIL".equals(result.getString("result")))result.putString("result","PASS");result.putString("stream",report.toString());finish("PASS".equals(result.getString("result"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    },"v151-native-checks").start();}
}
