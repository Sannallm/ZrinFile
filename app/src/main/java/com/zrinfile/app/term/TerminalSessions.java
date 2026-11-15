package com.zrinfile.app.term;

import com.zrinfile.app.App;

import java.util.ArrayList;
import java.util.List;

public final class TerminalSessions {

    public static final List<ShellSession> SESSIONS = new ArrayList<>();
    private static int seq = 1;

    private TerminalSessions() {
    }

    public static boolean termuxMode() {
        return TermuxPaths.supported()
                && TerminalPrefs.RUNTIME_TERMUX.equals(TerminalPrefs.runtime(App.get()));
    }

    public static synchronized ShellSession create(String cwd) {
        if (cwd == null || cwd.length() == 0) cwd = App.homeDir().getAbsolutePath();
        boolean termux = termuxMode();
        ShellSession s = new ShellSession(seq++, cwd,
                (termux ? "Termux " : "会话 ") + (seq - 1), termux);
        SESSIONS.add(s);
        return s;
    }

    public static synchronized void remove(ShellSession s) {
        if (s != null) s.cancel();
        SESSIONS.remove(s);
    }

    public static synchronized ShellSession firstOrCreate(String cwd) {
        if (SESSIONS.isEmpty()) return create(cwd);
        return SESSIONS.get(0);
    }

    public static synchronized void reset() {
        for (ShellSession s : new ArrayList<>(SESSIONS)) {
            s.cancel();
        }
        SESSIONS.clear();
        seq = 1;
    }
}
