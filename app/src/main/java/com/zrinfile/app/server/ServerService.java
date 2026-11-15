package com.zrinfile.app.server;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.zrinfile.app.R;
import com.zrinfile.app.ui.ServerActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

/** 前台服务：文件服务器常驻后台。 */
public class ServerService extends Service {

    public static final String ACTION_START = "com.zrinfile.app.server.START";
    public static final String ACTION_STOP = "com.zrinfile.app.server.STOP";
    public static final String EXTRA_TYPE = "type";
    public static final String EXTRA_ROOT = "root";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_AUTH = "auth";
    public static final String EXTRA_USER = "user";
    public static final String EXTRA_PASS = "pass";

    private static final String CHANNEL_ID = "zrinfile_server";
    private static final int NOTIFICATION_ID = 101;

    private static volatile String state = "stopped";
    private static volatile String info = "";
    private static volatile ServerConfig current;

    private FileServer server;
    private PowerManager.WakeLock wakeLock;

    public static boolean isRunning() {
        return "running".equals(state);
    }

    public static String getState() {
        return state;
    }

    public static String getInfo() {
        return info;
    }

    public static ServerConfig getCurrent() {
        return current == null ? null : current.copy();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_STICKY;
        }
        if (ACTION_STOP.equals(intent.getAction())) {
            stopEverything();
            return START_NOT_STICKY;
        }
        ServerConfig config = new ServerConfig();
        config.type = intent.getStringExtra(EXTRA_TYPE);
        if (config.type == null) config.type = ServerConfig.TYPE_HTTP;
        config.root = intent.getStringExtra(EXTRA_ROOT);
        if (config.root == null) config.root = "/";
        config.port = intent.getIntExtra(EXTRA_PORT, config.type.equals(ServerConfig.TYPE_FTP) ? 2121 : 8080);
        config.auth = intent.getBooleanExtra(EXTRA_AUTH, false);
        config.user = intent.getStringExtra(EXTRA_USER);
        if (config.user == null) config.user = "zrin";
        config.pass = intent.getStringExtra(EXTRA_PASS);
        if (config.pass == null) config.pass = "";
        current = config.copy();
        state = "starting";
        info = "正在启动…";
        startForeground(NOTIFICATION_ID, buildNotification("正在启动…"));
        acquireWakeLock();
        startServer(config);
        return START_STICKY;
    }

    private void startServer(final ServerConfig config) {
        stopServerQuietly();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (ServerConfig.TYPE_FTP.equals(config.type)) {
                        server = new FtpFileServer(config);
                    } else {
                        server = new HttpFileServer(config);
                    }
                    server.start();
                    state = "running";
                    info = urls(config);
                    updateNotification(info);
                } catch (Exception e) {
                    state = "error";
                    info = e.getMessage() == null ? "启动失败" : e.getMessage();
                    updateNotification("启动失败：" + info);
                    stopForeground(false);
                }
            }
        }, "zrinfile-server-start").start();
    }

    private void stopEverything() {
        stopServerQuietly();
        releaseWakeLock();
        state = "stopped";
        info = "";
        try {
            stopForeground(true);
        } catch (Exception ignored) {
        }
        stopSelf();
    }

    private void stopServerQuietly() {
        FileServer s = server;
        server = null;
        if (s != null) {
            try {
                s.stop();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public void onDestroy() {
        stopServerQuietly();
        releaseWakeLock();
        if (!"error".equals(state)) state = "stopped";
        info = "";
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void acquireWakeLock() {
        if (wakeLock != null) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ZrinFile:server");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire();
            WifiManager wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifi != null) {
                try {
                    WifiManager.WifiLock lock = wifi.createWifiLock(WifiManager.WIFI_MODE_FULL, "ZrinFile:wifi");
                    lock.setReferenceCounted(false);
                    lock.acquire();
                } catch (Exception ignored) {
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) {
        }
        wakeLock = null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "文件服务器",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("文件服务器运行状态");
            channel.setShowBadge(false);
            nm.createNotificationChannel(channel);
        } catch (Exception ignored) {
        }
    }

    private Notification buildNotification(String text) {
        Intent open = new Intent(this, ServerActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop = new Intent(this, ServerService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_terminal)
                .setContentTitle("ZrinFile 文件服务器")
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(openPi)
                .addAction(0, "停止服务", stopPi)
                .build();
    }

    private void updateNotification(String text) {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification(text));
        } catch (Exception ignored) {
        }
    }

    public static String urls(ServerConfig config) {
        String ip = localIp();
        if (ServerConfig.TYPE_FTP.equals(config.type)) {
            return "ftp://" + ip + ":" + config.port;
        }
        return "http://" + ip + ":" + config.port + "/";
    }

    public static String localIp() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            String fallback = null;
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface ni = interfaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        String host = addr.getHostAddress();
                        if (host == null || host.startsWith("127.")) continue;
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host;
                        }
                        if (fallback == null) fallback = host;
                    }
                }
            }
            if (fallback != null) return fallback;
        } catch (Exception ignored) {
        }
        return "127.0.0.1";
    }
}
