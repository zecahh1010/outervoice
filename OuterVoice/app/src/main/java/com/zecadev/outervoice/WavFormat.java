package com.zecadev.outervoice;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/** Strict PCM16 WAV metadata; no Android dependency, independently testable. */
public final class WavFormat {
    public final long offset, bytes;
    public final int rate, channels;
    private WavFormat(long offset, long bytes, int rate, int channels) {
        this.offset = offset; this.bytes = bytes; this.rate = rate; this.channels = channels;
    }
    public static WavFormat read(File file) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            if (length < 44 || !"RIFF".equals(tag(input))) throw new IOException("Not a WAV file");
            long riffEnd = uint(input) + 8;
            if (riffEnd > length || riffEnd < 44 || !"WAVE".equals(tag(input))) {
                throw new IOException("Invalid WAV container");
            }
            int rate = 0, channels = 0;
            long dataOffset = -1, dataBytes = 0;
            while (input.getFilePointer() + 8 <= riffEnd) {
                String id = tag(input);
                long size = uint(input), start = input.getFilePointer(), end = start + size;
                if (end > riffEnd) throw new IOException("Truncated WAV chunk");
                if ("fmt ".equals(id)) {
                    if (size < 16) throw new IOException("Invalid WAV format");
                    int format = ushort(input);
                    channels = ushort(input);
                    long sampleRate = uint(input), byteRate = uint(input);
                    int alignment = ushort(input), bits = ushort(input);
                    if (format != 1 || bits != 16 || (channels != 1 && channels != 2)
                            || sampleRate < 8000 || sampleRate > 192000
                            || alignment != channels * 2 || byteRate != sampleRate * alignment) {
                        throw new IOException("Use PCM16 WAV, mono or stereo (8–192 kHz)");
                    }
                    rate = (int) sampleRate;
                } else if ("data".equals(id) && dataOffset < 0) {
                    dataOffset = start; dataBytes = size;
                }
                input.seek(end + (size & 1));
            }
            if (rate == 0 || dataOffset < 0 || dataBytes == 0
                    || dataBytes % (channels * 2) != 0) throw new IOException("Missing or invalid WAV audio");
            return new WavFormat(dataOffset, dataBytes, rate, channels);
        }
    }
    private static String tag(RandomAccessFile f) throws IOException {
        byte[] bytes = new byte[4]; f.readFully(bytes);
        return new String(bytes, "US-ASCII");
    }
    private static int ushort(RandomAccessFile f) throws IOException {
        return f.readUnsignedByte() | (f.readUnsignedByte() << 8);
    }
    private static long uint(RandomAccessFile f) throws IOException {
        return (long) ushort(f) | ((long) ushort(f) << 16);
    }
}
