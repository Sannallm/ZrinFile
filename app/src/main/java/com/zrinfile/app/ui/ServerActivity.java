package com.zrinfile.app.ui;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.server.ServerConfig;
import com.zrinfile.app.server.ServerService;
import com.zrinfile.app.util.Prefs;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class ServerActivity extends AppCompatActivity {

    private static final String PREFS = "zrinfile_server";

    private TextView typeHttp;
    private TextView typeWeb;
    private TextView typeFtp;
    private TextView rootValue;
    private EditText port;
    private SwitchCompat auth;
    private LinearLayout authPanel;
    private EditText user;
    private EditText pass;
    private Button toggle;
    private TextView status;

    private String type = ServerConfig.TYPE_HTTP;
    private String rootPath;
    private boolean polling;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            updateStatus();
            handler.postDelayed(this, 800);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_server);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("文件服务器");
        toolbar.setSubtitle("HTTP · 网页 · FTP");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        typeHttp = findViewById(R.id.server_type_http);
        typeWeb = findViewById(R.id.server_type_web);
        typeFtp = findViewById(R.id.server_type_ftp);
        rootValue = findViewById(R.id.server_root_value);
        port = findViewById(R.id.server_port);
        auth = findViewById(R.id.server_auth);
        authPanel = findViewById(R.id.server_auth_panel);
        user = findViewById(R.id.server_user);
        pass = findViewById(R.id.server_pass);
        toggle = findViewById(R.id.server_toggle);
        status = findViewById(R.id.server_status);

        SharedPreferences sp = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        type = sp.getString("type", ServerConfig.TYPE_HTTP);
        rootPath = sp.getString("root", App.homeDir().getAbsolutePath());
        port.setText(String.valueOf(sp.getInt("port", defaultPort(type))));
        auth.setChecked(sp.getBoolean("auth", false));
        user.setText(sp.getString("user", "zrin"));
        pass.setText(sp.getString("pass", ""));
        authPanel.setVisibility(auth.isChecked() ? View.VISIBLE : View.GONE);
        auth.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(android.widget.CompoundButton buttonView, boolean isChecked) {
                authPanel.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            }
        });
        rootValue.setText(rootPath);

        typeHttp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setType(ServerConfig.TYPE_HTTP);
            }
        });
        typeWeb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setType(ServerConfig.TYPE_WEB);
            }
        });
        typeFtp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setType(ServerConfig.TYPE_FTP);
            }
        });
        findViewById(R.id.server_root_row).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseRoot();
            }
        });
        toggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ServerService.isRunning()) {
                    stopServer();
                } else {
                    startServer();
                }
            }
        });
        updateTypeUi();
        updateStatus();
    }

    private void setType(String t) {
        type = t;
        port.setText(String.valueOf(defaultPort(t)));
        updateTypeUi();
    }

    private static int defaultPort(String t) {
        return ServerConfig.TYPE_FTP.equals(t) ? 2121 : 8080;
    }

    private void updateTypeUi() {
        styleChip(typeHttp, ServerConfig.TYPE_HTTP.equals(type));
        styleChip(typeWeb, ServerConfig.TYPE_WEB.equals(type));
        styleChip(typeFtp, ServerConfig.TYPE_FTP.equals(type));
        rootValue.setText(rootPath);
    }

    private void styleChip(TextView v, boolean active) {
        v.setBackgroundResource(active ? R.drawable.bg_chip_active : R.drawable.bg_chip);
        v.setTextColor(UiUtils.color(this, active ? R.color.accent : R.color.text_secondary));
    }

    private void chooseRoot() {
        final File start = new File(rootPath);
        File dir = start.isDirectory() ? start : App.homeDir();
        showDirDialog(dir);
    }

    private void showDirDialog(final File dir) {
        File[] children = dir.listFiles();
        final List<File> dirs = new ArrayList<>();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory() && !f.isHidden()) dirs.add(f);
            }
        }
        Collections.sort(dirs, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        final String[] names = new String[dirs.size()];
        for (int i = 0; i < dirs.size(); i++) names[i] = dirs.get(i).getName();
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle(dir.getAbsolutePath())
                .setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        showDirDialog(dirs.get(which));
                    }
                })
                .setPositiveButton("选择此目录", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        rootPath = dir.getAbsolutePath();
                        rootValue.setText(rootPath);
                    }
                })
                .setNeutralButton("上级", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        File parent = dir.getParentFile();
                        showDirDialog(parent != null ? parent : dir);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void startServer() {
        final int p;
        try {
            p = Integer.parseInt(port.getText().toString().trim());
        } catch (Exception e) {
            UiUtils.toast(this, "端口无效");
            return;
        }
        if (p < 1024 || p > 65535) {
            UiUtils.toast(this, "端口范围 1024 - 65535");
            return;
        }
        File root = new File(rootPath);
        if (!root.isDirectory()) {
            UiUtils.toast(this, "共享目录不存在");
            return;
        }
        if (auth.isChecked() && pass.getText().toString().length() == 0) {
            UiUtils.toast(this, "请输入访问密码");
            return;
        }
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("type", type)
                .putString("root", rootPath)
                .putInt("port", p)
                .putBoolean("auth", auth.isChecked())
                .putString("user", user.getText().toString().trim())
                .putString("pass", pass.getText().toString())
                .apply();
        Intent i = new Intent(this, ServerService.class);
        i.setAction(ServerService.ACTION_START);
        i.putExtra(ServerService.EXTRA_TYPE, type);
        i.putExtra(ServerService.EXTRA_ROOT, rootPath);
        i.putExtra(ServerService.EXTRA_PORT, p);
        i.putExtra(ServerService.EXTRA_AUTH, auth.isChecked());
        i.putExtra(ServerService.EXTRA_USER, user.getText().toString().trim());
        i.putExtra(ServerService.EXTRA_PASS, pass.getText().toString());
        try {
            ContextCompat.startForegroundService(this, i);
            UiUtils.toast(this, "服务已启动");
        } catch (Exception e) {
            UiUtils.toast(this, "启动失败：" + e.getMessage());
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateStatus();
            }
        }, 400);
    }

    private void stopServer() {
        Intent i = new Intent(this, ServerService.class);
        i.setAction(ServerService.ACTION_STOP);
        try {
            startService(i);
            UiUtils.toast(this, "已停止服务");
        } catch (Exception e) {
            UiUtils.toast(this, "停止失败：" + e.getMessage());
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                updateStatus();
            }
        }, 300);
    }

    private void updateStatus() {
        if (ServerService.isRunning()) {
            toggle.setText("停止服务");
            ServerConfig config = ServerService.getCurrent();
            String root = config != null ? config.root : rootPath;
            status.setText("运行中\n" + ServerService.getInfo() + "\n共享目录: " + root
                    + "\n\n同一局域网内用浏览器或 FTP 客户端访问以上地址。服务会保持后台运行。");
        } else if ("starting".equals(ServerService.getState())) {
            toggle.setText("停止服务");
            status.setText("正在启动…");
        } else if ("error".equals(ServerService.getState())) {
            toggle.setText("重新启动");
            status.setText("启动失败：" + ServerService.getInfo());
        } else {
            toggle.setText("启动服务");
            status.setText("未运行\n共享目录: " + rootPath);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        polling = true;
        handler.post(poll);
    }

    @Override
    protected void onPause() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
        super.onPause();
    }
}
