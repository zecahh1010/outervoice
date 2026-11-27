package com.zecadev.outervoice.probe;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Local native navigation/setting persistence checks, not physical vehicle validation. */
public final class SettingsProbe extends Instrumentation {
    private Context c;private Activity activity;private Object voice;private File dir;
    private final StringBuilder report=new StringBuilder();private final Map<String,Map<String,?>> saved=new HashMap<>();
    private interface Task{void run()throws Exception;}
    private void ui(Task task)throws Exception{Throwable[] err={null};runOnMainSync(()->{try{task.run();}catch(Throwable t){err[0]=t;}});if(err[0]!=null)throw new Exception(err[0]);}
    private Class<?> cls(String name)throws Exception{return Class.forName("com.zecadev.outervoice."+name,true,c.getClassLoader());}
    private Object call(Object o,String name,Class<?>[] p,Object...a)throws Exception{Method m=o.getClass().getDeclaredMethod(name,p);m.setAccessible(true);return m.invoke(o,a);}
    private Object stat(String name,String method,Class<?>[] p,Object...a)throws Exception{Method m=cls(name).getDeclaredMethod(method,p);m.setAccessible(true);return m.invoke(null,a);}
    private Object field(Object o,String name)throws Exception{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    private SharedPreferences prefs(String name){return c.getSharedPreferences(name,0);}
    private void check(boolean good,String name){if(!good)throw new AssertionError(name);report.append("PASS ").append(name).append('\n');}
    private View find(View view,String text){if(text.equals(view.getContentDescription()))return view;if(view instanceof TextView&&((TextView)view).getText().toString().equals(text))return view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View hit=find(g.getChildAt(i),text);if(hit!=null)return hit;}}return null;}
    private View find(String text){return find(activity.getWindow().getDecorView(),text);}
    private void click(String text)throws Exception{ui(()->{View v=find(text);if(v==null)throw new AssertionError("Missing control: "+text);while(!v.isClickable()&&v.getParent() instanceof View)v=(View)v.getParent();if(!v.performClick())throw new AssertionError("Not clickable: "+text);});Thread.sleep(300);}
    private void choose(String text)throws Exception{
        for(int i=0;i<40;i++){AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();if(root!=null)for(AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText(text)){
            if(n.performAction(AccessibilityNodeInfo.ACTION_CLICK)){Thread.sleep(350);return;}
            android.graphics.Rect bounds=new android.graphics.Rect();n.getBoundsInScreen(bounds);
            if(n.isVisibleToUser()&&!bounds.isEmpty()){long now=android.os.SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();}Thread.sleep(350);return;}
        }Thread.sleep(100);}throw new AssertionError("Missing dialog choice: "+text);
    }
    private void waitText(String text)throws Exception{for(int i=0;i<40;i++){AccessibilityNodeInfo r=getUiAutomation().getRootInActiveWindow();if(r!=null&&!r.findAccessibilityNodeInfosByText(text).isEmpty())return;Thread.sleep(100);}throw new AssertionError("Window text missing: "+text);}
    private void screenshot(String name)throws Exception{Thread.sleep(250);Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("Screenshot unavailable");try(FileOutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private String page()throws Exception{return (String)field(activity,"page");}
    private void back()throws Exception{ui(()->activity.onBackPressed());Thread.sleep(300);}
    private long floatingSince(){ActivityManager am=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);for(ActivityManager.RunningServiceInfo s:am.getRunningServices(100))if(s.service.getClassName().endsWith("FloatingPanelService"))return s.activeSince;return -1;}
    @Override public void onCreate(Bundle b){super.onCreate(b);start();}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();try{
            c=getTargetContext();dir=new File(c.getExternalFilesDir(null),"v160-qa");dir.mkdirs();
            for(String key:new String[]{"speaking","floating","smart_buttons","sounds"})saved.put(key,new HashMap<>(prefs(key).getAll()));
            prefs("speaking").edit().putInt("screen_mode",0).putString("live_capture_mode","compat").putString("live_input","").putInt("external_db",6).putInt("live_mic_db",3).commit();
            prefs("floating").edit().putBoolean("enabled",false).commit();prefs("smart_buttons").edit().putBoolean("enabled",false).putInt("single.AA:BB:CC:DD:EE:03",1).putString("devices","[\"AA:BB:CC:DD:EE:03\"]").putString("name.AA:BB:CC:DD:EE:03","測試靈動貼（模擬）").commit();
            activity=startActivitySync(new Intent().setClassName(c,"com.zecadev.outervoice.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(500);
            check(find("傳聲／聲音")!=null&&find("設定")!=null,"two primary tabs are speaking and settings");
            check(find("Floating Panel settings")==null&&find("Audio settings and diagnostics")==null&&find("App information and credits")==null,"old top settings/info shortcuts are removed");
            check(find("收音模式  ▾")==null,"home has a read-only mode label instead of a settings dropdown");screenshot("v160-home.png");
            click("設定");check(page().equals("settings")&&find("設定").isSelected(),"Settings tab opens and is selected");
            for(String name:new String[]{"傳聲方式","Live 麥克風","音量及增益","懸浮面板","靈動貼","測試及關於"})check(find(name)!=null,"settings group is discoverable: "+name);
            screenshot("v160-settings.png");
            click("傳聲方式");choose("按一下錄音，再按一下停止並播放");check(page().equals("settings")&&prefs("speaking").getInt("screen_mode",-1)==1,"mode selection saves and returns to Settings");
            check(prefs("smart_buttons").getInt("single.AA:BB:CC:DD:EE:03",-1)==1,"screen mode change preserves smart button assignment");
            click("Live 麥克風");waitText("Live 麥克風設定");screenshot("v160-live-settings.png");choose("關閉");check(page().equals("settings"),"Live microphone settings return to the hub");
            click("音量及增益");waitText("Audio settings");check(prefs("speaking").getInt("external_db",-1)==6&&prefs("speaking").getInt("live_mic_db",-1)==3,"separate output and microphone gains are retained");choose("Close");
            click("靈動貼");check(page().equals("buttons")&&find("返回設定")!=null,"smart buttons open as a Settings child with a return action");screenshot("v160-buttons-settings.png");click("返回設定");check(page().equals("settings"),"smart button return action restores the hub");
            click("測試及關於");check(page().equals("checks")&&find("喇叭試音")!=null&&find("音訊診斷")!=null&&find("關於及授權")!=null,"speaker test, diagnostics and About are grouped");
            click("音訊診斷");waitText("Audio diagnostics");choose("Close");click("關於及授權");check(page().equals("credits"),"About is reachable from Settings");back();check(page().equals("checks"),"About Back returns to its tools parent");back();check(page().equals("settings"),"tools Back returns to Settings");
            ui(()->voice=stat("VoiceSession","get",new Class<?>[]{Context.class},c));
            prefs("floating").edit().putBoolean("enabled",true).putBoolean("minimized",true).putInt("bubble_x",930).putInt("bubble_y",500).commit();ui(()->stat("FloatingPanelService","sync",new Class<?>[]{Context.class,boolean.class},c,true));Thread.sleep(600);long since=floatingSince();
            click("靈動貼");check(floatingSince()==since&&since>0,"floating service survives Settings-to-smart-buttons navigation");back();
            ui(()->call(voice,"toggle",new Class<?>[]{Object.class,int.class},this,1));Thread.sleep(450);click("傳聲方式");choose("按住說話，放手播放");check((Boolean)call(voice,"recording",new Class<?>[0]),"changing mode inside Settings preserves remote recording");ui(()->call(voice,"cancel",new Class<?>[0]));Thread.sleep(300);
            click("懸浮面板");for(int i=0;i<30;i++){if(getUiAutomation().getRootInActiveWindow()!=null&&getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Floating Panel").size()>0)break;Thread.sleep(100);}choose("Cancel");Thread.sleep(500);
            check(page().equals("settings")&&prefs("floating").getBoolean("enabled",false),"floating editor Cancel returns to Settings without losing enabled state");
            click("傳聲／聲音");check(page().equals("home"),"speaking tab returns to operational home");
            report.append("BOUNDARY: native UI/navigation verification only; audio/BLE car verification remains documented separately.\n");result.putString("result","PASS");
        }catch(Throwable error){StringWriter sw=new StringWriter();error.printStackTrace(new PrintWriter(sw));report.append("FAIL ").append(sw);result.putString("result","FAIL");}
        finally{try{if(voice!=null)ui(()->call(voice,"cancel",new Class<?>[0]));c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.FloatingPanelService"));stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,"stop");Thread.sleep(350);for(String key:saved.keySet()){SharedPreferences.Editor e=prefs(key).edit().clear();for(Map.Entry<String,?> item:saved.get(key).entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);}e.commit();}try(FileOutputStream out=new FileOutputStream(new File(dir,"v160-probe.txt"))){out.write(report.toString().getBytes("UTF-8"));}}catch(Throwable ignored){}}
        result.putString("stream",report.toString());finish("PASS".equals(result.getString("result"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    },"settings-navigation-probe").start();}
}
