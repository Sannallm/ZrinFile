package com.zrinfile.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.fs.TrashManager;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TrashActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private RecyclerView list;
    private View empty;
    private TextView emptyText;
    private View bottomBar;
    private TrashAdapter adapter;
    private final List<TrashManager.Entry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list_page);
        toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("回收站");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        emptyText = findViewById(R.id.empty_text);
        bottomBar = findViewById(R.id.bottom_bar);
        TextView action = findViewById(R.id.bottom_action);
        TextView extra = findViewById(R.id.bottom_extra);
        bottomBar.setVisibility(View.VISIBLE);
        action.setText("清空回收站");
        extra.setText("长按项目可查看原始位置");
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (entries.isEmpty()) return;
                UiUtils.confirm(TrashActivity.this, "清空回收站", "所有回收站文件将被永久删除，此操作无法撤销。", new Runnable() {
                    @Override
                    public void run() {
                        TrashManager.empty(TrashActivity.this);
                        load();
                    }
                });
            }
        });
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TrashAdapter();
        list.setAdapter(adapter);
        load();
    }

    private void load() {
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final List<TrashManager.Entry> data = TrashManager.list(TrashActivity.this);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        entries.clear();
                        entries.addAll(data);
                        adapter.notifyDataSetChanged();
                        long size = 0;
                        for (TrashManager.Entry e : data) size += e.size;
                        toolbar.setSubtitle(data.size() + " 项 · " + FileUtils.formatSize(size));
                        if (data.isEmpty()) {
                            empty.setVisibility(View.VISIBLE);
                            emptyText.setText("回收站为空");
                            bottomBar.setVisibility(View.GONE);
                        } else {
                            empty.setVisibility(View.GONE);
                            bottomBar.setVisibility(View.VISIBLE);
                        }
                    }
                });
            }
        });
    }

    private void restore(final TrashManager.Entry e) {
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final boolean ok = TrashManager.restore(TrashActivity.this, e);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.toast(TrashActivity.this, ok ? "已还原到 " + e.originalPath : "还原失败");
                        load();
                    }
                });
            }
        });
    }

    private void deleteForever(final TrashManager.Entry e) {
        UiUtils.confirm(this, "永久删除", "「" + e.name + "」将被永久删除，无法恢复。", new Runnable() {
            @Override
            public void run() {
                App.io().execute(new Runnable() {
                    @Override
                    public void run() {
                        TrashManager.deleteForever(TrashActivity.this, e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                load();
                            }
                        });
                    }
                });
            }
        });
    }

    private class TrashAdapter extends RecyclerView.Adapter<TrashAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView path;
            final TextView meta;
            final ImageButton restore;
            final ImageButton delete;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.trash_icon);
                name = v.findViewById(R.id.trash_name);
                path = v.findViewById(R.id.trash_path);
                meta = v.findViewById(R.id.trash_meta);
                restore = v.findViewById(R.id.trash_restore);
                delete = v.findViewById(R.id.trash_delete);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trash, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final TrashManager.Entry e = entries.get(position);
            FileItem item = FileItem.of(e.trashPath, e.name, e.dir, e.size, e.time);
            int type = e.dir ? FileItem.TYPE_FOLDER : item.type();
            h.icon.setImageDrawable(UiUtils.tint(TrashActivity.this, UiUtils.iconFor(type),
                    UiUtils.typeColor(TrashActivity.this, type)));
            h.name.setText(e.name);
            h.path.setText(e.originalPath);
            h.meta.setText(FileUtils.formatSize(e.size) + " · 删除于 " + FileUtils.formatDate(e.time));
            h.restore.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    restore(e);
                }
            });
            h.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteForever(e);
                }
            });
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    new AlertDialog.Builder(TrashActivity.this, R.style.Theme_ZrinFile_Dialog)
                            .setTitle(e.name)
                            .setMessage("原始位置:\n" + e.originalPath + "\n\n大小: "
                                    + FileUtils.formatSize(e.size) + "\n删除时间: " + FileUtils.formatDate(e.time))
                            .setPositiveButton("还原", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    restore(e);
                                }
                            })
                            .setNegativeButton("关闭", null)
                            .setNeutralButton("永久删除", new android.content.DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(android.content.DialogInterface d, int w) {
                                    deleteForever(e);
                                }
                            })
                            .show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return entries.size();
        }
    }
}
