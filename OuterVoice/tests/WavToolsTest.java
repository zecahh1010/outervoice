import com.zecadev.outervoice.WavFormat;
import com.zecadev.outervoice.WavTools;
import java.io.*;
import java.nio.file.Files;

public class WavToolsTest {
    static File wave(short[] samples) throws Exception {
        File file = Files.createTempFile("voice-record", ".wav").toFile();
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            WavTools.header(output, samples.length * 2);
            for (short sample : samples) { output.write(sample & 255); output.write((sample >> 8) & 255); }
        }
        return file;
    }
    static void check(short[] samples, double expected) throws Exception {
        File file = wave(samples);
        try {
            WavFormat format = WavFormat.read(file);
            if (format.rate != 44100 || format.channels != 1 || format.offset != 44 || format.bytes != samples.length * 2) throw new AssertionError("Recorded WAV metadata");
            double gain = WavTools.normalization(file, format);
            if (Math.abs(gain - expected) > .001) throw new AssertionError("Gain " + gain);
        } finally { file.delete(); }
    }
    public static void main(String[] args) throws Exception {
        for (int rate : new int[]{8000, 16000, 32000, 44100, 48000, 96000, 192000}) for (int channels : new int[]{1,2}) {
            File adaptive = Files.createTempFile("voice-format", ".wav").toFile();
            try {
                try (RandomAccessFile out = new RandomAccessFile(adaptive,"rw")) { WavTools.header(out, channels * 8, rate, channels); out.write(new byte[channels * 8]); }
                WavFormat format = WavFormat.read(adaptive);
                if (format.rate != rate || format.channels != channels || format.bytes != channels * 8) throw new AssertionError("Adaptive capture metadata");
            } finally { adaptive.delete(); }
        }
        check(new short[]{0,0}, 1); check(new short[]{100,-100}, 16);
        check(new short[]{32767,-32768}, 1); check(new short[]{10000,-10000}, 32767 * .945 / 10000);
        byte[] pcm = {(byte)0xff,0x7f,0,(byte)0x80}; WavTools.amplify(pcm, 4, 16);
        if (pcm[0] != (byte)255 || pcm[1] != 127 || pcm[2] != 0 || pcm[3] != (byte)128) throw new AssertionError("Clipping limits");
        File invalid = Files.createTempFile("voice-invalid", ".wav").toFile();
        try (RandomAccessFile out = new RandomAccessFile(invalid, "rw")) {
            try { WavTools.header(out, 3); throw new AssertionError("Odd PCM accepted"); } catch (IOException expected) { }
        } finally { invalid.delete(); }
        System.out.println("PASS adaptive mono/stereo 8–192 kHz headers, silence, normalization ceiling, full-scale signed samples, clipping, invalid length");
    }
}
