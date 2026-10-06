import com.zecadev.outervoice.WavFormat;
import java.io.*;
import java.nio.file.Files;

public class WavFormatTest {
    static void le(ByteArrayOutputStream out, long value, int count) {
        for (int i = 0; i < count; i++) out.write((int) (value >> (8 * i)) & 255);
    }
    static byte[] fixture(int format, int channels, int bits, boolean junk) throws Exception {
        ByteArrayOutputStream body = new ByteArrayOutputStream(); body.write("WAVE".getBytes("US-ASCII"));
        if (junk) { body.write("JUNK".getBytes("US-ASCII")); le(body, 3, 4); body.write(new byte[4]); }
        body.write("fmt ".getBytes("US-ASCII")); le(body, 16, 4); le(body, format, 2); le(body, channels, 2);
        le(body, 44100, 4); le(body, 44100 * channels * bits / 8, 4); le(body, channels * bits / 8, 2); le(body, bits, 2);
        body.write("data".getBytes("US-ASCII")); le(body, 1764, 4); body.write(new byte[1764]);
        ByteArrayOutputStream output = new ByteArrayOutputStream(); output.write("RIFF".getBytes("US-ASCII")); le(output, body.size(), 4); output.write(body.toByteArray()); return output.toByteArray();
    }
    static WavFormat parse(byte[] bytes) throws Exception {
        File file = Files.createTempFile("outervoice-wav-test", ".wav").toFile();
        try { Files.write(file.toPath(), bytes); return WavFormat.read(file); } finally { file.delete(); }
    }
    static void reject(String name, byte[] bytes) throws Exception {
        try { parse(bytes); throw new AssertionError("Accepted " + name); } catch (IOException expected) { System.out.println("PASS reject " + name); }
    }
    public static void main(String[] arguments) throws Exception {
        WavFormat mono = parse(fixture(1, 1, 16, false));
        if (mono.rate != 44100 || mono.channels != 1 || mono.bytes != 1764 || mono.offset != 44) throw new AssertionError("Mono metadata");
        WavFormat stereo = parse(fixture(1, 2, 16, true));
        if (stereo.channels != 2 || stereo.offset != 56) throw new AssertionError("Odd chunk padding");
        System.out.println("PASS mono, stereo, odd-sized ancillary chunk");
        reject("float WAV", fixture(3, 1, 16, false));
        reject("24-bit WAV", fixture(1, 1, 24, false));
        reject("three channels", fixture(1, 3, 16, false));
        byte[] damaged = fixture(1, 1, 16, false); damaged[8] = 'X'; reject("wrong WAVE signature", damaged);
        damaged = fixture(1, 1, 16, false); reject("truncated data", java.util.Arrays.copyOf(damaged, damaged.length - 1));
        damaged = fixture(1, 1, 16, false); damaged[40] = (byte) 0xff; damaged[41] = (byte) 0xff; damaged[42] = (byte) 0xff; damaged[43] = (byte) 0xff; reject("overflow chunk size", damaged);
        damaged = fixture(1, 1, 16, false); damaged[40] = (byte) 0xe3; reject("unaligned PCM", damaged);
        damaged = fixture(1, 1, 16, false); damaged[28] = 0; reject("invalid byte rate", damaged);
        System.out.println("All WAV validation tests passed.");
    }
}
