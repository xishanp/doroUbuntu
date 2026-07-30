package com.termux.x11;

import com.termux.x11.input.InputHost;

/** Embedded fallback for callbacks that cannot be instance-bound in LorieView. */
public final class InputHostRegistry {
    private static volatile InputHost host;
    private InputHostRegistry() {}
    public static void set(InputHost value) { host = value; }
    public static InputHost get() { return host; }
    public static void clear() { host = null; }
}