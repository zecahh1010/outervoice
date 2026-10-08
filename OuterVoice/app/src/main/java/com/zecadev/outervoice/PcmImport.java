package com.zecadev.outervoice;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.function.BooleanSupplier;

/** Streaming PCM/float WAV conversion and linear resampling, independently testable. */
public final class PcmImport {
    private static long number(RandomAccessFile file, int bytes) throws IOException {
        long value = 0; for (int i = 0; i < bytes; i++) value |= (long)file.readUnsignedByte() << (i * 8); return value;
    }
    public static boolean convertWav(File input, File output, BooleanSupplier cancelled) throws IOException {
        try (RandomAccessFile source = new RandomAccessFile(input, "r")) {
            if (source.length() < 12) return false;
            if (source.readInt() != 0x52494646) return false; number(source, 4);
            if (source.readInt() != 0x57415645) return false;
            int format = 0, channels = 0, bits = 0, alignment = 0, rate = 0;
            long data = -1, length = 0;
            while (source.getFilePointer() + 8 <= source.length()) {
                int id = source.readInt(); long size = number(source, 4), start = source.getFilePointer();
                if (size > source.length() - start) throw new IOException("Truncated WAV file");
                if (id == 0x666d7420 && size >= 16) {
                    format = (int)number(source, 2); channels = (int)number(source, 2); rate = (int)number(source, 4);
                    number(source, 4); alignment = (int)number(source, 2); bits = (int)number(source, 2);
                    if (format == 0xfffe && size >= 40) {
                        // WAVE_FORMAT_EXTENSIBLE subtype GUID for PCM or IEEE float.
                        source.seek(start + 24); format = (int)number(source, 4);
                        byte[] tail = new byte[12]; source.readFully(tail);
                        byte[] expected = {0,0,16,0,(byte)128,0,0,(byte)170,0,56,(byte)155,113};
                        if (!java.util.Arrays.equals(tail, expected)) return false;
                    }
                } else if (id == 0x64617461 && data < 0) { data = start; length = size; }
                source.seek(start + size + (size & 1));
            }
            if (format != 1 && format != 3) return false; // compressed WAV uses Android's decoder
            if (channels < 1 || channels > 64 || rate < 1 || rate > 768000 || data < 0 || length == 0
                || alignment != channels * (bits / 8) || length % alignment != 0
                || (format == 1 && bits != 8 && bits != 16 && bits != 24 && bits != 32)
                || (format == 3 && bits != 32 && bits != 64)) throw new IOException("Invalid PCM WAV format");
            source.seek(data);
            try (Writer writer = new Writer(output, cancelled)) {
                long frames = length / alignment;
                byte[] block = new byte[alignment * 2048];
                while (frames > 0) {
                    int count = (int)Math.min(2048, frames); source.readFully(block, 0, count * alignment);
                    int position = 0;
                    for (int f = 0; f < count; f++) {
                    double sum = 0;
                    for (int c = 0; c < channels; c++) {
                        long raw = 0;
                        for (int b = 0; b < bits / 8; b++) raw |= (long)(block[position++] & 255) << (b * 8);
                        if (format == 3) sum += bits == 32 ? Float.intBitsToFloat((int)raw) : Double.longBitsToDouble(raw);
                        else if (bits == 8) sum += (raw - 128) / 128.0;
                        else { long signed = (raw << (64 - bits)) >> (64 - bits); sum += signed / Math.pow(2, bits - 1); }
                    }
                    writer.frame(sum / channels, rate);
                    }
                    frames -= count;
                }
                writer.finish();
            } catch (IOException failure) { output.delete(); throw failure; }
            return true;
        }
    }
    public static final class Writer implements AutoCloseable {
        private final RandomAccessFile out;
        private final BooleanSupplier cancelled;
        private final byte[] buffer = new byte[8192];
        private int used, rate; private long frames, bytes; private double previous, next;
        public Writer(File file, BooleanSupplier cancelled) throws IOException {
            this.cancelled = cancelled; out = new RandomAccessFile(file, "rw"); out.setLength(0); WavTools.header(out, 0);
        }
        public void frame(double value, int sampleRate) throws IOException {
            if (cancelled.getAsBoolean()) throw new IOException("Import cancelled");
            if (rate == 0) rate = sampleRate;
            if (rate != sampleRate || rate < 1) throw new IOException("Audio sample rate changed during import");
            value = Double.isFinite(value) ? Math.max(-1, Math.min(1, value)) : 0;
            if (frames == 0) { previous = value; next = 0; }
            while (next <= frames) {
                double fraction = frames == 0 ? 1 : next - (frames - 1);
                write(previous + (value - previous) * fraction); next += rate / 44100.0;
            }
            previous = value; frames++;
        }
        private void write(double value) throws IOException {
            int sample = (int)Math.max(-32768, Math.min(32767, Math.round(value * 32768)));
            buffer[used++] = (byte)sample; buffer[used++] = (byte)(sample >> 8); bytes += 2;
            if (bytes > 0xffffffffL - 36) throw new IOException("Sound exceeds the WAV container capacity");
            if (used == buffer.length) flush();
        }
        private void flush() throws IOException { out.write(buffer, 0, used); used = 0; }
        public void finish() throws IOException {
            if (frames == 0) throw new IOException("Sound contains no audio samples");
            while (next < frames) { write(previous); next += rate / 44100.0; }
            flush(); WavTools.header(out, bytes);
        }
        @Override public void close() throws IOException { out.close(); }
    }
}
