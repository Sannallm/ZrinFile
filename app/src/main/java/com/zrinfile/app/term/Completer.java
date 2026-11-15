package com.zrinfile.app.term;

import android.content.Context;

import com.zrinfile.app.util.Prefs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 命令与路径补全。内置命令条目超过百条，可按需扩展。 */
public final class Completer {

    public static class Item {
        public String value;
        public String display;
        public String desc;

        public Item(String value, String display, String desc) {
            this.value = value;
            this.display = display;
            this.desc = desc;
        }
    }

    public static class Command {
        public final String name;
        public final String desc;

        public Command(String name, String desc) {
            this.name = name;
            this.desc = desc;
        }
    }

    private static final List<Command> COMMANDS = new ArrayList<>();

    static {
        add("ls", "列出目录内容");
        add("cd", "切换目录");
        add("pwd", "显示当前目录");
        add("cat", "输出文件内容");
        add("tac", "反向输出文件内容");
        add("echo", "输出文本");
        add("printf", "格式化输出");
        add("touch", "创建空文件或更新时间");
        add("mkdir", "新建目录");
        add("rmdir", "删除空目录");
        add("rm", "删除文件或目录");
        add("cp", "复制文件");
        add("mv", "移动或重命名");
        add("ln", "创建链接");
        add("chmod", "修改权限");
        add("chown", "修改所有者");
        add("chgrp", "修改用户组");
        add("stat", "显示文件状态");
        add("file", "识别文件类型");
        add("find", "查找文件");
        add("grep", "文本搜索");
        add("egrep", "扩展正则搜索");
        add("fgrep", "固定字符串搜索");
        add("sed", "流式文本编辑");
        add("awk", "文本处理语言");
        add("sort", "排序");
        add("uniq", "去重");
        add("cut", "截取列");
        add("tr", "字符转换");
        add("head", "显示文件头部");
        add("tail", "显示文件尾部");
        add("less", "分页查看");
        add("more", "分页查看");
        add("wc", "统计行数/字数");
        add("diff", "比较文件");
        add("cmp", "逐字节比较");
        add("patch", "应用补丁");
        add("tar", "打包/解包");
        add("gzip", "gzip 压缩");
        add("gunzip", "gzip 解压");
        add("bzip2", "bzip2 压缩");
        add("xz", "xz 压缩");
        add("zip", "zip 压缩");
        add("unzip", "zip 解压");
        add("base64", "Base64 编解码");
        add("md5sum", "MD5 校验");
        add("sha1sum", "SHA1 校验");
        add("sha256sum", "SHA256 校验");
        add("xxd", "十六进制查看");
        add("hexdump", "十六进制转储");
        add("od", "八进制转储");
        add("strings", "提取可打印字符串");
        add("curl", "网络请求");
        add("wget", "下载文件");
        add("ping", "网络连通测试");
        add("ip", "网络配置");
        add("ifconfig", "查看网卡");
        add("netstat", "网络连接状态");
        add("ss", "套接字统计");
        add("nc", "TCP/UDP 工具");
        add("nslookup", "DNS 查询");
        add("dig", "DNS 查询");
        add("telnet", "远程登录");
        add("ssh", "SSH 客户端");
        add("scp", "安全复制");
        add("rsync", "增量同步");
        add("ftp", "FTP 客户端");
        add("uname", "系统信息");
        add("whoami", "当前用户");
        add("id", "用户与组信息");
        add("groups", "所属用户组");
        add("hostname", "主机名");
        add("ps", "进程列表");
        add("top", "实时进程");
        add("kill", "结束进程");
        add("killall", "按名称结束进程");
        add("pkill", "按模式结束进程");
        add("pgrep", "查找进程");
        add("jobs", "后台任务");
        add("fg", "前台任务");
        add("bg", "后台任务");
        add("nohup", "忽略挂断运行");
        add("free", "内存使用");
        add("df", "磁盘空间");
        add("du", "目录占用");
        add("mount", "挂载信息");
        add("umount", "卸载");
        add("dmesg", "内核日志");
        add("logcat", "Android 日志");
        add("getprop", "读取系统属性");
        add("setprop", "设置系统属性");
        add("settings", "系统设置读写");
        add("am", "Activity 管理器");
        add("pm", "包管理器");
        add("cmd", "系统服务命令");
        add("input", "模拟输入事件");
        add("screencap", "截屏");
        add("screenrecord", "录屏");
        add("wm", "窗口管理");
        add("svc", "电源/数据开关");
        add("dumpsys", "系统服务信息");
        add("date", "日期时间");
        add("cal", "日历");
        add("uptime", "运行时长");
        add("sleep", "延迟");
        add("timeout", "超时执行");
        add("watch", "周期执行");
        add("env", "环境变量");
        add("export", "设置环境变量");
        add("unset", "删除环境变量");
        add("alias", "设置别名");
        add("unalias", "删除别名");
        add("history", "命令历史");
        add("clear", "清屏");
        add("exit", "退出会话");
        add("sh", "Shell");
        add("su", "Root Shell");
        add("sudo", "提权执行");
        add("which", "命令位置");
        add("whereis", "查找命令");
        add("type", "命令类型");
        add("command", "执行命令");
        add("source", "加载脚本");
        add("eval", "求值执行");
        add("exec", "替换进程");
        add("test", "条件测试");
        add("true", "返回真");
        add("false", "返回假");
        add("yes", "重复输出");
        add("seq", "生成序列");
        add("sleep", "延迟");
        add("basename", "取文件名");
        add("dirname", "取目录名");
        add("realpath", "绝对路径");
        add("readlink", "读取链接");
        add("mktemp", "创建临时文件");
        add("lsof", "打开的文件");
        add("strace", "系统调用跟踪");
        add("tcpdump", "抓包（需 Root）");
        add("git", "版本控制");
        add("python", "Python 解释器");
        add("python3", "Python 3");
        add("node", "Node.js");
        add("npm", "Node 包管理");
        add("clang", "C/C++ 编译器");
        add("gcc", "GCC 编译器");
        add("make", "构建工具");
        add("cmake", "跨平台构建");
        add("vim", "Vim 编辑器");
        add("vi", "vi 编辑器");
        add("nano", "Nano 编辑器");
        add("emacs", "Emacs 编辑器");
        add("less", "分页查看");
        add("man", "命令手册");
        add("info", "GNU 文档");
        add("apt", "包管理");
        add("apt-get", "包管理");
        add("pkg", "Termux 包管理");
        add("dpkg", "Deb 包管理");
        add("termux-setup-storage", "申请存储访问");
        add("termux-info", "Termux 环境信息");
        add("termux-open", "调用系统打开文件");
        add("termux-open-url", "打开链接");
        add("termux-change-repo", "切换软件源");
        add("termux-reload-settings", "重载设置");
        add("termux-wake-lock", "保持唤醒");
        add("termux-wake-unlock", "解除唤醒");
        add("termux-battery-status", "电池状态");
        add("termux-camera-photo", "调用相机拍照");
        add("termux-clipboard-get", "读取剪贴板");
        add("termux-clipboard-set", "写入剪贴板");
        add("termux-notification", "发送通知");
        add("termux-toast", "显示 Toast");
        add("termux-vibrate", "震动");
        add("termux-tts-speak", "语音播报");
        add("termux-tts-engines", "语音引擎");
        add("termux-microphone-record", "录音");
        add("termux-location", "获取位置");
        add("termux-sensor", "读取传感器");
        add("termux-wifi-connectioninfo", "WiFi 信息");
        add("termux-wifi-scaninfo", "WiFi 扫描");
        add("termux-fix-shebang", "修复脚本解释器");
        add("zrin", "文件管理器桥接命令");
        add("fm", "zrin 的简写");
        add("help", "查看帮助");
    }

    private static void add(String name, String desc) {
        COMMANDS.add(new Command(name, desc));
    }

    private static final String[] PATH_COMMANDS = {
            "cd", "ls", "cat", "less", "more", "head", "tail", "cp", "mv", "rm", "mkdir",
            "touch", "stat", "file", "find", "du", "chmod", "chown", "unzip", "zip", "zrin", "fm", "open", "info"
    };

    private static final String[] ZRIN_SUB = {
            "ls", "mkdir", "touch", "rm", "open", "info", "find", "search", "zip", "unzip",
            "df", "trash", "theme", "bookmark", "settings", "help"
    };

    public static List<Item> complete(Context ctx, ShellSession session, String input, int max) {
        List<Item> out = new ArrayList<>();
        if (input == null) return out;
        String head = "";
        String token = input;
        int lastSpace = input.lastIndexOf(' ');
        if (lastSpace >= 0) {
            head = input.substring(0, lastSpace + 1);
            token = input.substring(lastSpace + 1);
        }
        String prev = lastSpace > 0 ? input.substring(0, lastSpace).trim() : "";
        String prevFirst = prev.length() > 0 ? prev.split("\\s+")[0] : "";

        String unquoted = token;
        if (unquoted.startsWith("\"") || unquoted.startsWith("'")) unquoted = unquoted.substring(1);

        boolean zrinSub = ("zrin".equals(prevFirst) || "fm".equals(prevFirst)) && !prev.contains(" ");
        if (zrinSub) {
            for (String s : ZRIN_SUB) {
                if (s.startsWith(unquoted)) {
                    out.add(new Item(head + s + " ", head + s, "文件管理器桥接 · " + s));
                    if (out.size() >= max) break;
                }
            }
            return out;
        }

        boolean pathMode = isPathCommand(prevFirst) || unquoted.contains("/") || unquoted.startsWith(".")
                || unquoted.startsWith("~") || prevFirst.length() == 0;
        if (pathMode && (unquoted.contains("/") || prevFirst.length() > 0)) {
            addPathItems(ctx, session, head, unquoted, out, max);
        }
        if (!unquoted.contains("/") && !unquoted.startsWith(".")) {
            for (Command c : COMMANDS) {
                if (c.name.startsWith(unquoted)) {
                    out.add(new Item(head + c.name + " ", head + c.name, c.desc));
                    if (out.size() >= max) break;
                }
            }
        }
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                boolean da = a.value.endsWith("/");
                boolean db = b.value.endsWith("/");
                if (da != db) return da ? 1 : -1;
                return a.display.compareToIgnoreCase(b.display);
            }
        });
        return out;
    }

    private static boolean isPathCommand(String cmd) {
        for (String p : PATH_COMMANDS) if (p.equals(cmd)) return true;
        return false;
    }

    private static void addPathItems(Context ctx, ShellSession session, String head, String token,
                                     List<Item> out, int max) {
        String dirPart = "";
        String prefix = token;
        int slash = token.lastIndexOf('/');
        if (slash >= 0) {
            dirPart = token.substring(0, slash + 1);
            prefix = token.substring(slash + 1);
        }
        String base = session.cwd;
        String expanded = dirPart;
        if (dirPart.startsWith("~")) {
            expanded = com.zrinfile.app.App.homeDir().getAbsolutePath()
                    + dirPart.substring(1);
            base = "";
        }
        File dir;
        if (expanded.startsWith("/")) dir = new File(expanded);
        else dir = new File(base, expanded);
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            files = null;
        }
        if (files == null) return;
        List<File> sorted = new ArrayList<>();
        for (File f : files) {
            if (!Prefs.showHidden(ctx) && f.getName().startsWith(".")) continue;
            if (f.getName().toLowerCase(Locale.US).startsWith(prefix.toLowerCase(Locale.US))) sorted.add(f);
        }
        Collections.sort(sorted, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        for (File f : sorted) {
            String insert = head + dirPart + f.getName() + (f.isDirectory() ? "/" : " ");
            String display = dirPart + f.getName() + (f.isDirectory() ? "/" : "");
            String desc = f.isDirectory() ? "目录" : com.zrinfile.app.fs.FileUtils.formatSize(f.length()) + " · 文件";
            out.add(new Item(insert, display, desc));
            if (out.size() >= max) break;
        }
    }
}
