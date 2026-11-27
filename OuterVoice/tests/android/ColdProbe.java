package com.zecadev.outervoice.probe;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.lang.reflect.*;
import org.json.*;

/** Set up/restore a cold-process receiver test. The host kills only this emulator app process. */
public final class ColdProbe extends Instrumentation {
    private boolean restore;
    private Class<?> cls(String n)throws Exception{return Class.forName("com.zecadev.outervoice."+n,true,getTargetContext().getClassLoader());}
    private Object stat(String c,String n,Class<?>[] p,Object...a)throws Exception{Method m=cls(c).getDeclaredMethod(n,p);m.setAccessible(true);return m.invoke(null,a);}
    private Object field(Object o,String n)throws Exception{Field f=o.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(o);}
    @Override public void onCreate(Bundle b){super.onCreate(b);restore=b!=null&&"true".equals(b.getString("restore"));start();}
    @Override public void onStart(){new Thread(()->{Bundle out=new Bundle();try{
        Context c=getTargetContext();File dir=new File(c.getExternalFilesDir(null),"v163-radio");dir.mkdirs();File saved=new File(dir,"cold-backup.json");
        SharedPreferences p=c.getSharedPreferences("smart_buttons",0);Throwable[] failure={null};
        if(!restore){
            JSONObject snapshot=new JSONObject();for(String key:p.getAll().keySet())snapshot.put(key,p.getAll().get(key));
            try(FileOutputStream stream=new FileOutputStream(saved)){stream.write(snapshot.toString().getBytes("UTF-8"));}
            p.edit().clear().putBoolean("enabled",true).putBoolean("listening_session",true).putString("pairing_method","shortcut").putString("devices","[\"F0:F1:F2:F3:F4:63\"]").putInt("single.F0:F1:F2:F3:F4:63",3).commit();
            runOnMainSync(()->{try{stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,"sync");}catch(Throwable e){failure[0]=e;}});
            if(failure[0]!=null)throw new Exception(failure[0]);Thread.sleep(1400);
            Field current=cls("ButtonScanner").getDeclaredField("current");current.setAccessible(true);Object scanner=current.get(null);
            if(scanner==null||!"shortcut".equals(field(scanner,"method")))throw new AssertionError("shortcut runtime missing");
            Object loop=field(scanner,"loop");if(!(Boolean)field(loop,"scanning"))throw new AssertionError("scan not started");
            out.putString("report","PASS cold receiver state prepared; real PendingIntent scan active");
        }else{
            runOnMainSync(()->{try{stat("SmartButtonsService","command",new Class<?>[]{Context.class,String.class},c,"stop");}catch(Throwable e){failure[0]=e;}});Thread.sleep(700);
            byte[] bytes=new byte[(int)saved.length()];try(FileInputStream stream=new FileInputStream(saved)){stream.read(bytes);}
            JSONObject snapshot=new JSONObject(new String(bytes,"UTF-8"));SharedPreferences.Editor edit=p.edit().clear();
            java.util.Iterator<String> keys=snapshot.keys();while(keys.hasNext()){String key=keys.next();Object value=snapshot.get(key);if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Number)edit.putLong(key,((Number)value).longValue());else edit.putString(key,(String)value);}
            edit.commit();saved.delete();out.putString("report","PASS cold receiver preferences restored");
        }
        out.putString("result","PASS");finish(Activity.RESULT_OK,out);
    }catch(Throwable e){out.putString("report","FAIL "+e);out.putString("result","FAIL");finish(Activity.RESULT_CANCELED,out);}},"cold-probe").start();}
}
