package com.zrinfile.app.term;

import android.content.Context;
import android.graphics.Color;

/** 命令行配色。全部为低饱和度的原生风格配色。 */
public final class TerminalTheme {

    public int bg;
    public int text;
    public int dim;
    public int accent;
    public int error;
    public int selection;
    public int surface;
    public int border;
    public boolean light;

    public static TerminalTheme of(Context c, String id) {
        TerminalTheme t = new TerminalTheme();
        if ("light".equals(id)) {
            t.bg = 0xFFFBFBFC;
            t.text = 0xFF232529;
            t.dim = 0xFF8A8F96;
            t.accent = 0xFF3F6FB5;
            t.error = 0xFFB33A34;
            t.selection = 0xFFDDE6F2;
            t.surface = 0xFFF1F2F4;
            t.border = 0xFFD8DADE;
            t.light = true;
        } else if ("graphite".equals(id)) {
            t.bg = 0xFF18191A;
            t.text = 0xFFC8CACD;
            t.dim = 0xFF777B80;
            t.accent = 0xFFA9B3BD;
            t.error = 0xFFD9746C;
            t.selection = 0xFF2E3339;
            t.surface = 0xFF212325;
            t.border = 0xFF35383D;
        } else if ("green".equals(id)) {
            t.bg = 0xFF0E1310;
            t.text = 0xFFB7D8B9;
            t.dim = 0xFF6B886E;
            t.accent = 0xFF6FAF7B;
            t.error = 0xFFD0736B;
            t.selection = 0xFF1F3226;
            t.surface = 0xFF151B17;
            t.border = 0xFF2A3A2E;
        } else {
            t.bg = 0xFF1B1C1F;
            t.text = 0xFFD7DADF;
            t.dim = 0xFF7C8189;
            t.accent = 0xFF6E9BE0;
            t.error = 0xFFE07A71;
            t.selection = 0xFF33404F;
            t.surface = 0xFF24262B;
            t.border = 0xFF3A3D44;
        }
        return t;
    }

    public int colorForSgr(int code) {
        switch (code) {
            case 30: return 0xFF4A4E55;
            case 31: return error;
            case 32: return 0xFF7FA57F;
            case 33: return 0xFFC4A35A;
            case 34: return accent;
            case 35: return 0xFFA98BC4;
            case 36: return 0xFF69A8A8;
            case 37: return text;
            case 90: return dim;
            case 91: return error;
            case 92: return 0xFF8FB88F;
            case 93: return 0xFFD3B36B;
            case 94: return 0xFF87AEE8;
            case 95: return 0xFFB99CD4;
            case 96: return 0xFF7CBDBD;
            case 97: return 0xFFFFFFFF;
            default: return text;
        }
    }

    public static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
}
