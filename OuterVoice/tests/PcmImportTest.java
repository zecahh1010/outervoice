import com.zecadev.outervoice.PcmImport;
import com.zecadev.outervoice.WavFormat;
import java.io.*;
import java.nio.file.Files;

public class PcmImportTest {
    static void le(RandomAccessFile f, long value, int n) throws Exception { for (int i=0;i<n;i++) f.write((int)(value>>(8*i))&255); }
    static File fixture(File dir, int format, int bits, int rate, int channels, int frames) throws Exception {
        File file=new File(dir,"input.wav");
        int alignment=channels*bits/8;
        try(RandomAccessFile f=new RandomAccessFile(file,"rw")) {
            f.setLength(0);f.writeBytes("RIFF");le(f,36+frames*alignment,4);f.writeBytes("WAVEfmt ");le(f,16,4);
            le(f,format,2);le(f,channels,2);le(f,rate,4);le(f,rate*alignment,4);le(f,alignment,2);le(f,bits,2);
            f.writeBytes("data");le(f,frames*alignment,4);
            for(int frame=0;frame<frames;frame++)for(int c=0;c<channels;c++) {
                long value=format==3 ? (bits==64?Double.doubleToLongBits(.25):Float.floatToIntBits(.25f)) : bits==8 ? 160 : 1L<<(bits-3);
                le(f,value,bits/8);
            }
        }return file;
    }
    public static void main(String[] args)throws Exception {
        File dir=Files.createTempDirectory("pcm-import-test").toFile(); File out=new File(dir,"out.wav");
        try {
            for(int bits:new int[]{8,16,24,32})for(int rate:new int[]{8000,22050,48000,96000}) {
                File input=fixture(dir,1,bits,rate,2,rate/10);
                if(!PcmImport.convertWav(input,out,()->false))throw new AssertionError("not WAV");
                WavFormat result=WavFormat.read(out);
                if(result.rate!=44100||result.channels!=1||Math.abs(result.bytes-8820)>2)throw new AssertionError("resampling "+result.bytes);
                try(RandomAccessFile f=new RandomAccessFile(out,"r")){f.seek(result.offset);int sample=f.readUnsignedByte()|(f.readUnsignedByte()<<8);if(sample!=8192)throw new AssertionError("amplitude "+sample);}
            }
            for(int bits:new int[]{32,64}) {
                PcmImport.convertWav(fixture(dir,3,bits,44100,3,4410),out,()->false);WavFormat.read(out);
            }
            boolean rejected=false;
            try{PcmImport.convertWav(fixture(dir,1,24,48000,1,4800),out,()->true);}catch(IOException e){rejected=true;}
            if(!rejected||out.exists())throw new AssertionError("cancel cleanup");
            File invalid=new File(dir,"invalid");Files.write(invalid.toPath(),new byte[]{1,2,3});
            if(PcmImport.convertWav(invalid,out,()->false))throw new AssertionError("invalid identified as WAV");
            File broken=fixture(dir,1,24,48000,1,4800);try(RandomAccessFile f=new RandomAccessFile(broken,"rw")){f.setLength(100);}
            rejected=false;try{PcmImport.convertWav(broken,out,()->false);}catch(IOException e){rejected=true;}
            if(!rejected)throw new AssertionError("truncation accepted");
            System.out.println("PASS PCM8/16/24/32, float32/64, downmix, resampling, cancellation and invalid WAV tests");
        }finally{for(File f:dir.listFiles())f.delete();dir.delete();}
    }
}
