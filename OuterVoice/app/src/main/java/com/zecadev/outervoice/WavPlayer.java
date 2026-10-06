package com.zecadev.outervoice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.SystemClock;
import java.io.File;
import java.io.RandomAccessFile;

final class WavPlayer {
    interface Listener { void done(String error); }
    private final Context context;
    private Thread worker;
    private volatile boolean requested;
    WavPlayer(Context context) { this.context = context.getApplicationContext(); }
    synchronized boolean active() { return worker != null; }
    void stop() { requested = false; }
    synchronized boolean play(File file, Listener listener) {
        if (worker != null) return false;
        AudioDeviceInfo target = AudioRoutes.outer(context);
        if (target == null) throw new IllegalStateException("Outer speaker is unavailable (BUS12)");
        requested = true;
        worker = new Thread(() -> pump(file, target, listener), "outer-wav");
        worker.start();
        return true;
    }
    private void pump(File file, AudioDeviceInfo target, Listener listener) {
        AudioTrack track = null;
        String error = null;
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            WavFormat format = WavFormat.read(file);
            double gain = file.getName().equals("outer-test.wav") ? 1 : WavTools.normalization(file, format);
            AudioDiagnostics.log("WAV requested: " + AudioRoutes.describe(target) + "; " + format.rate + " Hz channels=" + format.channels + "; normalization=" + gain);
            int mask = format.channels == 2 ? AudioFormat.CHANNEL_OUT_STEREO : AudioFormat.CHANNEL_OUT_MONO;
            int minimum = AudioTrack.getMinBufferSize(format.rate, mask, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) throw new IllegalStateException("Unsupported audio rate");
            if (!requested) return;
            track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(format.rate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(mask).build())
                    .setBufferSizeInBytes(Math.max(minimum, Math.max(format.rate, 8192)))
                    .setTransferMode(AudioTrack.MODE_STREAM).build();
            if (track.getState() != AudioTrack.STATE_INITIALIZED || !track.setPreferredDevice(target)) {
                throw new IllegalStateException("Outer speaker route rejected");
            }
            if (track.setVolume(0f) != AudioTrack.SUCCESS) throw new IllegalStateException("Cannot mute output");
            track.play();
            byte[] buffer = new byte[4096];
            long deadline = SystemClock.elapsedRealtime() + 1500;
            long primeBytes = 0;
            while (requested && !matches(track, target)) {
                if (SystemClock.elapsedRealtime() > deadline) throw new IllegalStateException("BUS12 route unverified");
                int n = track.write(buffer, 0, buffer.length, AudioTrack.WRITE_NON_BLOCKING);
                if (n < 0) throw new IllegalStateException("Output error: " + n);
                primeBytes += n;
                Thread.sleep(5);
            }
            if (!requested) return;
            if (track.setVolume(1f) != AudioTrack.SUCCESS) throw new IllegalStateException("Cannot enable output");
            AudioDiagnostics.log("WAV actual: " + AudioRoutes.describe(track.getRoutedDevice()) + "; track volume=1; stream=" + track.getStreamType());
            input.seek(format.offset);
            long remaining = format.bytes;
            while (requested && remaining > 0) {
                int length = (int) Math.min(buffer.length, remaining);
                input.readFully(buffer, 0, length);
                WavTools.amplify(buffer, length, gain);
                int offset = 0;
                deadline = SystemClock.elapsedRealtime() + 1500;
                while (requested && offset < length) {
                    requireRoute(track, target);
                    int n = track.write(buffer, offset, length - offset, AudioTrack.WRITE_NON_BLOCKING);
                    if (n < 0) throw new IllegalStateException("Output error: " + n);
                    if (n == 0) {
                        if (SystemClock.elapsedRealtime() > deadline) throw new IllegalStateException("Output stalled");
                        Thread.sleep(3);
                    } else { offset += n; deadline = SystemClock.elapsedRealtime() + 1500; }
                }
                remaining -= length;
            }
            // Wait for the queued audio to be rendered, including the silent route prime.
            long frames = (primeBytes + format.bytes) / (format.channels * 2);
            deadline = SystemClock.elapsedRealtime() + 5000;
            while (requested && Integer.toUnsignedLong(track.getPlaybackHeadPosition()) < frames) {
                requireRoute(track, target);
                if (SystemClock.elapsedRealtime() > deadline) throw new IllegalStateException("Playback did not finish");
                Thread.sleep(10);
            }
        } catch (Exception failure) {
            if (requested) error = failure.getMessage();
        } finally {
            if (track != null) {
                try { track.setVolume(0f); } catch (Exception ignored) { }
                try { track.pause(); track.flush(); } catch (Exception ignored) { }
                try { track.release(); } catch (Exception ignored) { }
            }
            synchronized (this) { requested = false; worker = null; }
            AudioDiagnostics.log("WAV ended: " + (error == null ? "complete / stopped" : error));
            listener.done(error);
        }
    }
    private static boolean matches(AudioTrack track, AudioDeviceInfo target) {
        AudioDeviceInfo device = track.getRoutedDevice();
        return device != null && device.getId() == target.getId();
    }
    private static void requireRoute(AudioTrack track, AudioDeviceInfo target) {
        if (!matches(track, target)) {
            track.setVolume(0f);
            throw new IllegalStateException("Outer speaker route lost");
        }
    }
}
