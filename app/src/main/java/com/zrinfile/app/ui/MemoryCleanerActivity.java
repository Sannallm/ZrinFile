package com.zrinfile.app.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.fs.TrashManager;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** 内存清理：缓存、后台进程、应用与 APK 检测、大文件。 */
public class MemoryCleanerActivity extends AppCompatActivity {

    private TextView memText;
    private TextView resultText;
    private TextView appsSummary;
    private TextView bigSummary;
    private LinearLayout appsBox;
    private LinearLayout bigBox;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;

    private static class ApkEntry {
        String label;
        String path;
        long size;
        boolean corrupt;
        boolean installed;
        String pkg;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cleaner);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("内存清理");
        toolbar.setSubtitle("缓存 · 应用 · 大文件");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        memText = findViewById(R.id.cleaner_mem);
        resultText = findViewById(R.id.cleaner_result);
        appsSummary = findViewById(R.id.cleaner_apps_summary);
        bigSummary = findViewById(R.id.cleaner_big_summary);
        appsBox = findViewById(R.id.cleaner_apps);
        bigBox = findViewById(R.id.cleaner_big);

        findViewById(R.id.cleaner_clean).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clean();
            }
        });
        findViewById(R.id.cleaner_scan_apps).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanApps();
            }
        });
        findViewById(R.id.cleaner_scan_big).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                scanBigFiles();
            }
        });

        refreshMemoryText();
        scanApps();
    }

    private void refreshMemoryText() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            memText.setText("运行内存 " + FileUtils.formatSize(info.totalMem - info.availMem)
                    + " / " + FileUtils.formatSize(info.totalMem)
                    + "\n可用 " + FileUtils.formatSize(info.availMem));
        } catch (Exception e) {
            memText.setText("无法读取内存信息");
        }
    }

    private void clean() {
        UiUtils.toast(this, "正在清理…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                long before = cacheSize();
                try {
                    ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                    for (ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
                        if (p.pkgList == null) continue;
                        for (String pkg : p.pkgList) {
                            if (!getPackageName().equals(pkg)) am.killBackgroundProcesses(pkg);
                        }
                    }
                } catch (Exception ignored) {
                }
                FileUtils.deleteRecursive(App.thumbCache());
                File cache = getCacheDir();
                if (cache != null) clearDir(cache);
                File ext = getExternalCacheDir();
                if (ext != null) clearDir(ext);
                long after = cacheSize();
                final long freed = Math.max(0, before - after);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        refreshMemoryText();
                        resultText.setText("已清理 " + FileUtils.formatSize(freed) + " 缓存，并释放后台应用占用的内存");
                    }
                });
            }
        }, "zrinfile-clean").start();
    }

    private long cacheSize() {
        long total = 0;
        try {
            total += FileUtils.sizeOf(App.thumbCache());
        } catch (Exception ignored) {
        }
        try {
            File c = getCacheDir();
            if (c != null) total += FileUtils.sizeOf(c);
        } catch (Exception ignored) {
        }
        try {
            File e = getExternalCacheDir();
            if (e != null) total += FileUtils.sizeOf(e);
        } catch (Exception ignored) {
        }
        return total;
    }

    private void clearDir(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) FileUtils.deleteRecursive(f);
            else f.delete();
        }
    }

    private void scanApps() {
        appsSummary.setText("正在扫描应用与安装包…");
        appsBox.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<ApkEntry> entries = new ArrayList<>();
                int installed = 0;
                int corrupt = 0;
                PackageManager pm = getPackageManager();
                try {
                    List<PackageInfo> packages = pm.getInstalledPackages(0);
                    for (PackageInfo pi : packages) {
                        if (cancelled) return;
                        ApplicationInfo ai = pi.applicationInfo;
                        if (ai == null || ai.sourceDir == null) continue;
                        ApkEntry e = new ApkEntry();
                        e.installed = true;
                        e.pkg = pi.packageName;
                        e.label = String.valueOf(ai.loadLabel(pm));
                        e.path = ai.sourceDir;
                        e.size = new File(ai.sourceDir).length();
                        e.corrupt = pm.getPackageArchiveInfo(ai.sourceDir, 0) == null;
                        entries.add(e);
                        installed++;
                        if (e.corrupt) corrupt++;
                    }
                } catch (Exception ignored) {
                }
                List<File> apks = findApks();
                for (File f : apks) {
                    if (cancelled) return;
                    PackageInfo info = pm.getPackageArchiveInfo(f.getAbsolutePath(), 0);
                    ApkEntry e = new ApkEntry();
                    e.installed = false;
                    e.path = f.getAbsolutePath();
                    e.size = f.length();
                    e.corrupt = info == null;
                    e.label = info != null && info.applicationInfo != null
                            ? String.valueOf(info.applicationInfo.loadLabel(pm)) : f.getName();
                    if (info != null) e.pkg = info.packageName;
                    entries.add(e);
                    if (e.corrupt) corrupt++;
                }
                final int totalInstalled = installed;
                final int corruptCount = corrupt;
                final int apkCount = apks.size();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || cancelled) return;
                        appsSummary.setText("已安装 " + totalInstalled + " 个应用，扫描到 " + apkCount
                                + " 个 APK 文件，异常 " + corruptCount + " 个");
                        appsBox.removeAllViews();
                        int shown = 0;
                        for (ApkEntry e : entries) {
                            if (shown >= 60) break;
                            if (!e.installed && !e.corrupt && shown >= 20) break;
                            addApkRow(e);
                            shown++;
                        }
                        if (entries.isEmpty()) {
                            appsSummary.setText("没有可显示的应用");
                        }
                    }
                });
            }
        }, "zrinfile-scan-apps").start();
    }

    private List<File> findApks() {
        List<File> out = new ArrayList<>();
        File root = Environment.getExternalStorageDirectory();
        List<File> dirs = new ArrayList<>();
        dirs.add(root);
        dirs.add(new File(root, "Download"));
        dirs.add(new File(root, "Documents"));
        dirs.add(new File(root, "Desktop"));
        for (File dir : dirs) {
            walkApk(dir, 0, out);
            if (out.size() > 300) break;
        }
        return out;
    }

    private void walkApk(File dir, int depth, List<File> out) {
        if (cancelled || out.size() > 300 || depth > 3) return;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            return;
        }
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getName().startsWith(".") && !"Android".equals(f.getName())) walkApk(f, depth + 1, out);
            } else if (f.getName().toLowerCase().endsWith(".apk")) {
                out.add(f);
            }
        }
    }

    private void addApkRow(final ApkEntry e) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 9), UiUtils.dp(this, 4), UiUtils.dp(this, 9));
        row.setBackgroundResource(R.drawable.bg_item_selectable);
        TextView title = new TextView(this);
        String tag = e.corrupt ? "  [异常]" : (e.installed ? "  [已安装]" : "");
        title.setText(e.label + tag);
        title.setTextSize(14);
        title.setTextColor(e.corrupt ? UiUtils.color(this, R.color.danger) : UiUtils.color(this, R.color.text_primary));
        row.addView(title);
        TextView sub = new TextView(this);
        sub.setText(FileUtils.formatSize(e.size) + " · " + e.path);
        sub.setTextSize(11);
        sub.setTextColor(UiUtils.color(this, R.color.text_tertiary));
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(sub);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onApkClick(e);
            }
        });
        appsBox.addView(row);
    }

    private void onApkClick(final ApkEntry e) {
        if (e.corrupt) {
            UiUtils.confirm(this, "文件异常", "该 APK 无法解析，可能已损坏。是否删除？", new Runnable() {
                @Override
                public void run() {
                    new File(e.path).delete();
                    scanApps();
                }
            });
            return;
        }
        if (e.installed) {
            new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                    .setTitle(e.label)
                    .setItems(new String[]{"应用信息", "打开", "卸载"}, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            try {
                                if (which == 0) {
                                    Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    i.setData(Uri.parse("package:" + e.pkg));
                                    startActivity(i);
                                } else if (which == 1) {
                                    Intent i = getPackageManager().getLaunchIntentForPackage(e.pkg);
                                    if (i != null) startActivity(i);
                                    else UiUtils.toast(MemoryCleanerActivity.this, "该应用没有启动入口");
                                } else {
                                    Intent i = new Intent(Intent.ACTION_DELETE);
                                    i.setData(Uri.parse("package:" + e.pkg));
                                    startActivity(i);
                                }
                            } catch (Exception ex) {
                                UiUtils.toast(MemoryCleanerActivity.this, "无法完成操作");
                            }
                        }
                    })
                    .setNegativeButton("关闭", null)
                    .show();
            return;
        }
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle(e.label)
                .setMessage("是否安装此 APK？")
                .setPositiveButton("安装", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        UiUtils.installApk(MemoryCleanerActivity.this, new File(e.path));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void scanBigFiles() {
        bigSummary.setText("正在扫描大文件…");
        bigBox.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<File> big = new ArrayList<>();
                File root = Environment.getExternalStorageDirectory();
                walkBig(root, big);
                Collections.sort(big, new Comparator<File>() {
                    @Override
                    public int compare(File a, File b) {
                        return Long.compare(b.length(), a.length());
                    }
                });
                while (big.size() > 100) big.remove(big.size() - 1);
                final long total = sum(big);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || cancelled) return;
                        bigSummary.setText("共 " + big.size() + " 个，合计 " + FileUtils.formatSize(total));
                        bigBox.removeAllViews();
                        for (File f : big) addBigRow(f);
                        if (big.isEmpty()) bigSummary.setText("没有超过 50MB 的文件");
                    }
                });
            }
        }, "zrinfile-scan-big").start();
    }

    private void walkBig(File dir, List<File> out) {
        if (cancelled || out.size() > 400) return;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Exception e) {
            return;
        }
        if (files == null) return;
        for (File f : files) {
            if (cancelled) return;
            try {
                if (f.isDirectory()) {
                    if (f.getName().startsWith(".")) continue;
                    if (dir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())
                            && "Android".equals(f.getName())) continue;
                    walkBig(f, out);
                } else if (f.length() >= 50L * 1024 * 1024) {
                    out.add(f);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private long sum(List<File> files) {
        long total = 0;
        for (File f : files) total += f.length();
        return total;
    }

    private void addBigRow(final File f) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(UiUtils.dp(this, 4), UiUtils.dp(this, 9), UiUtils.dp(this, 4), UiUtils.dp(this, 9));
        row.setBackgroundResource(R.drawable.bg_item_selectable);
        TextView title = new TextView(this);
        title.setText(f.getName());
        title.setTextSize(14);
        title.setTextColor(UiUtils.color(this, R.color.text_primary));
        row.addView(title);
        TextView sub = new TextView(this);
        sub.setText(FileUtils.formatSize(f.length()) + " · " + f.getAbsolutePath());
        sub.setTextSize(11);
        sub.setTextColor(UiUtils.color(this, R.color.text_tertiary));
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        row.addView(sub);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenHelper.open(MemoryCleanerActivity.this, f);
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                UiUtils.confirm(MemoryCleanerActivity.this, "删除文件", "永久删除「" + f.getName() + "」？", new Runnable() {
                    @Override
                    public void run() {
                        f.delete();
                        scanBigFiles();
                    }
                });
                return true;
            }
        });
        bigBox.addView(row);
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
    }
}
