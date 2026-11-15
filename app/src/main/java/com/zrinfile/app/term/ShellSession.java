package com.zrinfile.app.term;

import android.text.SpannableStringBuilder;

import java.util.ArrayList;
import java.util.List;

public class ShellSession {

    public final int id;
    public final boolean termux;
    public String title;
    public String cwd;
    public String lastCwd;
    public final SpannableStringBuilder buffer = new SpannableStringBuilder();
    public final List<String> history = new ArrayList<>();
    public volatile Process process;
    public volatile TermuxPty pty;
    public volatile boolean running;
    public volatile boolean cancelRequested;
    public boolean welcomeShown;

    public ShellSession(int id, String cwd, String title) {
        this(id, cwd, title, false);
    }

    public ShellSession(int id, String cwd, String title, boolean termux) {
        this.id = id;
        this.cwd = cwd;
        this.title = title;
        this.termux = termux;
    }

    public void append(CharSequence text, int maxLines) {
        buffer.append(text);
        trim(maxLines);
    }

    private void trim(int maxLines) {
        if (maxLines <= 0) maxLines = 2000;
        int lines = 0;
        for (int i = buffer.length() - 1; i >= 0; i--) {
            if (buffer.charAt(i) == '\n') {
                lines++;
                if (lines > maxLines) {
                    buffer.delete(0, i + 1);
                    return;
                }
            }
        }
        if (buffer.length() > 400000) {
            buffer.delete(0, buffer.length() - 300000);
        }
    }

    public void clear() {
        buffer.clear();
    }

    public void cancel() {
        cancelRequested = true;
        TermuxPty p = pty;
        if (p != null) {
            p.close();
            pty = null;
        }
        Process proc = process;
        if (proc != null) {
            try {
                proc.destroy();
            } catch (Exception ignored) {
            }
        }
    }
}
