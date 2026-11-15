package com.zrinfile.app.fs;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** 可选的 Root 通道。没有 Root 时全部操作自动退化为普通文件访问。 */
public final class RootShell {

    private static Boolean cached;
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor();

    private RootShell() {
    }

    public static synchronized boolean available() {
        if (cached != null) return cached;
        cached = false;
        try {
            Future<String> f = POOL.submit(new Callable<String>() {
                @Override
                public String call() {
                    return exec("id", 2500);
                }
            });
            String out = f.get(3, TimeUnit.SECONDS);
            cached = out != null && out.contains("uid=0");
        } catch (Exception ignored) {
            cached = false;
        }
        return cached;
    }

    public static void resetCache() {
        cached = null;
    }

    public static String exec(String command) {
        return exec(command, 8000);
    }

    public static String exec(String command, int timeoutMs) {
        Process p = null;
        try {
            p = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            final Process proc = p;
            final StringBuilder sb = new StringBuilder();
            Thread reader = new Thread(new Runnable() {
                @Override
                public void run() {
                    BufferedReader br = null;
                    try {
                        br = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                        String line;
                        while ((line = br.readLine()) != null) {
                            sb.append(line).append('\n');
                        }
                    } catch (Exception ignored) {
                    } finally {
                        try {
                            if (br != null) br.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
            reader.start();
            OutputStream os = p.getOutputStream();
            try {
                os.close();
            } catch (Exception ignored) {
            }
            boolean done = false;
            long deadline = System.currentTimeMillis() + timeoutMs;
            while (System.currentTimeMillis() < deadline) {
                try {
                    p.exitValue();
                    done = true;
                    break;
                } catch (IllegalThreadStateException e) {
                    try {
                        Thread.sleep(40);
                    } catch (InterruptedException ie) {
                        break;
                    }
                }
            }
            if (!done) {
                p.destroy();
                reader.join(300);
                return null;
            }
            reader.join(600);
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (p != null) {
                try {
                    p.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static List<FileItem> list(String path) {
        if (!available()) return Collections.emptyList();
        String esc = quote(path);
        String out = exec("ls -la " + esc);
        List<FileItem> list = new ArrayList<>();
        if (out == null) return list;
        String[] lines = out.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.length() == 0) continue;
            if (line.startsWith("total ")) continue;
            if (line.startsWith("ls:") || line.contains("Permission denied")) continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 7) continue;
            String name = parts[parts.length - 1];
            if (name.equals(".") || name.equals("..")) continue;
            String perms = parts[0];
            if (perms.length() < 10) continue;
            boolean dir = perms.charAt(0) == 'd';
            boolean link = perms.charAt(0) == 'l';
            long size = 0;
            try {
                size = Long.parseLong(parts[4]);
            } catch (Exception ignored) {
            }
            String full = path.endsWith("/") ? path + name : path + "/" + name;
            FileItem item = FileItem.of(full, name, dir, dir ? 0 : size, 0);
            item.symlink = link;
            item.rootOnly = true;
            if (dir) item.size = 0;
            list.add(item);
        }
        return list;
    }

    public static String readSmall(String path, int maxBytes) {
        if (!available()) return null;
        return exec("cat " + quote(path) + " | head -c " + maxBytes);
    }

    public static boolean delete(String path) {
        if (!available()) return false;
        return exec("rm -rf " + quote(path)) != null;
    }

    public static boolean mkdir(String path) {
        if (!available()) return false;
        return exec("mkdir -p " + quote(path)) != null;
    }

    public static boolean rename(String from, String to) {
        if (!available()) return false;
        return exec("mv " + quote(from) + " " + quote(to)) != null;
    }

    public static boolean copy(String from, String to) {
        if (!available()) return false;
        return exec("cp -R " + quote(from) + " " + quote(to)) != null;
    }

    public static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    public static boolean looksRootOnly(String path) {
        String p = path.toLowerCase(Locale.US);
        return p.startsWith("/data/") || p.startsWith("/system/") || p.startsWith("/vendor/")
                || p.startsWith("/product/") || p.startsWith("/apex/") || p.startsWith("/proc/")
                || p.startsWith("/sys/") || p.startsWith("/root/") || p.startsWith("/etc/");
    }
}
