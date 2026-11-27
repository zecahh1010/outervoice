package com.zecadev.outervoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.*;
import android.os.Process;
import android.os.SystemClock;
import java.util.LinkedHashSet;
import java.util.concurrent.locks.LockSupport;

/** MiclessKaraoke capture/rate/buffer policy, with strict exterior output and no focus request. */
public final class LiveMicPassthrough implements AutoCloseable {
    public interface Listener {void onStatus(String text);void onLevel(double rms);}
    private final Context context;private final Listener listener;
    private Thread worker;private volatile boolean requested;private volatile float gain=1;
    static volatile String lastReport="Live Speak 尚未開始";
    public LiveMicPassthrough(Context c,Listener l){context=c.getApplicationContext();listener=l;}
    public synchronized boolean start(){
        if(worker!=null)return false;
        if(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw new SecurityException("Microphone permission required");
        AudioDeviceInfo outer=AudioRoutes.outer(context);if(outer==null)throw new IllegalStateException("BUS12_OUTER_NOTIFY is unavailable");
        LiveAudioConfig.input(context);requested=true;worker=new Thread(()->pump(outer),"outer-live-pcm");worker.start();return true;
    }
    public synchronized boolean isActive(){return worker!=null;}
    public void setGain(float v){if(Float.isNaN(v)||Float.isInfinite(v))throw new IllegalArgumentException("Gain must be finite");gain=Math.max(0,Math.min(1,v));}
    public void stop(){requested=false;}
    public void close(){stop();}
    private static int number(String value,int fallback){try{int n=Integer.parseInt(value);return n>0?n:fallback;}catch(Exception e){return fallback;}}
    private static boolean same(AudioDeviceInfo a,AudioDeviceInfo b){return a!=null&&b!=null&&a.getId()==b.getId();}
    private void pump(AudioDeviceInfo outer){
        AudioRecord input=null;AudioTrack output=null;String ending="Live microphone stopped";
        long frames=0;int rate=0,source=0;double rms=0,peak=0;
        try{
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
            AudioManager manager=LiveAudioConfig.manager(context);AudioDeviceInfo mic=LiveAudioConfig.input(context);
            String mode=LiveAudioConfig.mode(context);
            source=LiveCaptureMode.source(mode,android.os.Build.VERSION.SDK_INT,"true".equals(manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED)));
            int nativeRate=number(manager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE),48000);
            int burst=number(manager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER),nativeRate/200);
            LinkedHashSet<Integer> rates=new LinkedHashSet<>();rates.add(nativeRate);rates.add(48000);rates.add(44100);Exception last=null;
            for(int candidate:rates){AudioRecord in=null;AudioTrack out=null;try{
                int inMin=AudioRecord.getMinBufferSize(candidate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
                int outMin=AudioTrack.getMinBufferSize(candidate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);
                if(inMin<=0||outMin<=0)throw new IllegalStateException("不支援採樣率 "+candidate);
                int block=Math.max(64,Math.min(burst,candidate/100));
                in=new AudioRecord.Builder().setAudioSource(source).setAudioFormat(new AudioFormat.Builder().setSampleRate(candidate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setBufferSizeInBytes(Math.max(inMin,block*4)).build();
                out=new AudioTrack.Builder().setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()).setAudioFormat(new AudioFormat.Builder().setSampleRate(candidate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build()).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(Math.max(outMin,block*4)).setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build();
                if(in.getState()!=AudioRecord.STATE_INITIALIZED||out.getState()!=AudioTrack.STATE_INITIALIZED)throw new IllegalStateException("音訊初始化失敗");
                input=in;output=out;rate=candidate;break;
            }catch(Exception e){last=e;if(in!=null)in.release();if(out!=null)out.release();}}
            if(input==null||output==null)throw new IllegalStateException("無法開啟收音："+(last==null?"格式不支援":last.getMessage()));
            if(input.getAudioSource()!=source)throw new IllegalStateException("車機回報收音來源不符");
            if(!requested)return;
            if(!output.setPreferredDevice(outer))throw new IllegalStateException("車外喇叭路由被拒絕");
            if(mic!=null&&!input.setPreferredDevice(mic))throw new IllegalStateException("所選 Live 麥克風路由被拒絕");
            int block=Math.max(64,Math.min(burst,rate/100));output.setBufferSizeInFrames(block*2);short[] pcm=new short[block];
            output.setVolume(0);output.play();long deadline=SystemClock.elapsedRealtime()+1800;
            while(requested&&!same(output.getRoutedDevice(),outer)){
                if(SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("BUS12 輸出路由未能確認");
                if(output.write(pcm,0,pcm.length,AudioTrack.WRITE_NON_BLOCKING)<0)throw new IllegalStateException("輸出準備失敗");LockSupport.parkNanos(1_000_000);
            }
            if(!requested)return;
            input.startRecording();if(input.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("麥克風無法開始收音");
            if(output.setVolume(1)!=AudioTrack.SUCCESS)throw new IllegalStateException("無法開啟車外人聲輸出");
            status("即時傳聲中 · "+LiveCaptureMode.label(mode));
            AudioDiagnostics.log("Live PCM started: source="+LiveCaptureMode.sourceName(source)+"; "+rate+" Hz; block="+block+"; buffer="+output.getBufferSizeInFrames()+"; mic="+AudioRoutes.describe(input.getRoutedDevice())+"; output="+AudioRoutes.describe(output.getRoutedDevice()));
            lastReport="Live source="+LiveCaptureMode.sourceName(source)+"; rate="+rate+"; PCM frames=0; 等待收音資料";
            long meterAt=0,diagnosticAt=0,nonSilentAt=SystemClock.elapsedRealtime(),packetAt=nonSilentAt;boolean silent=false,stalled=false;
            while(requested){
                if(!same(output.getRoutedDevice(),outer))throw new IllegalStateException("車外輸出路由中斷");
                int n=input.read(pcm,0,pcm.length,AudioRecord.READ_NON_BLOCKING);if(n<0)throw new IllegalStateException("收音失敗 "+n);
                if(n==0){if(!stalled&&SystemClock.elapsedRealtime()-packetAt>3000){stalled=true;lastReport="Live source="+LiveCaptureMode.sourceName(source)+"; rate="+rate+"; PCM frames="+frames+"; 麥克風未送出 PCM";status("未收到 PCM 收音資料 · 請檢查 Live 麥克風選擇");}LockSupport.parkNanos(500_000);continue;}
                packetAt=SystemClock.elapsedRealtime();if(stalled){stalled=false;status("即時傳聲中");}
                if(mic!=null&&!same(input.getRoutedDevice(),mic))throw new IllegalStateException("所選 Live 麥克風路由中斷");
                double sum=0;peak=0;
                double boost=gain*ExteriorGain.linear(SpeakingOptions.prefs(context).getInt("live_mic_db",0))*ExteriorGain.linear(SpeakingOptions.prefs(context).getInt("external_db",0));
                for(int i=0;i<n;i++){sum+=(double)pcm[i]*pcm[i];pcm[i]=ExteriorGain.sample(pcm[i],boost);peak=Math.max(peak,Math.abs((int)pcm[i])/32768.0);}rms=Math.sqrt(sum/n)/32768;
                int off=0;deadline=SystemClock.elapsedRealtime()+500;
                while(requested&&off<n){
                    if(!same(output.getRoutedDevice(),outer))throw new IllegalStateException("車外輸出路由中斷");
                    int written=output.write(pcm,off,n-off,AudioTrack.WRITE_NON_BLOCKING);if(written<0)throw new IllegalStateException("輸出失敗 "+written);
                    if(written==0){if(SystemClock.elapsedRealtime()>deadline)throw new IllegalStateException("輸出停滯");LockSupport.parkNanos(500_000);}else{off+=written;frames+=written;deadline=SystemClock.elapsedRealtime()+500;}
                }
                long now=SystemClock.elapsedRealtime();if(rms>.00001)nonSilentAt=now;
                boolean quiet=now-nonSilentAt>3000;if(quiet!=silent){silent=quiet;status(quiet?"未收到收音訊號 · 請檢查 Live 麥克風選擇":"即時傳聲中");}
                if(now-meterAt>150){if(manager.getMode()!=AudioManager.MODE_NORMAL)throw new IllegalStateException("通話音訊啟用，已停止傳聲");meterAt=now;level(rms);}
                if(now-diagnosticAt>1000){diagnosticAt=now;lastReport="Live source="+LiveCaptureMode.sourceName(source)+"; rate="+rate+"; PCM frames="+frames+"; input RMS="+rms+"; output peak="+peak+"; mic="+AudioRoutes.describe(input.getRoutedDevice())+"; output="+AudioRoutes.describe(output.getRoutedDevice());AudioDiagnostics.log(lastReport);}
            }
        }catch(Exception e){if(requested)ending="Live microphone failed: "+e.getMessage();}
        finally{
            if(output!=null){try{output.setVolume(0);output.pause();output.flush();}catch(Exception ignored){}output.release();}
            if(input!=null){try{input.stop();}catch(Exception ignored){}input.release();}
            lastReport="Live source="+LiveCaptureMode.sourceName(source)+"; rate="+rate+"; PCM frames="+frames+"; input RMS="+rms+"; output peak="+peak+"; "+ending;
            AudioDiagnostics.log(lastReport);status(ending);level(0);synchronized(this){requested=false;worker=null;}
        }
    }
    private void status(String text){if(listener!=null)listener.onStatus(text);}
    private void level(double rms){if(listener!=null)listener.onLevel(rms);}
}
