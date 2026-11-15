package com.zrinfile.app.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 轻量 HTTP 文件共享 / 网页服务器。 */
public class HttpFileServer implements FileServer {

    private static final int MAX_UPLOAD = 64 * 1024 * 1024;

    private final ServerConfig config;
    private volatile boolean running;
    private ServerSocket serverSocket;

    public HttpFileServer(ServerConfig config) {
        this.config = config;
    }

    @Override
    public void start() throws Exception {
        running = true;
        serverSocket = new ServerSocket(config.port);
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running) {
                    try {
                        final Socket socket = serverSocket.accept();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                handle(socket);
                            }
                        }, "zrinfile-http-client").start();
                    } catch (Exception e) {
                        if (running) {
                            try {
                                Thread.sleep(120);
                            } catch (InterruptedException ignored) {
                            }
                        }
                    }
                }
            }
        }, "zrinfile-http");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop() {
        running = false;
        try {
            if (serverSocket != null) serverSocket.close();
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void handle(Socket socket) {
        InputStream rawIn = null;
        OutputStream rawOut = null;
        try {
            socket.setSoTimeout(20000);
            rawIn = new BufferedInputStream(socket.getInputStream(), 16384);
            rawOut = new BufferedOutputStream(socket.getOutputStream(), 16384);
            String requestLine = readLine(rawIn);
            if (requestLine == null || requestLine.length() == 0) return;
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) return;
            String method = parts[0];
            String target = parts[1];
            Map<String, String> headers = new HashMap<>();
            String line;
            while ((line = readLine(rawIn)) != null && line.length() > 0) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    headers.put(line.substring(0, idx).trim().toLowerCase(Locale.US),
                            line.substring(idx + 1).trim());
                }
            }
            if (config.auth && !checkAuth(headers.get("authorization"))) {
                writeResponse(rawOut, 401, "text/plain; charset=utf-8", "需要登录".getBytes("UTF-8"),
                        "WWW-Authenticate: Basic realm=\"ZrinFile\"\r\n");
                return;
            }
            String path = target;
            int q = path.indexOf('?');
            if (q >= 0) path = path.substring(0, q);
            path = URLDecoder.decode(path, "UTF-8");
            File root = new File(config.root);
            File file = resolve(root, path);
            if (file == null) {
                writeResponse(rawOut, 403, "text/plain; charset=utf-8", "禁止访问".getBytes("UTF-8"), null);
                return;
            }
            if ("POST".equalsIgnoreCase(method) && ServerConfig.TYPE_HTTP.equals(config.type)) {
                handleUpload(rawIn, rawOut, headers, file.isDirectory() ? file : root);
                return;
            }
            if ("PUT".equalsIgnoreCase(method)) {
                handlePut(rawIn, rawOut, headers, file);
                return;
            }
            if (!file.exists()) {
                writeResponse(rawOut, 404, "text/plain; charset=utf-8", "文件不存在".getBytes("UTF-8"), null);
                return;
            }
            if (file.isDirectory()) {
                if (ServerConfig.TYPE_WEB.equals(config.type)) {
                    File index = new File(file, "index.html");
                    if (!index.isFile()) index = new File(file, "index.htm");
                    if (index.isFile()) {
                        serveFile(rawOut, index, headers);
                        return;
                    }
                }
                byte[] html = listing(root, file, path).getBytes("UTF-8");
                writeResponse(rawOut, 200, "text/html; charset=utf-8", html, null);
                return;
            }
            serveFile(rawOut, file, headers);
        } catch (Exception ignored) {
        } finally {
            try {
                if (rawOut != null) rawOut.flush();
            } catch (Exception ignored) {
            }
            try {
                if (rawIn != null) rawIn.close();
            } catch (Exception ignored) {
            }
            try {
                socket.close();
            } catch (Exception ignored) {
            }
        }
    }

    private boolean checkAuth(String header) {
        if (header == null || !header.toLowerCase(Locale.US).startsWith("basic ")) return false;
        try {
            String decoded = new String(android.util.Base64.decode(header.substring(6).trim(),
                    android.util.Base64.DEFAULT), "UTF-8");
            return decoded.equals(config.user + ":" + config.pass);
        } catch (Exception e) {
            return false;
        }
    }

    private File resolve(File root, String path) {
        try {
            File canonicalRoot = root.getCanonicalFile();
            File f = new File(canonicalRoot, path);
            String canonical = f.getCanonicalPath();
            if (!canonical.equals(canonicalRoot.getPath()) && !canonical.startsWith(canonicalRoot.getPath() + File.separator)) {
                return null;
            }
            return new File(canonical);
        } catch (Exception e) {
            return null;
        }
    }

    private void serveFile(OutputStream out, File file, Map<String, String> headers) throws Exception {
        long length = file.length();
        String mime = mimeOf(file.getName());
        String range = headers.get("range");
        long start = 0;
        long end = length - 1;
        boolean partial = false;
        if (range != null && range.startsWith("bytes=")) {
            try {
                String spec = range.substring(6).trim();
                int dash = spec.indexOf('-');
                if (dash > 0) {
                    start = Long.parseLong(spec.substring(0, dash));
                    String tail = spec.substring(dash + 1).trim();
                    if (tail.length() > 0) end = Long.parseLong(tail);
                } else if (dash == 0) {
                    long tail = Long.parseLong(spec.substring(1));
                    start = Math.max(0, length - tail);
                }
                if (end >= length) end = length - 1;
                partial = start >= 0 && end >= start;
            } catch (Exception e) {
                partial = false;
            }
        }
        if (!partial) {
            start = 0;
            end = length - 1;
        }
        StringBuilder head = new StringBuilder();
        head.append(partial ? "HTTP/1.1 206 Partial Content\r\n" : "HTTP/1.1 200 OK\r\n");
        head.append("Content-Type: ").append(mime).append("\r\n");
        head.append("Content-Length: ").append(end - start + 1).append("\r\n");
        head.append("Accept-Ranges: bytes\r\n");
        if (partial) head.append("Content-Range: bytes ").append(start).append("-").append(end)
                .append("/").append(length).append("\r\n");
        head.append("Connection: close\r\n");
        head.append("Content-Disposition: inline; filename=\"").append(encodeName(file.getName())).append("\"\r\n");
        head.append("\r\n");
        out.write(head.toString().getBytes("ISO-8859-1"));
        FileInputStream in = new FileInputStream(file);
        try {
            long skip = start;
            while (skip > 0) {
                long s = in.skip(skip);
                if (s <= 0) break;
                skip -= s;
            }
            byte[] buf = new byte[64 * 1024];
            long remain = end - start + 1;
            while (remain > 0) {
                int n = in.read(buf, 0, (int) Math.min(buf.length, remain));
                if (n <= 0) break;
                out.write(buf, 0, n);
                remain -= n;
            }
        } finally {
            in.close();
        }
    }

    private void handleUpload(InputStream in, OutputStream out, Map<String, String> headers, File dir) throws Exception {
        String contentType = headers.get("content-type");
        int length = 0;
        try {
            length = Integer.parseInt(headers.get("content-length"));
        } catch (Exception ignored) {
        }
        if (length <= 0 || length > MAX_UPLOAD) {
            writeResponse(out, 413, "text/plain; charset=utf-8", "文件过大".getBytes("UTF-8"), null);
            return;
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n <= 0) break;
            read += n;
        }
        int saved = 0;
        if (contentType != null && contentType.startsWith("multipart/form-data")) {
            String boundary = null;
            String[] bits = contentType.split(";");
            for (String b : bits) {
                String s = b.trim();
                if (s.startsWith("boundary=")) boundary = s.substring(9).replace("\"", "");
            }
            if (boundary != null) saved = parseMultipart(body, read, boundary, dir);
        } else if (contentType != null && contentType.startsWith("application/octet-stream")) {
            String name = headers.get("x-file-name");
            if (name == null) name = "upload-" + System.currentTimeMillis() + ".bin";
            saved = saveFile(body, 0, read, dir, name);
        }
        String msg = saved > 0 ? "已上传 " + saved + " 个文件" : "没有收到文件";
        writeResponse(out, 200, "text/html; charset=utf-8",
                ("<html><meta charset=\"utf-8\"><body style=\"font-family:sans-serif;padding:24px\">"
                        + msg + "<p><a href=\"/\">返回</a></p></body></html>").getBytes("UTF-8"), null);
    }

    private void handlePut(InputStream in, OutputStream out, Map<String, String> headers, File file) throws Exception {
        int length = 0;
        try {
            length = Integer.parseInt(headers.get("content-length"));
        } catch (Exception ignored) {
        }
        if (length <= 0 || length > MAX_UPLOAD) {
            writeResponse(out, 413, "text/plain; charset=utf-8", "文件过大".getBytes("UTF-8"), null);
            return;
        }
        if (file.isDirectory()) {
            writeResponse(out, 400, "text/plain; charset=utf-8", "目标是目录".getBytes("UTF-8"), null);
            return;
        }
        byte[] body = new byte[length];
        int read = 0;
        while (read < length) {
            int n = in.read(body, read, length - read);
            if (n <= 0) break;
            read += n;
        }
        int saved = saveFile(body, 0, read, file.getParentFile(), file.getName());
        writeResponse(out, saved > 0 ? 201 : 500, "text/plain; charset=utf-8",
                (saved > 0 ? "ok" : "failed").getBytes("UTF-8"), null);
    }

    private int parseMultipart(byte[] body, int length, String boundary, File dir) {
        int saved = 0;
        byte[] sep = ("--" + boundary).getBytes();
        int pos = indexOf(body, sep, 0, length);
        while (pos >= 0) {
            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(), pos, length);
            if (headerEnd < 0) break;
            int dataStart = headerEnd + 4;
            int next = indexOf(body, sep, dataStart, length);
            if (next < 0) next = length;
            String headerText = new String(body, pos, headerEnd - pos);
            String filename = null;
            int fn = headerText.indexOf("filename=\"");
            if (fn >= 0) {
                int fEnd = headerText.indexOf('"', fn + 10);
                if (fEnd > fn) filename = headerText.substring(fn + 10, fEnd);
            }
            if (filename != null && filename.length() > 0) {
                int dataEnd = next;
                if (dataEnd >= 2 && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') dataEnd -= 2;
                saved += saveFile(body, dataStart, dataEnd - dataStart, dir, filename);
            }
            pos = next;
            if (pos >= length) break;
        }
        return saved;
    }

    private int saveFile(byte[] data, int offset, int count, File dir, String name) {
        try {
            String clean = new File(name).getName();
            if (clean.length() == 0) clean = "upload-" + System.currentTimeMillis();
            File target = new File(dir, clean);
            int index = 1;
            while (target.exists()) {
                String base = clean;
                String ext = "";
                int dot = clean.lastIndexOf('.');
                if (dot > 0) {
                    base = clean.substring(0, dot);
                    ext = clean.substring(dot);
                }
                target = new File(dir, base + "-" + index + ext);
                index++;
            }
            FileOutputStream out = new FileOutputStream(target);
            out.write(data, offset, count);
            out.close();
            return 1;
        } catch (Exception e) {
            return 0;
        }
    }

    private String listing(File root, File dir, String path) {
        String parent = path.length() <= 1 ? null : path.substring(0, path.lastIndexOf('/'));
        if (parent != null && parent.length() == 0) parent = "/";
        StringBuilder sb = new StringBuilder();
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>ZrinFile · ").append(escape(path)).append("</title>")
                .append("<style>body{font-family:-apple-system,'Segoe UI',sans-serif;margin:0;background:#f6f7f8;color:#1d1f23}")
                .append("header{background:#fff;border-bottom:1px solid #e3e4e7;padding:14px 20px;font-size:15px}")
                .append("main{padding:8px 12px 40px}table{width:100%;border-collapse:collapse;background:#fff}")
                .append("td{padding:11px 14px;border-bottom:1px solid #eee;font-size:14px}")
                .append("a{color:#2f5fa8;text-decoration:none} .size{color:#8a8f96;text-align:right;white-space:nowrap}")
                .append(".up{color:#666} form{margin:16px;padding:14px;background:#fff;border:1px solid #e3e4e7}")
                .append("input[type=file]{font-size:13px} button{margin-top:8px;padding:7px 14px;border:1px solid #2f5fa8;background:#2f5fa8;color:#fff}")
                .append("</style></head><body><header>ZrinFile · ").append(escape(path)).append("</header><main><table>");
        if (parent != null) {
            sb.append("<tr><td colspan=\"2\"><a class=\"up\" href=\"").append(encodePath(parent))
                    .append("\">进入上级目录</a></td></tr>");
        }
        File[] children = dir.listFiles();
        List<File> files = new ArrayList<>();
        if (children != null) {
            for (File f : children) {
                if (!f.getName().startsWith(".")) files.add(f);
            }
        }
        java.util.Collections.sort(files, new java.util.Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        String base = path.endsWith("/") ? path : path + "/";
        for (File f : files) {
            String childPath = base + f.getName();
            sb.append("<tr><td><a href=\"").append(encodePath(childPath)).append("\">")
                    .append(f.isDirectory() ? "📁 " : "📄 ")
                    .append(escape(f.getName())).append(f.isDirectory() ? "/" : "").append("</a></td>")
                    .append("<td class=\"size\">").append(f.isDirectory() ? "-" : size(f.length())).append("</td></tr>");
        }
        if (files.isEmpty()) sb.append("<tr><td colspan=\"2\" style=\"color:#999\">空目录</td></tr>");
        sb.append("</table>");
        if (ServerConfig.TYPE_HTTP.equals(config.type)) {
            sb.append("<form method=\"post\" enctype=\"multipart/form-data\"><div>上传文件到当前目录</div>")
                    .append("<input type=\"file\" name=\"file\" multiple> ")
                    .append("<button type=\"submit\">上传</button></form>");
        }
        sb.append("</main></body></html>");
        return sb.toString();
    }

    private void writeResponse(OutputStream out, int code, String type, byte[] body, String extra) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 ").append(code).append(" ").append(reason(code)).append("\r\n");
        sb.append("Content-Type: ").append(type).append("\r\n");
        sb.append("Content-Length: ").append(body.length).append("\r\n");
        sb.append("Connection: close\r\n");
        if (extra != null) sb.append(extra);
        sb.append("\r\n");
        out.write(sb.toString().getBytes("ISO-8859-1"));
        out.write(body);
    }

    private static String reason(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 206: return "Partial Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 413: return "Payload Too Large";
            case 500: return "Internal Server Error";
            default: return "OK";
        }
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\n') break;
            if (c != '\r') out.write(c);
            if (out.size() > 16384) break;
        }
        if (c == -1 && out.size() == 0) return null;
        return out.toString("ISO-8859-1");
    }

    private static int indexOf(byte[] data, byte[] pattern, int from, int to) {
        outer:
        for (int i = Math.max(0, from); i <= to - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String mimeOf(String name) {
        String guess = java.net.URLConnection.guessContentTypeFromName(name);
        if (guess != null) return guess + (guess.startsWith("text/") ? "; charset=utf-8" : "");
        String lower = name.toLowerCase(Locale.US);
        if (lower.endsWith(".mkv")) return "video/x-matroska";
        if (lower.endsWith(".flac")) return "audio/flac";
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        return "application/octet-stream";
    }

    private static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1048576.0);
        return String.format(Locale.US, "%.1f GB", bytes / 1073741824.0);
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String encodePath(String path) {
        try {
            String[] parts = path.split("/");
            StringBuilder sb = new StringBuilder();
            for (String p : parts) {
                if (p.length() == 0) continue;
                if (sb.length() > 0) sb.append('/');
                sb.append(URLEncoder.encode(p, "UTF-8").replace("+", "%20"));
            }
            return "/" + sb;
        } catch (UnsupportedEncodingException e) {
            return "/";
        }
    }

    private static String encodeName(String name) {
        try {
            return URLEncoder.encode(name, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return name;
        }
    }
}
