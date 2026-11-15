package com.zrinfile.app.update;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class UpdateInfo {

    public String versionName = "";
    public int versionCode = 0;
    public String apkUrl = "";
    public String sha256 = "";
    public long size = 0;
    public String releaseUrl = "";
    public List<String> notes = new ArrayList<>();

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            o.put("versionName", versionName);
            o.put("versionCode", versionCode);
            o.put("apkUrl", apkUrl);
            o.put("sha256", sha256);
            o.put("size", size);
            o.put("releaseUrl", releaseUrl);
            JSONArray arr = new JSONArray();
            for (String n : notes) arr.put(n);
            o.put("notes", arr);
        } catch (Exception ignored) {
        }
        return o;
    }

    public static UpdateInfo fromJson(String raw) {
        try {
            JSONObject o = new JSONObject(raw);
            UpdateInfo info = new UpdateInfo();
            info.versionName = o.optString("versionName", "");
            info.versionCode = o.optInt("versionCode", 0);
            info.apkUrl = o.optString("apkUrl", "");
            info.sha256 = o.optString("sha256", "");
            info.size = o.optLong("size", 0);
            info.releaseUrl = o.optString("releaseUrl", "");
            JSONArray arr = o.optJSONArray("notes");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String n = arr.optString(i, "");
                    if (n.length() > 0) info.notes.add(n);
                }
            }
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    public String notesText() {
        StringBuilder sb = new StringBuilder();
        for (String n : notes) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("· ").append(n);
        }
        return sb.toString();
    }
}
