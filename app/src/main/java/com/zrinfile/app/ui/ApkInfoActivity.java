package com.zrinfile.app.ui;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.security.MessageDigest;
import java.util.Locale;

public class ApkInfoActivity extends AppCompatActivity {

    private File file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_apk_info);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("安装包信息");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        load();
    }

    private void load() {
        final LinearLayout container = findViewById(R.id.apk_container);
        container.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("正在读取安装包…");
        loading.setTextColor(UiUtils.color(this, R.color.text_secondary));
        loading.setTextSize(14);
        container.addView(loading);
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final PackageManager pm = getPackageManager();
                final PackageInfo info = pm.getPackageArchiveInfo(file.getAbsolutePath(),
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        container.removeAllViews();
                        if (info == null) {
                            TextView tv = new TextView(ApkInfoActivity.this);
                            tv.setText("无法解析此安装包。");
                            tv.setTextColor(UiUtils.color(ApkInfoActivity.this, R.color.text_secondary));
                            tv.setTextSize(14);
                            container.addView(tv);
                            return;
                        }
                        buildHeader(container, info);
                        buildInfo(container, info);
                    }
                });
            }
        });
    }

    private void buildHeader(LinearLayout container, PackageInfo info) {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        try {
            ApplicationInfo ai = info.applicationInfo;
            if (ai != null) {
                ai.sourceDir = file.getAbsolutePath();
                ai.publicSourceDir = file.getAbsolutePath();
                Drawable d = ai.loadIcon(getPackageManager());
                icon.setImageDrawable(d);
            }
        } catch (Exception ignored) {
        }
        if (icon.getDrawable() == null) {
            icon.setImageDrawable(UiUtils.tint(this, R.drawable.ic_apk,
                    UiUtils.color(this, R.color.type_apk)));
        }
        header.addView(icon, new LinearLayout.LayoutParams(UiUtils.dp(this, 58), UiUtils.dp(this, 58)));
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = UiUtils.dp(this, 14);
        String label = info.applicationInfo != null
                ? String.valueOf(info.applicationInfo.loadLabel(getPackageManager())) : file.getName();
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(18);
        name.setTextColor(UiUtils.color(this, R.color.text_primary));
        text.addView(name);
        TextView pkg = new TextView(this);
        pkg.setText(info.packageName);
        pkg.setTextSize(12);
        pkg.setTextColor(UiUtils.color(this, R.color.text_secondary));
        pkg.setPadding(0, UiUtils.dp(this, 3), 0, 0);
        text.addView(pkg);
        header.addView(text, lp);
        container.addView(header);

        View divider = new View(this);
        divider.setBackgroundColor(UiUtils.color(this, R.color.divider));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        dlp.topMargin = UiUtils.dp(this, 14);
        dlp.bottomMargin = UiUtils.dp(this, 6);
        container.addView(divider, dlp);
    }

    private void buildInfo(LinearLayout container, PackageInfo info) {
        addRow(container, "版本", info.versionName + " (" + info.versionCode + ")");
        addRow(container, "文件大小", FileUtils.formatSize(file.length()));
        addRow(container, "文件路径", file.getAbsolutePath());
        if (info.applicationInfo != null) {
            addRow(container, "应用名称", String.valueOf(info.applicationInfo.loadLabel(getPackageManager())));
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                addRow(container, "最低系统", "API " + info.applicationInfo.minSdkVersion);
            }
            addRow(container, "目标系统", "API " + info.applicationInfo.targetSdkVersion);
        }
        if (info.signatures != null && info.signatures.length > 0) {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] digest = md.digest(info.signatures[0].toByteArray());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < digest.length; i++) {
                    sb.append(String.format(Locale.US, "%02X", digest[i]));
                    if (i < digest.length - 1) sb.append(':');
                }
                addRow(container, "签名 SHA-256", sb.toString());
            } catch (Exception ignored) {
            }
        }
        if (info.requestedPermissions != null && info.requestedPermissions.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < info.requestedPermissions.length && i < 60; i++) {
                sb.append("· ").append(info.requestedPermissions[i]).append('\n');
            }
            if (info.requestedPermissions.length > 60) {
                sb.append("… 共 ").append(info.requestedPermissions.length).append(" 项");
            }
            addBlock(container, "权限 (" + info.requestedPermissions.length + ")", sb.toString());
        }
        findViewById(R.id.apk_install).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.installApk(ApkInfoActivity.this, file);
            }
        });
    }

    private void addRow(LinearLayout container, String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, UiUtils.dp(this, 6), 0, UiUtils.dp(this, 6));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(UiUtils.color(this, R.color.text_secondary));
        t.setWidth(UiUtils.dp(this, 96));
        row.addView(t);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(13);
        v.setTextColor(UiUtils.color(this, R.color.text_primary));
        v.setTextIsSelectable(true);
        row.addView(v, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        container.addView(row);
    }

    private void addBlock(LinearLayout container, String title, String value) {
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(13);
        t.setTextColor(UiUtils.color(this, R.color.text_secondary));
        t.setPadding(0, UiUtils.dp(this, 12), 0, UiUtils.dp(this, 4));
        container.addView(t);
        TextView v = new TextView(this);
        v.setText(value);
        v.setTextSize(12);
        v.setTextColor(UiUtils.color(this, R.color.text_primary));
        v.setBackgroundColor(UiUtils.color(this, R.color.surface_raised));
        v.setPadding(UiUtils.dp(this, 10), UiUtils.dp(this, 8), UiUtils.dp(this, 10), UiUtils.dp(this, 8));
        container.addView(v);
    }
}
