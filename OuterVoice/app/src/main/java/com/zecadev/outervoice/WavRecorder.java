package com.zecadev.outervoice;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.os.SystemClock;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.LinkedHashSet;

/** Selects a supported microphone format, preserving its rate and channel count. */
final class WavRecorder {
    interface Listener { void done(File file,String error); void meter(double level,long milliseconds); }
    private final Context context;
    private Thread worker;
    private volatile boolean requested,discard;
    WavRecorder(Context context){this.context=context;}
    synchronized boolean active(){return worker!=null;}
    synchronized boolean start(File destination,Listener listener){if(worker!=null)return false;requested=true;discard=false;worker=new Thread(()->record(destination,listener),"voice-record");worker.start();return true;}
    void stop(){requested=false;}
    void cancel(){discard=true;requested=false;}
    private AudioRecord openMicrophone(){
        LinkedHashSet<Integer> rates=new LinkedHashSet<>();rates.add(0); // Let Android choose its default first.
        AudioManager manager=(AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
        if(manager!=null)for(AudioDeviceInfo device:manager.getDevices(AudioManager.GET_DEVICES_INPUTS))if(device.getType()==AudioDeviceInfo.TYPE_BUILTIN_MIC)for(int rate:device.getSampleRates())if(rate>=8000&&rate<=192000)rates.add(rate);
        for(int rate:new int[]{48000,44100,32000,24000,22050,16000,8000,96000})rates.add(rate);
        for(int rate:rates)for(int encoding:new int[]{AudioFormat.ENCODING_PCM_FLOAT,AudioFormat.ENCODING_PCM_16BIT})for(int channels:new int[]{AudioFormat.CHANNEL_IN_STEREO,AudioFormat.CHANNEL_IN_MONO}){
            if(!requested)return null;AudioRecord candidate=null;
            try{
                AudioFormat.Builder format=new AudioFormat.Builder().setChannelMask(channels).setEncoding(encoding);if(rate!=0)format.setSampleRate(rate);
                int minimum=rate==0?8192:AudioRecord.getMinBufferSize(rate,channels,encoding);if(minimum<=0)continue;
                candidate=new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC).setAudioFormat(format.build()).setBufferSizeInBytes(Math.max(minimum,16384)).build();
                if(candidate.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Microphone format unavailable");
                candidate.startRecording();if(candidate.getRecordingState()!=AudioRecord.RECORDSTATE_RECORDING)throw new IllegalStateException("Microphone unavailable");
                if(candidate.getSampleRate()<8000||candidate.getSampleRate()>192000||candidate.getChannelCount()<1||candidate.getChannelCount()>2)throw new IllegalStateException("Unexpected microphone format");
                return candidate;
            }catch(RuntimeException e){if(candidate!=null){try{candidate.stop();}catch(Exception ignored){}candidate.release();}}
        }
        throw new IllegalStateException("Cannot open a supported microphone format");
    }
    private void record(File file,Listener listener){
        AudioRecord input=null;String error=null;long bytes=0;
        try(RandomAccessFile output=new RandomAccessFile(file,"rw")){
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);output.setLength(0);
            input=openMicrophone();if(input==null){discard=true;return;}
            int rate=input.getSampleRate(),channels=input.getChannelCount(),encoding=input.getAudioFormat();
            WavTools.header(output,0,rate,channels);
            AudioDiagnostics.log("Record microphone: "+AudioRoutes.describe(input.getRoutedDevice())+"; "+rate+" Hz channels="+channels+" input encoding="+encoding+"; PCM16 WAV storage");
            short[] samples=new short[4096];float[] floats=new float[4096];byte[] buffer=new byte[8192];long last=0,wait=SystemClock.elapsedRealtime();
            while(requested&&bytes<rate*channels*2L*180){
                int n=encoding==AudioFormat.ENCODING_PCM_FLOAT?input.read(floats,0,floats.length,AudioRecord.READ_NON_BLOCKING):input.read(samples,0,samples.length,AudioRecord.READ_NON_BLOCKING);
                if(n<0)throw new IllegalStateException("Microphone read error "+n);
                if(n==0){if(SystemClock.elapsedRealtime()-wait>3000)throw new IllegalStateException("Microphone stalled");Thread.sleep(5);continue;}
                n-=n%channels;wait=SystemClock.elapsedRealtime();double sum=0;
                for(int i=0;i<n;i++){
                    int sample=encoding==AudioFormat.ENCODING_PCM_FLOAT?(Float.isNaN(floats[i])?0:Math.round(Math.max(-1,Math.min(1,floats[i]))*32767)):samples[i];
                    buffer[2*i]=(byte)sample;buffer[2*i+1]=(byte)(sample>>8);double v=sample/32768.0;sum+=v*v;
                }
                output.write(buffer,0,n*2);bytes+=n*2;
                if(n>0&&wait-last>=120){listener.meter(Math.sqrt(sum/n),bytes*1000/(rate*channels*2L));last=wait;}
            }
            if(bytes==0&&!discard)throw new IllegalStateException("No microphone audio captured");WavTools.header(output,bytes,rate,channels);
        }catch(Exception e){error=e.getMessage();}
        finally{
            if(input!=null){try{input.stop();}catch(Exception ignored){}input.release();}
            boolean deleted=discard||error!=null;if(deleted)file.delete();
            synchronized(this){requested=false;worker=null;}listener.done(deleted?null:file,discard?null:error);
        }
    }
}
