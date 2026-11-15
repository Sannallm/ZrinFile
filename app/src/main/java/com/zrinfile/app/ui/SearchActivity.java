package com.zrinfile.app.ui;

import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.Prefs;
import com.zrinfile.app.util.UiUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SearchActivity extends AppCompatActivity implements FileListAdapter.Listener {

    private EditText input;
    private TextView scope;
    private RecyclerView list;
    private FileListAdapter adapter;
    private ProgressBar progress;
    private TextView empty;
    private String rootPath;
    private boolean searchAll = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private AtomicBoolean stop = new AtomicBoolean(false);
    private final List<FileItem> found = Collections.synchronizedList(new ArrayList<FileItem>());
    private boolean searching;
    private int resultCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);
        rootPath = getIntent().getStringExtra("path");
        if (rootPath == null) rootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle("搜索");
        toolbar.setSubtitle(rootPath);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        input = findViewById(R.id.search_input);
        scope = findViewById(R.id.search_scope);
        list = findViewById(R.id.search_list);
        progress = findViewById(R.id.search_progress);
        empty = findViewById(R.id.search_empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FileListAdapter(this, this);
        list.setAdapter(adapter);
        input.requestFocus();
        input.postDelayed(new Runnable() {
            @Override
            public void run() {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
            }
        }, 200);
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                schedule();
            }
        });
        input.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    handler.removeCallbacks(pending);
                    startSearch();
                    return true;
                }
                return false;
            }
        });
        scope.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                searchAll = !searchAll;
                scope.setText(searchAll ? "全盘" : "本目录");
                startSearch();
            }
        });
    }

    private void schedule() {
        if (pending != null) handler.removeCallbacks(pending);
        pending = new Runnable() {
            @Override
            public void run() {
                startSearch();
            }
        };
        handler.postDelayed(pending, 320);
    }

    private void startSearch() {
        final String q = input.getText().toString().trim();
        stop.set(true);
        stop = new AtomicBoolean(false);
        final AtomicBoolean token = stop;
        searching = true;
        resultCount = 0;
        found.clear();
        adapter.setItems(new ArrayList<FileItem>(), false);
        empty.setVisibility(View.GONE);
        if (q.length() == 0) {
            searching = false;
            progress.setVisibility(View.GONE);
            return;
        }
        progress.setVisibility(View.VISIBLE);
        final File root = searchAll ? Environment.getExternalStorageDirectory() : new File(rootPath);
        final boolean showHidden = Prefs.showHidden(this);
        final Runnable flusher = new Runnable() {
            @Override
            public void run() {
                List<FileItem> batch;
                synchronized (found) {
                    batch = new ArrayList<>(found);
                    found.clear();
                }
                if (!batch.isEmpty()) {
                    adapter.addItems(batch);
                    resultCount += batch.size();
                }
                if (searching && !token.get()) {
                    handler.postDelayed(this, 180);
                }
            }
        };
        handler.post(flusher);
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                FileUtils.search(root, q, showHidden, new FileUtils.Cancel() {
                    @Override
                    public boolean isCancelled() {
                        return token.get();
                    }
                }, new FileUtils.FileVisitor() {
                    @Override
                    public void onFile(FileItem item) {
                        found.add(item);
                    }
                }, 500);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (token != stop && token.get()) return;
                        searching = false;
                        progress.setVisibility(View.GONE);
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                if (resultCount == 0) empty.setVisibility(View.VISIBLE);
                                Toolbar toolbar = findViewById(R.id.toolbar);
                                toolbar.setSubtitle(resultCount + " 个结果 · " + root.getAbsolutePath());
                            }
                        }, 260);
                    }
                });
            }
        });
    }

    @Override
    public void onClick(FileItem item) {
        OpenHelper.open(this, item.file());
    }

    @Override
    public void onLongClick(FileItem item) {
    }

    @Override
    protected void onDestroy() {
        stop.set(true);
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
