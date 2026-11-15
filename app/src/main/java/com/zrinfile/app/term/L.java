package com.zrinfile.app.term;

import android.content.Context;

/** 命令行界面的中英文切换（只作用于命令行与命令行设置）。 */
public final class L {

    private L() {
    }

    public static String s(Context c, String zh, String en) {
        return TerminalPrefs.english(c) ? en : zh;
    }
}
