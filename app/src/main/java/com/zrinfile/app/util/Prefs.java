package com.zrinfile.app.util;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/** 文件管理器的偏好设置。与命令行设置完全隔离。 */
public final class Prefs {

    public static final String NAME = "zrinfile_prefs";

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_LIGHT = 2;

    public static final int SORT_NAME = 0;
    public static final int SORT_SIZE = 1;
    public static final int SORT_DATE = 2;
    public static final int SORT_TYPE = 3;

    private Prefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static int theme(Context c) {
        return get(c).getInt("theme", THEME_DARK);
    }

    public static void setTheme(Context c, int v) {
        get(c).edit().putInt("theme", v).apply();
    }

    public static boolean showHidden(Context c) {
        return get(c).getBoolean("show_hidden", false);
    }

    public static void setShowHidden(Context c, boolean v) {
        get(c).edit().putBoolean("show_hidden", v).apply();
    }

    public static boolean grid(Context c) {
        return get(c).getBoolean("grid", false);
    }

    public static void setGrid(Context c, boolean v) {
        get(c).edit().putBoolean("grid", v).apply();
    }

    public static boolean animations(Context c) {
        return get(c).getBoolean("animations", true);
    }

    public static void setAnimations(Context c, boolean v) {
        get(c).edit().putBoolean("animations", v).apply();
    }

    public static int sortBy(Context c) {
        return get(c).getInt("sort_by", SORT_NAME);
    }

    public static void setSortBy(Context c, int v) {
        get(c).edit().putInt("sort_by", v).apply();
    }

    public static boolean sortAsc(Context c) {
        return get(c).getBoolean("sort_asc", true);
    }

    public static void setSortAsc(Context c, boolean v) {
        get(c).edit().putBoolean("sort_asc", v).apply();
    }

    public static boolean foldersFirst(Context c) {
        return get(c).getBoolean("folders_first", true);
    }

    public static void setFoldersFirst(Context c, boolean v) {
        get(c).edit().putBoolean("folders_first", v).apply();
    }

    public static boolean confirmDelete(Context c) {
        return get(c).getBoolean("confirm_delete", true);
    }

    public static void setConfirmDelete(Context c, boolean v) {
        get(c).edit().putBoolean("confirm_delete", v).apply();
    }

    public static boolean useTrash(Context c) {
        return get(c).getBoolean("use_trash", true);
    }

    public static void setUseTrash(Context c, boolean v) {
        get(c).edit().putBoolean("use_trash", v).apply();
    }

    public static int trashDays(Context c) {
        return get(c).getInt("trash_days", 30);
    }

    public static void setTrashDays(Context c, int v) {
        get(c).edit().putInt("trash_days", v).apply();
    }

    public static boolean showSize(Context c) {
        return get(c).getBoolean("show_size", true);
    }

    public static void setShowSize(Context c, boolean v) {
        get(c).edit().putBoolean("show_size", v).apply();
    }

    public static boolean showDate(Context c) {
        return get(c).getBoolean("show_date", true);
    }

    public static void setShowDate(Context c, boolean v) {
        get(c).edit().putBoolean("show_date", v).apply();
    }

    public static String lastPath(Context c) {
        return get(c).getString("last_path", "");
    }

    public static void setLastPath(Context c, String v) {
        get(c).edit().putString("last_path", v).apply();
    }

    public static String bookmarks(Context c) {
        return get(c).getString("bookmarks", "[]");
    }

    public static void setBookmarks(Context c, String json) {
        get(c).edit().putString("bookmarks", json).apply();
    }

    public static List<String> bookmarkList(Context c) {
        List<String> out = new ArrayList<>();
        try {
            org.json.JSONArray arr = new org.json.JSONArray(bookmarks(c));
            for (int i = 0; i < arr.length(); i++) {
                String p = arr.optString(i, "");
                if (p.length() > 0) out.add(p);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void addBookmark(Context c, String path) {
        List<String> list = bookmarkList(c);
        for (String p : list) {
            if (p.equals(path)) return;
        }
        list.add(path);
        saveBookmarks(c, list);
    }

    public static void removeBookmark(Context c, String path) {
        List<String> list = bookmarkList(c);
        list.remove(path);
        saveBookmarks(c, list);
    }

    public static boolean isBookmarked(Context c, String path) {
        return bookmarkList(c).contains(path);
    }

    private static void saveBookmarks(Context c, List<String> list) {
        org.json.JSONArray arr = new org.json.JSONArray();
        for (String p : list) arr.put(p);
        setBookmarks(c, arr.toString());
    }

    public static boolean batteryPromptShown(Context c) {
        return get(c).getBoolean("battery_prompt", false);
    }

    public static void setBatteryPromptShown(Context c) {
        get(c).edit().putBoolean("battery_prompt", true).apply();
    }

    public static int trashUsedCount(Context c) {
        return get(c).getInt("trash_count", 0);
    }

    public static void setTrashUsedCount(Context c, int n) {
        get(c).edit().putInt("trash_count", n).apply();
    }
}
