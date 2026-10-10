package com.zecadev.outervoice.pickerqa;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.widget.Button;
import java.io.IOException;

/** Disposable emulator probe: legacy arbitrary-file picker versus images-only gallery. */
public class ProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        Button choose=new Button(this);choose.setText("Choose QA sound");setContentView(choose);
        choose.setOnClickListener(v->{
            setResult(RESULT_OK,new Intent().setData(Uri.parse("content://com.zecadev.outervoice.pickerqa.tone/tone")).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));finish();
        });
    }
    public static class Gallery extends Activity {
        @Override public void onCreate(Bundle state){super.onCreate(state);android.util.Log.e("PickerProbe","GALLERY_OPENED");finish();}
    }
    public static class ToneProvider extends ContentProvider {
        @Override public boolean onCreate(){return true;}
        @Override public String getType(Uri uri){return "audio/wav";}
        @Override public Cursor query(Uri uri,String[] projection,String selection,String[] args,String order){
            MatrixCursor cursor=new MatrixCursor(new String[]{"_display_name","_size"});cursor.addRow(new Object[]{"picker-qa.wav",2044});return cursor;
        }
        @Override public ParcelFileDescriptor openFile(Uri uri,String mode) throws java.io.FileNotFoundException {
            try {
                ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
                new Thread(()->{
                    try(ParcelFileDescriptor.AutoCloseOutputStream output=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){
                        byte[] wav=new byte[2044];tag(wav,0,"RIFF");number(wav,4,wav.length-8,4);tag(wav,8,"WAVEfmt ");number(wav,16,16,4);number(wav,20,1,2);number(wav,22,1,2);number(wav,24,48000,4);number(wav,28,96000,4);number(wav,32,2,2);number(wav,34,16,2);tag(wav,36,"data");number(wav,40,2000,4);
                        for(int i=0;i<1000;i++)number(wav,44+i*2,(int)(Math.sin(i*.1)*12000),2);output.write(wav);
                    }catch(IOException ignored){}
                }).start();return pipe[0];
            }catch(IOException e){throw new java.io.FileNotFoundException(e.getMessage());}
        }
        private static void tag(byte[] b,int p,String s){for(int i=0;i<s.length();i++)b[p+i]=(byte)s.charAt(i);}
        private static void number(byte[] b,int p,int n,int length){for(int i=0;i<length;i++)b[p+i]=(byte)(n>>(i*8));}
        @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
        @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
        @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
    }
}
