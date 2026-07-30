package com.termux.x11;

/** Embedded fallback for code paths that originally read MainActivity.prefs. */
public final class InputPreferencesRegistry {
    private static volatile Prefs prefs;
    private InputPreferencesRegistry() {}
    public static void set(Prefs value) { prefs = value; }
    public static Prefs get() {
        Prefs value = MainActivity.getPrefs();
        return value != null ? value : prefs;
    }
    public static void clear() { prefs = null; }
}