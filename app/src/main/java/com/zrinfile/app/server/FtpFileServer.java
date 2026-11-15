package com.zrinfile.app.server;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 轻量 FTP 服务器，支持目录浏览、下载、上传、删除。 */
public class FtpFileServer implements FileServer {

    private final ServerConfig config;
    private volatile boolean running;
    private ServerSocket serverSocket;
    private final List<Socket> clients = new ArrayList<>();

    public FtpFileServer(ServerConfig config) {
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
                        synchronized (clients) {
                            clients.add(socket);
                        }
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                new Client(socket).run();
                            }
                        }, "zrinfile-ftp-client").start();
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
        }, "zrinfile-ftp");
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
        synchronized (clients) {
            for (Socket s : clients) {
                try {
                    s.close();
                } catch (Exception ignored) {
                }
            }
            clients.clear();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private final class Client {

        private final Socket socket;
        private InputStream in;
        private OutputStream out;
        private File cwd;
        private boolean loggedIn;
        private String pendingUser;
        private ServerSocket dataServer;
        private String renameFrom;

        Client(Socket socket) {
            this.socket = socket;
        }

        void run() {
            try {
                socket.setSoTimeout(60000);
                in = new BufferedInputStream(socket.getInputStream());
                out = new BufferedOutputStream(socket.getOutputStream());
                cwd = new File(config.root).getCanonicalFile();
                reply("220 ZrinFile FTP ready");
                String line;
                while (running && (line = readLine()) != null) {
                    if (line.length() == 0) continue;
                    int space = line.indexOf(' ');
                    String cmd = (space < 0 ? line : line.substring(0, space)).toUpperCase(Locale.US);
                    String arg = space < 0 ? "" : line.substring(space + 1).trim();
                    if (!handle(cmd, arg)) break;
                }
            } catch (Exception ignored) {
            } finally {
                closeData();
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
                synchronized (clients) {
                    clients.remove(socket);
                }
            }
        }

        private boolean handle(String cmd, String arg) throws Exception {
            if ("USER".equals(cmd)) {
                pendingUser = arg;
                reply("331 Password required");
            } else if ("PASS".equals(cmd)) {
                if (config.auth) {
                    if (pendingUser != null && pendingUser.equals(config.user) && arg.equals(config.pass)) {
                        loggedIn = true;
                        reply("230 Logged in");
                    } else {
                        reply("530 Login incorrect");
                    }
                } else {
                    loggedIn = true;
                    reply("230 Logged in");
                }
            } else if (!loggedIn && !"QUIT".equals(cmd) && !"SYST".equals(cmd) && !"FEAT".equals(cmd)) {
                reply("530 Please login");
            } else if ("SYST".equals(cmd)) {
                reply("215 UNIX Type: L8");
            } else if ("FEAT".equals(cmd)) {
                reply("211-Features");
                raw(" UTF8\r\n");
                raw(" PASV\r\n");
                raw(" SIZE\r\n");
                reply("211 End");
            } else if ("OPTS".equals(cmd)) {
                reply("200 OK");
            } else if ("PWD".equals(cmd) || "XPWD".equals(cmd)) {
                reply("257 \"" + displayPath() + "\"");
            } else if ("CWD".equals(cmd)) {
                File target = resolve(arg);
                if (target != null && target.isDirectory()) {
                    cwd = target;
                    reply("250 Directory changed");
                } else {
                    reply("550 No such directory");
                }
            } else if ("CDUP".equals(cmd)) {
                File parent = cwd.getParentFile();
                if (parent != null && parent.getPath().startsWith(new File(config.root).getCanonicalPath())) {
                    cwd = parent;
                }
                reply("250 OK");
            } else if ("TYPE".equals(cmd)) {
                reply("200 Type set");
            } else if ("PASV".equals(cmd)) {
                closeData();
                dataServer = new ServerSocket(0);
                int port = dataServer.getLocalPort();
                String ip = localIp();
                reply("227 Entering Passive Mode (" + ip.replace('.', ',') + "," + (port / 256) + "," + (port % 256) + ")");
            } else if ("EPSV".equals(cmd)) {
                closeData();
                dataServer = new ServerSocket(0);
                reply("229 Entering Extended Passive Mode (|||" + dataServer.getLocalPort() + "|)");
            } else if ("LIST".equals(cmd) || "NLST".equals(cmd)) {
                sendListing("NLST".equals(cmd));
            } else if ("RETR".equals(cmd)) {
                sendFile(resolve(arg));
            } else if ("STOR".equals(cmd)) {
                receiveFile(resolve(arg));
            } else if ("SIZE".equals(cmd)) {
                File f = resolve(arg);
                reply(f != null && f.isFile() ? "213 " + f.length() : "550 No such file");
            } else if ("DELE".equals(cmd)) {
                File f = resolve(arg);
                reply(f != null && f.isFile() && f.delete() ? "250 Deleted" : "550 Delete failed");
            } else if ("MKD".equals(cmd)) {
                File f = resolve(arg);
                reply(f != null && f.mkdirs() ? "257 \"" + displayPath() + "\"" : "550 Create failed");
            } else if ("RMD".equals(cmd)) {
                File f = resolve(arg);
                reply(f != null && f.isDirectory() && f.delete() ? "250 Removed" : "550 Remove failed");
            } else if ("RNFR".equals(cmd)) {
                renameFrom = arg;
                reply("350 Ready for RNTO");
            } else if ("RNTO".equals(cmd)) {
                File from = resolve(renameFrom);
                File to = resolve(arg);
                reply(from != null && to != null && from.renameTo(to) ? "250 Renamed" : "550 Rename failed");
            } else if ("NOOP".equals(cmd)) {
                reply("200 OK");
            } else if ("QUIT".equals(cmd)) {
                reply("221 Bye");
                return false;
            } else {
                reply("502 Not implemented");
            }
            return true;
        }

        private void sendListing(boolean namesOnly) throws Exception {
            if (dataServer == null) {
                reply("425 Use PASV first");
                return;
            }
            reply("150 Opening data connection");
            Socket data = acceptData();
            try {
                OutputStream dout = new BufferedOutputStream(data.getOutputStream());
                File[] files = cwd.listFiles();
                SimpleDateFormat fmt = new SimpleDateFormat("MMM dd HH:mm", Locale.US);
                if (files != null) {
                    for (File f : files) {
                        if (namesOnly) {
                            dout.write((f.getName() + "\r\n").getBytes("UTF-8"));
                        } else {
                            String perm = f.isDirectory() ? "drwxr-xr-x" : "-rw-r--r--";
                            String line = perm + " 1 owner group " + f.length() + " "
                                    + fmt.format(new Date(f.lastModified())) + " " + f.getName() + "\r\n";
                            dout.write(line.getBytes("UTF-8"));
                        }
                    }
                }
                dout.flush();
                data.close();
                reply("226 Transfer complete");
            } finally {
                closeData();
            }
        }

        private void sendFile(File f) throws Exception {
            if (f == null || !f.isFile()) {
                reply("550 No such file");
                return;
            }
            if (dataServer == null) {
                reply("425 Use PASV first");
                return;
            }
            reply("150 Opening data connection");
            Socket data = acceptData();
            InputStream fin = new FileInputStream(f);
            try {
                OutputStream dout = new BufferedOutputStream(data.getOutputStream());
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = fin.read(buf)) > 0) dout.write(buf, 0, n);
                dout.flush();
            } finally {
                fin.close();
                data.close();
                closeData();
            }
            reply("226 Transfer complete");
        }

        private void receiveFile(File f) throws Exception {
            if (f == null) {
                reply("550 Invalid path");
                return;
            }
            if (dataServer == null) {
                reply("425 Use PASV first");
                return;
            }
            reply("150 Ready for data");
            Socket data = acceptData();
            FileOutputStream fout = new FileOutputStream(f);
            try {
                InputStream din = new BufferedInputStream(data.getInputStream());
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = din.read(buf)) > 0) fout.write(buf, 0, n);
                fout.flush();
            } finally {
                fout.close();
                data.close();
                closeData();
            }
            reply("226 Transfer complete");
        }

        private Socket acceptData() throws Exception {
            dataServer.setSoTimeout(15000);
            return dataServer.accept();
        }

        private void closeData() {
            try {
                if (dataServer != null) dataServer.close();
            } catch (Exception ignored) {
            }
            dataServer = null;
        }

        private File resolve(String arg) {
            try {
                File root = new File(config.root).getCanonicalFile();
                String path = arg;
                if (path.startsWith("/")) path = path.substring(1);
                File f = path.length() == 0 ? cwd.getCanonicalFile() : new File(cwd, path).getCanonicalFile();
                if (!f.getPath().equals(root.getPath()) && !f.getPath().startsWith(root.getPath() + File.separator)) {
                    return null;
                }
                return f;
            } catch (Exception e) {
                return null;
            }
        }

        private String displayPath() {
            try {
                String root = new File(config.root).getCanonicalPath();
                String p = cwd.getCanonicalPath();
                if (!p.startsWith(root)) return "/";
                return "/" + p.substring(root.length()).replace(File.separatorChar, '/');
            } catch (Exception e) {
                return "/";
            }
        }

        private String readLine() throws Exception {
            StringBuilder sb = new StringBuilder();
            int c;
            while ((c = in.read()) != -1) {
                if (c == '\n') break;
                if (c != '\r') sb.append((char) c);
                if (sb.length() > 4096) break;
            }
            if (c == -1 && sb.length() == 0) return null;
            return sb.toString();
        }

        private void reply(String text) throws Exception {
            raw(text + "\r\n");
        }

        private void raw(String text) throws Exception {
            out.write(text.getBytes("UTF-8"));
            out.flush();
        }
    }

    private static String localIp() {
        try {
            java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                java.net.NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                java.util.Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof java.net.Inet4Address) {
                        String host = addr.getHostAddress();
                        if (host != null && !host.startsWith("127.")) return host;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
