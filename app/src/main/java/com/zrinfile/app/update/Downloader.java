package com.zrinfile.app.update;

import android.content.Context;

import com.zrinfile.app.BuildConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** 多线程分段下载更新包，服务器不支持 Range 时自动退化为单线程。 */
public final class Downloader {

    public interface Listener {
        void onProgress(int percent, long done, long total);

        void onStage(String message);

        void onDone(File file);

        void onError(String message);

        void onCancelled();
    }

    public static final class Task {
        private volatile boolean cancelled;
        private final List<HttpURLConnection> connections = new ArrayList<>();

        void register(HttpURLConnection c) {
            synchronized (connections) {
                connections.add(c);
            }
            if (cancelled) c.disconnect();
        }

        void unregister(HttpURLConnection c) {
            synchronized (connections) {
                connections.remove(c);
            }
        }

        public void cancel() {
            cancelled = true;
            synchronized (connections) {
                for (HttpURLConnection c : connections) {
                    try {
                        c.disconnect();
                    } catch (Exception ignored) {
                    }
                }
                connections.clear();
            }
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    private static final int THREADS = 4;
    private static final int BUFFER = 64 * 1024;

    private Downloader() {
    }

    public static Task start(final Context context, final UpdateInfo info, final Listener listener) {
        final Task task = new Task();
        final File dir = new File(context.getCacheDir(), "update");
        final File target = new File(dir, "ZrinFile-" + info.versionName + ".apk");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runDownload(info, dir, target, task, listener);
            }
        }, "zrinfile-update");
        t.setPriority(Thread.NORM_PRIORITY - 1);
        t.start();
        return task;
    }

    private static void runDownload(UpdateInfo info, File dir, File target, Task task, Listener listener) {
        try {
            if (!dir.exists() && !dir.mkdirs()) {
                listener.onError("无法创建下载目录");
                return;
            }
            if (target.exists()) {
                boolean sizeOk = info.size <= 0 || target.length() == info.size;
                if (sizeOk && (info.sha256.length() == 0 || matchSha(target, info.sha256))) {
                    listener.onDone(target);
                    return;
                }
                target.delete();
            }
            File part = new File(dir, target.getName() + ".part");
            if (part.exists()) part.delete();

            long usable = dir.getUsableSpace();
            if (usable > 0 && info.size > 0 && usable < info.size + 4L * 1024 * 1024) {
                listener.onError("存储空间不足，需要 " + formatSize(info.size));
                return;
            }

            List<String> sources = UpdateChecker.downloadSources(info.apkUrl);
            long total = 0;
            boolean success = false;
            for (int i = 0; i < sources.size(); i++) {
                if (task.isCancelled()) break;
                String url = sources.get(i);
                if (i > 0) {
                    listener.onStage("正在尝试备用下载源 " + i + "/" + (sources.size() - 1));
                }
                part.delete();
                boolean[] rangeOk = new boolean[]{false};
                long expected = probeTotal(url, task, rangeOk);
                if (task.isCancelled()) break;
                if (expected <= 0) expected = info.size;
                if (expected <= 0) continue;
                if (info.size > 0 && expected != info.size) expected = info.size;
                if (!downloadSegments(url, part, expected, rangeOk[0], task, listener)) continue;
                if (part.length() != expected) continue;
                total = expected;
                success = true;
                break;
            }

            if (task.isCancelled()) {
                part.delete();
                listener.onCancelled();
                return;
            }
            if (!success) {
                part.delete();
                listener.onError("下载失败，请检查网络后重试");
                return;
            }
            if (info.sha256.length() > 0 && !matchSha(part, info.sha256)) {
                part.delete();
                listener.onError("安装包校验失败，已删除文件");
                return;
            }
            if (target.exists()) target.delete();
            if (!part.renameTo(target)) {
                listener.onError("无法保存安装包");
                return;
            }
            listener.onDone(target);
        } catch (Exception e) {
            if (task.isCancelled()) {
                listener.onCancelled();
            } else {
                listener.onError(e.getMessage() == null ? "下载失败" : e.getMessage());
            }
        }
    }

    private static boolean downloadSegments(String url, File part, long total, boolean rangeOk,
                                            Task task, Listener listener) {
        int threads = rangeOk ? (total > 2L * 1024 * 1024 ? THREADS : (total > 1024 * 1024 ? 2 : 1)) : 1;
        AtomicLong done = new AtomicLong();
        AtomicReference<String> error = new AtomicReference<>();
        List<Thread> workers = new ArrayList<>();

        try {
            RandomAccessFile raf = new RandomAccessFile(part, "rw");
            raf.setLength(total);
            raf.close();
        } catch (Exception e) {
            return false;
        }

        for (int i = 0; i < threads; i++) {
            final long start = total * i / threads;
            final long end = (i == threads - 1) ? total - 1 : total * (i + 1) / threads - 1;
            final int index = i;
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (task.isCancelled()) return;
                        downloadRange(url, part, start, end, done, task);
                    } catch (Exception e) {
                        if (!task.isCancelled()) {
                            error.compareAndSet(null, "分段 " + (index + 1) + " " + e.getMessage());
                        }
                    }
                }
            }, "zrinfile-update-" + i);
            workers.add(t);
            t.start();
        }

        int lastPercent = -1;
        boolean alive = true;
        while (alive) {
            alive = false;
            for (Thread t : workers) {
                if (t.isAlive()) {
                    alive = true;
                    break;
                }
            }
            int percent = (int) Math.min(100, done.get() * 100 / total);
            if (percent != lastPercent) {
                lastPercent = percent;
                listener.onProgress(percent, done.get(), total);
            }
            if (alive) {
                try {
                    Thread.sleep(180);
                } catch (InterruptedException ignored) {
                }
            }
            if (task.isCancelled()) break;
        }

        if (task.isCancelled()) return false;
        if (error.get() != null) return false;
        listener.onProgress(100, total, total);
        return part.length() == total;
    }

    private static void downloadRange(String url, File part, long start, long end,
                                      AtomicLong done, Task task) throws Exception {
        if (start > end) return;
        HttpURLConnection conn = null;
        RandomAccessFile raf = null;
        try {
            conn = open(url, task, "bytes=" + start + "-" + end);
            int code = conn.getResponseCode();
            if (code != 206 && code != 200) {
                throw new Exception("服务器返回 " + code);
            }
            if (code == 200 && start > 0) {
                throw new Exception("服务器不支持分段下载");
            }
            InputStream in = conn.getInputStream();
            raf = new RandomAccessFile(part, "rw");
            if (code == 200) {
                raf.seek(0);
            } else {
                raf.seek(start);
            }
            byte[] buf = new byte[BUFFER];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (task.isCancelled()) break;
                raf.write(buf, 0, n);
                done.addAndGet(n);
            }
            in.close();
        } finally {
            if (raf != null) {
                try {
                    raf.close();
                } catch (Exception ignored) {
                }
            }
            if (conn != null) {
                task.unregister(conn);
                conn.disconnect();
            }
        }
    }

    private static long probeTotal(String url, Task task, boolean[] rangeOk) {
        HttpURLConnection conn = null;
        try {
            conn = open(url, task, "bytes=0-0");
            int code = conn.getResponseCode();
            if (code == 206) {
                rangeOk[0] = true;
                String cr = conn.getHeaderField("Content-Range");
                if (cr != null) {
                    int slash = cr.lastIndexOf('/');
                    if (slash > 0) {
                        try {
                            return Long.parseLong(cr.substring(slash + 1).trim());
                        } catch (Exception ignored) {
                        }
                    }
                }
            } else if (code == 200) {
                return conn.getContentLength();
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) {
                task.unregister(conn);
                conn.disconnect();
            }
        }
        return -1;
    }

    private static HttpURLConnection open(String url, Task task, String range) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "ZrinFile/" + BuildConfig.VERSION_NAME);
        conn.setRequestProperty("Accept-Encoding", "identity");
        if (range != null) conn.setRequestProperty("Range", range);
        task.register(conn);
        return conn;
    }

    private static boolean matchSha(File file, String expected) {
        InputStream in = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            in = new java.io.FileInputStream(file);
            byte[] buf = new byte[BUFFER];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02X", b));
            return sb.toString().equalsIgnoreCase(expected.trim());
        } catch (Exception e) {
            return false;
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(java.util.Locale.US, "%.1f KB", bytes / 1024.0);
        return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}
