package com.zecadev.outervoice;

/** Digital output gain. A soft knee bounds PCM instead of wrapping on overload. */
final class ExteriorGain {
    static double linear(int db) { return Math.pow(10, Math.max(0, Math.min(24, db)) / 20.0); }
    static short sample(double value, double gain) {
        if (gain == 1) return (short)Math.max(-32768, Math.min(32767, Math.round(value)));
        double x = value * gain / 32768.0, a = Math.abs(x);
        if (a > .85) x = Math.copySign(.85 + .15 * Math.tanh((a - .85) / .15), x);
        return (short)Math.max(-32768, Math.min(32767, Math.round(x * 32768)));
    }
    static void apply(byte[] pcm, int length, double gain) {
        if (gain == 1) return;
        for (int i = 0; i + 1 < length; i += 2) {
            short s = sample((short)((pcm[i] & 255) | (pcm[i + 1] << 8)), gain);
            pcm[i] = (byte)s; pcm[i + 1] = (byte)(s >> 8);
        }
    }
}
