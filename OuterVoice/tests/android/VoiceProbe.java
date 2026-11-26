package com.zecadev.outervoice.probe;

import android.app.*;
import android.content.*;
import android.os.Bundle;
import android.graphics.Bitmap;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;

/** Local emulator instrumentation only. No test entry point is included in the release APK. */
public final class VoiceProbe extends Instrumentation {
    private Context c;
    private Object voice;
    private final Object owner = this;
    private final String address="AA:BB:CC:DD:EE:01", id="12345678-1234-1234-1234-123456789abc";
    private final StringBuilder report=new StringBuilder();
    private final Map<String,Map<String,?>> original=new HashMap<>();
    private File directory,fixture;
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    private Class<?> type(String name)throws Exception{return Class.forName("com.zecadev.outervoice."+name,true,c.getClassLoader());}
    private Object call(Object target,String method,Class<?>[] params,Object...args)throws Exception{Method m=target.getClass().getDeclaredMethod(method,params);m.setAccessible(true);return m.invoke(target,args);}
    private Object stat(String cls,String method,Class<?>[] params,Object...args)throws Exception{Method m=type(cls).getDeclaredMethod(method,params);m.setAccessible(true);return m.invoke(null,args);}
    private void ui(Throwing r)throws Exception{final Throwable[] error={null};runOnMainSync(()->{try{r.run();}catch(Throwable t){error[0]=t;}});if(error[0]!=null)throw new Exception(error[0]);}
    private interface Throwing{void run()throws Exception;}
    private boolean recording()throws Exception{final boolean[] value={false};ui(()->value[0]=(Boolean)call(voice,"recording",new Class<?>[0]));return value[0];}
    private void cancel()throws Exception{ui(()->call(voice,"cancel",new Class<?>[0]));Thread.sleep(350);}
    private void action(int gesture)throws Exception{ui(()->stat("SmartButtonActions","run",new Class<?>[]{Context.class,Object.class,String.class,int.class},c,owner,address,gesture));}
    private void check(boolean good,String name){if(!good)throw new AssertionError(name);report.append("PASS ").append(name).append('\n');}
    private SharedPreferences prefs(String name){return c.getSharedPreferences(name,0);}
    private void screenshot(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();if(b==null)throw new AssertionError("No screenshot");try(FileOutputStream out=new FileOutputStream(new File(directory,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
    private android.view.View find(android.view.View v,String prefix){if(v instanceof android.widget.TextView && ((android.widget.TextView)v).getText().toString().startsWith(prefix))return v;if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++){android.view.View hit=find(g.getChildAt(i),prefix);if(hit!=null)return hit;}}return null;}
    private void click(Activity a,String prefix)throws Exception{ui(()->{android.view.View v=find(a.getWindow().getDecorView(),prefix);if(v==null)throw new AssertionError("UI control missing: "+prefix);v.performClick();});Thread.sleep(250);}
    private android.view.accessibility.AccessibilityNodeInfo activeRoot()throws Exception{for(int i=0;i<50;i++){android.view.accessibility.AccessibilityNodeInfo r=getUiAutomation().getRootInActiveWindow();if(r!=null)return r;Thread.sleep(100);}throw new AssertionError("Accessibility window did not become available");}
    private void accessibleClick(String text)throws Exception{for(int i=0;i<30;i++){android.view.accessibility.AccessibilityNodeInfo r=activeRoot();for(android.view.accessibility.AccessibilityNodeInfo n:r.findAccessibilityNodeInfosByText(text)){
        if(n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)){Thread.sleep(600);return;}
        // Android's radio list rows are non-clickable CheckedTextViews; AdapterView consumes touch.
        android.graphics.Rect bounds=new android.graphics.Rect();n.getBoundsInScreen(bounds);
        if(!bounds.isEmpty() && n.isVisibleToUser()){long now=android.os.SystemClock.uptimeMillis();for(int action:new int[]{android.view.MotionEvent.ACTION_DOWN,android.view.MotionEvent.ACTION_UP}){android.view.MotionEvent e=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0);e.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();}Thread.sleep(600);return;}
    }Thread.sleep(100);}throw new AssertionError("Dialog choice missing: "+text);}
    private void gainProgress(float value)throws Exception{android.view.accessibility.AccessibilityNodeInfo root=activeRoot();for(android.view.accessibility.AccessibilityNodeInfo n:root.findAccessibilityNodeInfosByText("車外喇叭數碼增益")){if("android.widget.SeekBar".contentEquals(n.getClassName())){Bundle b=new Bundle();b.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,value);if(n.performAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),b)){Thread.sleep(300);return;}}}throw new AssertionError("Gain slider unavailable");}
    @Override public void onStart(){new Thread(()->{
        Bundle result=new Bundle();
        try{
            c=getTargetContext();directory=new File(c.getExternalFilesDir(null),"v150-qa");directory.mkdirs();
            for(String p:new String[]{"sounds","speaking","floating","smart_buttons"})original.put(p,new HashMap<>(prefs(p).getAll()));
            ui(()->voice=stat("VoiceSession","get",new Class<?>[]{Context.class},c));cancel();
            prefs("speaking").edit().putInt("screen_mode",2).putInt("external_db",24).commit();
            prefs("smart_buttons").edit().putString("devices","[\""+address+"\"]").putBoolean("enabled",true).putString("name."+address,"測試靈動貼（模擬）").putInt("single."+address,0).putString("double_sound."+address,id).commit();
            fixture=new File(c.getFilesDir(),id+".wav");
            try(RandomAccessFile wave=new RandomAccessFile(fixture,"rw")){
                Method header=type("WavTools").getDeclaredMethod("header",RandomAccessFile.class,long.class);header.setAccessible(true);header.invoke(null,wave,96000L);
                for(int i=0;i<48000;i++){short sample=(short)(3000*Math.sin(2*Math.PI*440*i/48000));wave.write(sample&255);wave.write((sample>>8)&255);}
            }
            prefs("sounds").edit().putString("items","[{\"id\":\""+id+"\",\"name\":\"測試音效（模擬）\"}]").commit();
            Intent launch=new Intent().setClassName(c,"com.zecadev.outervoice.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Activity activity=startActivitySync(launch);Thread.sleep(600);
            action(1);Thread.sleep(1200);check(recording(),"smart single press starts recording and remains recording after click");
            check(prefs("speaking").getInt("screen_mode",-1)==2,"smart record action preserves independent screen Live Speak mode");
            final Activity modeActivity=activity;ui(()->call(modeActivity,"selectSpeakingMode",new Class<?>[0]));Thread.sleep(250);accessibleClick("按住說話，放手播放");
            check(recording() && prefs("speaking").getInt("screen_mode",-1)==0,"changing screen mode through native picker does not cancel a remote recording");
            prefs("speaking").edit().putInt("screen_mode",2).commit();
            prefs("floating").edit().putBoolean("enabled",true).putBoolean("minimized",false).putInt("x",650).putInt("y",260).commit();
            ui(()->stat("FloatingPanelService","sync",new Class<?>[]{Context.class,boolean.class},c,true));Thread.sleep(600);
            screenshot("v150-floating-recording.png");
            sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_HOME);Thread.sleep(600);
            check(recording(),"remote recording survives activity pause and Home");screenshot("v150-background-recording.png");
            action(1);Thread.sleep(700);check(!recording(),"second smart single press stops recording");
            check(prefs("speaking").getInt("external_db",-1)==24,"external gain persists at +24 dB");
            cancel();action(2);Thread.sleep(300);
            Field message=voice.getClass().getDeclaredField("message");message.setAccessible(true);
            check(((String)message.get(voice)).contains("BUS12"),"assigned double sound reaches strict exterior playback and fails closed without BUS12");
            fixture.delete();action(2);Thread.sleep(100);
            check(((String)message.get(voice)).contains("removed"),"deleted assigned sound is reported without choosing another sound");
            prefs("smart_buttons").edit().putInt("single."+address,1).commit();
            prefs("speaking").edit().putInt("screen_mode",0).commit();action(1);Thread.sleep(100);
            check(prefs("speaking").getInt("screen_mode",-1)==0,"smart Live Speak action preserves screen hold mode");cancel();
            prefs("smart_buttons").edit().putInt("single."+address,0).putString("double_sound."+address,"").commit();action(1);Thread.sleep(600);action(2);
            check(recording(),"unassigned double press is a no-op during recording");
            prefs("smart_buttons").edit().putBoolean("enabled",false).putInt("single."+address,3).commit();action(1);check(recording(),"disabled smart buttons cannot stop or trigger audio");
            prefs("smart_buttons").edit().putBoolean("enabled",true).commit();action(1);Thread.sleep(350);check(!recording(),"assigned stop audio action cancels recording");
            cancel();ui(()->call(voice,"down",new Class<?>[]{Object.class,int.class},owner,0));Thread.sleep(600);check(recording(),"hold down starts capture");
            ui(()->call(voice,"up",new Class<?>[]{Object.class,int.class},owner,0));Thread.sleep(500);check(!recording(),"hold release stops and schedules playback");
            cancel();ui(()->call(voice,"up",new Class<?>[]{Object.class,int.class},owner,1));Thread.sleep(600);check(recording(),"screen toggle starts capture");
            ui(()->call(voice,"up",new Class<?>[]{Object.class,int.class},owner,1));Thread.sleep(500);check(!recording(),"screen toggle stops capture on second click");
            cancel();
            prefs("smart_buttons").edit().putInt("single."+address,0).putString("double_sound."+address,id).commit();
            try(RandomAccessFile wave=new RandomAccessFile(fixture,"rw")){Method h=type("WavTools").getDeclaredMethod("header",RandomAccessFile.class,long.class);h.setAccessible(true);h.invoke(null,wave,2L);wave.writeShort(0);}
            // UI-only fixture to inspect native rename/action/sound controls; not BLE pairing evidence.
            Intent tab=new Intent(launch).putExtra("tab","buttons");activity.finish();activity=startActivitySync(tab);Thread.sleep(600);screenshot("v150-button-assignments-fixture.png");
            click(activity,"單擊");accessibleClick("Live Speak／停止即時傳聲");
            check(prefs("smart_buttons").getInt("single."+address,-1)==1 && prefs("speaking").getInt("screen_mode",-1)==0,"native single-action picker saves independently of screen mode");
            click(activity,"改名");android.view.accessibility.AccessibilityNodeInfo r=activeRoot();
            java.util.ArrayList<android.view.accessibility.AccessibilityNodeInfo> nodes=new java.util.ArrayList<>();nodes.add(r);boolean renamed=false;
            for(int i=0;i<nodes.size();i++){android.view.accessibility.AccessibilityNodeInfo n=nodes.get(i);if("android.widget.EditText".contentEquals(n.getClassName())){Bundle text=new Bundle();text.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,"司機靈動貼（模擬）");renamed=n.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT,text);}for(int j=0;j<n.getChildCount();j++){android.view.accessibility.AccessibilityNodeInfo child=n.getChild(j);if(child!=null)nodes.add(child);}}
            check(renamed,"native rename input accepts a new name");accessibleClick("儲存");
            check(prefs("smart_buttons").getString("name."+address,"").equals("司機靈動貼（模擬）"),"native rename persists per device");
            click(activity,"雙擊播放");screenshot("v150-sound-picker-fixture.png");accessibleClick("測試音效（模擬）");
            check(prefs("smart_buttons").getString("double_sound."+address,"").equals(id),"native double-press picker saves the selected sound ID");
            screenshot("v150-button-assignments-fixture.png");
            final Activity current=activity;ui(()->call(current,"showAudioSettings",new Class<?>[0]));Thread.sleep(400);
            gainProgress(12);check(prefs("speaking").getInt("external_db",-1)==12,"native gain slider saves +12 dB");gainProgress(24);check(prefs("speaking").getInt("external_db",-1)==24,"native gain slider saves +24 dB");
            screenshot("v150-external-gain.png");accessibleClick("Close");
            report.append("BOUNDARY: BLE pairing/radio, BUS12 acoustics and live latency require target-car verification.\n");
            result.putString("stream",report.toString());result.putString("result","PASS");
        }catch(Throwable error){report.append("FAIL ").append(error).append('\n');StringWriter sw=new StringWriter();error.printStackTrace(new PrintWriter(sw));report.append(sw);result.putString("stream",report.toString());result.putString("result","FAIL");}
        finally{
            try{cancel();c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.FloatingPanelService"));c.stopService(new Intent().setClassName(c,"com.zecadev.outervoice.SmartButtonsService"));
                for(String name:original.keySet()){SharedPreferences.Editor e=prefs(name).edit().clear();for(Map.Entry<String,?> item:original.get(name).entrySet()){Object v=item.getValue();if(v instanceof String)e.putString(item.getKey(),(String)v);else if(v instanceof Integer)e.putInt(item.getKey(),(Integer)v);else if(v instanceof Boolean)e.putBoolean(item.getKey(),(Boolean)v);else if(v instanceof Long)e.putLong(item.getKey(),(Long)v);else if(v instanceof Float)e.putFloat(item.getKey(),(Float)v);}e.commit();}
                if(fixture!=null)fixture.delete();try(FileOutputStream out=new FileOutputStream(new File(directory,"v150-probe.txt"))){out.write(report.toString().getBytes("UTF-8"));}
            }catch(Throwable ignored){}
        }
        finish("PASS".equals(result.getString("result"))?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    },"voice-native-probe").start();}
}
