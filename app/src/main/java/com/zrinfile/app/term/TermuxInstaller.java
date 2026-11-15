package com.zrinfile.app.term;

import android.content.Context;
import android.system.Os;

import com.zrinfile.app.fs.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 解压 Termux bootstrap 并安装 proot。 */
public final class TermuxInstaller {

    public interface Callback {
        void onProgress(String stage, int percent);

        void onDone();

        void onError(String message);
    }

    private static volatile boolean running;

    private TermuxInstaller() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void install(final Context context, final Callback callback) {
        if (running) return;
        running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doInstall(context, callback);
                    running = false;
                    callback.onDone();
                } catch (Exception e) {
                    running = false;
                    FileUtils.deleteRecursive(TermuxPaths.usr(context));
                    callback.onError(e.getMessage() == null ? "安装失败" : e.getMessage());
                }
            }
        }, "zrinfile-termux-install").start();
    }

    public static void uninstall(Context context) {
        FileUtils.deleteRecursive(TermuxPaths.usr(context));
        FileUtils.deleteRecursive(TermuxPaths.home(context));
    }

    private static void doInstall(Context context, Callback cb) throws Exception {
        File usr = TermuxPaths.usr(context);
        FileUtils.deleteRecursive(usr);
        if (!usr.mkdirs()) throw new Exception("无法创建运行时目录");

        cb.onProgress("正在解压基础环境…", 1);
        extractBootstrap(context, usr, cb);
        cb.onProgress("正在创建符号链接…", 72);
        applySymlinks(usr);
        cb.onProgress("正在安装 proot…", 80);
        copyProot(context, usr);
        cb.onProgress("正在设置权限…", 92);
        applyPermissions(usr);
        writeMarker(usr);
        TermuxPaths.home(context).mkdirs();
        TermuxPaths.tmp(context).mkdirs();
        cb.onProgress("完成", 100);
    }

    private static void extractBootstrap(Context context, File usr, Callback cb) throws Exception {
        InputStream raw = context.getAssets().open(TermuxPaths.bootstrapAsset());
        ZipInputStream zip = new ZipInputStream(raw);
        byte[] buf = new byte[64 * 1024];
        int count = 0;
        ZipEntry entry;
        while ((entry = zip.getNextEntry()) != null) {
            String name = entry.getName();
            if (name.startsWith("/")) name = name.substring(1);
            File target = new File(usr, name);
            if (entry.isDirectory()) {
                target.mkdirs();
            } else {
                File parent = target.getParentFile();
                if (parent != null) parent.mkdirs();
                if (entry.getName().endsWith("SYMLINKS.txt")) {
                    copy(zip, target, buf);
                } else if (entry.getSize() == 0 && name.endsWith(".sym")) {
                    copy(zip, target, buf);
                } else {
                    copy(zip, target, buf);
                }
            }
            zip.closeEntry();
            count++;
            if (count % 150 == 0) {
                int percent = 1 + Math.min(70, count * 70 / 3500);
                cb.onProgress("正在解压基础环境… " + count, percent);
            }
        }
        zip.close();
        raw.close();
    }

    private static void copy(InputStream in, File outFile, byte[] buf) throws Exception {
        OutputStream out = new FileOutputStream(outFile);
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        out.close();
    }

    private static void applySymlinks(File usr) {
        File links = new File(usr, "SYMLINKS.txt");
        if (!links.isFile()) return;
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new java.io.FileInputStream(links), "UTF-8"));
            String line;
            while ((line = reader.readLine()) != null) {
                int idx = line.indexOf('←');
                if (idx <= 0 || idx >= line.length() - 1) continue;
                String target = line.substring(0, idx).trim();
                String link = line.substring(idx + 1).trim();
                while (link.startsWith("./")) link = link.substring(2);
                if (link.length() == 0 || target.length() == 0) continue;
                File linkFile = new File(usr, link);
                File parent = linkFile.getParentFile();
                if (parent != null) parent.mkdirs();
                try {
                    Os.symlink(target, linkFile.getAbsolutePath());
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        } finally {
            FileUtils.closeQuietly(reader);
        }
    }

    private static void copyProot(Context context, File usr) throws Exception {
        String dir = TermuxPaths.prootAssetDir();
        String[] names = context.getAssets().list(dir);
        if (names == null || names.length == 0) throw new Exception("安装包缺少 proot 组件");
        File binDir = new File(usr, "bin");
        File libDir = new File(usr, "lib");
        File execDir = new File(usr, "libexec/proot");
        binDir.mkdirs();
        libDir.mkdirs();
        execDir.mkdirs();
        byte[] buf = new byte[64 * 1024];
        for (String name : names) {
            InputStream in = context.getAssets().open(dir + "/" + name);
            File out;
            if (name.equals("proot")) {
                out = new File(binDir, "proot");
            } else if (name.startsWith("loader")) {
                out = new File(execDir, name);
            } else {
                out = new File(libDir, name);
            }
            OutputStream os = new FileOutputStream(out);
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.close();
            in.close();
        }
        // libtalloc 的 soname 链接
        File talloc = new File(libDir, "libtalloc.so.2.5.0");
        if (talloc.isFile()) {
            symlinkQuiet("libtalloc.so.2.5.0", new File(libDir, "libtalloc.so.2"));
            symlinkQuiet("libtalloc.so.2.5.0", new File(libDir, "libtalloc.so"));
        }
    }

    private static void symlinkQuiet(String target, File link) {
        try {
            if (!link.exists()) Os.symlink(target, link.getAbsolutePath());
        } catch (Exception ignored) {
        }
    }

    private static void applyPermissions(File usr) {
        chmodTree(usr, 0700);
    }

    private static void chmodTree(File file, int mode) {
        if (file == null || !file.exists()) return;
        try {
            if (!isSymlink(file)) Os.chmod(file.getAbsolutePath(), mode);
        } catch (Exception ignored) {
        }
        if (file.isDirectory() && !isSymlink(file)) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) chmodTree(child, mode);
            }
        }
    }

    private static boolean isSymlink(File f) {
        try {
            android.system.StructStat st = Os.lstat(f.getAbsolutePath());
            return (st.st_mode & android.system.OsConstants.S_IFMT) == android.system.OsConstants.S_IFLNK;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeMarker(File usr) throws Exception {
        File marker = new File(usr, ".bootstrap-version");
        FileOutputStream out = new FileOutputStream(marker);
        out.write(TermuxPaths.BOOTSTRAP_VERSION.getBytes("UTF-8"));
        out.close();
        try {
            Os.chmod(marker.getAbsolutePath(), 0600);
        } catch (Exception ignored) {
        }
    }
}
