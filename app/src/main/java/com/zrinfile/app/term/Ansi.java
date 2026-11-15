package com.zrinfile.app.term;

import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;

import java.util.Locale;

/** 精简 ANSI 解析：颜色、粗体、错误行着色，其余控制序列直接过滤。 */
public final class Ansi {

    private Ansi() {
    }

    public static CharSequence parse(String raw, TerminalTheme theme, boolean highlightErrors) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        StringBuilder run = new StringBuilder();
        int color = theme.text;
        boolean bold = false;
        int i = 0;
        int len = raw.length();
        while (i < len) {
            char c = raw.charAt(i);
            if (c == 0x1B) {
                flush(out, run, color, bold);
                int j = i + 1;
                if (j < len && raw.charAt(j) == '[') {
                    j++;
                    while (j < len && !(raw.charAt(j) >= 0x40 && raw.charAt(j) <= 0x7E)) j++;
                    if (j < len) {
                        String finalChar = String.valueOf(raw.charAt(j));
                        String body = raw.substring(i + 2, j);
                        if ("m".equals(finalChar)) {
                            int[] newColor = new int[]{color};
                            bold = applySgr(body, theme, newColor, bold);
                            color = newColor[0];
                        }
                        i = j + 1;
                        continue;
                    }
                    break;
                } else if (j < len && raw.charAt(j) == ']') {
                    j++;
                    while (j < len && raw.charAt(j) != 0x07 && raw.charAt(j) != 0x1B) j++;
                    if (j < len && raw.charAt(j) == 0x1B && j + 1 < len && raw.charAt(j + 1) == '\\') j++;
                    i = j + 1;
                    continue;
                } else {
                    i += 2;
                    continue;
                }
            }
            if (c == '\r' || c == 0x07 || c == 0x08) {
                i++;
                continue;
            }
            run.append(c);
            i++;
        }
        flush(out, run, color, bold);

        if (highlightErrors && looksLikeError(raw) && out.length() > 0) {
            out.setSpan(new ForegroundColorSpan(theme.error), 0, out.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return out;
    }

    private static void flush(SpannableStringBuilder out, StringBuilder run, int color, boolean bold) {
        if (run.length() == 0) return;
        int start = out.length();
        out.append(run);
        int end = out.length();
        out.setSpan(new ForegroundColorSpan(color), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        if (bold) {
            out.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        run.setLength(0);
    }

    private static boolean applySgr(String body, TerminalTheme theme, int[] color, boolean bold) {
        if (body.length() == 0) {
            color[0] = theme.text;
            return false;
        }
        String[] parts = body.split(";");
        for (int k = 0; k < parts.length; k++) {
            int code;
            try {
                code = Integer.parseInt(parts[k]);
            } catch (Exception e) {
                continue;
            }
            if (code == 0) {
                color[0] = theme.text;
                bold = false;
            } else if (code == 1) {
                bold = true;
            } else if (code == 22) {
                bold = false;
            } else if (code == 39) {
                color[0] = theme.text;
            } else if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) {
                color[0] = theme.colorForSgr(code);
            } else if (code == 38 || code == 48) {
                if (k + 1 < parts.length) {
                    int mode = parseInt(parts[k + 1]);
                    if (mode == 5) k += 2;
                    else if (mode == 2) k += 4;
                }
            }
        }
        return bold;
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return -1;
        }
    }

    private static boolean looksLikeError(String s) {
        String l = s.toLowerCase(Locale.US);
        return l.contains("not found") || l.contains("permission denied") || l.contains("no such file")
                || l.contains("cannot") || l.contains("failed") || l.contains("error")
                || l.contains("denied") || l.contains("invalid") || l.contains("not a directory")
                || l.contains("is a directory") || l.contains("usage:")
                || l.contains("失败") || l.contains("错误") || l.contains("不存在")
                || l.contains("没有权限") || l.contains("无法") || l.contains("不是目录")
                || l.contains("拒绝");
    }
}
