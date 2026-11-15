package com.zrinfile.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.zrinfile.app.App;
import com.zrinfile.app.BuildConfig;
import com.zrinfile.app.R;
import com.zrinfile.app.util.Prefs;
import com.zrinfile.app.util.UiUtils;
import com.zrinfile.app.update.UpdateChecker;
import com.zrinfile.app.update.UpdateInfo;

import java.io.File;

public class SettingsActivity extends AppCompatActivity {

    private LinearLayout container;
    private boolean checking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_settings);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        container = findViewById(R.id.settings_container);
        build();
    }

    @Override
    protected void onResume() {
        super.onResume();
        build();
    }

    private void build() {
        container.removeAllViews();
        section("外观");
        valueRow("主题", themeName(Prefs.theme(this)), new Runnable() {
            @Override
            public void run() {
                chooseTheme();
            }
        });
        switchRow("默认网格视图", "使用三列网格显示文件", Prefs.grid(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setGrid(SettingsActivity.this, isChecked);
            }
        });
        switchRow("显示隐藏文件", "以 . 开头的文件与目录", Prefs.showHidden(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setShowHidden(SettingsActivity.this, isChecked);
            }
        });
        switchRow("列表动画", "加载时轻微淡入与位移动画", Prefs.animations(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setAnimations(SettingsActivity.this, isChecked);
            }
        });
        switchRow("显示文件大小", "在列表右侧显示大小", Prefs.showSize(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setShowSize(SettingsActivity.this, isChecked);
            }
        });
        switchRow("显示修改时间", "在副标题中显示日期", Prefs.showDate(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setShowDate(SettingsActivity.this, isChecked);
            }
        });

        section("文件操作");
        switchRow("删除前确认", "避免误删重要文件", Prefs.confirmDelete(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setConfirmDelete(SettingsActivity.this, isChecked);
            }
        });
        switchRow("删除到回收站", "关闭后删除将不可恢复", Prefs.useTrash(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setUseTrash(SettingsActivity.this, isChecked);
            }
        });
        valueRow("回收站保留天数", trashDaysName(Prefs.trashDays(this)), new Runnable() {
            @Override
            public void run() {
                chooseTrashDays();
            }
        });
        switchRow("文件夹优先", "排序时文件夹总在前面", Prefs.foldersFirst(this), new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                Prefs.setFoldersFirst(SettingsActivity.this, isChecked);
            }
        });
        valueRow("默认排序", sortName(Prefs.sortBy(this)) + (Prefs.sortAsc(SettingsActivity.this) ? " · 升序" : " · 降序"), new Runnable() {
            @Override
            public void run() {
                chooseSort();
            }
        });

        section("权限与系统");
        valueRow("存储权限", hasStorage() ? "已授予" : "未授予", new Runnable() {
            @Override
            public void run() {
                if (hasStorage()) {
                    UiUtils.toast(SettingsActivity.this, "存储权限已授予");
                } else {
                    ActivityCompat.requestPermissions(SettingsActivity.this, new String[]{
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE}, 201);
                }
            }
        });
        if (Build.VERSION.SDK_INT >= 30) {
            actionRow("所有文件访问", "Android 11 及以上可申请完整存储访问", new Runnable() {
                @Override
                public void run() {
                    try {
                        Intent i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                        i.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(i);
                    } catch (Exception e) {
                        try {
                            startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                        } catch (Exception ignored) {
                        }
                    }
                }
            });
        }
        actionRow("忽略电池优化", "避免大文件传输时被系统中断", new Runnable() {
            @Override
            public void run() {
                try {
                    Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    i.setData(Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                } catch (Exception e) {
                    UiUtils.toast(SettingsActivity.this, "系统未提供该设置入口");
                }
            }
        });
        actionRow("清理缩略图缓存", "释放图片缩略图占用的内存与缓存文件", new Runnable() {
            @Override
            public void run() {
                ThumbLoader.clear();
                File dir = App.thumbCache();
                File[] files = dir.listFiles();
                if (files != null) for (File f : files) f.delete();
                UiUtils.toast(SettingsActivity.this, "已清理缩略图缓存");
            }
        });

        section("命令行");
        valueRow("命令行设置", "字体、补全、配色与软件源", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, TerminalSettingsActivity.class));
            }
        });
        section("其他");
        actionRow("检查更新", "当前版本 v" + BuildConfig.VERSION_NAME, new Runnable() {
            @Override
            public void run() {
                checkUpdate();
            }
        });
        actionRow("开源许可", "MIT、Apache-2.0、OFL、GPL 等", new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(SettingsActivity.this, LicenseActivity.class));
            }
        });
        actionRow("关于 ZrinFile 文件管理", "v" + BuildConfig.VERSION_NAME + " · 通用 32/64 位", new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(SettingsActivity.this, R.style.Theme_ZrinFile_Dialog)
                        .setTitle("ZrinFile 文件管理")
                        .setMessage("版本: " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")\n"
                                + "最低系统: Android 5.0 (API 21)\n"
                                + "架构: 通用安装包，兼容 32 位与 64 位设备\n"
                                + "引擎: Android 系统 Shell + 原生文件访问\n\n"
                                + getString(R.string.about_desc) + "\n\n"
                                + "本项目源代码以 MIT 许可证发布。")
                        .setPositiveButton("关闭", null)
                        .show();
            }
        });
        actionRow("恢复默认设置", "重置文件管理器偏好，不影响回收站与文件", new Runnable() {
            @Override
            public void run() {
                UiUtils.confirm(SettingsActivity.this, "恢复默认设置", "所有文件管理器偏好将恢复默认值。", new Runnable() {
                    @Override
                    public void run() {
                        Prefs.get(SettingsActivity.this).edit().clear().apply();
                        App.get().applyTheme();
                        UiUtils.toast(SettingsActivity.this, "已恢复默认设置");
                        recreate();
                    }
                });
            }
        });
        TextView footer = new TextView(this);
        footer.setText("ZrinFile 文件管理 · 免费使用 · 无广告 · 无账号");
        footer.setTextSize(11);
        footer.setTextColor(UiUtils.color(this, R.color.text_tertiary));
        footer.setGravity(android.view.Gravity.CENTER);
        footer.setPadding(0, UiUtils.dp(this, 22), 0, UiUtils.dp(this, 10));
        container.addView(footer);
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
        if (summary == null || summary.length() == 0) {
            sub.setVisibility(View.GONE);
        } else {
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

    private boolean hasStorage() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 201) {
            UiUtils.toast(this, grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    ? "已授予存储权限" : "权限未授予");
            build();
        }
    }

    private void checkUpdate() {
        if (checking) return;
        checking = true;
        UiUtils.toast(this, "正在检查更新…");
        UpdateChecker.checkAsync(this, new UpdateChecker.Callback() {
            @Override
            public void onResult(UpdateInfo info, String error) {
                checking = false;
                if (isFinishing()) return;
                if (info == null) {
                    UiUtils.toast(SettingsActivity.this, "检查失败：" + (error == null ? "网络连接失败" : error));
                    return;
                }
                if (UpdateChecker.isNewer(info.versionName, BuildConfig.VERSION_NAME)) {
                    startActivity(UpdateActivity.intent(SettingsActivity.this, info));
                } else {
                    UiUtils.toast(SettingsActivity.this, "当前已是最新版本 v" + BuildConfig.VERSION_NAME);
                }
            }
        });
    }

    private void chooseTheme() {
        final String[] items = {"跟随系统", "深色（Unity）", "浅色"};
        int current = Prefs.theme(this);
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("主题")
                .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Prefs.setTheme(SettingsActivity.this, which);
                        App.get().applyTheme();
                        dialog.dismiss();
                        recreate();
                    }
                })
                .show();
    }

    private void chooseTrashDays() {
        final int[] values = {7, 30, 90, 0};
        String[] items = {"7 天", "30 天", "90 天", "永久保留"};
        int current = 1;
        for (int i = 0; i < values.length; i++) if (values[i] == Prefs.trashDays(this)) current = i;
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("回收站保留天数")
                .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Prefs.setTrashDays(SettingsActivity.this, values[which]);
                        dialog.dismiss();
                        build();
                    }
                })
                .show();
    }

    private void chooseSort() {
        final String[] items = {"按名称", "按大小", "按日期", "按类型"};
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("默认排序")
                .setSingleChoiceItems(items, Prefs.sortBy(this), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Prefs.setSortBy(SettingsActivity.this, which);
                        dialog.dismiss();
                        build();
                    }
                })
                .setNeutralButton("切换升降序", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        Prefs.setSortAsc(SettingsActivity.this, !Prefs.sortAsc(SettingsActivity.this));
                        build();
                    }
                })
                .show();
    }

    private String themeName(int theme) {
        if (theme == Prefs.THEME_DARK) return "深色（Unity）";
        if (theme == Prefs.THEME_LIGHT) return "浅色";
        return "跟随系统";
    }

    private String trashDaysName(int days) {
        if (days <= 0) return "永久保留";
        return days + " 天";
    }

    private String sortName(int sort) {
        switch (sort) {
            case Prefs.SORT_SIZE: return "按大小";
            case Prefs.SORT_DATE: return "按日期";
            case Prefs.SORT_TYPE: return "按类型";
            default: return "按名称";
        }
    }
}
