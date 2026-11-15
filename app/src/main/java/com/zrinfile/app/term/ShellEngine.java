package com.zrinfile.app.term;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;

import com.zrinfile.app.App;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.fs.RootShell;
import com.zrinfile.app.fs.TrashManager;
import com.zrinfile.app.util.Prefs;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** 命令执行引擎：真实 /system/bin/sh + 文件管理器桥接命令。 */
public final class ShellEngine {

    public interface Listener {
        void onOutput(ShellSession session, CharSequence text);

        void onClear(ShellSession session);

        void onExit(ShellSession session);

        void onOpenPath(String path);

        void onOpenAppSettings();

        void onToast(String message);

        void onFinished(ShellSession session);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private ShellEngine() {
    }

    public static void execute(final Context context, final ShellSession session, final String rawCommand,
                               final Listener listener) {
        final String command = rawCommand == null ? "" : rawCommand.trim();
        if (command.length() == 0) {
            listener.onFinished(session);
            return;
        }
        session.running = true;
        session.cancelRequested = false;
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    runCommand(context, session, command, listener);
                } catch (Exception e) {
                    emit(session, "错误: " + e.getMessage() + "\n", listener, true);
                } finally {
                    session.running = false;
                    session.process = null;
                    MAIN.post(new Runnable() {
                        @Override
                        public void run() {
                            listener.onFinished(session);
                        }
                    });
                }
            }
        });
    }

    private static void runCommand(Context ctx, ShellSession s, String command, Listener l) {
        String[] args = splitArgs(command);
        if (args.length == 0) return;
        String head = args[0];
        if ("cd".equals(head)) {
            doCd(s, args, l);
            return;
        }
        if ("pwd".equals(head) && args.length == 1) {
            emit(s, s.cwd + "\n", l, false);
            return;
        }
        if ("clear".equals(head) && args.length == 1) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    l.onClear(s);
                }
            });
            return;
        }
        if ("exit".equals(head) || "logout".equals(head)) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    l.onExit(s);
                }
            });
            return;
        }
        if ("help".equals(head) && args.length == 1) {
            printHelp(ctx, s, l);
            return;
        }
        if ("zrin".equals(head) || "fm".equals(head)) {
            bridge(ctx, s, args, l);
            return;
        }
        runShell(ctx, s, command, l);
    }

    private static void doCd(ShellSession s, String[] args, Listener l) {
        String target;
        if (args.length < 2) {
            target = App.homeDir().getAbsolutePath();
        } else {
            target = args[1];
            if ("-".equals(target)) {
                target = s.lastCwd != null ? s.lastCwd : s.cwd;
            }
        }
        target = expand(s, target);
        File f = new File(target);
        if (!f.isAbsolute()) f = new File(s.cwd, target);
        String path = f.getAbsolutePath();
        boolean ok;
        try {
            ok = f.exists() && f.isDirectory();
        } catch (Exception e) {
            ok = false;
        }
        if (!ok && RootShell.available()) {
            ok = RootShell.exec("test -d " + RootShell.quote(path) + " && echo yes").contains("yes");
        }
        if (!ok) {
            emit(s, "cd: " + target + ": 不是目录\n", l, true);
            return;
        }
        s.lastCwd = s.cwd;
        s.cwd = path;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                // 提示符由界面刷新
            }
        });
    }

    private static void runShell(Context ctx, ShellSession s, String command, Listener l) {
        Process p = null;
        BufferedReader reader = null;
        try {
            ProcessBuilder pb = new ProcessBuilder("/system/bin/sh", "-c", command);
            File dir = new File(s.cwd);
            if (dir.exists() && dir.isDirectory()) pb.directory(dir);
            pb.redirectErrorStream(true);
            pb.environment().put("HOME", App.homeDir().getAbsolutePath());
            pb.environment().put("TMPDIR", ctx.getCacheDir().getAbsolutePath());
            pb.environment().put("PATH",
                    "/sbin:/system/sbin:/system/bin:/system/xbin:/vendor/bin:/system_ext/bin:/product/bin");
            pb.environment().put("TERM", "xterm-256color");
            pb.environment().put("LANG", "en_US.UTF-8");
            p = pb.start();
            s.process = p;
            reader = new BufferedReader(new InputStreamReader(p.getInputStream()), 8192);
            StringBuilder line = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                if (s.cancelRequested) break;
                char c = (char) ch;
                if (c == '\n') {
                    final String text = line.toString();
                    emit(s, text + "\n", l, false);
                    line.setLength(0);
                } else {
                    line.append(c);
                    if (line.length() > 8192) {
                        final String text = line.toString();
                        emit(s, text, l, false);
                        line.setLength(0);
                    }
                }
            }
            if (line.length() > 0 && !s.cancelRequested) {
                emit(s, line.toString() + "\n", l, false);
            }
            if (s.cancelRequested) {
                emit(s, "^C\n", l, true);
            }
        } catch (Exception e) {
            emit(s, "无法执行: " + e.getMessage() + "\n", l, true);
        } finally {
            try {
                if (reader != null) reader.close();
            } catch (Exception ignored) {
            }
            if (p != null) {
                try {
                    p.destroy();
                } catch (Exception ignored) {
                }
            }
            s.process = null;
        }
    }

    private static void bridge(Context ctx, ShellSession s, String[] args, Listener l) {
        if (args.length < 2) {
            printBridgeHelp(s, l);
            return;
        }
        String sub = args[1];
        if ("help".equals(sub)) {
            printBridgeHelp(s, l);
            return;
        }
        if ("ls".equals(sub)) {
            String path = args.length >= 3 ? expand(s, args[2]) : s.cwd;
            File dir = new File(path);
            if (!dir.isAbsolute()) dir = new File(s.cwd, dir.getPath());
            List<FileItem> items = FileUtils.list(dir, Prefs.showHidden(ctx));
            FileUtils.sort(items, Prefs.sortBy(ctx), Prefs.sortAsc(ctx), Prefs.foldersFirst(ctx));
            StringBuilder sb = new StringBuilder();
            for (FileItem i : items) {
                if (i.isDir) sb.append("  d  ").append(i.name).append("/\n");
                else sb.append("  -  ").append(FileUtils.formatSize(i.size)).append('\t').append(i.name).append('\n');
            }
            if (items.isEmpty()) sb.append("（空目录）\n");
            emit(s, sb.toString(), l, false);
            return;
        }
        if ("mkdir".equals(sub)) {
            if (args.length < 3) {
                emit(s, "用法: zrin mkdir <路径>\n", l, true);
                return;
            }
            File f = new File(expand(s, args[2]));
            if (!f.isAbsolute()) f = new File(s.cwd, f.getPath());
            boolean ok = f.mkdirs();
            if (!ok && RootShell.available()) ok = RootShell.mkdir(f.getAbsolutePath());
            emit(s, ok ? "已创建目录 " + f.getAbsolutePath() + "\n" : "创建失败: " + f.getAbsolutePath() + "\n", l, !ok);
            return;
        }
        if ("touch".equals(sub)) {
            if (args.length < 3) {
                emit(s, "用法: zrin touch <文件>\n", l, true);
                return;
            }
            File f = new File(expand(s, args[2]));
            if (!f.isAbsolute()) f = new File(s.cwd, f.getPath());
            try {
                File parent = f.getParentFile();
                if (parent != null) parent.mkdirs();
                boolean ok = f.exists() || f.createNewFile();
                emit(s, ok ? "已创建文件 " + f.getAbsolutePath() + "\n" : "创建失败\n", l, !ok);
            } catch (Exception e) {
                emit(s, "创建失败: " + e.getMessage() + "\n", l, true);
            }
            return;
        }
        if ("rm".equals(sub)) {
            if (args.length < 3) {
                emit(s, "用法: zrin rm <路径>（进入回收站）\n", l, true);
                return;
            }
            File f = new File(expand(s, args[2]));
            if (!f.isAbsolute()) f = new File(s.cwd, f.getPath());
            if (!f.exists()) {
                emit(s, "不存在: " + f.getPath() + "\n", l, true);
                return;
            }
            List<FileItem> list = new ArrayList<>();
            list.add(FileItem.fromFile(f));
            boolean ok = TrashManager.moveToTrash(ctx, list);
            emit(s, ok ? "已移入回收站: " + f.getName() + "\n" : "删除失败\n", l, !ok);
            return;
        }
        if ("open".equals(sub)) {
            if (args.length < 3) {
                emit(s, "用法: zrin open <文件>\n", l, true);
                return;
            }
            final File f = new File(expand(s, args[2]));
            if (!f.isAbsolute()) {
                emit(s, "请使用绝对路径\n", l, true);
                return;
            }
            if (!f.exists()) {
                emit(s, "不存在: " + f.getAbsolutePath() + "\n", l, true);
                return;
            }
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    l.onOpenPath(f.getAbsolutePath());
                }
            });
            emit(s, "打开 " + f.getName() + "\n", l, false);
            return;
        }
        if ("info".equals(sub)) {
            if (args.length < 3) {
                emit(s, "用法: zrin info <路径>\n", l, true);
                return;
            }
            File f = new File(expand(s, args[2]));
            if (!f.isAbsolute()) f = new File(s.cwd, f.getPath());
            if (!f.exists()) {
                emit(s, "不存在: " + f.getPath() + "\n", l, true);
                return;
            }
            long size = f.isDirectory() ? FileUtils.sizeOf(f) : f.length();
            int count = f.isDirectory() ? FileUtils.countFiles(f) : 1;
            String sb = "路径: " + f.getAbsolutePath() + "\n"
                    + "类型: " + (f.isDirectory() ? "目录" : "文件") + "\n"
                    + "大小: " + FileUtils.formatSize(size) + "\n"
                    + "文件数: " + count + "\n"
                    + "修改: " + FileUtils.formatDate(f.lastModified()) + "\n"
                    + "权限: " + FileUtils.readPermission(f) + "\n";
            emit(s, sb, l, false);
            return;
        }
        if ("find".equals(sub) || "search".equals(sub)) {
            if (args.length < 3) {
                emit(s, "用法: zrin find <关键字>\n", l, true);
                return;
            }
            final String q = args[2];
            final ShellSession session = s;
            emit(s, "搜索 " + q + " …\n", l, false);
            final List<String> found = new ArrayList<>();
            FileUtils.search(new File(s.cwd), q, Prefs.showHidden(ctx), new FileUtils.Cancel() {
                @Override
                public boolean isCancelled() {
                    return session.cancelRequested;
                }
            }, new FileUtils.FileVisitor() {
                @Override
                public void onFile(FileItem item) {
                    found.add(item.path);
                }
            }, 200);
            StringBuilder sb = new StringBuilder();
            for (String p : found) sb.append(p).append('\n');
            if (found.isEmpty()) sb.append("没有匹配结果\n");
            emit(s, sb.toString(), l, false);
            return;
        }
        if ("df".equals(sub)) {
            String path = args.length >= 3 ? expand(s, args[2]) : s.cwd;
            long free = FileUtils.availableSpace(path);
            long total = FileUtils.totalSpace(path);
            emit(s, "路径: " + path + "\n总计: " + FileUtils.formatSize(total)
                    + "\n可用: " + FileUtils.formatSize(free) + "\n", l, false);
            return;
        }
        if ("trash".equals(sub)) {
            if (args.length >= 3 && "empty".equals(args[2])) {
                TrashManager.empty(ctx);
                emit(s, "回收站已清空\n", l, false);
            } else {
                List<TrashManager.Entry> entries = TrashManager.list(ctx);
                StringBuilder sb = new StringBuilder("回收站项目: " + entries.size()
                        + "，占用 " + FileUtils.formatSize(TrashManager.totalSize(ctx)) + "\n");
                for (int i = 0; i < Math.min(entries.size(), 30); i++) {
                    TrashManager.Entry e = entries.get(i);
                    sb.append("  ").append(e.name).append("  ").append(FileUtils.formatSize(e.size)).append('\n');
                }
                emit(s, sb.toString(), l, false);
            }
            return;
        }
        if ("zip".equals(sub)) {
            if (args.length < 4) {
                emit(s, "用法: zrin zip <输出.zip> <文件或目录…>\n", l, true);
                return;
            }
            File out = new File(expand(s, args[2]));
            if (!out.isAbsolute()) out = new File(s.cwd, out.getPath());
            List<FileItem> items = new ArrayList<>();
            for (int i = 3; i < args.length; i++) {
                File f = new File(expand(s, args[i]));
                if (!f.isAbsolute()) f = new File(s.cwd, f.getPath());
                if (f.exists()) items.add(FileItem.fromFile(f));
            }
            if (items.isEmpty()) {
                emit(s, "没有可压缩的项目\n", l, true);
                return;
            }
            final ShellSession session = s;
            boolean ok = FileUtils.zip(items, out, new FileUtils.Cancel() {
                @Override
                public boolean isCancelled() {
                    return session.cancelRequested;
                }
            }, null);
            emit(s, ok ? "已压缩到 " + out.getAbsolutePath() + "\n" : "压缩失败\n", l, !ok);
            return;
        }
        if ("unzip".equals(sub)) {
            if (args.length < 3) {
                emit(s, "用法: zrin unzip <压缩包.zip> [目标目录]\n", l, true);
                return;
            }
            File zip = new File(expand(s, args[2]));
            if (!zip.isAbsolute()) zip = new File(s.cwd, zip.getPath());
            if (!zip.exists()) {
                emit(s, "不存在: " + zip.getPath() + "\n", l, true);
                return;
            }
            File dest = args.length >= 4 ? new File(expand(s, args[3])) : new File(s.cwd, zip.getName().replace(".zip", ""));
            if (!dest.isAbsolute()) dest = new File(s.cwd, dest.getPath());
            final ShellSession session = s;
            int n = FileUtils.unzip(zip, dest, new FileUtils.Cancel() {
                @Override
                public boolean isCancelled() {
                    return session.cancelRequested;
                }
            }, null);
            emit(s, "已解压 " + n + " 个文件到 " + dest.getAbsolutePath() + "\n", l, n == 0);
            return;
        }
        if ("bookmark".equals(sub)) {
            String path = args.length >= 3 ? expand(s, args[2]) : s.cwd;
            Prefs.addBookmark(ctx, path);
            emit(s, "已收藏 " + path + "\n", l, false);
            return;
        }
        if ("theme".equals(sub)) {
            if (args.length >= 3) {
                String v = args[2];
                if ("dark".equals(v)) Prefs.setTheme(ctx, Prefs.THEME_DARK);
                else if ("light".equals(v)) Prefs.setTheme(ctx, Prefs.THEME_LIGHT);
                else Prefs.setTheme(ctx, Prefs.THEME_SYSTEM);
                App.get().applyTheme();
                emit(s, "主题已切换\n", l, false);
            } else {
                emit(s, "用法: zrin theme dark|light|system\n", l, true);
            }
            return;
        }
        if ("settings".equals(sub)) {
            MAIN.post(new Runnable() {
                @Override
                public void run() {
                    l.onOpenAppSettings();
                }
            });
            return;
        }
        emit(s, "未知命令: zrin " + sub + "\n", l, true);
    }

    private static void printBridgeHelp(ShellSession s, Listener l) {
        String text = "文件管理桥接命令（zrin / fm 均可）\n"
                + "  zrin ls [目录]              列出目录内容\n"
                + "  zrin mkdir <目录>           新建目录\n"
                + "  zrin touch <文件>           新建空文件\n"
                + "  zrin rm <路径>              移入回收站\n"
                + "  zrin open <文件>            用内置预览打开\n"
                + "  zrin info <路径>            查看大小与权限\n"
                + "  zrin find <关键字>          递归搜索当前目录\n"
                + "  zrin zip <输出.zip> <路径…> 压缩\n"
                + "  zrin unzip <压缩包> [目录]  解压\n"
                + "  zrin df [路径]              查看存储空间\n"
                + "  zrin trash [empty]          查看或清空回收站\n"
                + "  zrin bookmark [路径]        添加收藏\n"
                + "  zrin theme dark|light|system 切换主题\n"
                + "  zrin settings               打开文件管理器设置\n";
        emit(s, text, l, false);
    }

    private static void printHelp(Context ctx, ShellSession s, Listener l) {
        String text;
        if (TerminalPrefs.english(ctx)) {
            text = "ZrinFile Shell — built on Android /system/bin/sh\n"
                    + "  Common: ls cd pwd cat echo touch mkdir rm cp mv ln chmod\n"
                    + "  Text:   grep sed awk sort uniq cut tr head tail wc diff\n"
                    + "  Archive: tar gzip unzip zip\n"
                    + "  Network: ping curl wget netstat ip\n"
                    + "  Device: getprop dumpsys logcat pm am screencap input\n"
                    + "  Bridge: zrin help  — create files, open previews, manage trash\n"
                    + "  clear — clear screen, exit — close session\n";
        } else {
            text = "ZrinFile 命令行 — 基于 Android /system/bin/sh\n"
                    + "  常用: ls cd pwd cat echo touch mkdir rm cp mv ln chmod\n"
                    + "  文本: grep sed awk sort uniq cut tr head tail wc diff\n"
                    + "  压缩: tar gzip unzip zip\n"
                    + "  网络: ping curl wget netstat ip\n"
                    + "  系统: getprop dumpsys logcat pm am screencap input\n"
                    + "  桥接: zrin help — 在文件管理器内建目录、打开预览、管理回收站\n"
                    + "  其他: clear 清屏，exit 关闭当前会话\n";
        }
        emit(s, text, l, false);
    }

    private static String expand(ShellSession s, String arg) {
        if (arg == null) return "";
        String a = arg;
        if (a.startsWith("~/")) {
            a = App.homeDir().getAbsolutePath() + a.substring(1);
        } else if ("~".equals(a)) {
            a = App.homeDir().getAbsolutePath();
        }
        if ((a.startsWith("\"") && a.endsWith("\"")) || (a.startsWith("'") && a.endsWith("'"))) {
            if (a.length() >= 2) a = a.substring(1, a.length() - 1);
        }
        return a;
    }

    public static String[] splitArgs(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean single = false, dbl = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\'' && !dbl) {
                single = !single;
                continue;
            }
            if (c == '"' && !single) {
                dbl = !dbl;
                continue;
            }
            if (c == ' ' && !single && !dbl) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(c);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    private static void emit(final ShellSession s, final String text, final Listener l, final boolean error) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                boolean highlight = TerminalPrefs.highlightErrors(App.get());
                CharSequence cs = Ansi.parse(text, TerminalTheme.of(App.get(), TerminalPrefs.scheme(App.get())),
                        highlight || error);
                l.onOutput(s, cs);
            }
        });
    }

    public static CharSequence colorize(CharSequence text, int color, boolean bold) {
        SpannableStringBuilder sb = new SpannableStringBuilder(text);
        sb.setSpan(new ForegroundColorSpan(color), 0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            sb.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    0, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return sb;
    }
}
