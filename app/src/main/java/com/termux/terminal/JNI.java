package com.termux.terminal;

public final class JNI {
    static {
        System.loadLibrary("termux");
    }

    private JNI() {}

    public static native int createSubprocess(
        String cmd,
        String cwd,
        String[] args,
        String[] envVars,
        int[] processId,
        int rows,
        int columns,
        int cellWidth,
        int cellHeight
    );

    public static native void setPtyWindowSize(
        int fd,
        int rows,
        int cols,
        int cellWidth,
        int cellHeight
    );

    public static native int waitFor(int processId);
    public static native void close(int fileDescriptor);
}