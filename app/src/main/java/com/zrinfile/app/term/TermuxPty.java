package com.zrinfile.app.term;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import com.zrinfile.app.fs.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** 基于 PTY 的 Termux 会话：proot + 官方 bootstrap。 */
public final class TermuxPty {

    public interface Listener {
        void onOutput(ShellSession session, CharSequence text);

        void onClear(ShellSession session);

        void onExit(ShellSession session);

        void onError(ShellSession session, String message);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Listener uiListener;

    private final Context context;
    private final ShellSession session;
    private final TerminalTheme theme;
    private final Stream stream;
    private final Stream.Sink sink;

    private int fd = -1;
    private int pid = -1;
    private ParcelFileDescriptor pfd;
    private FileInputStream in;
    private FileOutputStream out;
    private volatile boolean closed;
    private volatile long lastRead;

    private TermuxPty(Context context, ShellSession session, TerminalTheme theme) {
        this.context = context.getApplicationContext();
        this.session = session;
        this.theme = theme;
        this.stream = new Stream(theme);
        this.sink = new Stream.Sink() {
            @Override
            public void onSubmit(final CharSequence line) {
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (closed) return;
                        TermuxPty.this.session.append(line, 5000);
                        Listener l = uiListener;
                        if (l != null) l.onOutput(TermuxPty.this.session, line);
                    }
                });
            }

            @Override
            public void onClearScreen() {
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (closed) return;
                        TermuxPty.this.session.clear();
                        Listener l = uiListener;
                        if (l != null) l.onClear(TermuxPty.this.session);
                    }
                });
            }
        };
    }

    public static void setUiListener(Listener listener) {
        uiListener = listener;
    }

    public static TermuxPty start(Context context, final ShellSession session, TerminalTheme theme) {
        final TermuxPty pty = new TermuxPty(context, session, theme);
        pty.launch();
        return pty;
    }

    private void launch() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doLaunch();
                } catch (final Exception e) {
                    final String message = e.getMessage() == null ? "Termux 启动失败" : e.getMessage();
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            session.append("[启动失败] " + message + "\n", 5000);
                            Listener l = uiListener;
                            if (l != null) l.onError(session, message);
                        }
                    });
                }
            }
        }, "zrinfile-termux-launch").start();
    }

    private void doLaunch() throws Exception {
        if (!TermuxPaths.installed(context)) throw new Exception("Termux 环境尚未安装");
        File usr = TermuxPaths.usr(context);
        File home = TermuxPaths.home(context);
        File cache = new File(context.getCacheDir(), "proot-tmp");
        if (!cache.exists()) cache.mkdirs();

        String guestPrefix = TermuxPaths.GUEST_PREFIX;
        String guestHome = TermuxPaths.GUEST_HOME;

        List<String> args = new ArrayList<>();
        args.add("proot");
        args.add("--link2symlink");
        args.add("--kill-on-exit");
        args.add("-r");
        args.add("/");
        addBind(args, usr.getAbsolutePath(), guestPrefix);
        addBind(args, home.getAbsolutePath(), guestHome);
        addBindIfExists(args, "/dev");
        addBindIfExists(args, "/proc");
        addBindIfExists(args, "/sys");
        addBindIfExists(args, "/system");
        addBindIfExists(args, "/vendor");
        addBindIfExists(args, "/product");
        addBindIfExists(args, "/data");
        addBindIfExists(args, "/storage");
        addBindIfExists(args, "/sdcard");
        args.add("-w");
        args.add(guestHome);
        args.add(guestPrefix + "/bin/bash");
        args.add("-l");

        String[] env = new String[]{
                "HOME=" + guestHome,
                "PREFIX=" + guestPrefix,
                "TERMUX__PREFIX=" + guestPrefix,
                "TERMUX__HOME=" + guestHome,
                "TERMUX_APP__PACKAGE_NAME=" + context.getPackageName(),
                "PATH=" + guestPrefix + "/bin:/system/bin:/system/xbin",
                "LD_LIBRARY_PATH=" + usr.getAbsolutePath() + "/lib:" + guestPrefix + "/lib",
                "TMPDIR=" + guestPrefix + "/tmp",
                "LANG=en_US.UTF-8",
                "TERM=xterm-256color",
                "SHELL=" + guestPrefix + "/bin/bash",
                "PROOT_LOADER=" + TermuxPaths.loader(context).getAbsolutePath(),
                "PROOT_TMP_DIR=" + cache.getAbsolutePath(),
                "PROOT_NO_SECCOMP=1"
        };

        int[] result = Pty.start(TermuxPaths.proot(context).getAbsolutePath(),
                args.toArray(new String[0]), env, home.getAbsolutePath(), 100, 300);
        if (result == null) throw new Exception("无法创建终端进程");
        fd = result[0];
        pid = result[1];
        pfd = ParcelFileDescriptor.adoptFd(fd);
        in = new FileInputStream(pfd.getFileDescriptor());
        out = new FileOutputStream(pfd.getFileDescriptor());
        session.pty = this;
        session.running = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                readLoop();
            }
        }, "zrinfile-termux-reader").start();
        scheduleIdleFlush();
    }

    private static void addBind(List<String> args, String source, String target) {
        args.add("-b");
        args.add(source + ":" + target);
    }

    private static void addBindIfExists(List<String> args, String path) {
        if (new File(path).exists()) {
            args.add("-b");
            args.add(path);
        }
    }

    private void readLoop() {
        InputStreamReader reader = null;
        try {
            reader = new InputStreamReader(in, "UTF-8");
            char[] buf = new char[4096];
            int n;
            while (!closed && (n = reader.read(buf)) > 0) {
                lastRead = System.currentTimeMillis();
                stream.feed(new String(buf, 0, n), sink);
            }
        } catch (final Exception ignored) {
        } finally {
            FileUtils.closeQuietly(reader);
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    session.running = false;
                    Listener l = uiListener;
                    if (!closed && l != null) l.onExit(session);
                }
            });
        }
    }

    private void scheduleIdleFlush() {
        MAIN.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (closed) return;
                if (session.running && System.currentTimeMillis() - lastRead > 450) {
                    stream.flushPartial(sink);
                }
                MAIN.postDelayed(this, 400);
            }
        }, 400);
    }

    public void write(String text) {
        if (out == null || closed) return;
        try {
            out.write(text.getBytes("UTF-8"));
            out.flush();
        } catch (Exception ignored) {
        }
    }

    public void resize(int cols, int rows) {
        if (fd >= 0) {
            try {
                Pty.resize(fd, cols, rows);
            } catch (Exception ignored) {
            }
        }
    }

    public void close() {
        closed = true;
        try {
            if (pid > 0) Pty.killProcess(pid);
        } catch (Exception ignored) {
        }
        FileUtils.closeQuietly(in);
        FileUtils.closeQuietly(out);
        try {
            if (pfd != null) pfd.close();
        } catch (Exception ignored) {
        }
        session.running = false;
        if (session.pty == this) session.pty = null;
    }

    /** 逐行输出解析：颜色、CRLF、回车覆盖、退格、清行。 */
    static final class Stream {

        interface Sink {
            void onSubmit(CharSequence line);

            void onClearScreen();
        }

        private final TerminalTheme theme;
        private final StringBuilder text = new StringBuilder();
        private final List<int[]> runs = new ArrayList<>();
        private int color;
        private boolean bold;
        private int state;
        private boolean oscEsc;
        private boolean pendingCr;
        private boolean lastEmpty;
        private final StringBuilder seq = new StringBuilder();

        Stream(TerminalTheme theme) {
            this.theme = theme;
            this.color = theme.text;
        }

        synchronized void feed(String chunk, Sink sink) {
            for (int i = 0; i < chunk.length(); i++) {
                char c = chunk.charAt(i);
                if (pendingCr) {
                    pendingCr = false;
                    if (c == '\n') {
                        submit(sink);
                        continue;
                    }
                    text.setLength(0);
                    runs.clear();
                    lastEmpty = false;
                }
                if (state == 1) {
                    if (c == '[') {
                        state = 2;
                        seq.setLength(0);
                    } else if (c == ']') {
                        state = 3;
                        oscEsc = false;
                    } else {
                        state = 0;
                    }
                    continue;
                }
                if (state == 2) {
                    if (c >= 0x40 && c <= 0x7E) {
                        handleCsi(seq.toString(), c, sink);
                        state = 0;
                    } else {
                        seq.append(c);
                        if (seq.length() > 64) {
                            state = 0;
                            seq.setLength(0);
                        }
                    }
                    continue;
                }
                if (state == 3) {
                    if (c == 0x07) {
                        state = 0;
                    } else if (c == 0x1B) {
                        oscEsc = true;
                    } else if (oscEsc && c == '\\') {
                        state = 0;
                        oscEsc = false;
                    } else {
                        oscEsc = false;
                    }
                    continue;
                }
                if (c == 0x1B) {
                    state = 1;
                } else if (c == '\r') {
                    pendingCr = true;
                } else if (c == '\n') {
                    submit(sink);
                } else if (c == '\b') {
                    if (text.length() > 0) {
                        text.setLength(text.length() - 1);
                        trimRuns(text.length());
                    }
                } else if (c == '\t') {
                    append("    ");
                } else if (c >= 0x20) {
                    append(String.valueOf(c));
                }
            }
        }

        synchronized boolean hasPending() {
            return pendingCr || text.length() > 0;
        }

        synchronized void flushPartial(Sink sink) {
            if (pendingCr) {
                pendingCr = false;
                text.setLength(0);
                runs.clear();
                return;
            }
            if (text.length() > 0 && text.length() < 400) {
                submit(sink);
            }
        }

        private void handleCsi(String body, char finalChar, Sink sink) {
            if (finalChar == 'm') {
                applySgr(body);
            } else if (finalChar == 'K') {
                text.setLength(0);
                runs.clear();
            } else if (finalChar == 'J') {
                if ("2".equals(body) || "3".equals(body) || body.length() == 0) {
                    text.setLength(0);
                    runs.clear();
                    sink.onClearScreen();
                }
            }
        }

        private void applySgr(String body) {
            if (body.length() == 0) {
                resetStyle();
                return;
            }
            String[] parts = body.split(";");
            for (int k = 0; k < parts.length; k++) {
                int code;
                try {
                    code = Integer.parseInt(parts[k]);
                } catch (Exception e) {
                    continue;
                }
                if (code == 0) {
                    resetStyle();
                } else if (code == 1) {
                    bold = true;
                } else if (code == 22) {
                    bold = false;
                } else if (code == 39) {
                    color = theme.text;
                } else if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) {
                    color = theme.colorForSgr(code);
                } else if (code == 38 || code == 48) {
                    if (k + 1 < parts.length) {
                        int mode;
                        try {
                            mode = Integer.parseInt(parts[k + 1]);
                        } catch (Exception e) {
                            mode = -1;
                        }
                        if (mode == 5) k += 2;
                        else if (mode == 2) k += 4;
                    }
                }
            }
        }

        private void resetStyle() {
            color = theme.text;
            bold = false;
        }

        private void append(String s) {
            int start = text.length();
            text.append(s);
            if (runs.isEmpty()) {
                runs.add(new int[]{start, text.length(), color, bold ? 1 : 0});
                return;
            }
            int[] last = runs.get(runs.size() - 1);
            if (last[2] == color && (last[3] == 1) == bold) {
                last[1] = text.length();
            } else {
                runs.add(new int[]{start, text.length(), color, bold ? 1 : 0});
            }
        }

        private void trimRuns(int len) {
            while (!runs.isEmpty()) {
                int[] last = runs.get(runs.size() - 1);
                if (last[0] >= len) {
                    runs.remove(runs.size() - 1);
                } else {
                    if (last[1] > len) last[1] = len;
                    break;
                }
            }
        }

        private void submit(Sink sink) {
            if (text.length() == 0) {
                if (lastEmpty) return;
                lastEmpty = true;
                sink.onSubmit("\n");
                return;
            }
            lastEmpty = false;
            SpannableStringBuilder sb = new SpannableStringBuilder(text);
            for (int[] run : runs) {
                int start = Math.min(run[0], sb.length());
                int end = Math.min(run[1], sb.length());
                if (start >= end) continue;
                sb.setSpan(new ForegroundColorSpan(run[2]), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                if (run[3] == 1) {
                    sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
            sink.onSubmit(sb.append("\n"));
            text.setLength(0);
            runs.clear();
        }
    }
}
