package com.zrinfile.app.term;

/** 通过 JNI 启动带 PTY 的子进程。 */
public final class Pty {

    static {
        System.loadLibrary("zrinpty");
    }

    private Pty() {
    }

    /**
     * 启动子进程并返回 {fd, pid}。失败返回 null。
     */
    public static native int[] start(String cmd, String[] args, String[] env,
                                     String cwd, int cols, int rows);

    public static native void resize(int fd, int cols, int rows);

    public static native void killProcess(int pid);

    public static native void closeFd(int fd);
}
