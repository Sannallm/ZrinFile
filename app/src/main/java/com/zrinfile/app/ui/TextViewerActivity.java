package com.zrinfile.app.ui;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.fs.RootShell;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.io.FileInputStream;

public class TextViewerActivity extends AppCompatActivity {

    private static final int MAX_BYTES = 2 * 1024 * 1024;

    private Toolbar toolbar;
    private TextView content;
    private TextView status;
    private TextView wrapButton;
    private File file;
    private String encodingOverride;
    private int fontSize = 14;
    private boolean wrap = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_viewer);
        toolbar = findViewById(R.id.toolbar);
        content = findViewById(R.id.text_content);
        status = findViewById(R.id.text_status);
        wrapButton = findViewById(R.id.text_wrap);
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
        toolbar.setTitle(file.getName());
        toolbar.setSubtitle(FileUtils.formatSize(file.length()));
        content.setTypeface(android.graphics.Typeface.MONOSPACE);
        content.setTextSize(fontSize);
        findViewById(R.id.text_font_down).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fontSize = Math.max(9, fontSize - 1);
                content.setTextSize(fontSize);
            }
        });
        findViewById(R.id.text_font_up).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fontSize = Math.min(30, fontSize + 1);
                content.setTextSize(fontSize);
            }
        });
        wrapButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                wrap = !wrap;
                applyWrap();
            }
        });
        findViewById(R.id.text_share).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UiUtils.shareFile(TextViewerActivity.this, file, "text/plain");
            }
        });
        findViewById(R.id.text_encoding).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                chooseEncoding();
            }
        });
        applyWrap();
        load();
    }

    private void applyWrap() {
        wrapButton.setText(wrap ? "自动换行" : "不换行");
        wrapButton.setTextColor(UiUtils.color(this, wrap ? R.color.accent : R.color.text_secondary));
        content.setHorizontallyScrolling(!wrap);
        android.view.ViewGroup.LayoutParams lp = content.getLayoutParams();
        lp.width = wrap ? android.view.ViewGroup.LayoutParams.MATCH_PARENT
                : android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
        content.setLayoutParams(lp);
        content.setMinWidth(getResources().getDisplayMetrics().widthPixels - UiUtils.dp(this, 28));
    }

    private void load() {
        content.setText("正在读取…");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                String loadedText;
                String enc = "UTF-8";
                boolean truncated = false;
                if (file.canRead()) {
                    if (encodingOverride != null) {
                        byte[] data = readBytes(file, MAX_BYTES);
                        truncated = file.length() > MAX_BYTES;
                        try {
                            loadedText = new String(data, encodingOverride);
                            enc = encodingOverride;
                        } catch (Exception e) {
                            loadedText = new String(data);
                            enc = "UTF-8";
                        }
                    } else {
                        FileUtils.TextResult r = FileUtils.readText(file, MAX_BYTES);
                        loadedText = r.text;
                        enc = r.encoding;
                        truncated = r.truncated;
                    }
                } else {
                    String s = RootShell.readSmall(file.getAbsolutePath(), MAX_BYTES);
                    loadedText = s == null ? "无法读取文件（需要 Root 权限）" : s;
                }
                final String text = loadedText;
                final String finalEnc = enc;
                final boolean finalTruncated = truncated;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        content.setText(text);
                        content.post(new Runnable() {
                            @Override
                            public void run() {
                                status.setText(finalEnc + " · " + content.getLineCount() + " 行 · "
                                        + FileUtils.formatSize(file.length())
                                        + (finalTruncated ? " · 仅显示前 2 MB" : ""));
                            }
                        });
                        ((TextView) findViewById(R.id.text_encoding)).setText(finalEnc);
                        toolbar.setSubtitle(finalEnc + " · " + FileUtils.formatSize(file.length()));
                    }
                });
            }
        });
    }

    private byte[] readBytes(File f, int max) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(f);
            int len = (int) Math.min(max, f.length());
            byte[] data = new byte[len];
            int read = 0;
            while (read < len) {
                int n = in.read(data, read, len - read);
                if (n <= 0) break;
                read += n;
            }
            return data;
        } catch (Exception e) {
            return new byte[0];
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void chooseEncoding() {
        final String[] items = {"自动识别", "UTF-8", "GBK", "UTF-16LE", "UTF-16BE"};
        int current = 0;
        if (encodingOverride != null) {
            for (int i = 1; i < items.length; i++) if (items[i].equals(encodingOverride)) current = i;
        }
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("文本编码")
                .setSingleChoiceItems(items, current, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        encodingOverride = which == 0 ? null : items[which];
                        dialog.dismiss();
                        load();
                    }
                })
                .show();
    }
}
