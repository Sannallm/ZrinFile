package com.zrinfile.app.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipViewerActivity extends AppCompatActivity implements FileListAdapter.Listener {

    private Toolbar toolbar;
    private RecyclerView list;
    private FileListAdapter adapter;
    private View empty;
    private File zipFile;
    private final List<FileItem> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_page);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        ((TextView) findViewById(R.id.empty_text)).setText("压缩包为空");
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileListAdapter(this, this);
        list.setAdapter(adapter);
        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        zipFile = new File(path);
        toolbar.setTitle(zipFile.getName());
        toolbar.setSubtitle(FileUtils.formatSize(zipFile.length()));
        View bottom = findViewById(R.id.bottom_bar);
        bottom.setVisibility(View.VISIBLE);
        TextView action = findViewById(R.id.bottom_action);
        TextView extra = findViewById(R.id.bottom_extra);
        action.setText("解压到当前目录");
        extra.setText("点击项目可预览");
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                extractAll();
            }
        });
        load();
    }

    private void load() {
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final List<FileItem> data = new ArrayList<>();
                ZipFile zf = null;
                try {
                    zf = new ZipFile(zipFile);
                    Enumeration<? extends ZipEntry> en = zf.entries();
                    while (en.hasMoreElements()) {
                        ZipEntry e = en.nextElement();
                        if (e.isDirectory()) continue;
                        FileItem item = FileItem.of(e.getName(), e.getName(), false, e.getSize(), 0);
                        data.add(item);
                    }
                } catch (Exception ignored) {
                } finally {
                    try {
                        if (zf != null) zf.close();
                    } catch (Exception ignored) {
                    }
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        entries.clear();
                        entries.addAll(data);
                        adapter.setItems(data, true);
                        toolbar.setSubtitle(data.size() + " 个文件 · " + FileUtils.formatSize(zipFile.length()));
                        empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    @Override
    public void onClick(FileItem item) {
        final String entryName = item.path;
        final UiUtils.ProgressHandle h = UiUtils.progress(this, "正在解压预览");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                File out = new File(App.thumbCache(), "zip_preview_" + System.currentTimeMillis()
                        + "_" + new File(entryName).getName());
                boolean ok = extractEntry(entryName, out);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(h);
                        if (ok) OpenHelper.open(ZipViewerActivity.this, out);
                        else UiUtils.toast(ZipViewerActivity.this, "无法提取此文件");
                    }
                });
            }
        });
    }

    @Override
    public void onLongClick(FileItem item) {
    }

    private boolean extractEntry(String name, File out) {
        ZipFile zf = null;
        InputStream in = null;
        OutputStream os = null;
        try {
            File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            zf = new ZipFile(zipFile);
            ZipEntry e = zf.getEntry(name);
            if (e == null) return false;
            in = zf.getInputStream(e);
            os = new BufferedOutputStream(new FileOutputStream(out), 65536);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
            try {
                if (os != null) os.close();
            } catch (Exception ignored) {
            }
            try {
                if (zf != null) zf.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void extractAll() {
        final UiUtils.ProgressHandle h = UiUtils.progress(this, "正在解压");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                String base = zipFile.getName();
                int dot = base.lastIndexOf('.');
                if (dot > 0) base = base.substring(0, dot);
                File dest = FileUtils.uniqueFile(zipFile.getParentFile(), base);
                final int count = FileUtils.unzip(zipFile, dest, new FileUtils.Cancel() {
                    @Override
                    public boolean isCancelled() {
                        return h.cancelled;
                    }
                }, new FileUtils.Progress() {
                    @Override
                    public void onProgress(String current, long done, long total) {
                        final int percent = total > 0 ? (int) (done * 100 / total) : 0;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (h.dialog.isShowing()) h.text.setText(percent + "%");
                            }
                        });
                    }
                });
                final String destPath = dest.getAbsolutePath();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(h);
                        if (count > 0) {
                            new androidx.appcompat.app.AlertDialog.Builder(ZipViewerActivity.this, R.style.Theme_ZrinFile_Dialog)
                                    .setTitle("解压完成")
                                    .setMessage("已解压 " + count + " 个文件到:\n" + destPath)
                                    .setPositiveButton("打开目录", new android.content.DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(android.content.DialogInterface dialog, int which) {
                                            android.content.Intent i = new android.content.Intent(ZipViewerActivity.this, MainActivity.class);
                                            i.putExtra("browse", destPath);
                                            i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                                            startActivity(i);
                                        }
                                    })
                                    .setNegativeButton("关闭", null)
                                    .show();
                        } else {
                            UiUtils.toast(ZipViewerActivity.this, "解压失败");
                        }
                    }
                });
            }
        });
    }
}
