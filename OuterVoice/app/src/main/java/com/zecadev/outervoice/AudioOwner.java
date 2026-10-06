package com.zecadev.outervoice;

/** UI-thread audio ownership shared by the Activity and overlay service. */
final class AudioOwner {
    private static Object owner;
    private static Runnable stop;
    static void claim(Object next, Runnable cancel) {
        if (owner != null && owner != next && stop != null) stop.run();
        owner = next; stop = cancel;
    }
    static void release(Object current) {
        if (owner == current) { owner = null; stop = null; }
    }
}
