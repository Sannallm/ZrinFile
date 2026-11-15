package com.zrinfile.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.term.L;
import com.zrinfile.app.term.MirrorManager;
import com.zrinfile.app.term.TerminalPrefs;
import com.zrinfile.app.term.TerminalSessions;
import com.zrinfile.app.term.TermuxInstaller;
import com.zrinfile.app.term.TermuxPaths;
import com.zrinfile.app.util.UiUtils;

public class TerminalSettingsActivity extends AppCompatActivity {

    private LinearLayout container;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_terminal_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        container = findViewById(R.id.ts_container);
        build();
        maybeDetectMirror();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private String t(String zh, String en) {
        return L.s(this, zh, en);
    }

    private void build() {
        container.removeAllViews();
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(t("命令行设置", "Terminal settings"));
        toolbar.setSubtitle(t("仅作用于命令行，不影响文件管理器", "Applies to the terminal only"));

        section(t("运行时", "Runtime"));
        valueRow(t("终端运行时", "Terminal runtime"),
                runtimeName(), new Runnable() {
                    @Override
                    public void run() {
                        chooseRuntime();
                    }
                });
        if (TermuxPaths.supported()) {
            actionRow(t("重新安装 Termux 环境", "Reinstall Termux environment"),
                    t("删除已解压的 bootstrap，下次进入命令行时重新解压", "Remove the unpacked bootstrap"),
                    new Runnable() {
                        @Override
                        public void run() {
                            UiUtils.confirm(TerminalSettingsActivity.this,
                                    t("重新安装 Termux 环境", "Reinstall Termux environment"),
                                    t("将删除已解压的 Linux 环境与 home 目录，下次进入命令行时重新安装。",
                                            "This deletes the unpacked environment and home directory."),
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            TerminalSessions.reset();
                                            TermuxInstaller.uninstall(TerminalSettingsActivity.this);
                                            UiUtils.toast(TerminalSettingsActivity.this, t("已清除，返回命令行可重新安装",
                                                    "Cleared. Reopen the terminal to install again"));
                                        }
                                    });
                        }
                    });
        }

        section(t("补全", "Completion"));
        switchRow(t("自动补全", "在输入框上方显示补全建议气泡"), t("输入时显示完整候选内容，点击插入", "Show full candidates above the input; tap to insert"),
                TerminalPrefs.autocomplete(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        TerminalPrefs.setAutocomplete(TerminalSettingsActivity.this, isChecked);
                    }
                });
        valueRow(t("最大建议数", "Maximum suggestions"), String.valueOf(TerminalPrefs.maxSuggestions(this)), new Runnable() {
            @Override
            public void run() {
                final int[] values = {20, 40, 60, 100, 200};
                String[] items = {"20", "40", "60", "100", "200"};
                int current = 2;
                for (int i = 0; i < values.length; i++) if (values[i] == TerminalPrefs.maxSuggestions(TerminalSettingsActivity.this)) current = i;
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("最大建议数", "Maximum suggestions"))
                        .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                TerminalPrefs.setMaxSuggestions(TerminalSettingsActivity.this, values[which]);
                                dialog.dismiss();
                                build();
                            }
                        })
                        .show();
            }
        });

        section(t("外观", "Appearance"));
        valueRow(t("语言", "Language"), TerminalPrefs.english(this) ? "English" : "中文", new Runnable() {
            @Override
            public void run() {
                final String[] items = {"中文", "English"};
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("语言", "Language"))
                        .setSingleChoiceItems(items, TerminalPrefs.english(TerminalSettingsActivity.this) ? 1 : 0,
                                new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dialog, int which) {
                                        TerminalPrefs.setEnglish(TerminalSettingsActivity.this, which == 1);
                                        dialog.dismiss();
                                        recreate();
                                    }
                                })
                        .show();
            }
        });
        valueRow(t("字体", "Font"), fontName(TerminalPrefs.font(this)), new Runnable() {
            @Override
            public void run() {
                final String[] ids = {TerminalPrefs.FONT_JETBRAINS, TerminalPrefs.FONT_FIRA,
                        TerminalPrefs.FONT_SOURCE, TerminalPrefs.FONT_UBUNTU, TerminalPrefs.FONT_SYSTEM};
                String[] items = {"JetBrains Mono", "Fira Code", "Source Code Pro", "Ubuntu Mono", "系统等宽"};
                int current = 0;
                for (int i = 0; i < ids.length; i++) if (ids[i].equals(TerminalPrefs.font(TerminalSettingsActivity.this))) current = i;
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("命令行字体", "Terminal font"))
                        .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                TerminalPrefs.setFont(TerminalSettingsActivity.this, ids[which]);
                                dialog.dismiss();
                                build();
                            }
                        })
                        .show();
            }
        });
        valueRow(t("字号", "Font size"), TerminalPrefs.fontSize(this) + " sp", new Runnable() {
            @Override
            public void run() {
                final int[] sizes = {11, 12, 13, 14, 15, 16, 18, 20};
                String[] items = new String[sizes.length];
                for (int i = 0; i < sizes.length; i++) items[i] = sizes[i] + " sp";
                int current = 2;
                for (int i = 0; i < sizes.length; i++) if (sizes[i] == TerminalPrefs.fontSize(TerminalSettingsActivity.this)) current = i;
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("字号", "Font size"))
                        .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                TerminalPrefs.setFontSize(TerminalSettingsActivity.this, sizes[which]);
                                dialog.dismiss();
                                build();
                            }
                        })
                        .show();
            }
        });
        valueRow(t("行距", "Line spacing"), spacingName(TerminalPrefs.lineSpacing(this)), new Runnable() {
            @Override
            public void run() {
                final int[] values = {0, 2, 4, 6};
                String[] items = {t("紧凑", "Compact"), t("标准", "Normal"), t("宽松", "Relaxed"), t("很宽", "Wide")};
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("行距", "Line spacing"))
                        .setSingleChoiceItems(items, 1, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                TerminalPrefs.setLineSpacing(TerminalSettingsActivity.this, values[which]);
                                dialog.dismiss();
                                build();
                            }
                        })
                        .show();
            }
        });
        valueRow(t("配色方案", "Color scheme"), schemeName(TerminalPrefs.scheme(this)), new Runnable() {
            @Override
            public void run() {
                final String[] ids = {"unity", "graphite", "light", "green"};
                String[] items = {t("Unity 深色", "Unity dark"), t("石墨", "Graphite"), t("浅色", "Light"), t("终端绿", "Terminal green")};
                int current = 0;
                for (int i = 0; i < ids.length; i++) if (ids[i].equals(TerminalPrefs.scheme(TerminalSettingsActivity.this))) current = i;
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("配色方案", "Color scheme"))
                        .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                TerminalPrefs.setScheme(TerminalSettingsActivity.this, ids[which]);
                                dialog.dismiss();
                                build();
                            }
                        })
                        .show();
            }
        });
        switchRow(t("命令高亮", "Command highlight"), t("为输入的命令着色", "Colorize the entered command"),
                TerminalPrefs.highlightCommands(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        TerminalPrefs.setHighlightCommands(TerminalSettingsActivity.this, isChecked);
                    }
                });
        switchRow(t("错误红色高亮", "Red error highlight"), t("识别 not found、denied 等错误并标红", "Highlight errors such as not found and denied"),
                TerminalPrefs.highlightErrors(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        TerminalPrefs.setHighlightErrors(TerminalSettingsActivity.this, isChecked);
                    }
                });

        section(t("行为", "Behavior"));
        valueRow(t("回滚行数", "Scrollback lines"), String.valueOf(TerminalPrefs.scrollback(this)), new Runnable() {
            @Override
            public void run() {
                final int[] values = {1000, 2500, 5000, 10000};
                String[] items = {"1000", "2500", "5000", "10000"};
                int current = 1;
                for (int i = 0; i < values.length; i++) if (values[i] == TerminalPrefs.scrollback(TerminalSettingsActivity.this)) current = i;
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("回滚行数", "Scrollback lines"))
                        .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                TerminalPrefs.setScrollback(TerminalSettingsActivity.this, values[which]);
                                dialog.dismiss();
                                build();
                            }
                        })
                        .show();
            }
        });
        switchRow(t("保持屏幕常亮", "Keep screen on"), t("执行长时间命令时防止息屏", "Prevent screen sleep during long commands"),
                TerminalPrefs.keepAwake(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        TerminalPrefs.setKeepAwake(TerminalSettingsActivity.this, isChecked);
                    }
                });
        switchRow(t("触感反馈", "Haptic feedback"), t("按键与补全时轻微震动", "Subtle vibration on keys and completions"),
                TerminalPrefs.haptic(this), new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        TerminalPrefs.setHaptic(TerminalSettingsActivity.this, isChecked);
                    }
                });

        section(t("下载源", "Download mirror"));
        String mode = TerminalPrefs.MIRROR_AUTO.equals(TerminalPrefs.mirrorMode(this))
                ? t("自动测速", "Auto (latency)") : t("手动指定", "Manual");
        String name = TerminalPrefs.mirrorName(this);
        long latency = TerminalPrefs.mirrorLatency(this);
        valueRow(t("当前软件源", "Current mirror"), mode + (name.length() > 0 ? " · " + name : ""), new Runnable() {
            @Override
            public void run() {
                UiUtils.alert(TerminalSettingsActivity.this, t("软件源", "Mirror"),
                        t("当前地址:\n", "Current URL:\n") + (TerminalPrefs.mirrorUrl(TerminalSettingsActivity.this).length() > 0
                                ? TerminalPrefs.mirrorUrl(TerminalSettingsActivity.this)
                                : t("尚未选择，将自动测速", "Not selected yet; auto detection will run")));
            }
        });
        valueRow(t("选择软件源", "Choose mirror"), t("手动指定国内镜像", "Pick a domestic mirror"), new Runnable() {
            @Override
            public void run() {
                String[] items = new String[MirrorManager.MIRRORS.size()];
                for (int i = 0; i < items.length; i++) items[i] = MirrorManager.MIRRORS.get(i).name;
                new AlertDialog.Builder(TerminalSettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle(t("选择软件源", "Choose mirror"))
                        .setItems(items, new android.content.DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface dialog, int which) {
                                final MirrorManager.Mirror m = MirrorManager.MIRRORS.get(which);
                                MirrorManager.saveManual(TerminalSettingsActivity.this, m);
                                UiUtils.toast(TerminalSettingsActivity.this, t("正在测速…", "Measuring latency…"));
                                App.io().execute(new Runnable() {
                                    @Override
                                    public void run() {
                                        final long ms = MirrorManager.latency(m.host);
                                        TerminalPrefs.setMirrorLatency(TerminalSettingsActivity.this, ms);
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                UiUtils.toast(TerminalSettingsActivity.this,
                                                        m.name + (ms > 0 ? " · " + ms + " ms" : t(" · 连接失败", " · unreachable")));
                                                build();
                                            }
                                        });
                                    }
                                });
                            }
                        })
                        .show();
            }
        });
        actionRow(t("自定义地址", "Custom URL"),
                TerminalPrefs.mirrorUrl(this).length() > 0 ? TerminalPrefs.mirrorUrl(this) : t("输入任意镜像地址", "Enter any mirror URL"),
                new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.showInput(TerminalSettingsActivity.this, t("自定义软件源", "Custom mirror"),
                                t("镜像地址", "Mirror URL"), TerminalPrefs.mirrorUrl(TerminalSettingsActivity.this),
                                "https://…", new UiUtils.InputCallback() {
                                    @Override
                                    public void onInput(String text) {
                                        if (!text.startsWith("http://") && !text.startsWith("https://")) {
                                            UiUtils.toast(TerminalSettingsActivity.this, t("地址需以 http 开头", "URL must start with http"));
                                            return;
                                        }
                                        MirrorManager.saveCustom(TerminalSettingsActivity.this, text);
                                        UiUtils.toast(TerminalSettingsActivity.this, t("已保存自定义源", "Custom mirror saved"));
                                        build();
                                    }
                                });
                    }
                });
        actionRow(t("还原默认值", "Restore default"),
                t("重新根据位置与延迟选择最优源", "Re-detect the best mirror by latency"),
                new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.confirm(TerminalSettingsActivity.this, t("还原默认值", "Restore default"),
                                t("将清除当前选择，并重新测速选择延迟最低的镜像。", "This clears the current choice and re-detects the lowest-latency mirror."),
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        TerminalPrefs.setMirrorMode(TerminalSettingsActivity.this, TerminalPrefs.MIRROR_AUTO);
                                        TerminalPrefs.setMirrorName(TerminalSettingsActivity.this, "");
                                        TerminalPrefs.setMirrorUrl(TerminalSettingsActivity.this, "");
                                        TerminalPrefs.setMirrorLatency(TerminalSettingsActivity.this, -1);
                                        detectAndShow();
                                    }
                                });
                    }
                });
        TextView mirrorInfo = new TextView(this);
        mirrorInfo.setText(t("下载地址: ", "URL: ") + (TerminalPrefs.mirrorUrl(this).length() > 0
                ? TerminalPrefs.mirrorUrl(this) : t("未设置", "not set"))
                + (latency > 0 ? "\n" + t("延迟: ", "Latency: ") + latency + " ms" : ""));
        mirrorInfo.setTextSize(11);
        mirrorInfo.setTextColor(UiUtils.color(this, R.color.text_tertiary));
        mirrorInfo.setPadding(UiUtils.dp(this, 18), UiUtils.dp(this, 2), UiUtils.dp(this, 18), UiUtils.dp(this, 10));
        container.addView(mirrorInfo);

        section(t("其他", "Other"));
        actionRow(t("开源字体与许可证", "Font licenses"), t("查看 OFL、UFL、MIT、Apache、GPL 全文", "Read OFL, UFL, MIT, Apache and GPL texts"), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(TerminalSettingsActivity.this, LicenseActivity.class));
            }
        });
        actionRow(t("恢复命令行默认值", "Reset terminal settings"), t("不影响文件管理器设置", "Does not affect the file manager"), new Runnable() {
            @Override
            public void run() {
                UiUtils.confirm(TerminalSettingsActivity.this, t("恢复默认值", "Reset"), t("命令行设置将恢复默认，字体与补全偏好会被清除。", "Terminal preferences will be reset."), new Runnable() {
                    @Override
                    public void run() {
                        TerminalPrefs.get(TerminalSettingsActivity.this).edit().clear().apply();
                        detectAndShow();
                        build();
                    }
                });
            }
        });

        TextView footer = new TextView(this);
        footer.setText(t("ZrinFile Shell · 基于系统 Shell · 补全与命令桥接为本应用实现",
                "ZrinFile Shell · system shell · completion and bridge implemented by this app"));
        footer.setTextSize(11);
        footer.setTextColor(UiUtils.color(this, R.color.text_tertiary));
        footer.setGravity(android.view.Gravity.CENTER);
        footer.setPadding(0, UiUtils.dp(this, 20), 0, UiUtils.dp(this, 10));
        container.addView(footer);
    }

    private void maybeDetectMirror() {
        if (TerminalPrefs.MIRROR_AUTO.equals(TerminalPrefs.mirrorMode(this))
                && TerminalPrefs.mirrorName(this).length() == 0) {
            detectAndShow();
        }
    }

    private void detectAndShow() {
        UiUtils.toast(this, t("正在测速并选择最优节点…", "Detecting the fastest mirror…"));
        MirrorManager.detect(this, new MirrorManager.Callback() {
            @Override
            public void onResult(MirrorManager.Result result) {
                if (isFinishing()) return;
                UiUtils.toast(TerminalSettingsActivity.this,
                        result.mirror.name + (result.latency > 0 ? " · " + result.latency + " ms" : ""));
                build();
            }
        });
    }

    private void section(String title) {
        TextView tv = (TextView) LayoutInflater.from(this).inflate(R.layout.row_section, container, false);
        tv.setText(title);
        container.addView(tv);
    }

    private View row(String title, String summary) {
        View v = LayoutInflater.from(this).inflate(R.layout.row_setting, container, false);
        ((TextView) v.findViewById(R.id.row_title)).setText(title);
        TextView sub = v.findViewById(R.id.row_summary);
        if (summary == null || summary.length() == 0) sub.setVisibility(View.GONE);
        else {
            sub.setVisibility(View.VISIBLE);
            sub.setText(summary);
        }
        container.addView(v);
        return v;
    }

    private void switchRow(String title, String summary, boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        View v = row(title, summary);
        final SwitchCompat sw = v.findViewById(R.id.row_switch);
        sw.setVisibility(View.VISIBLE);
        sw.setId(View.generateViewId());
        sw.setSaveEnabled(false);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener(listener);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sw.setChecked(!sw.isChecked());
            }
        });
    }

    private void valueRow(String title, String value, final Runnable action) {
        View v = row(title, null);
        TextView tv = v.findViewById(R.id.row_value);
        tv.setVisibility(View.VISIBLE);
        tv.setText(value);
        v.findViewById(R.id.row_chevron).setVisibility(View.VISIBLE);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
    }

    private void actionRow(String title, String summary, final Runnable action) {
        View v = row(title, summary);
        v.findViewById(R.id.row_chevron).setVisibility(View.VISIBLE);
        v.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
    }

    private String runtimeName() {
        if (!TermuxPaths.supported()) return t("系统 Shell（Termux 需 Android 7.0+）", "System shell (Termux needs Android 7+)");
        return TerminalPrefs.RUNTIME_TERMUX.equals(TerminalPrefs.runtime(this)) ? "Termux" : t("系统 Shell", "System shell");
    }

    private void chooseRuntime() {
        if (!TermuxPaths.supported()) {
            UiUtils.toast(this, t("Termux 运行时需要 Android 7.0 及以上", "Termux runtime requires Android 7.0+"));
            return;
        }
        final String[] ids = {TerminalPrefs.RUNTIME_TERMUX, TerminalPrefs.RUNTIME_SHELL};
        String[] items = {"Termux（完整 Linux 环境）", t("系统 Shell（轻量）", "System shell (light)")};
        int current = TerminalPrefs.RUNTIME_TERMUX.equals(TerminalPrefs.runtime(this)) ? 0 : 1;
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle(t("终端运行时", "Terminal runtime"))
                .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        TerminalPrefs.setRuntime(TerminalSettingsActivity.this, ids[which]);
                        TerminalSessions.reset();
                        dialog.dismiss();
                        build();
                        UiUtils.toast(TerminalSettingsActivity.this,
                                t("已切换，返回命令行后生效", "Switched. Reopen the terminal to apply"));
                    }
                })
                .show();
    }

    private String fontName(String id) {
        switch (id) {
            case TerminalPrefs.FONT_FIRA: return "Fira Code";
            case TerminalPrefs.FONT_SOURCE: return "Source Code Pro";
            case TerminalPrefs.FONT_UBUNTU: return "Ubuntu Mono";
            case TerminalPrefs.FONT_SYSTEM: return t("系统等宽", "System mono");
            default: return "JetBrains Mono";
        }
    }

    private String schemeName(String id) {
        switch (id) {
            case "graphite": return t("石墨", "Graphite");
            case "light": return t("浅色", "Light");
            case "green": return t("终端绿", "Terminal green");
            default: return t("Unity 深色", "Unity dark");
        }
    }

    private String spacingName(int value) {
        if (value <= 0) return t("紧凑", "Compact");
        if (value >= 6) return t("很宽", "Wide");
        if (value >= 4) return t("宽松", "Relaxed");
        return t("标准", "Normal");
    }
}
