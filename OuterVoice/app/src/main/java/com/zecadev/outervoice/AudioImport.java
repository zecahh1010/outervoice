package com.zecadev.outervoice;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.function.BooleanSupplier;

/** Decode audio to the existing BUS12 player's canonical PCM WAV. No extension gate. */
final class AudioImport {
    static void convert(File input, File output, BooleanSupplier cancelled) throws Exception {
        if (PcmImport.convertWav(input, output, cancelled)) return;
        MediaExtractor extractor = new MediaExtractor(); MediaCodec decoder = null;
        try {
            extractor.setDataSource(input.getAbsolutePath());
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) { format = candidate; extractor.selectTrack(i); break; }
            }
            if (format == null) throw new IOException("No decodable audio in this file");
            AudioDiagnostics.log("Import source format: " + format);
            // Decode every packet. Some Android 9 MP4 extractors expose invalid gapless
            // delay values (e.g. 45158 samples for a 2-second AAC clip), cutting audio.
            // Retain codec priming/padding rather than trusting destructive trim metadata.
            format.setInteger(MediaFormat.KEY_ENCODER_DELAY, 0);
            format.setInteger(MediaFormat.KEY_ENCODER_PADDING, 0);
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0); decoder.start();
            int rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE), channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            int encoding = AudioFormat.ENCODING_PCM_16BIT;
            boolean inputDone = false, done = false; long progress = System.nanoTime();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            try (PcmImport.Writer writer = new PcmImport.Writer(output, cancelled)) {
                while (!done) {
                    if (cancelled.getAsBoolean()) throw new IOException("Import cancelled");
                    if (!inputDone) {
                        int index = decoder.dequeueInputBuffer(10000);
                        if (index >= 0) {
                            ByteBuffer buffer = decoder.getInputBuffer(index); buffer.clear();
                            int count = extractor.readSampleData(buffer, 0);
                            if (count < 0) { decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true; }
                            else { decoder.queueInputBuffer(index, 0, count, extractor.getSampleTime(), 0); extractor.advance(); }
                        }
                    }
                    int index = decoder.dequeueOutputBuffer(info, 10000);
                    if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        MediaFormat actual = decoder.getOutputFormat();
                        AudioDiagnostics.log("Import decoded format: " + actual);
                        rate = actual.getInteger(MediaFormat.KEY_SAMPLE_RATE); channels = actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                        encoding = actual.containsKey(MediaFormat.KEY_PCM_ENCODING) ? actual.getInteger(MediaFormat.KEY_PCM_ENCODING) : AudioFormat.ENCODING_PCM_16BIT;
                        if (encoding != AudioFormat.ENCODING_PCM_16BIT && encoding != AudioFormat.ENCODING_PCM_FLOAT)
                            throw new IOException("Decoder returned unsupported PCM encoding");
                        progress = System.nanoTime();
                    } else if (index >= 0) {
                        ByteBuffer buffer = decoder.getOutputBuffer(index).duplicate().order(ByteOrder.LITTLE_ENDIAN);
                        buffer.position(info.offset); buffer.limit(info.offset + info.size);
                        int sampleBytes = encoding == AudioFormat.ENCODING_PCM_FLOAT ? 4 : 2;
                        if (channels < 1 || channels > 64 || rate < 1 || info.size % (sampleBytes * channels) != 0)
                            throw new IOException("Invalid decoded audio frames");
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            while (buffer.remaining() >= sampleBytes * channels) {
                                double sample = 0;
                                for (int c = 0; c < channels; c++) sample += sampleBytes == 4 ? buffer.getFloat() : buffer.getShort() / 32768.0;
                                writer.frame(sample / channels, rate);
                            }
                        }
                        done = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        decoder.releaseOutputBuffer(index, false); progress = System.nanoTime();
                    }
                    if (System.nanoTime() - progress > 30_000_000_000L) throw new IOException("Audio decoder stopped responding");
                }
                writer.finish();
            }
        } catch (Exception failure) {
            output.delete();
            throw new IOException("Cannot decode this sound: " + (failure.getMessage() == null ? "unsupported or damaged audio" : failure.getMessage()), failure);
        } finally {
            if (decoder != null) { try { decoder.stop(); } catch (RuntimeException ignored) { } decoder.release(); }
            extractor.release();
        }
        WavFormat.read(output);
    }
}
