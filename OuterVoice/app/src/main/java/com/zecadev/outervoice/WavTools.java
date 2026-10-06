package com.zecadev.outervoice;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Pure Java WAV writing and peak normalization. */
public final class WavTools {
    public static void header(RandomAccessFile output, long dataBytes) throws IOException {
        if (dataBytes < 0 || dataBytes > 0xffffffffL - 36 || (dataBytes & 1) != 0) throw new IOException("Invalid PCM length");
        output.seek(0); output.writeBytes("RIFF"); le(output, dataBytes + 36, 4); output.writeBytes("WAVEfmt ");
        le(output, 16, 4); le(output, 1, 2); le(output, 1, 2); le(output, 44100, 4); le(output, 88200, 4);
        le(output, 2, 2); le(output, 16, 2); output.writeBytes("data"); le(output, dataBytes, 4);
    }
    private static void le(RandomAccessFile f, long value, int count) throws IOException {
        for (int i = 0; i < count; i++) f.write((int)(value >> (8 * i)) & 255);
    }
    public static double normalization(File file, WavFormat format) throws IOException {
        int peak = 0;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            input.seek(format.offset); byte[] data = new byte[8192]; long left = format.bytes;
            while (left > 0) {
                int n = (int)Math.min(data.length, left); input.readFully(data, 0, n);
                for (int i = 0; i < n; i += 2) peak = Math.max(peak, Math.abs(sample(data, i)));
                left -= n;
            }
        }
        if (peak == 0) return 1;
        double gain = 32767.0 * 0.945 / peak;
        return gain < 1.02 ? 1 : Math.min(16, gain);
    }
    private static int sample(byte[] bytes, int i) { return (short)((bytes[i] & 255) | (bytes[i + 1] << 8)); }
    public static void amplify(byte[] bytes, int length, double gain) {
        for (int i = 0; i < length; i += 2) {
            int value = (int)Math.max(-32768, Math.min(32767, Math.round(sample(bytes, i) * gain)));
            bytes[i] = (byte)value; bytes[i + 1] = (byte)(value >> 8);
        }
    }
    static File testTone(File directory) throws IOException {
        File file = new File(directory, "outer-test.wav");
        try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            out.setLength(0); header(out, 44100 * 2);
            for (int i = 0; i < 44100; i++) {
                double envelope = Math.min(1, Math.min(i / 2205.0, (44100 - i) / 2205.0));
                le(out, Math.round(3000 * envelope * Math.sin(2 * Math.PI * 660 * i / 44100)), 2);
            }
        }
        return file;
    }
}
