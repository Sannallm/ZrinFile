package com.zrinfile.app.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.BuildConfig;
import com.zrinfile.app.R;
import com.zrinfile.app.update.Downloader;
import com.zrinfile.app.update.UpdateInfo;
import com.zrinfile.app.util.Prefs;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

public class UpdateActivity extends AppCompatActivity {

    public static final String EXTRA_INFO = "info";

    private UpdateInfo info;
    private Downloader.Task task;
    private ProgressBar progress;
    private TextView status;
    private Button action;
    private boolean downloading;

    public static Intent intent(Context c, UpdateInfo info) {
        Intent i = new Intent(c, UpdateActivity.class);
        i.putExtra(EXTRA_INFO, info.toJson().toString());
        return i;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_update);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("软件更新");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        info = UpdateInfo.fromJson(getIntent().getStringExtra(EXTRA_INFO));
        if (info == null) {
            finish();
            return;
        }

        progress = findViewById(R.id.update_progress);
        status = findViewById(R.id.update_status);
        action = findViewById(R.id.update_button);
        TextView version = findViewById(R.id.update_version);
        TextView current = findViewById(R.id.update_current);
        TextView notes = findViewById(R.id.update_notes);
        TextView source = findViewById(R.id.update_source);
        Button later = findViewById(R.id.update_later);

        version.setText("v" + info.versionName);
        StringBuilder meta = new StringBuilder("当前版本 v" + BuildConfig.VERSION_NAME);
        meta.append(" · 通用 32/64 位");
        if (info.size > 0) meta.append(" · ").append(Downloader.formatSize(info.size));
        current.setText(meta.toString());
        String text = info.notesText();
        notes.setText(text.length() > 0 ? text : "本次更新包含问题修复与稳定性改进。");
        source.setText(info.apkUrl.contains("github.com") ? "下载源: GitHub Releases" : "下载源: 更新服务器");

        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (downloading) {
                    cancelDownload();
                } else {
                    startDownload();
                }
            }
        });
        later.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Prefs.get(UpdateActivity.this).edit().putString("skipped_update", info.versionName).apply();
                finish();
            }
        });

        if (info.apkUrl.length() == 0) {
            action.setEnabled(false);
            status.setText("暂未找到可下载的安装包");
        }
    }

    private void startDownload() {
        downloading = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progress.setVisibility(View.VISIBLE);
        progress.setProgress(0);
        action.setText("取消下载");
        status.setText("正在连接服务器…");

        task = Downloader.start(this, info, new Downloader.Listener() {
            @Override
            public void onProgress(final int percent, final long done, final long total) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || !downloading) return;
                        progress.setProgress(percent);
                        status.setText("已下载 " + Downloader.formatSize(done) + " / "
                                + Downloader.formatSize(total) + "  (" + percent + "%)");
                    }
                });
            }

            @Override
            public void onStage(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing() || !downloading) return;
                        status.setText(message);
                    }
                });
            }

            @Override
            public void onDone(final File file) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        downloading = false;
                        task = null;
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        progress.setProgress(100);
                        action.setText("重新安装");
                        status.setText("下载完成 · 已通过校验，正在启动安装程序");
                        UiUtils.installApk(UpdateActivity.this, file);
                    }
                });
            }

            @Override
            public void onError(final String message) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        downloading = false;
                        task = null;
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        progress.setVisibility(View.GONE);
                        action.setText("重试");
                        status.setText("下载失败：" + message);
                    }
                });
            }

            @Override
            public void onCancelled() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        downloading = false;
                        task = null;
                        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        progress.setVisibility(View.GONE);
                        action.setText("立即更新");
                        status.setText("已取消下载");
                    }
                });
            }
        });
    }

    private void cancelDownload() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        downloading = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        progress.setVisibility(View.GONE);
        action.setText("立即更新");
        status.setText("已取消下载");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
