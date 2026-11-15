package com.zrinfile.app.term;

import android.content.Context;
import android.content.SharedPreferences;

/** 命令行独立设置，不与文件管理器共享。 */
public final class TerminalPrefs {

    public static final String NAME = "zrinfile_terminal";

    public static final String FONT_JETBRAINS = "jetbrains";
    public static final String FONT_FIRA = "fira";
    public static final String FONT_SOURCE = "source";
    public static final String FONT_UBUNTU = "ubuntu";
    public static final String FONT_SYSTEM = "system";

    public static final String MIRROR_AUTO = "auto";
    public static final String MIRROR_MANUAL = "manual";

    public static final String RUNTIME_TERMUX = "termux";
    public static final String RUNTIME_SHELL = "shell";

    private TerminalPrefs() {
    }

    public static SharedPreferences get(Context c) {
        return c.getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String runtime(Context c) {
        return get(c).getString("runtime", RUNTIME_TERMUX);
    }

    public static void setRuntime(Context c, String v) {
        get(c).edit().putString("runtime", v).apply();
    }

    public static boolean autocomplete(Context c) {
        return get(c).getBoolean("autocomplete", true);
    }

    public static void setAutocomplete(Context c, boolean v) {
        get(c).edit().putBoolean("autocomplete", v).apply();
    }

    public static int maxSuggestions(Context c) {
        return get(c).getInt("max_suggestions", 60);
    }

    public static void setMaxSuggestions(Context c, int v) {
        get(c).edit().putInt("max_suggestions", v).apply();
    }

    public static boolean english(Context c) {
        return "en".equals(get(c).getString("language", "zh"));
    }

    public static void setEnglish(Context c, boolean en) {
        get(c).edit().putString("language", en ? "en" : "zh").apply();
    }

    public static String font(Context c) {
        return get(c).getString("font", FONT_JETBRAINS);
    }

    public static void setFont(Context c, String v) {
        get(c).edit().putString("font", v).apply();
    }

    public static String fontAsset(String font) {
        switch (font) {
            case FONT_FIRA: return "fonts/FiraCode-Regular.ttf";
            case FONT_SOURCE: return "fonts/SourceCodePro-Regular.ttf";
            case FONT_UBUNTU: return "fonts/UbuntuMono-Regular.ttf";
            case FONT_SYSTEM: return "";
            default: return "fonts/JetBrainsMono-Regular.ttf";
        }
    }

    public static int fontSize(Context c) {
        return get(c).getInt("font_size", 13);
    }

    public static void setFontSize(Context c, int v) {
        get(c).edit().putInt("font_size", v).apply();
    }

    public static int lineSpacing(Context c) {
        return get(c).getInt("line_spacing", 3);
    }

    public static void setLineSpacing(Context c, int v) {
        get(c).edit().putInt("line_spacing", v).apply();
    }

    public static String scheme(Context c) {
        return get(c).getString("scheme", "unity");
    }

    public static void setScheme(Context c, String v) {
        get(c).edit().putString("scheme", v).apply();
    }

    public static boolean highlightErrors(Context c) {
        return get(c).getBoolean("highlight_errors", true);
    }

    public static void setHighlightErrors(Context c, boolean v) {
        get(c).edit().putBoolean("highlight_errors", v).apply();
    }

    public static boolean highlightCommands(Context c) {
        return get(c).getBoolean("highlight_commands", true);
    }

    public static void setHighlightCommands(Context c, boolean v) {
        get(c).edit().putBoolean("highlight_commands", v).apply();
    }

    public static int scrollback(Context c) {
        return get(c).getInt("scrollback", 2500);
    }

    public static void setScrollback(Context c, int v) {
        get(c).edit().putInt("scrollback", v).apply();
    }

    public static boolean keepAwake(Context c) {
        return get(c).getBoolean("keep_awake", true);
    }

    public static void setKeepAwake(Context c, boolean v) {
        get(c).edit().putBoolean("keep_awake", v).apply();
    }

    public static boolean haptic(Context c) {
        return get(c).getBoolean("haptic", false);
    }

    public static void setHaptic(Context c, boolean v) {
        get(c).edit().putBoolean("haptic", v).apply();
    }

    public static String mirrorMode(Context c) {
        return get(c).getString("mirror_mode", MIRROR_AUTO);
    }

    public static void setMirrorMode(Context c, String v) {
        get(c).edit().putString("mirror_mode", v).apply();
    }

    public static String mirrorUrl(Context c) {
        return get(c).getString("mirror_url", "");
    }

    public static void setMirrorUrl(Context c, String v) {
        get(c).edit().putString("mirror_url", v).apply();
    }

    public static String mirrorName(Context c) {
        return get(c).getString("mirror_name", "");
    }

    public static void setMirrorName(Context c, String v) {
        get(c).edit().putString("mirror_name", v).apply();
    }

    public static long mirrorLatency(Context c) {
        return get(c).getLong("mirror_latency", -1);
    }

    public static void setMirrorLatency(Context c, long v) {
        get(c).edit().putLong("mirror_latency", v).apply();
    }
}
