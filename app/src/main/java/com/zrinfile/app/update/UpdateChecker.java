package com.zrinfile.app.update;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.zrinfile.app.App;
import com.zrinfile.app.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 通过 GitHub Releases 检测新版本，失败时回退到仓库内的 update.json。 */
public final class UpdateChecker {

    public static final String OWNER = "Sannallm";
    public static final String REPO = "ZrinFile";

    private static final String API_LATEST =
            "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    private static final String MANIFEST_CDN =
            "https://cdn.jsdelivr.net/gh/" + OWNER + "/" + REPO + "@main/update.json";
    private static final String MANIFEST_RAW =
            "https://raw.githubusercontent.com/" + OWNER + "/" + REPO + "/main/update.json";

    public interface Callback {
        void onResult(UpdateInfo info, String error);
    }

    private UpdateChecker() {
    }

    public static void checkAsync(final Context context, final Callback callback) {
        final Handler main = new Handler(Looper.getMainLooper());
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                UpdateInfo info = null;
                String error = null;
                try {
                    info = fetch();
                } catch (Exception e) {
                    error = e.getMessage() == null ? "网络连接失败" : e.getMessage();
                }
                android.util.Log.d("ZrinFileUpdate", info != null
                        ? "latest=" + info.versionName + " url=" + info.apkUrl
                        : "check failed: " + error);
                final UpdateInfo result = info;
                final String err = error;
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (callback != null) callback.onResult(result, err);
                    }
                });
            }
        });
    }

    public static UpdateInfo fetch() throws Exception {
        Exception last = null;
        try {
            UpdateInfo info = parseRelease(fetchText(API_LATEST));
            if (info != null && info.apkUrl.startsWith("https://")) return info;
        } catch (Exception e) {
            last = e;
        }
        try {
            UpdateInfo info = parseManifest(fetchText(MANIFEST_CDN));
            if (info != null && info.apkUrl.startsWith("https://")) return info;
        } catch (Exception e) {
            last = e;
        }
        try {
            UpdateInfo info = parseManifest(fetchText(MANIFEST_RAW));
            if (info != null && info.apkUrl.startsWith("https://")) return info;
        } catch (Exception e) {
            last = e;
        }
        throw last != null ? last : new Exception("未找到可用的版本信息");
    }

    /** 主下载地址与备用镜像，GitHub 下载受限时依次尝试。 */
    public static List<String> downloadSources(String apkUrl) {
        List<String> list = new ArrayList<>();
        if (apkUrl == null || apkUrl.length() == 0) return list;
        list.add(apkUrl);
        if (apkUrl.startsWith("https://github.com/")) {
            list.add("https://ghproxy.net/" + apkUrl);
            list.add("https://ghfast.top/" + apkUrl);
            list.add("https://gh-proxy.com/" + apkUrl);
            list.add("https://github.moeyy.xyz/" + apkUrl);
        }
        return list;
    }

    public static boolean isNewer(String version, String current) {
        int[] a = parseVersion(version);
        int[] b = parseVersion(current);
        for (int i = 0; i < 3; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    private static int[] parseVersion(String v) {
        int[] out = new int[]{0, 0, 0};
        if (v == null) return out;
        String s = v.trim();
        if (s.startsWith("v") || s.startsWith("V")) s = s.substring(1);
        int dash = s.indexOf('-');
        if (dash >= 0) s = s.substring(0, dash);
        String[] parts = s.split("\\.");
        for (int i = 0; i < 3 && i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    private static String fetchText(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "ZrinFile/" + BuildConfig.VERSION_NAME);
            conn.setRequestProperty("Accept", "application/vnd.github+json");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != 200) throw new Exception("服务器返回 " + code);
            InputStream in = conn.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int n;
            while ((n = reader.read(buf)) > 0) sb.append(buf, 0, n);
            reader.close();
            return sb.toString();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static List<String> splitNotes(String body) {
        List<String> out = new ArrayList<>();
        if (body == null) return out;
        String[] lines = body.replace("\r", "").split("\n");
        for (String line : lines) {
            String s = line.trim();
            if (s.length() == 0) continue;
            if (s.startsWith("#")) continue;
            if (s.startsWith("- ")) s = s.substring(2).trim();
            else if (s.startsWith("* ")) s = s.substring(2).trim();
            else if (s.startsWith("· ")) s = s.substring(2).trim();
            if (s.startsWith("|") || s.startsWith("```")) continue;
            if (s.length() > 0) out.add(s);
            if (out.size() >= 30) break;
        }
        return out;
    }

    private static UpdateInfo parseRelease(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        UpdateInfo info = new UpdateInfo();
        info.versionName = o.optString("tag_name", "").replaceFirst("^[vV]", "");
        if (info.versionName.length() == 0) throw new Exception("版本号缺失");
        info.versionCode = toVersionCode(info.versionName);
        info.releaseUrl = o.optString("html_url", "");
        info.notes = splitNotes(o.optString("body", ""));
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.optJSONObject(i);
                if (a == null) continue;
                String name = a.optString("name", "");
                if (!name.toLowerCase().endsWith(".apk")) continue;
                info.apkUrl = a.optString("browser_download_url", "");
                info.size = a.optLong("size", 0);
                break;
            }
        }
        if (info.apkUrl.length() == 0) {
            UpdateInfo manifest = parseManifest(fetchText(MANIFEST_CDN));
            if (manifest != null) {
                if (info.notes.isEmpty()) info.notes = manifest.notes;
                info.apkUrl = manifest.apkUrl;
                if (info.size <= 0) info.size = manifest.size;
                info.sha256 = manifest.sha256;
            }
        }
        return info;
    }

    private static UpdateInfo parseManifest(String json) throws Exception {
        JSONObject o = new JSONObject(json);
        UpdateInfo info = new UpdateInfo();
        info.versionName = o.optString("versionName", "");
        if (info.versionName.length() == 0) throw new Exception("版本号缺失");
        info.versionCode = o.optInt("versionCode", toVersionCode(info.versionName));
        info.apkUrl = o.optString("apkUrl", "");
        info.sha256 = o.optString("sha256", "");
        info.size = o.optLong("size", 0);
        JSONArray notes = o.optJSONArray("notes");
        if (notes != null) {
            for (int i = 0; i < notes.length(); i++) {
                String n = notes.optString(i, "");
                if (n.length() > 0) info.notes.add(n);
            }
        }
        return info;
    }

    private static int toVersionCode(String version) {
        int[] v = parseVersion(version);
        return v[0] * 10000 + v[1] * 100 + v[2];
    }
}
