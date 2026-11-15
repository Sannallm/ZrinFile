package com.zrinfile.app.term;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 软件源测速与自动选择，默认不采用境外源。 */
public final class MirrorManager {

    public static class Mirror {
        public final String name;
        public final String url;
        public final String host;

        public Mirror(String name, String url, String host) {
            this.name = name;
            this.url = url;
            this.host = host;
        }
    }

    public static class Result {
        public Mirror mirror;
        public long latency = -1;
    }

    public interface Callback {
        void onResult(Result result);
    }

    public static final List<Mirror> MIRRORS = new ArrayList<>();

    static {
        MIRRORS.add(new Mirror("清华 TUNA", "https://mirrors.tuna.tsinghua.edu.cn/termux", "mirrors.tuna.tsinghua.edu.cn"));
        MIRRORS.add(new Mirror("中科大 USTC", "https://mirrors.ustc.edu.cn/termux", "mirrors.ustc.edu.cn"));
        MIRRORS.add(new Mirror("阿里云", "https://mirrors.aliyun.com/termux", "mirrors.aliyun.com"));
        MIRRORS.add(new Mirror("华为云", "https://repo.huaweicloud.com/termux", "repo.huaweicloud.com"));
        MIRRORS.add(new Mirror("北外 BFSU", "https://mirrors.bfsu.edu.cn/termux", "mirrors.bfsu.edu.cn"));
        MIRRORS.add(new Mirror("南京大学", "https://mirror.nju.edu.cn/termux", "mirror.nju.edu.cn"));
        MIRRORS.add(new Mirror("官方源", "https://packages.termux.dev/apt/termux-main", "packages.termux.dev"));
    }

    private static final ExecutorService POOL = Executors.newCachedThreadPool();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile boolean detecting;

    private MirrorManager() {
    }

    public static boolean isDetecting() {
        return detecting;
    }

    public static Mirror byName(String name) {
        for (Mirror m : MIRRORS) if (m.name.equals(name)) return m;
        return null;
    }

    public static void detect(final Context ctx, final Callback callback) {
        if (detecting) return;
        detecting = true;
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Result best = new Result();
                List<Future<Result>> futures = new ArrayList<>();
                for (final Mirror m : MIRRORS) {
                    if ("官方源".equals(m.name)) continue;
                    futures.add(POOL.submit(new Callable<Result>() {
                        @Override
                        public Result call() {
                            Result r = new Result();
                            r.mirror = m;
                            r.latency = latency(m.host);
                            return r;
                        }
                    }));
                }
                for (Future<Result> f : futures) {
                    try {
                        Result r = f.get(4, TimeUnit.SECONDS);
                        if (r != null && r.latency > 0) {
                            if (best.mirror == null || r.latency < best.latency) best = r;
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (best.mirror == null) {
                    best.mirror = MIRRORS.get(0);
                    best.latency = -1;
                }
                TerminalPrefs.setMirrorName(ctx, best.mirror.name);
                TerminalPrefs.setMirrorUrl(ctx, best.mirror.url);
                TerminalPrefs.setMirrorLatency(ctx, best.latency);
                TerminalPrefs.setMirrorMode(ctx, TerminalPrefs.MIRROR_AUTO);
                final Result result = best;
                detecting = false;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onResult(result);
                    }
                });
            }
        });
    }

    public static long latency(String host) {
        Socket socket = null;
        try {
            long start = System.currentTimeMillis();
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, 443), 2200);
            long cost = System.currentTimeMillis() - start;
            return cost <= 0 ? 1 : cost;
        } catch (Exception e) {
            return -1;
        } finally {
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static void saveManual(Context ctx, Mirror m) {
        TerminalPrefs.setMirrorMode(ctx, TerminalPrefs.MIRROR_MANUAL);
        TerminalPrefs.setMirrorName(ctx, m.name);
        TerminalPrefs.setMirrorUrl(ctx, m.url);
        TerminalPrefs.setMirrorLatency(ctx, -1);
    }

    public static void saveCustom(Context ctx, String url) {
        TerminalPrefs.setMirrorMode(ctx, TerminalPrefs.MIRROR_MANUAL);
        TerminalPrefs.setMirrorName(ctx, "自定义");
        TerminalPrefs.setMirrorUrl(ctx, url);
        TerminalPrefs.setMirrorLatency(ctx, -1);
    }
}
