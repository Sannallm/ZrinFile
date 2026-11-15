package com.zrinfile.app.ui;

import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.term.Ansi;
import com.zrinfile.app.term.Completer;
import com.zrinfile.app.term.L;
import com.zrinfile.app.term.MirrorManager;
import com.zrinfile.app.term.ShellEngine;
import com.zrinfile.app.term.ShellSession;
import com.zrinfile.app.term.TerminalPrefs;
import com.zrinfile.app.term.TerminalSessions;
import com.zrinfile.app.term.TerminalTheme;
import com.zrinfile.app.term.TermuxInstaller;
import com.zrinfile.app.term.TermuxPaths;
import com.zrinfile.app.term.TermuxPty;
import com.zrinfile.app.util.Prefs;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TerminalActivity extends AppCompatActivity implements ShellEngine.Listener {

    private DrawerLayout drawer;
    private Toolbar toolbar;
    private ScrollView scroll;
    private TextView output;
    private TextView prompt;
    private EditText input;
    private LinearLayout bottom;
    private RecyclerView sessionsView;
    private SessionAdapter sessionAdapter;

    private ShellSession current;
    private TerminalTheme theme;
    private PopupWindow popup;
    private RecyclerView completionList;
    private CompletionAdapter completionAdapter;
    private final List<Completer.Item> completions = new ArrayList<>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private int historyIndex = 0;
    private String initialCwd;
    private Typeface mono;
    private boolean popupVisible;
    private boolean installingTermux;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal);
        initialCwd = getIntent().getStringExtra("cwd");
        bind();
        setupToolbar();
        setupDrawer();
        setupKeys();
        setupInput();
        setupCompletionPopup();
        applyPrefs();
        TermuxPty.setUiListener(termuxListener());
        current = TerminalSessions.firstOrCreate(initialCwd);
        if (TerminalSessions.SESSIONS.contains(current) && TerminalSessions.SESSIONS.size() > 1) {
            current = TerminalSessions.SESSIONS.get(TerminalSessions.SESSIONS.size() - 1);
        }
        sessionAdapter.notifyDataSetChanged();
        showSession(current, true);
        applyPrefs();
        input.requestFocus();
        scroll.setFocusable(false);
        scroll.setFocusableInTouchMode(false);
        main.postDelayed(new Runnable() {
            @Override
            public void run() {
                input.requestFocus();
                showKeyboard();
            }
        }, 220);
    }

    private void bind() {
        drawer = findViewById(R.id.term_drawer);
        toolbar = findViewById(R.id.term_toolbar);
        scroll = findViewById(R.id.term_scroll);
        output = findViewById(R.id.term_output);
        prompt = findViewById(R.id.term_prompt);
        input = findViewById(R.id.term_input);
        bottom = findViewById(R.id.term_bottom);
        sessionsView = findViewById(R.id.term_sessions);
    }

    private void setupToolbar() {
        toolbar.setTitle(L.s(this, "命令行", "Terminal"));
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        toolbar.inflateMenu(R.menu.menu_terminal);
        MenuItem item = toolbar.getMenu().findItem(R.id.term_sessions_menu);
        if (item != null) item.setIcon(UiUtils.tint(this, R.drawable.ic_menu, UiUtils.color(this, R.color.icon)));
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.term_sessions_menu) {
                    hideCompletion();
                    if (drawer.isDrawerOpen(GravityCompat.END)) drawer.closeDrawer(GravityCompat.END);
                    else drawer.openDrawer(GravityCompat.END);
                    return true;
                }
                return false;
            }
        });
    }

    private void setupDrawer() {
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, GravityCompat.START);
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, GravityCompat.END);
        sessionsView.setLayoutManager(new LinearLayoutManager(this));
        sessionAdapter = new SessionAdapter();
        sessionsView.setAdapter(sessionAdapter);
        findViewById(R.id.term_new_session).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String cwd = current != null ? current.cwd : home();
                ShellSession s = TerminalSessions.create(cwd);
                sessionAdapter.notifyDataSetChanged();
                showSession(s, true);
                drawer.closeDrawer(GravityCompat.END);
            }
        });
        findViewById(R.id.term_settings_entry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawer.closeDrawer(GravityCompat.END);
                startActivity(new Intent(TerminalActivity.this, TerminalSettingsActivity.class));
            }
        });
    }

    private void setupInput() {
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEND
                        || (event != null && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER)) {
                    send();
                    return true;
                }
                return false;
            }
        });
        findViewById(R.id.term_send).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                send();
            }
        });
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) hideCompletion();
            }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refreshCompletion();
            }
        });
    }

    private void setupKeys() {
        LinearLayout keys = findViewById(R.id.term_keys);
        keys.removeAllViews();
        addKey(keys, "ESC", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\u001b");
                else insert("\u001b");
            }
        });
        addKey(keys, "TAB", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\t");
                else refreshCompletion(true);
            }
        });
        addKey(keys, "↑", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\u001b[A");
                else historyUp();
            }
        });
        addKey(keys, "↓", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\u001b[B");
                else historyDown();
            }
        });
        addKey(keys, "←", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\u001b[D");
                else moveCursor(-1);
            }
        });
        addKey(keys, "→", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\u001b[C");
                else moveCursor(1);
            }
        });
        addKey(keys, "HOME", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\u001b[H");
                else input.setSelection(0);
            }
        });
        addKey(keys, "END", new Runnable() {
            @Override
            public void run() {
                if (term()) writeTerm("\u001b[F");
                else input.setSelection(input.getText().length());
            }
        });
        addKey(keys, "⌃C", new Runnable() {
            @Override
            public void run() {
                interrupt();
            }
        });
        addKey(keys, "⌃L", new Runnable() {
            @Override
            public void run() {
                if (term()) {
                    writeTerm("\u000c");
                } else {
                    if (current != null) current.clear();
                    output.setText("");
                }
            }
        });
    }

    private boolean term() {
        return current != null && current.termux;
    }

    private void writeTerm(String text) {
        if (current != null && current.pty != null) current.pty.write(text);
    }

    private void addKey(LinearLayout parent, String label, final Runnable action) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(11);
        tv.setTextColor(UiUtils.color(this, R.color.text_secondary));
        tv.setGravity(Gravity.CENTER);
        tv.setBackgroundResource(R.drawable.bg_terminal_key);
        tv.setPadding(UiUtils.dp(this, 9), 0, UiUtils.dp(this, 9), 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, UiUtils.dp(this, 26));
        lp.rightMargin = UiUtils.dp(this, 5);
        tv.setLayoutParams(lp);
        tv.setClickable(true);
        tv.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (TerminalPrefs.haptic(TerminalActivity.this)) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                }
                action.run();
            }
        });
        parent.addView(tv);
    }

    private void setupCompletionPopup() {
        View content = LayoutInflater.from(this).inflate(R.layout.completion_popup, null);
        completionList = content.findViewById(R.id.completion_list);
        completionList.setLayoutManager(new LinearLayoutManager(this));
        completionAdapter = new CompletionAdapter();
        completionList.setAdapter(completionAdapter);
        popup = new PopupWindow(content, ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popup.setBackgroundDrawable(new ColorDrawable(0));
        popup.setOutsideTouchable(false);
        popup.setFocusable(false);
        popup.setTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        popup.setClippingEnabled(true);
        popup.setAnimationStyle(R.style.ZrinFileBubbleAnimation);
    }

    private void showSession(ShellSession s, boolean scrollBottom) {
        current = s;
        historyIndex = s.history.size();
        if (!s.welcomeShown) {
            s.welcomeShown = true;
            if (s.termux) termuxWelcome(s);
            else welcome(s);
        }
        output.setText(s.buffer);
        prompt.setVisibility(s.termux ? View.GONE : View.VISIBLE);
        input.setHint(s.termux
                ? L.s(this, "输入命令，回车发送到 Termux", "Type a command, Enter sends to Termux")
                : L.s(this, "输入命令，回车执行", "Type a command, Enter to run"));
        updatePrompt();
        if (scrollBottom) scrollBottom();
        sessionAdapter.notifyDataSetChanged();
        hideCompletion();
        input.requestFocus();
        if (s.termux) ensureTermux(s);
    }

    private void termuxWelcome(ShellSession s) {
        String text1 = L.s(this, "ZrinFile Terminal · Termux 运行时", "ZrinFile Terminal · Termux runtime");
        String text2 = L.s(this,
                "官方 bootstrap + proot，支持 apt、python、node、ssh 等完整命令。\n首次进入会自动解压环境，需要数秒。\n\n",
                "Official bootstrap with proot. apt, python, node and ssh work as usual.\nThe environment is unpacked on first launch.\n\n");
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(text1);
        sb.setSpan(new ForegroundColorSpan(theme != null ? theme.accent : UiUtils.color(this, R.color.accent)),
                start, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n");
        start = sb.length();
        sb.append(text2);
        sb.setSpan(new ForegroundColorSpan(theme != null ? theme.dim : UiUtils.color(this, R.color.text_tertiary)),
                start, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.buffer.append(sb);
    }

    private void ensureTermux(final ShellSession s) {
        if (!TermuxPaths.supported()) {
            if (s.buffer.length() == 0) {
                s.buffer.append(L.s(this, "当前系统低于 Android 7.0，Termux 运行时不可用，已回退到系统 Shell。\n",
                        "Termux runtime requires Android 7.0 or newer.\n"));
            }
            if (output.getText().length() == 0) output.setText(s.buffer);
            return;
        }
        if (TermuxPaths.installed(this)) {
            if (s.pty == null) {
                s.pty = TermuxPty.start(this, s, theme);
            }
            return;
        }
        if (installingTermux) return;
        installingTermux = true;
        final UiUtils.ProgressHandle handle = UiUtils.progress(this, "正在安装 Termux 环境");
        TermuxInstaller.install(this, new TermuxInstaller.Callback() {
            @Override
            public void onProgress(final String stage, final int percent) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (handle.text != null) handle.text.setText(stage + "  " + percent + "%");
                    }
                });
            }

            @Override
            public void onDone() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        installingTermux = false;
                        UiUtils.dismiss(handle);
                        if (isFinishing()) return;
                        if (s.pty == null) s.pty = TermuxPty.start(TerminalActivity.this, s, theme);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        installingTermux = false;
                        UiUtils.dismiss(handle);
                        UiUtils.toast(TerminalActivity.this, "Termux 环境安装失败：" + message);
                        if (current == s) {
                            output.append("[安装失败] " + message + "\n");
                        }
                    }
                });
            }
        });
    }

    private TermuxPty.Listener termuxListener() {
        return new TermuxPty.Listener() {
            @Override
            public void onOutput(ShellSession session, CharSequence text) {
                if (session == current) {
                    output.append(text);
                    scrollBottom();
                }
            }

            @Override
            public void onClear(ShellSession session) {
                if (session == current) output.setText("");
            }

            @Override
            public void onExit(ShellSession session) {
                sessionAdapter.notifyDataSetChanged();
                if (session == current) {
                    output.append(L.s(TerminalActivity.this, "\n[Termux 进程已退出]\n", "\n[Termux process exited]\n"));
                    scrollBottom();
                    input.requestFocus();
                }
            }

            @Override
            public void onError(ShellSession session, String message) {
                if (session == current) {
                    output.append("[Termux] " + message + "\n");
                    scrollBottom();
                }
            }
        };
    }

    private void welcome(ShellSession s) {
        String text1 = L.s(this, "ZrinFile Shell · 系统 Shell 模式", "ZrinFile Shell · system shell mode");
        String text2 = L.s(this,
                "输入 help 查看命令，输入 zrin help 查看文件管理器桥接命令。\n补全建议会显示在输入框上方，点击即可插入。\n\n",
                "Type help for commands, zrin help for file-manager bridge commands.\nSuggestions appear above the input; tap to insert.\n\n");
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int start = sb.length();
        sb.append(text1);
        sb.setSpan(new ForegroundColorSpan(theme != null ? theme.accent : UiUtils.color(this, R.color.accent)),
                start, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append("\n");
        start = sb.length();
        sb.append(text2);
        sb.setSpan(new ForegroundColorSpan(theme != null ? theme.dim : UiUtils.color(this, R.color.text_tertiary)),
                start, sb.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        s.buffer.append(sb);
        if (current == s) {
            output.setText(s.buffer);
            scrollBottom();
        }
    }

    private void applyPrefs() {
        theme = TerminalTheme.of(this, TerminalPrefs.scheme(this));
        int fontSize = TerminalPrefs.fontSize(this);
        mono = loadTypeface(TerminalPrefs.font(this));
        output.setTextColor(theme.text);
        output.setTextSize(fontSize);
        output.setLineSpacing(UiUtils.dp(this, TerminalPrefs.lineSpacing(this)), 1f);
        output.setTypeface(mono);
        output.setBackgroundColor(theme.bg);
        scroll.setBackgroundColor(theme.bg);
        bottom.setBackgroundColor(theme.bg);
        prompt.setTypeface(mono);
        prompt.setTextColor(theme.accent);
        prompt.setTextSize(fontSize - 1);
        input.setTypeface(mono);
        input.setTextColor(theme.text);
        input.setTextSize(fontSize - 1);
        input.setHintTextColor(theme.dim);
        input.setBackground(panel(theme.surface, theme.border));
        findViewById(R.id.term_keys_scroll).setBackgroundColor(theme.bg);
        findViewById(R.id.term_keys).setBackgroundColor(theme.bg);
        android.widget.ImageButton send = findViewById(R.id.term_send);
        send.setImageDrawable(UiUtils.tint(this, R.drawable.ic_send, theme.accent));
        android.view.View toolbarDivider = findViewById(R.id.term_toolbar_divider);
        if (toolbarDivider != null) toolbarDivider.setBackgroundColor(theme.border);
        toolbar.setBackgroundColor(theme.bg);
        toolbar.setTitleTextColor(theme.text);
        toolbar.setSubtitleTextColor(theme.dim);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, theme.text));
        MenuItem menuItem = toolbar.getMenu().findItem(R.id.term_sessions_menu);
        if (menuItem != null) menuItem.setIcon(UiUtils.tint(this, R.drawable.ic_menu, theme.text));
        if (bottom.getChildCount() > 0) {
            bottom.getChildAt(0).setBackgroundColor(theme.border);
        }
        LinearLayout keys = findViewById(R.id.term_keys);
        for (int i = 0; i < keys.getChildCount(); i++) {
            View key = keys.getChildAt(i);
            if (key instanceof TextView) {
                ((TextView) key).setTextColor(theme.text);
                key.setBackground(keyBackground(theme));
            }
        }
        applyStatusBarTheme(theme);
        View drawerPanel = findViewById(R.id.term_drawer_panel);
        drawerPanel.setBackgroundColor(theme.surface);
        TextView sessionsTitle = findViewById(R.id.term_sessions_title);
        sessionsTitle.setTextColor(theme.text);
        TextView settingsLabel = findViewById(R.id.term_settings_label);
        settingsLabel.setTextColor(theme.text);
        ImageButton newSession = findViewById(R.id.term_new_session);
        newSession.setImageDrawable(UiUtils.tint(this, R.drawable.ic_add, theme.text));
        android.widget.ImageView settingsIcon = findViewById(R.id.term_settings_icon);
        settingsIcon.setImageDrawable(UiUtils.tint(this, R.drawable.ic_settings, theme.text));
        if (drawerPanel instanceof ViewGroup) {
            ViewGroup dg = (ViewGroup) drawerPanel;
            if (dg.getChildCount() > 4) {
                dg.getChildAt(1).setBackgroundColor(theme.border);
                dg.getChildAt(3).setBackgroundColor(theme.border);
            }
        }
        if (popup != null && popup.getContentView() != null) {
            View content = popup.getContentView();
            content.setBackground(panel(theme.surface, theme.border));
            if (content instanceof ViewGroup && ((ViewGroup) content).getChildCount() > 1) {
                ((ViewGroup) content).getChildAt(1).setBackgroundColor(theme.border);
            }
            TextView header = content.findViewById(R.id.completion_header);
            if (header != null) {
                header.setBackgroundColor(theme.surface);
                header.setTextColor(theme.dim);
                header.setText(L.s(this, "补全 · 点击插入", "Completion · tap to insert"));
            }
        }
        if (current != null) {
            output.setText(current.buffer);
            updatePrompt();
            scrollBottom();
        }
        if (!TerminalPrefs.autocomplete(this)) hideCompletion();
        input.setHint(term()
                ? L.s(this, "输入命令，回车发送到 Termux", "Type a command, Enter sends to Termux")
                : L.s(this, "输入命令，回车执行", "Enter a command"));
        toolbar.setTitle(L.s(this, "命令行", "Terminal"));
        if (TerminalPrefs.keepAwake(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private android.graphics.drawable.Drawable panel(int color, int border) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(color);
        g.setStroke(UiUtils.dp(this, 1), border);
        g.setCornerRadius(0);
        return g;
    }

    private android.graphics.drawable.Drawable keyBackground(TerminalTheme t) {
        android.content.res.ColorStateList rippleColor = android.content.res.ColorStateList.valueOf(
                t.light ? 0x22000000 : 0x33FFFFFF);
        return new android.graphics.drawable.RippleDrawable(rippleColor, panel(t.surface, t.border), null);
    }

    private void applyStatusBarTheme(TerminalTheme t) {
        int color = t.bg;
        try {
            if (android.os.Build.VERSION.SDK_INT < 23 && t.light) {
                color = 0xFF3A3C41;
            }
            getWindow().setStatusBarColor(color);
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                View decor = getWindow().getDecorView();
                int flags = decor.getSystemUiVisibility();
                if (t.light && color != 0xFF3A3C41) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                }
                decor.setSystemUiVisibility(flags);
            }
        } catch (Exception ignored) {
        }
    }

    private Typeface loadTypeface(String id) {
        String asset = TerminalPrefs.fontAsset(id);
        if (TextUtils.isEmpty(asset)) return Typeface.MONOSPACE;
        try {
            return Typeface.createFromAsset(getAssets(), asset);
        } catch (Exception e) {
            return Typeface.MONOSPACE;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyPrefs();
        if (TerminalPrefs.mirrorMode(this).equals(TerminalPrefs.MIRROR_AUTO)
                && TextUtils.isEmpty(TerminalPrefs.mirrorName(this))) {
            MirrorManager.detect(this, null);
        }
    }

    private void updatePrompt() {
        if (current == null) return;
        prompt.setText(shortCwd(current.cwd) + " $");
        toolbar.setSubtitle(current.termux
                ? L.s(this, "会话 " + current.id + " · Termux", "Session " + current.id + " · Termux")
                : L.s(this, "会话 " + current.id + " · 系统 Shell", "Session " + current.id + " · system shell"));
    }

    private String shortCwd(String cwd) {
        if (cwd == null) return "~";
        String home = home();
        if (cwd.equals(home)) return "~";
        if (cwd.startsWith(home + "/")) return "~/" + cwd.substring(home.length() + 1);
        return cwd;
    }

    private String home() {
        return App.homeDir().getAbsolutePath();
    }

    private void send() {
        if (current == null) return;
        String cmd = input.getText().toString();
        if (cmd.trim().length() == 0) return;
        hideCompletion();
        if (TerminalPrefs.haptic(this)) input.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
        if (current.termux) {
            TermuxPty pty = current.pty;
            if (pty == null) {
                UiUtils.toast(this, L.s(this, "Termux 环境正在启动", "Termux is starting"));
                return;
            }
            current.history.add(cmd);
            historyIndex = current.history.size();
            input.setText("");
            sessionAdapter.notifyDataSetChanged();
            pty.write(cmd + "\n");
            return;
        }
        if (!current.running) {
            echo(cmd);
            current.history.add(cmd);
            historyIndex = current.history.size();
            input.setText("");
            sessionAdapter.notifyDataSetChanged();
            ShellEngine.execute(this, current, cmd, this);
        } else {
            UiUtils.toast(this, L.s(this, "命令正在执行，按 ⌃C 可中断", "Command is running, press ⌃C to interrupt"));
        }
    }

    private void echo(String cmd) {
        String p = shortCwd(current.cwd) + " $";
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(p);
        sb.setSpan(new ForegroundColorSpan(theme.accent), 0, p.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.append(" ");
        int start = sb.length();
        sb.append(cmd);
        if (TerminalPrefs.highlightCommands(this)) {
            sb.setSpan(new ForegroundColorSpan(theme.text), start, sb.length(),
                    SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sb.append("\n");
        current.buffer.append(sb);
        output.append(sb);
        scrollBottom();
    }

    private void interrupt() {
        if (current == null) return;
        if (current.termux) {
            if (current.pty != null) current.pty.write("\u0003");
            input.requestFocus();
            return;
        }
        current.cancel();
        current.buffer.append("^C\n");
        output.append("^C\n");
        scrollBottom();
        input.requestFocus();
    }

    private void scrollBottom() {
        scroll.post(new Runnable() {
            @Override
            public void run() {
                scroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    private boolean atBottom() {
        View child = scroll.getChildAt(0);
        if (child == null) return true;
        return scroll.getScrollY() + scroll.getHeight() >= child.getMeasuredHeight() - UiUtils.dp(this, 30);
    }

    private void showKeyboard() {
        android.view.inputmethod.InputMethodManager imm =
                (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }

    private void insert(String text) {
        int start = Math.max(0, input.getSelectionStart());
        input.getText().insert(start, text);
    }

    private void moveCursor(int delta) {
        int pos = input.getSelectionStart();
        int target = Math.max(0, Math.min(input.getText().length(), pos + delta));
        input.setSelection(target);
    }

    private void historyUp() {
        if (current == null || current.history.isEmpty()) return;
        if (historyIndex > 0) historyIndex--;
        String cmd = current.history.get(historyIndex);
        input.setText(cmd);
        input.setSelection(cmd.length());
    }

    private void historyDown() {
        if (current == null || current.history.isEmpty()) return;
        if (historyIndex < current.history.size() - 1) {
            historyIndex++;
            String cmd = current.history.get(historyIndex);
            input.setText(cmd);
            input.setSelection(cmd.length());
        } else {
            historyIndex = current.history.size();
            input.setText("");
        }
    }

    private void refreshCompletion() {
        refreshCompletion(false);
    }

    private void refreshCompletion(boolean force) {
        if (current == null || !TerminalPrefs.autocomplete(this)) {
            hideCompletion();
            return;
        }
        String text = input.getText().toString();
        if (text.length() == 0) {
            hideCompletion();
            return;
        }
        List<Completer.Item> result = Completer.complete(this, current, text, TerminalPrefs.maxSuggestions(this));
        if (result.isEmpty()) {
            hideCompletion();
            return;
        }
        completions.clear();
        completions.addAll(result);
        completionAdapter.notifyDataSetChanged();
        completionList.scrollToPosition(0);
        showPopup(force);
    }

    private void showPopup(boolean force) {
        if (isFinishing()) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int width = screenWidth - UiUtils.dp(this, 16);
        int rowHeight = UiUtils.dp(this, 52);
        int rows = Math.min(completions.size(), 4);
        int height = Math.min(UiUtils.dp(this, 33) + rows * rowHeight, UiUtils.dp(this, 241));
        ViewGroup.LayoutParams lp = completionList.getLayoutParams();
        lp.height = rows * rowHeight;
        completionList.setLayoutParams(lp);
        popup.setWidth(width);
        popup.setHeight(height);
        int[] loc = new int[2];
        bottom.getLocationOnScreen(loc);
        int y = loc[1] - height - UiUtils.dp(this, 4);
        if (y < UiUtils.dp(this, 60)) y = loc[1] + bottom.getHeight() + UiUtils.dp(this, 4);
        try {
            if (popup.isShowing()) {
                popup.update(UiUtils.dp(this, 8), y, width, height);
            } else {
                popup.showAtLocation(getWindow().getDecorView(), Gravity.NO_GRAVITY,
                        UiUtils.dp(this, 8), y);
            }
            popupVisible = true;
        } catch (Exception ignored) {
        }
    }

    private void hideCompletion() {
        if (popup != null && popup.isShowing()) {
            try {
                popup.dismiss();
            } catch (Exception ignored) {
            }
        }
        popupVisible = false;
    }

    @Override
    public void onOutput(ShellSession session, CharSequence text) {
        boolean bottomNow = session == current && atBottom();
        session.buffer.append(text);
        if (session == current) {
            output.append(text);
            if (bottomNow) scrollBottom();
        }
    }

    @Override
    public void onClear(ShellSession session) {
        session.clear();
        if (session == current) output.setText("");
    }

    @Override
    public void onExit(ShellSession session) {
        closeSession(session);
    }

    @Override
    public void onOpenPath(String path) {
        OpenHelper.open(this, new File(path));
    }

    @Override
    public void onOpenAppSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void onToast(String message) {
        UiUtils.toast(this, message);
    }

    @Override
    public void onFinished(ShellSession session) {
        if (session == current) {
            updatePrompt();
            sessionAdapter.notifyDataSetChanged();
            input.post(new Runnable() {
                @Override
                public void run() {
                    input.requestFocus();
                }
            });
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && current != null && !current.running) {
            input.postDelayed(new Runnable() {
                @Override
                public void run() {
                    input.requestFocus();
                }
            }, 60);
        }
    }

    private void closeSession(ShellSession s) {
        s.cancel();
        TerminalSessions.remove(s);
        sessionAdapter.notifyDataSetChanged();
        if (current == s) {
            if (TerminalSessions.SESSIONS.isEmpty()) {
                current = TerminalSessions.create(initialCwd != null ? initialCwd : home());
                sessionAdapter.notifyDataSetChanged();
                showSession(current, true);
            } else {
                showSession(TerminalSessions.SESSIONS.get(TerminalSessions.SESSIONS.size() - 1), true);
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.END)) {
            drawer.closeDrawer(GravityCompat.END);
            return;
        }
        if (popupVisible) {
            hideCompletion();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onPause() {
        super.onPause();
        hideCompletion();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TermuxPty.setUiListener(null);
    }

    private class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final TextView title;
            final TextView cwd;
            final ImageButton close;
            final View root;

            Holder(View v) {
                super(v);
                root = v.findViewById(R.id.session_root);
                title = v.findViewById(R.id.session_title);
                cwd = v.findViewById(R.id.session_cwd);
                close = v.findViewById(R.id.session_close);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_terminal_session, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final ShellSession s = TerminalSessions.SESSIONS.get(position);
            h.title.setText((s == current ? "● " : "") + s.title
                    + (s.running ? L.s(TerminalActivity.this, " · 运行中", " · running") : ""));
            h.cwd.setText(s.cwd);
            h.title.setTextColor(s == current ? theme.accent : theme.text);
            h.cwd.setTextColor(theme.dim);
            h.root.setBackgroundColor(s == current ? theme.selection : android.graphics.Color.TRANSPARENT);
            h.close.setImageDrawable(UiUtils.tint(TerminalActivity.this, R.drawable.ic_close, theme.dim));
            h.root.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showSession(s, true);
                    drawer.closeDrawer(GravityCompat.END);
                }
            });
            h.close.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeSession(s);
                }
            });
        }

        @Override
        public int getItemCount() {
            return TerminalSessions.SESSIONS.size();
        }
    }

    private class CompletionAdapter extends RecyclerView.Adapter<CompletionAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final TextView text;
            final TextView desc;

            Holder(View v) {
                super(v);
                text = v.findViewById(R.id.comp_text);
                desc = v.findViewById(R.id.comp_desc);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_completion, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final Completer.Item item = completions.get(position);
            h.text.setText(item.display);
            h.text.setTextColor(theme != null ? theme.accent : UiUtils.color(TerminalActivity.this, R.color.accent));
            h.desc.setText(item.desc);
            h.desc.setTextColor(theme != null ? theme.dim : UiUtils.color(TerminalActivity.this, R.color.text_tertiary));
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    input.setText(item.value);
                    input.setSelection(input.getText().length());
                    input.requestFocus();
                    hideCompletion();
                    if (TerminalPrefs.haptic(TerminalActivity.this)) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return completions.size();
        }
    }
}
