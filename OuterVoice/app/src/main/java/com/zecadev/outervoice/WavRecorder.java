package com.zecadev.outervoice;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import android.os.SystemClock;
import java.io.File;
import java.io.RandomAccessFile;

final class WavRecorder {
    interface Listener { void done(File file, String error); void meter(double level, long milliseconds); }
    private Thread worker;
    private volatile boolean requested, discard;
    synchronized boolean active() { return worker != null; }
    synchronized boolean start(File destination, Listener listener) {
        if (worker != null) return false;
        requested = true; discard = false;
        worker = new Thread(() -> record(destination, listener), "voice-record"); worker.start(); return true;
    }
    void stop() { requested = false; }
    void cancel() { discard = true; requested = false; }
    private void record(File file, Listener listener) {
        AudioRecord input = null; String error = null; long bytes = 0;
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            output.setLength(0); WavTools.header(output, 0);
            int min = AudioRecord.getMinBufferSize(44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (min <= 0) throw new IllegalStateException("44.1 kHz recording unsupported");
            input = new AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.MIC)
                .setAudioFormat(new AudioFormat.Builder().setSampleRate(44100).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(Math.max(min, 8192)).build();
            if (input.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Cannot open microphone");
            input.startRecording();
            if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) throw new IllegalStateException("Cannot start recording");
            AudioDiagnostics.log("Record microphone: " + AudioRoutes.describe(input.getRoutedDevice()));
            short[] samples = new short[2048]; byte[] buffer = new byte[4096]; long last = 0, wait = SystemClock.elapsedRealtime();
            while (requested && bytes < 44100L * 2 * 180) {
                int n = input.read(samples, 0, samples.length, AudioRecord.READ_NON_BLOCKING);
                if (n < 0) throw new IllegalStateException("Microphone read error " + n);
                if (n == 0) {
                    if (SystemClock.elapsedRealtime() - wait > 3000) throw new IllegalStateException("Microphone stalled");
                    Thread.sleep(5); continue;
                }
                wait = SystemClock.elapsedRealtime(); double sum = 0;
                for (int i = 0; i < n; i++) {
                    buffer[2 * i] = (byte)samples[i]; buffer[2 * i + 1] = (byte)(samples[i] >> 8);
                    double value = samples[i] / 32768.0; sum += value * value;
                }
                output.write(buffer, 0, n * 2); bytes += n * 2;
                if (wait - last >= 120) { listener.meter(Math.sqrt(sum / n), bytes * 1000 / 88200); last = wait; }
            }
            if (bytes == 0 && !discard) throw new IllegalStateException("No microphone audio captured");
            WavTools.header(output, bytes);
        } catch (Exception e) { error = e.getMessage(); }
        finally {
            if (input != null) { try { input.stop(); } catch (Exception ignored) { } input.release(); }
            boolean deleted = discard || error != null;
            if (deleted) file.delete();
            synchronized (this) { requested = false; worker = null; }
            listener.done(deleted ? null : file, discard ? null : error);
        }
    }
}
