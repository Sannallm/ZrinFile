package com.zrinfile.app.term;

import android.content.Context;
import android.os.Build;

import java.io.File;

/** Termux 运行时的路径约定。 */
public final class TermuxPaths {

    public static final String GUEST_PREFIX = "/data/data/com.termux/files/usr";
    public static final String GUEST_HOME = "/data/data/com.termux/files/home";
    public static final String BOOTSTRAP_VERSION = "bootstrap-2026.11.15";

    private TermuxPaths() {
    }

    public static boolean supported() {
        return Build.VERSION.SDK_INT >= 24;
    }

    public static File root(Context c) {
        return new File(c.getFilesDir(), "termux");
    }

    public static File usr(Context c) {
        return new File(root(c), "usr");
    }

    public static File home(Context c) {
        File f = new File(root(c), "home");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File tmp(Context c) {
        File f = new File(usr(c), "tmp");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    public static File proot(Context c) {
        return new File(usr(c), "bin/proot");
    }

    public static File loader(Context c) {
        return new File(usr(c), "libexec/proot/loader");
    }

    public static File marker(Context c) {
        return new File(usr(c), ".bootstrap-version");
    }

    public static boolean installed(Context c) {
        return marker(c).isFile() && proot(c).isFile() && loader(c).isFile();
    }

    public static String abi() {
        String[] abis = Build.SUPPORTED_ABIS;
        String a = abis != null && abis.length > 0 ? abis[0] : Build.CPU_ABI;
        if (a == null) a = "arm64-v8a";
        if (a.startsWith("arm64")) return "aarch64";
        if (a.startsWith("armeabi")) return "arm";
        if (a.startsWith("x86_64")) return "x86_64";
        if (a.startsWith("x86")) return "i686";
        return "aarch64";
    }

    public static String bootstrapAsset() {
        return "termux/bootstrap-" + abi() + ".zip";
    }

    public static String prootAssetDir() {
        return "termux/proot/" + abi();
    }
}
