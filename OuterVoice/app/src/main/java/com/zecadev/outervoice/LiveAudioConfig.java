package com.zecadev.outervoice;

import android.content.Context;
import android.media.*;
import android.provider.Settings;
import java.util.*;

/** Same port identity and capture-source policy as MiclessKaraoke. */
final class LiveAudioConfig {
    private static String selectedThisProcess="";
    static String mode(Context c){return LiveCaptureMode.normalize(SpeakingOptions.prefs(c).getString("live_capture_mode","compat"));}
    static AudioManager manager(Context c){return (AudioManager)c.getSystemService(Context.AUDIO_SERVICE);}
    static List<AudioDeviceInfo> inputs(Context c){return Arrays.asList(manager(c).getDevices(AudioManager.GET_DEVICES_INPUTS));}
    static EndpointInfo info(AudioDeviceInfo d){return new EndpointInfo(d.getId(),d.getType(),d.getAddress(),String.valueOf(d.getProductName()));}
    static int boot(Context c){try{return Settings.Global.getInt(c.getContentResolver(),Settings.Global.BOOT_COUNT,-1);}catch(Exception e){return -1;}}
    static void select(Context c,String key){selectedThisProcess=key;SpeakingOptions.prefs(c).edit().putString("live_input",key).putInt("live_input_boot",boot(c)).apply();}
    static AudioDeviceInfo input(Context c){
        String key=SpeakingOptions.prefs(c).getString("live_input","");if(key.isEmpty())return null;
        List<AudioDeviceInfo> all=inputs(c);List<EndpointInfo> ports=new ArrayList<>();for(AudioDeviceInfo d:all)ports.add(info(d));
        int current=boot(c);EndpointInfo found=EndpointInfo.resolve(ports,key,key.equals(selectedThisProcess)||(current>=0&&SpeakingOptions.prefs(c).getInt("live_input_boot",-2)==current));
        if(found==null)throw new IllegalStateException("所選 Live 麥克風已離線或無法唯一辨認，請重新選擇");
        for(AudioDeviceInfo d:all)if(d.getId()==found.id)return d;throw new IllegalStateException("Live 麥克風已離線");
    }
    static String label(AudioDeviceInfo d,int position){if(d==null)return "自動 · 系統選擇麥克風";String text=info(d).label(position,true,android.os.Build.MODEL);return position==0?text.replaceFirst("^00｜",""):text;}
}
