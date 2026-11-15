package com.zrinfile.app.ui;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class StorageAnalyzerActivity extends AppCompatActivity {

    private RecyclerView list;
    private TextView summary;
    private ProgressBar progress;
    private AnalyzerAdapter adapter;
    private final List<FileItem> items = new ArrayList<>();
    private volatile boolean cancelled;
    private File root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_analyzer);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("存储分析");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        String extra = getIntent().getStringExtra("path");
        root = extra != null ? new File(extra) : Environment.getExternalStorageDirectory();
        toolbar.setSubtitle(root.getAbsolutePath());
        list = findViewById(R.id.analyzer_list);
        summary = findViewById(R.id.analyzer_summary);
        progress = findViewById(R.id.analyzer_progress);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AnalyzerAdapter();
        list.setAdapter(adapter);
        scan();
    }

    private void scan() {
        summary.setText("正在分析…");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                File[] files = null;
                try {
                    files = root.listFiles();
                } catch (Exception ignored) {
                }
                final List<FileItem> result = new ArrayList<>();
                long total = 0;
                if (files != null) {
                    for (File f : files) {
                        if (cancelled) return;
                        FileItem item = FileItem.fromFile(f);
                        if (item.isDir) {
                            item.size = FileUtils.sizeOf(f);
                        }
                        total += item.size;
                        result.add(item);
                    }
                }
                Collections.sort(result, new Comparator<FileItem>() {
                    @Override
                    public int compare(FileItem a, FileItem b) {
                        return Long.compare(b.size, a.size);
                    }
                });
                while (result.size() > 200) result.remove(result.size() - 1);
                final long finalTotal = total;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (cancelled) return;
                        items.clear();
                        items.addAll(result);
                        adapter.notifyDataSetChanged();
                        progress.setVisibility(View.GONE);
                        summary.setText("总占用 " + FileUtils.formatSize(finalTotal) + " · " + result.size() + " 项");
                        if (result.isEmpty()) {
                            summary.setText("没有可分析的内容");
                        }
                    }
                });
            }
        });
    }

    private class AnalyzerAdapter extends RecyclerView.Adapter<AnalyzerAdapter.Holder> {
        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView size;
            final ProgressBar bar;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.analyzer_name);
                size = v.findViewById(R.id.analyzer_size);
                bar = v.findViewById(R.id.analyzer_bar);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_analyzer, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final FileItem item = items.get(position);
            h.name.setText(item.name + (item.isDir ? "/" : ""));
            h.size.setText(FileUtils.formatSize(item.size));
            long max = items.isEmpty() ? 1 : items.get(0).size;
            h.bar.setProgress((int) Math.max(1, item.size * 100 / Math.max(1, max)));
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (item.isDir) {
                        android.content.Intent i = new android.content.Intent(StorageAnalyzerActivity.this, MainActivity.class);
                        i.putExtra("browse", item.path);
                        i.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP | android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP);
                        startActivity(i);
                    } else {
                        OpenHelper.open(StorageAnalyzerActivity.this, item.file());
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
    }
}
