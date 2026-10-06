package com.zecadev.outervoice;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Process;
import android.os.SystemClock;

/** Live MIC -> PCM16 -> BUS12. Worker owns and releases all audio resources. */
public final class LiveMicPassthrough implements AutoCloseable {
    public interface Listener {
        // Worker-thread callbacks; post UI changes to the main thread.
        void onStatus(String message);
        void onLevel(double rms);
    }

    private static final int RATE = 44100;
    private static final int CHUNK = 441; // 10 ms, hardware buffering adds latency.
    private final Context context;
    private final Listener listener;
    private Thread worker;
    private volatile boolean requested;
    private volatile float gain = 1f;

    public LiveMicPassthrough(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    /** Rejects a second session until the previous worker has released resources. */
    public synchronized boolean start() {
        if (worker != null) return false;
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Microphone permission is required");
        }
        final AudioDeviceInfo outer = AudioRoutes.outer(context);
        if (outer == null) {
            throw new IllegalStateException("BUS12_OUTER_NOTIFY is unavailable");
        }
        requested = true;
        worker = new Thread(() -> pump(outer), "car-live-mic");
        worker.start();
        return true;
    }

    /** Includes startup and stopping, preventing overlapping capture sessions. */
    public synchronized boolean isActive() {
        return worker != null;
    }

    /** Linear attenuation only; never boosts microphone samples above unity. */
    public void setGain(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            throw new IllegalArgumentException("Gain must be finite");
        }
        gain = Math.max(0f, Math.min(1f, value));
    }

    /** Nonblocking: nonblocking audio I/O lets the worker exit and release promptly. */
    public void stop() {
        requested = false;
    }

    @Override
    public void close() {
        stop();
    }

    private void pump(AudioDeviceInfo outer) {
        AudioRecord input = null;
        AudioTrack output = null;
        String finalStatus = "Live microphone stopped";
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
            status("Starting live microphone -> outer speaker");
            int inputMin = AudioRecord.getMinBufferSize(RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int outputMin = AudioTrack.getMinBufferSize(RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (inputMin <= 0 || outputMin <= 0) {
                throw new IllegalStateException("44.1 kHz mono PCM16 is unsupported");
            }
            if (!requested) return;
            input = new AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(Math.max(inputMin, CHUNK * 2 * 4))
                    .build();
            output = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(Math.max(outputMin, CHUNK * 2 * 4))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if (input.getState() != AudioRecord.STATE_INITIALIZED
                    || output.getState() != AudioTrack.STATE_INITIALIZED) {
                throw new IllegalStateException("Audio initialization failed");
            }
            if (!output.setPreferredDevice(outer)) {
                throw new IllegalStateException("Outer speaker routing was rejected");
            }
            AudioDiagnostics.log("Live requested: " + AudioRoutes.describe(outer) + "; preferred accepted; gain=" + gain);
            if (output.setVolume(0f) != AudioTrack.SUCCESS) {
                throw new IllegalStateException("Cannot mute route verification");
            }
            if (!requested) return;
            output.play();
            // Route information exists only while active. Prime with silence, not MIC audio.
            short[] samples = new short[CHUNK];
            long routeDeadline = SystemClock.elapsedRealtime() + 1500;
            while (requested && !matches(output.getRoutedDevice(), outer)) {
                if (SystemClock.elapsedRealtime() >= routeDeadline) {
                    throw new IllegalStateException("BUS12 route could not be verified");
                }
                int count = output.write(samples, 0, samples.length, AudioTrack.WRITE_NON_BLOCKING);
                if (count < 0) throw new IllegalStateException("Route prime failed: " + count);
                Thread.sleep(5);
            }
            if (!requested) return;
            input.startRecording();
            if (input.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                throw new IllegalStateException("Microphone capture did not start");
            }
            if (output.setVolume(1f) != AudioTrack.SUCCESS) {
                throw new IllegalStateException("Cannot enable live output");
            }
            status("Live microphone -> BUS12_OUTER_NOTIFY | MIC: "
                    + String.valueOf(input.getRoutedDevice()));
            AudioDiagnostics.log("Live actual: " + AudioRoutes.describe(output.getRoutedDevice()) + "; track volume=1; stream=" + output.getStreamType());
            long totalWritten = 0;
            long lastMeter = 0, lastDiagnostic = 0;
            while (requested) {
                requireRoute(output, outer);
                int count = input.read(samples, 0, samples.length, AudioRecord.READ_NON_BLOCKING);
                if (count < 0) throw new IllegalStateException("Microphone read failed: " + count);
                if (count == 0) {
                    Thread.sleep(3);
                    continue;
                }
                double sum = 0;
                float currentGain = gain;
                for (int i = 0; i < count; i++) {
                    double value = samples[i] / 32768.0;
                    sum += value * value;
                    samples[i] = (short) Math.round(samples[i] * currentGain);
                }
                int offset = 0;
                long writeDeadline = SystemClock.elapsedRealtime() + 1000;
                while (requested && offset < count) {
                    requireRoute(output, outer);
                    int written = output.write(samples, offset, count - offset,
                            AudioTrack.WRITE_NON_BLOCKING);
                    if (written < 0) throw new IllegalStateException("Speaker write failed: " + written);
                    if (written == 0) {
                        if (SystemClock.elapsedRealtime() >= writeDeadline) {
                            throw new IllegalStateException("Speaker output stalled");
                        }
                        Thread.sleep(3);
                    } else {
                        offset += written; // Respect partial writes; do not drop the remainder.
                        totalWritten += written;
                        writeDeadline = SystemClock.elapsedRealtime() + 1000;
                    }
                }
                long now = SystemClock.elapsedRealtime();
                if (requested && now - lastMeter >= 120) {
                    lastMeter = now;
                    level(Math.sqrt(sum / count));
                    if (now - lastDiagnostic >= 3000) {
                        AudioDiagnostics.log("Live frames=" + totalWritten + " RMS=" + Math.sqrt(sum / count) + "; gain=" + gain);
                        lastDiagnostic = now;
                    }
                }
            }
        } catch (Exception e) {
            if (requested) finalStatus = "Live microphone failed: " + e.getMessage();
        } finally {
            // Only this worker accesses/release its native resources; stop() sets a flag.
            if (output != null) {
                try { output.setVolume(0f); } catch (Exception ignored) { }
                try { output.pause(); output.flush(); } catch (Exception ignored) { }
                try { output.release(); } catch (Exception ignored) { }
            }
            if (input != null) {
                try { input.stop(); } catch (Exception ignored) { }
                try { input.release(); } catch (Exception ignored) { }
            }
            // Publish final status before permitting a new session, avoiding stale callbacks.
            status(finalStatus);
            AudioDiagnostics.log(finalStatus);
            level(0);
            synchronized (this) {
                requested = false;
                worker = null;
            }
        }
    }

    private static boolean matches(AudioDeviceInfo actual, AudioDeviceInfo expected) {
        return actual != null && actual.getId() == expected.getId();
    }

    private static void requireRoute(AudioTrack output, AudioDeviceInfo outer) {
        if (!matches(output.getRoutedDevice(), outer)) {
            output.setVolume(0f);
            throw new IllegalStateException("Outer route was lost; live output stopped");
        }
    }

    private void status(String message) {
        try { if (listener != null) listener.onStatus(message); } catch (RuntimeException ignored) { }
    }

    private void level(double rms) {
        try { if (listener != null) listener.onLevel(rms); } catch (RuntimeException ignored) { }
    }
}
