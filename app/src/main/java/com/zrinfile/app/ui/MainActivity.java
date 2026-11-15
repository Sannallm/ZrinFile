package com.zrinfile.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.Clipboard;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.fs.RootShell;
import com.zrinfile.app.fs.TrashManager;
import com.zrinfile.app.util.Prefs;
import com.zrinfile.app.util.UiUtils;
import com.zrinfile.app.update.UpdateChecker;
import com.zrinfile.app.update.UpdateInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements FileListAdapter.Listener {

    private static final int REQ_STORAGE = 100;

    private DrawerLayout drawer;
    private Toolbar toolbar;
    private RecyclerView fileList;
    private FileListAdapter adapter;
    private LinearLayout emptyView;
    private TextView emptyText;
    private TextView emptyButton;
    private ProgressBar loading;
    private LinearLayout bottomBar;
    private HorizontalScrollView tabsScroll;
    private LinearLayout tabContainer;
    private View tabsDivider;
    private LinearLayout navContainer;
    private LinearLayout navBottom;
    private LinearLayout bookmarkContainer;
    private TextView bookmarkEmpty;
    private TextView storageSummary;
    private ProgressBar storageBar;
    private TextView rootBadge;

    private LinearLayout btnPaste;

    private File currentDir;
    private boolean loadedShowHidden;
    private int loadedSortBy = -1;
    private boolean loadedSortAsc = true;
    private boolean loadedFoldersFirst = true;
    private int loadSeq = 0;
    private boolean rootAvailable = false;
    private boolean selectionMode = false;
    private long lastBack = 0;

    private final List<Tab> tabs = new ArrayList<>();
    private int tabIndex = 0;
    private final Map<View, String> navPaths = new java.util.LinkedHashMap<>();

    private static class Tab {
        String path;

        Tab(String path) {
            this.path = path;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        setupToolbar();
        setupList();
        setupDrawer();
        setupBottomBar();
        setupTabs();

        boolean granted = hasStoragePermission();
        if (!granted) {
            requestStorage();
        } else {
            startHome();
            maybeAskBattery();
            maybeCheckUpdate();
        }
        checkRoot();
        handleIntent(getIntent());
    }

    private void bindViews() {
        drawer = findViewById(R.id.drawer_layout);
        toolbar = findViewById(R.id.toolbar);
        fileList = findViewById(R.id.file_list);
        emptyView = findViewById(R.id.empty_view);
        emptyText = findViewById(R.id.empty_text);
        emptyButton = findViewById(R.id.empty_button);
        loading = findViewById(R.id.loading);
        bottomBar = findViewById(R.id.bottom_bar);
        tabsScroll = findViewById(R.id.tabs_scroll);
        tabContainer = findViewById(R.id.tab_container);
        tabsDivider = findViewById(R.id.tabs_divider);
        navContainer = findViewById(R.id.nav_container);
        navBottom = findViewById(R.id.nav_container_bottom);
        bookmarkContainer = findViewById(R.id.bookmark_container);
        bookmarkEmpty = findViewById(R.id.bookmark_empty);
        storageSummary = findViewById(R.id.storage_summary);
        storageBar = findViewById(R.id.storage_bar);
        rootBadge = findViewById(R.id.root_badge);
        btnPaste = findViewById(R.id.btn_paste);
        ((TextView) findViewById(R.id.drawer_version)).setText("v" + com.zrinfile.app.BuildConfig.VERSION_NAME + " · 永久免费");
    }

    private void setupToolbar() {
        toolbar.setNavigationIcon(R.drawable.ic_menu);
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (selectionMode) exitSelection();
                else drawer.openDrawer(GravityCompat.START);
            }
        });
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_menu, UiUtils.color(this, R.color.icon)));
        toolbar.inflateMenu(R.menu.menu_main);
        tintMenuIcons(toolbar.getMenu());
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                return onToolbarMenu(item);
            }
        });
        toolbar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDir != null) jumpToPath();
            }
        });
    }

    private boolean onToolbarMenu(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_search) {
            Intent i = new Intent(this, SearchActivity.class);
            i.putExtra("path", currentDir != null ? currentDir.getAbsolutePath() : "/");
            startActivity(i);
        } else if (id == R.id.action_sort) {
            showSortDialog();
        } else if (id == R.id.action_new_folder) {
            newFolder();
        } else if (id == R.id.action_new_file) {
            newFile();
        } else if (id == R.id.action_select_all) {
            enterSelection(null);
            adapter.selectAll();
            updateSelectionUi();
        } else if (id == R.id.action_refresh) {
            reload();
        } else if (id == R.id.action_terminal_here) {
            openTerminalHere();
        } else if (id == R.id.action_bookmark) {
            toggleBookmark();
        } else if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
        } else if (id == R.id.action_trash) {
            startActivity(new Intent(this, TrashActivity.class));
        } else if (id == R.id.action_license) {
            startActivity(new Intent(this, LicenseActivity.class));
        } else if (id == R.id.sel_copy) {
            copySelection(false);
        } else if (id == R.id.sel_cut) {
            copySelection(true);
        } else if (id == R.id.sel_delete) {
            deleteSelection();
        } else if (id == R.id.sel_rename) {
            renameSelection();
        } else if (id == R.id.sel_batch_rename) {
            batchRenameSelection();
        } else if (id == R.id.sel_compress) {
            compressSelection();
        } else if (id == R.id.sel_share) {
            shareSelection();
        } else if (id == R.id.sel_properties) {
            showProperties();
        } else if (id == R.id.sel_bookmark) {
            bookmarkSelection();
        } else if (id == R.id.sel_select_all) {
            adapter.selectAll();
            updateSelectionUi();
        } else if (id == R.id.sel_open_with) {
            List<FileItem> items = adapter.getSelectedItems();
            if (items.size() == 1) showOpenWith(items.get(0));
        }
        return true;
    }

    private void setupList() {
        adapter = new FileListAdapter(this, this);
        fileList.setAdapter(adapter);
        applyLayoutManager();
        fileList.addItemDecoration(new DividerDecoration());
        fileList.setHasFixedSize(true);
        emptyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!hasStoragePermission()) requestStorage();
                else if (currentDir != null && currentDir.getParentFile() != null) openDir(currentDir.getParentFile());
            }
        });
    }

    private void applyLayoutManager() {
        if (adapter.isGrid()) {
            fileList.setLayoutManager(new GridLayoutManager(this, 3));
        } else {
            fileList.setLayoutManager(new LinearLayoutManager(this));
        }
    }

    private void setupTabs() {
        tabs.clear();
        String path = Prefs.lastPath(this);
        File f = new File(path);
        if (TextUtils.isEmpty(path) || !f.exists() || !f.isDirectory()) {
            path = homePath();
        }
        tabs.add(new Tab(path));
        tabIndex = 0;
    }

    private void refreshBottomPaste() {
        float alpha = Clipboard.isEmpty() ? 0.38f : 1f;
        btnPaste.setAlpha(alpha);
    }

    private void setupBottomBar() {
        findViewById(R.id.btn_new_folder).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newFolder();
            }
        });
        findViewById(R.id.btn_new_file).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                newFile();
            }
        });
        findViewById(R.id.btn_paste).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteClipboard();
            }
        });
        findViewById(R.id.btn_sort).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSortDialog();
            }
        });
        findViewById(R.id.btn_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleViewMode();
            }
        });
        refreshBottomPaste();
        updateViewButton();
    }

    private void updateViewButton() {
        ImageView icon = findViewById(R.id.btn_view_icon);
        TextView label = findViewById(R.id.btn_view_label);
        if (adapter.isGrid()) {
            icon.setImageDrawable(UiUtils.tint(this, R.drawable.ic_list, UiUtils.color(this, R.color.text_secondary)));
            label.setText("列表");
        } else {
            icon.setImageDrawable(UiUtils.tint(this, R.drawable.ic_grid, UiUtils.color(this, R.color.text_secondary)));
            label.setText("网格");
        }
    }

    private void setupDrawer() {
        navContainer.removeAllViews();
        navBottom.removeAllViews();
        addNav(navContainer, R.drawable.ic_home, "主页", homePath());
        addNav(navContainer, R.drawable.ic_storage, "内部存储", homePath());
        String sd = secondaryStorage();
        if (sd != null) addNav(navContainer, R.drawable.ic_storage, "存储卡", sd);
        addNav(navContainer, R.drawable.ic_root, "根目录", "/");

        addNav(navBottom, R.drawable.ic_terminal, "命令行", null);
        addNav(navBottom, R.drawable.ic_delete, "回收站", null);
        addNav(navBottom, R.drawable.ic_analyze, "存储分析", null);
        addNav(navBottom, R.drawable.ic_settings, "设置", null);

        findViewById(R.id.drawer_license).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawer.closeDrawers();
                startActivity(new Intent(MainActivity.this, LicenseActivity.class));
            }
        });
        storageSummary.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawer.closeDrawers();
                startActivity(new Intent(MainActivity.this, StorageAnalyzerActivity.class));
            }
        });
        storageBar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawer.closeDrawers();
                startActivity(new Intent(MainActivity.this, StorageAnalyzerActivity.class));
            }
        });
        updateStorageCard();
        rebuildBookmarks();
    }

    private void addNav(LinearLayout container, int icon, String label, final String path) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiUtils.dp(this, 18), 0, UiUtils.dp(this, 14), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 44)));
        row.setBackgroundResource(R.drawable.bg_item_selectable);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(UiUtils.tint(this, icon, UiUtils.color(this, R.color.icon)));
        row.addView(iv, new LinearLayout.LayoutParams(UiUtils.dp(this, 19), UiUtils.dp(this, 19)));
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(15);
        tv.setTextColor(UiUtils.color(this, R.color.text_primary));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = UiUtils.dp(this, 14);
        tv.setLayoutParams(lp);
        row.addView(tv);
        if (path != null) navPaths.put(row, path);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawer.closeDrawers();
                if (path != null) openDir(new File(path));
                else handleToolNav(v);
            }
        });
        container.addView(row);
    }

    private void handleToolNav(View v) {
        String label = "";
        if (v instanceof LinearLayout) {
            View child = ((LinearLayout) v).getChildAt(1);
            if (child instanceof TextView) label = ((TextView) child).getText().toString();
        }
        if ("命令行".equals(label)) openTerminalHere();
        else if ("回收站".equals(label)) startActivity(new Intent(this, TrashActivity.class));
        else if ("存储分析".equals(label)) startActivity(new Intent(this, StorageAnalyzerActivity.class));
        else if ("设置".equals(label)) startActivity(new Intent(this, SettingsActivity.class));
    }

    private void rebuildBookmarks() {
        bookmarkContainer.removeAllViews();
        List<String> marks = Prefs.bookmarkList(this);
        if (marks.isEmpty()) {
            bookmarkEmpty.setVisibility(View.VISIBLE);
        } else {
            bookmarkEmpty.setVisibility(View.GONE);
            View header = bookmarkContainer;
            for (final String path : marks) {
                addBookmarkRow(bookmarkContainer, path);
            }
        }
        final String cur = currentDir != null ? currentDir.getAbsolutePath() : null;
        if (cur != null && !Prefs.isBookmarked(this, cur)) {
            TextView add = new TextView(this);
            add.setText("+ 收藏当前目录");
            add.setTextSize(13);
            add.setTextColor(UiUtils.color(this, R.color.accent));
            add.setPadding(UiUtils.dp(this, 18), UiUtils.dp(this, 6), UiUtils.dp(this, 18), UiUtils.dp(this, 8));
            add.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Prefs.addBookmark(MainActivity.this, cur);
                    rebuildBookmarks();
                }
            });
            bookmarkContainer.addView(add);
        }
    }

    private void addBookmarkRow(LinearLayout container, final String path) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(UiUtils.dp(this, 18), 0, UiUtils.dp(this, 14), 0);
        row.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 42)));
        row.setBackgroundResource(R.drawable.bg_item_selectable);
        ImageView iv = new ImageView(this);
        iv.setImageDrawable(UiUtils.tint(this, R.drawable.ic_folder, UiUtils.color(this, R.color.folder)));
        row.addView(iv, new LinearLayout.LayoutParams(UiUtils.dp(this, 18), UiUtils.dp(this, 18)));
        TextView tv = new TextView(this);
        tv.setText(new File(path).getName().length() == 0 ? path : new File(path).getName());
        tv.setTextSize(14);
        tv.setTextColor(UiUtils.color(this, R.color.text_primary));
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        lp.leftMargin = UiUtils.dp(this, 12);
        tv.setLayoutParams(lp);
        row.addView(tv);
        navPaths.put(row, path);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                drawer.closeDrawers();
                openDir(new File(path));
            }
        });
        row.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                UiUtils.confirm(MainActivity.this, "取消收藏", "移除「" + path + "」？", new Runnable() {
                    @Override
                    public void run() {
                        Prefs.removeBookmark(MainActivity.this, path);
                        rebuildBookmarks();
                    }
                });
                return true;
            }
        });
        container.addView(row);
    }

    private void updateStorageCard() {
        try {
            File path = new File(homePath());
            long total = FileUtils.totalSpace(path.getAbsolutePath());
            long free = FileUtils.availableSpace(path.getAbsolutePath());
            if (total <= 0) {
                storageSummary.setText("存储空间不可用");
                return;
            }
            long used = total - free;
            int percent = (int) (used * 100 / total);
            storageBar.setProgress(Math.max(1, percent));
            storageSummary.setText("已用 " + FileUtils.formatSize(used) + " / 共 " + FileUtils.formatSize(total)
                    + " · 可用 " + FileUtils.formatSize(free));
        } catch (Exception e) {
            storageSummary.setText("存储空间不可用");
        }
    }

    private void updateNavSelection() {
        String cur = currentDir != null ? currentDir.getAbsolutePath() : "";
        boolean homeHighlighted = false;
        for (Map.Entry<View, String> e : navPaths.entrySet()) {
            String path = e.getValue();
            boolean active = false;
            if (path != null && path.equals(cur)) {
                if (path.equals(homePath())) {
                    if (!homeHighlighted) {
                        active = true;
                        homeHighlighted = true;
                    }
                } else {
                    active = true;
                }
            }
            e.getKey().setBackgroundResource(active
                    ? R.drawable.bg_drawer_item_selected : R.drawable.bg_item_selectable);
        }
    }

    private void startHome() {
        String path = tabs.isEmpty() ? homePath() : tabs.get(tabIndex).path;
        openDir(new File(path));
    }

    private void openDir(File dir) {
        if (dir == null) return;
        if (dir.isFile()) dir = dir.getParentFile();
        if (dir == null) dir = new File("/");
        final File target = dir;
        if (!target.exists()) {
            UiUtils.toast(this, "目录不存在");
            return;
        }
        if (selectionMode) exitSelection();
        if ("/".equals(target.getAbsolutePath()) && !rootAvailable) {
            UiUtils.toastLong(this, "未检测到 Root，仅显示系统允许读取的目录");
        }
        currentDir = target;
        if (tabs.isEmpty()) tabs.add(new Tab(target.getAbsolutePath()));
        else tabs.get(tabIndex).path = target.getAbsolutePath();
        Prefs.setLastPath(this, target.getAbsolutePath());
        renderTabs();
        updateTitle();
        rebuildBookmarks();
        updateNavSelection();
        updateStorageCard();
        loadDir(target);
    }

    private void updateTitle() {
        if (currentDir == null) return;
        String path = currentDir.getAbsolutePath();
        String name;
        if ("/".equals(path)) name = "根目录";
        else if (path.equals(homePath())) name = "内部存储";
        else {
            name = currentDir.getName();
            if (TextUtils.isEmpty(name)) name = path;
        }
        toolbar.setTitle(name);
        toolbar.setSubtitle(path);
    }

    private void loadDir(final File dir) {
        final int seq = ++loadSeq;
        loading.setVisibility(View.VISIBLE);
        emptyView.setVisibility(View.GONE);
        final boolean showHidden = Prefs.showHidden(this);
        final int sortBy = Prefs.sortBy(this);
        final boolean asc = Prefs.sortAsc(this);
        final boolean foldersFirst = Prefs.foldersFirst(this);
        loadedShowHidden = showHidden;
        loadedSortBy = sortBy;
        loadedSortAsc = asc;
        loadedFoldersFirst = foldersFirst;
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final List<FileItem> data = FileUtils.list(dir, showHidden);
                FileUtils.sort(data, sortBy, asc, foldersFirst);
                Handler main = new Handler(Looper.getMainLooper());
                main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (seq != loadSeq || isFinishing()) return;
                        loading.setVisibility(View.GONE);
                        adapter.setGrid(Prefs.grid(MainActivity.this));
                        adapter.setItems(data, true);
                        updateViewButton();
                        fileList.scrollToPosition(0);
                        if (data.isEmpty()) {
                            emptyView.setVisibility(View.VISIBLE);
                            if (!hasStoragePermission()) {
                                emptyText.setText("需要存储权限才能管理文件");
                                emptyButton.setText("授予权限");
                                emptyButton.setVisibility(View.VISIBLE);
                            } else if (!dir.canRead() && dir.getAbsolutePath().startsWith("/")) {
                                emptyText.setText("无法读取此目录\n部分系统目录需要 Root 权限");
                                emptyButton.setText("返回上一级");
                                emptyButton.setVisibility(dir.getParentFile() != null ? View.VISIBLE : View.GONE);
                            } else {
                                emptyText.setText("空文件夹");
                                emptyButton.setVisibility(View.GONE);
                            }
                        }
                        refreshBottomPaste();
                    }
                });
            }
        });
    }

    private void reload() {
        if (currentDir != null) loadDir(currentDir);
        updateStorageCard();
        rebuildBookmarks();
    }

    private void renderTabs() {
        tabContainer.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            Tab tab = tabs.get(i);
            String name = new File(tab.path).getName();
            if (TextUtils.isEmpty(name)) name = "/";
            TextView chip = new TextView(this);
            chip.setText(name);
            chip.setTextSize(12);
            chip.setSingleLine(true);
            chip.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            chip.setMaxWidth(UiUtils.dp(this, 130));
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(UiUtils.dp(this, 10), 0, UiUtils.dp(this, 10), 0);
            chip.setTextColor(UiUtils.color(this, index == tabIndex ? R.color.accent : R.color.text_secondary));
            chip.setBackgroundResource(index == tabIndex ? R.drawable.bg_chip_active : R.drawable.bg_chip);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, UiUtils.dp(this, 28));
            lp.rightMargin = UiUtils.dp(this, 6);
            chip.setLayoutParams(lp);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (index == tabIndex) return;
                    tabIndex = index;
                    openDir(new File(tabs.get(tabIndex).path));
                }
            });
            chip.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    closeTab(index);
                    return true;
                }
            });
            tabContainer.addView(chip);
        }
        TextView plus = new TextView(this);
        plus.setText("+");
        plus.setTextSize(16);
        plus.setTextColor(UiUtils.color(this, R.color.text_secondary));
        plus.setGravity(Gravity.CENTER);
        plus.setBackgroundResource(R.drawable.bg_chip);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(UiUtils.dp(this, 30), UiUtils.dp(this, 28));
        plus.setLayoutParams(lp);
        plus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentDir != null) {
                    tabs.add(new Tab(currentDir.getAbsolutePath()));
                    tabIndex = tabs.size() - 1;
                    renderTabs();
                }
            }
        });
        tabContainer.addView(plus);
        tabsScroll.post(new Runnable() {
            @Override
            public void run() {
                View child = tabContainer.getChildAt(tabIndex);
                if (child != null) tabsScroll.smoothScrollTo(child.getLeft() - UiUtils.dp(MainActivity.this, 8), 0);
            }
        });
    }

    private void closeTab(int index) {
        if (tabs.size() <= 1) {
            UiUtils.toast(this, "至少保留一个标签页");
            return;
        }
        tabs.remove(index);
        if (tabIndex >= tabs.size()) tabIndex = tabs.size() - 1;
        else if (tabIndex > index) tabIndex--;
        renderTabs();
        openDir(new File(tabs.get(tabIndex).path));
    }

    private void toggleViewMode() {
        boolean grid = !adapter.isGrid();
        Prefs.setGrid(this, grid);
        int pos = 0;
        RecyclerView.LayoutManager lm = fileList.getLayoutManager();
        if (lm instanceof LinearLayoutManager) pos = ((LinearLayoutManager) lm).findFirstVisibleItemPosition();
        adapter.setGrid(grid);
        applyLayoutManager();
        updateViewButton();
        fileList.scrollToPosition(Math.max(0, pos));
    }

    @Override
    public void onClick(FileItem item) {
        if (selectionMode) {
            adapter.toggle(item);
            updateSelectionUi();
            return;
        }
        openItem(item);
    }

    @Override
    public void onLongClick(FileItem item) {
        if (!selectionMode) {
            enterSelection(item);
        } else {
            adapter.toggle(item);
            updateSelectionUi();
        }
    }

    private void openItem(FileItem item) {
        File f = item.file();
        if (!f.exists()) {
            UiUtils.toast(this, "文件不存在");
            reload();
            return;
        }
        if (item.isDir) {
            if (!f.canRead() && !rootAvailable) {
                UiUtils.toastLong(this, "没有权限读取此目录，可尝试 Root");
                return;
            }
            openDir(f);
            return;
        }
        if (!f.canRead()) {
            if (rootAvailable) {
                copyRootAndOpen(item);
            } else {
                UiUtils.toastLong(this, "没有权限读取此文件");
            }
            return;
        }
        Intent i;
        switch (item.type()) {
            case FileItem.TYPE_IMAGE:
                i = new Intent(this, ImageViewerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                startActivity(i);
                break;
            case FileItem.TYPE_VIDEO:
            case FileItem.TYPE_AUDIO:
                i = new Intent(this, MediaPlayerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                startActivity(i);
                break;
            case FileItem.TYPE_ARCHIVE:
                i = new Intent(this, ZipViewerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                startActivity(i);
                break;
            case FileItem.TYPE_APK:
                i = new Intent(this, ApkInfoActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                startActivity(i);
                break;
            case FileItem.TYPE_TEXT:
                i = new Intent(this, TextViewerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                startActivity(i);
                break;
            case FileItem.TYPE_PDF:
                UiUtils.openExternally(this, f, "application/pdf");
                break;
            default:
                showOpenWith(item);
                break;
        }
    }

    private void copyRootAndOpen(final FileItem item) {
        if (item.size > 64L * 1024 * 1024) {
            UiUtils.toastLong(this, "该文件超过 64 MB，Root 预览暂不支持");
            return;
        }
        final UiUtils.ProgressHandle progress = UiUtils.progress(this, "正在通过 Root 读取");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                File dir = new File(getCacheDir(), "root_preview");
                dir.mkdirs();
                File dest = new File(dir, item.name);
                RootShell.exec("cp -f " + RootShell.quote(item.path) + " " + RootShell.quote(dest.getAbsolutePath()), 40000);
                final boolean ok = dest.exists() && dest.length() > 0;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(progress);
                        if (ok) {
                            openItem(FileItem.fromFile(dest));
                        } else {
                            UiUtils.toast(MainActivity.this, "Root 读取失败");
                        }
                    }
                });
            }
        });
    }

    private void showOpenWith(final FileItem item) {
        final File f = item.file();
        final List<String> labels = new ArrayList<>();
        final List<Integer> actions = new ArrayList<>();
        labels.add("文本方式查看");
        actions.add(1);
        labels.add("图片查看器");
        actions.add(2);
        if (item.type() == FileItem.TYPE_VIDEO || item.type() == FileItem.TYPE_AUDIO) {
            labels.add("内置播放器");
            actions.add(3);
        }
        if (item.type() == FileItem.TYPE_ARCHIVE) {
            labels.add("压缩包浏览");
            actions.add(4);
        }
        if (item.type() == FileItem.TYPE_APK) {
            labels.add("安装包信息");
            actions.add(5);
        }
        labels.add("用其他应用打开");
        actions.add(6);
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("打开方式")
                .setItems(labels.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        int action = actions.get(which);
                        Intent i;
                        if (action == 1) {
                            i = new Intent(MainActivity.this, TextViewerActivity.class);
                            i.putExtra("path", f.getAbsolutePath());
                            startActivity(i);
                        } else if (action == 2) {
                            i = new Intent(MainActivity.this, ImageViewerActivity.class);
                            i.putExtra("path", f.getAbsolutePath());
                            startActivity(i);
                        } else if (action == 3) {
                            i = new Intent(MainActivity.this, MediaPlayerActivity.class);
                            i.putExtra("path", f.getAbsolutePath());
                            startActivity(i);
                        } else if (action == 4) {
                            i = new Intent(MainActivity.this, ZipViewerActivity.class);
                            i.putExtra("path", f.getAbsolutePath());
                            startActivity(i);
                        } else if (action == 5) {
                            i = new Intent(MainActivity.this, ApkInfoActivity.class);
                            i.putExtra("path", f.getAbsolutePath());
                            startActivity(i);
                        } else {
                            UiUtils.openExternally(MainActivity.this, f, item.mime());
                        }
                    }
                })
                .show();
    }

    private void enterSelection(FileItem first) {
        selectionMode = true;
        adapter.setSelectionMode(true);
        if (first != null) adapter.toggle(first);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_close, UiUtils.color(this, R.color.icon)));
        toolbar.getMenu().clear();
        toolbar.inflateMenu(R.menu.menu_selection);
        tintMenuIcons(toolbar.getMenu());
        tabsScroll.setVisibility(View.GONE);
        tabsDivider.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        toolbar.setTitle("已选择 " + adapter.getSelectedCount() + " 项");
        toolbar.setSubtitle("");
    }

    private void exitSelection() {
        selectionMode = false;
        adapter.clearSelection();
        adapter.setSelectionMode(false);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_menu, UiUtils.color(this, R.color.icon)));
        toolbar.getMenu().clear();
        toolbar.inflateMenu(R.menu.menu_main);
        tintMenuIcons(toolbar.getMenu());
        tabsScroll.setVisibility(View.VISIBLE);
        tabsDivider.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        updateTitle();
    }

    private void updateSelectionUi() {
        int count = adapter.getSelectedCount();
        if (count == 0) {
            exitSelection();
            return;
        }
        toolbar.setTitle("已选择 " + count + " 项");
        toolbar.setSubtitle("");
        updateTitleIfNotSelection();
    }

    private void updateTitleIfNotSelection() {
        if (!selectionMode) updateTitle();
    }

    private void newFolder() {
        if (currentDir == null) return;
        UiUtils.showInput(this, "新建文件夹", "文件夹名称", "", "支持中文、空格与符号，不能包含 /", new UiUtils.InputCallback() {
            @Override
            public void onInput(String text) {
                if (text.contains("/")) {
                    UiUtils.toast(MainActivity.this, "名称不能包含 /");
                    return;
                }
                File f = new File(currentDir, text);
                if (f.exists()) {
                    UiUtils.toast(MainActivity.this, "同名文件已存在");
                    return;
                }
                boolean ok = f.mkdirs();
                if (!ok && rootAvailable) ok = RootShell.mkdir(f.getAbsolutePath());
                UiUtils.toast(MainActivity.this, ok ? "已创建文件夹" : "创建失败");
                reload();
            }
        });
    }

    private void newFile() {
        if (currentDir == null) return;
        UiUtils.showInput(this, "新建文件", "文件名称", "", "可包含扩展名，例如 note.txt", new UiUtils.InputCallback() {
            @Override
            public void onInput(String text) {
                if (text.contains("/")) {
                    UiUtils.toast(MainActivity.this, "名称不能包含 /");
                    return;
                }
                File f = new File(currentDir, text);
                if (f.exists()) {
                    UiUtils.toast(MainActivity.this, "同名文件已存在");
                    return;
                }
                try {
                    File parent = f.getParentFile();
                    if (parent != null) parent.mkdirs();
                    boolean ok = f.createNewFile();
                    if (!ok && rootAvailable) {
                        ok = RootShell.exec("touch " + RootShell.quote(f.getAbsolutePath())) != null;
                    }
                    UiUtils.toast(MainActivity.this, ok ? "已创建文件" : "创建失败");
                    reload();
                } catch (Exception e) {
                    UiUtils.toast(MainActivity.this, "创建失败: " + e.getMessage());
                }
            }
        });
    }

    private void showSortDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiUtils.dp(this, 20), UiUtils.dp(this, 4), UiUtils.dp(this, 20), 0);
        final RadioGroup group = new RadioGroup(this);
        String[] names = {"按名称", "按大小", "按日期", "按类型"};
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(names[i]);
            rb.setTextSize(15);
            rb.setTextColor(UiUtils.color(this, R.color.text_primary));
            rb.setPadding(0, UiUtils.dp(this, 4), 0, UiUtils.dp(this, 4));
            group.addView(rb);
        }
        group.check(1000 + Prefs.sortBy(this));
        box.addView(group);
        final CheckBox desc = new CheckBox(this);
        desc.setText("倒序排列");
        desc.setTextSize(14);
        desc.setTextColor(UiUtils.color(this, R.color.text_secondary));
        desc.setChecked(!Prefs.sortAsc(this));
        box.addView(desc);
        final CheckBox folders = new CheckBox(this);
        folders.setText("文件夹优先");
        folders.setTextSize(14);
        folders.setTextColor(UiUtils.color(this, R.color.text_secondary));
        folders.setChecked(Prefs.foldersFirst(this));
        box.addView(folders);
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("排序方式")
                .setView(box)
                .setPositiveButton("应用", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        Prefs.setSortBy(MainActivity.this, Math.max(0, group.getCheckedRadioButtonId() - 1000));
                        Prefs.setSortAsc(MainActivity.this, !desc.isChecked());
                        Prefs.setFoldersFirst(MainActivity.this, folders.isChecked());
                        reload();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void copySelection(boolean cut) {
        List<FileItem> items = adapter.getSelectedItems();
        if (items.isEmpty()) return;
        Clipboard.set(items, cut);
        refreshBottomPaste();
        UiUtils.toast(this, cut ? "已剪切 " + items.size() + " 项" : "已复制 " + items.size() + " 项");
        exitSelection();
    }

    private void pasteClipboard() {
        if (Clipboard.isEmpty()) {
            UiUtils.toast(this, "剪贴板为空");
            return;
        }
        if (currentDir == null) return;
        final List<FileItem> items = new ArrayList<>(Clipboard.ITEMS);
        final boolean cut = Clipboard.cut;
        for (FileItem i : items) {
            if (i.path.equals(currentDir.getAbsolutePath())
                    || currentDir.getAbsolutePath().startsWith(i.path + "/")) {
                UiUtils.toast(this, "不能粘贴到自身目录");
                return;
            }
        }
        final int[] strategy = new int[]{FileUtils.CONFLICT_RENAME};
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("粘贴 " + items.size() + " 项")
                .setItems(new String[]{"同名文件自动重命名", "覆盖同名文件", "跳过同名文件"}, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        if (which == 0) strategy[0] = FileUtils.CONFLICT_RENAME;
                        else if (which == 1) strategy[0] = FileUtils.CONFLICT_OVERWRITE;
                        else strategy[0] = FileUtils.CONFLICT_SKIP;
                        runPaste(items, cut, strategy[0]);
                    }
                })
                .show();
    }

    private void runPaste(final List<FileItem> items, final boolean cut, final int conflictMode) {
        final UiUtils.ProgressHandle progress = UiUtils.progress(this, cut ? "正在移动" : "正在复制");
        final long[] total = new long[]{0};
        for (FileItem i : items) total[0] += i.isDir ? FileUtils.sizeOf(i.file()) : i.size;
        final long[] done = new long[]{0};
        final int[] okCount = new int[]{0};
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                for (FileItem item : items) {
                    if (progress.cancelled) break;
                    File src = item.file();
                    File dst = new File(currentDir, item.name);
                    FileUtils.Cancel cancel = new FileUtils.Cancel() {
                        @Override
                        public boolean isCancelled() {
                            return progress.cancelled;
                        }
                    };
                    FileUtils.Conflict conflict = new FileUtils.Conflict() {
                        @Override
                        public int resolve(File target) {
                            return conflictMode;
                        }
                    };
                    FileUtils.Progress p = new FileUtils.Progress() {
                        @Override
                        public void onProgress(String current, long d, long t) {
                            updateProgress(progress, current, d, t);
                        }
                    };
                    boolean ok;
                    if (cut) ok = FileUtils.moveRecursive(src, dst, cancel, p, conflict, done);
                    else ok = FileUtils.copyRecursive(src, dst, cancel, p, conflict, done);
                    if (ok) okCount[0]++;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(progress);
                        if (progress.cancelled) {
                            UiUtils.toast(MainActivity.this, "已取消");
                        } else {
                            if (cut && okCount[0] == items.size()) Clipboard.clear();
                            refreshBottomPaste();
                            UiUtils.toast(MainActivity.this, "完成 " + okCount[0] + " 项");
                        }
                        reload();
                    }
                });
            }
        });
    }

    private void updateProgress(final UiUtils.ProgressHandle h, final String name, final long done, final long total) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!h.dialog.isShowing()) return;
                int percent = total > 0 ? (int) (done * 100 / total) : 0;
                h.text.setText(name + "  " + percent + "%");
            }
        });
    }

    private void deleteSelection() {
        final List<FileItem> items = adapter.getSelectedItems();
        if (items.isEmpty()) return;
        boolean useTrash = Prefs.useTrash(this);
        StringBuilder msg = new StringBuilder();
        msg.append("共 ").append(items.size()).append(" 项。");
        msg.append(useTrash ? "\n将移入回收站，可在回收站中还原。" : "\n此操作将永久删除，无法恢复。");
        Runnable run = new Runnable() {
            @Override
            public void run() {
                doDelete(items, Prefs.useTrash(MainActivity.this));
            }
        };
        if (Prefs.confirmDelete(this)) {
            UiUtils.confirm(this, "删除", msg.toString(), run);
        } else {
            run.run();
        }
    }

    private void doDelete(final List<FileItem> items, final boolean toTrash) {
        final UiUtils.ProgressHandle progress = UiUtils.progress(this, "正在删除");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                int ok = 0;
                for (FileItem item : items) {
                    if (progress.cancelled) break;
                    if (toTrash && !item.rootOnly) {
                        List<FileItem> one = new ArrayList<>();
                        one.add(item);
                        if (TrashManager.moveToTrash(MainActivity.this, one)) ok++;
                    } else if (rootAvailable && (item.rootOnly || !item.file().canWrite())) {
                        if (RootShell.delete(item.path)) ok++;
                    } else {
                        if (FileUtils.deleteRecursive(item.file())) ok++;
                    }
                }
                final int finalOk = ok;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(progress);
                        UiUtils.toast(MainActivity.this, "已删除 " + finalOk + " 项");
                        exitSelection();
                        reload();
                    }
                });
            }
        });
    }

    private void renameSelection() {
        final List<FileItem> items = adapter.getSelectedItems();
        if (items.size() != 1) {
            UiUtils.toast(this, "请选择单个文件重命名");
            return;
        }
        final FileItem item = items.get(0);
        UiUtils.showInput(this, "重命名", "新名称", item.name, null, new UiUtils.InputCallback() {
            @Override
            public void onInput(String text) {
                if (text.contains("/")) {
                    UiUtils.toast(MainActivity.this, "名称不能包含 /");
                    return;
                }
                if (text.equals(item.name)) return;
                File src = item.file();
                File dst = new File(src.getParentFile(), text);
                if (dst.exists()) {
                    UiUtils.toast(MainActivity.this, "同名文件已存在");
                    return;
                }
                boolean ok = src.renameTo(dst);
                if (!ok && rootAvailable) ok = RootShell.rename(src.getAbsolutePath(), dst.getAbsolutePath());
                UiUtils.toast(MainActivity.this, ok ? "已重命名" : "重命名失败");
                exitSelection();
                reload();
            }
        });
    }

    private void batchRenameSelection() {
        final List<FileItem> items = adapter.getSelectedItems();
        if (items.isEmpty()) return;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(UiUtils.dp(this, 20), UiUtils.dp(this, 4), UiUtils.dp(this, 20), 0);
        TextView l1 = new TextView(this);
        l1.setText("在原名前后添加内容，扩展名保持不变");
        l1.setTextSize(12);
        l1.setTextColor(UiUtils.color(this, R.color.text_secondary));
        box.addView(l1);
        final EditText prefix = new EditText(this);
        prefix.setHint("前缀，例如：照片_");
        prefix.setSingleLine(true);
        prefix.setTextSize(14);
        prefix.setTextColor(UiUtils.color(this, R.color.text_primary));
        prefix.setBackgroundResource(R.drawable.bg_input);
        prefix.setPadding(UiUtils.dp(this, 10), 0, UiUtils.dp(this, 10), 0);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 42));
        plp.topMargin = UiUtils.dp(this, 10);
        box.addView(prefix, plp);
        final EditText suffix = new EditText(this);
        suffix.setHint("后缀，例如：_备份");
        suffix.setSingleLine(true);
        suffix.setTextSize(14);
        suffix.setTextColor(UiUtils.color(this, R.color.text_primary));
        suffix.setBackgroundResource(R.drawable.bg_input);
        suffix.setPadding(UiUtils.dp(this, 10), 0, UiUtils.dp(this, 10), 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UiUtils.dp(this, 42));
        slp.topMargin = UiUtils.dp(this, 8);
        box.addView(suffix, slp);
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle("批量重命名 " + items.size() + " 项")
                .setView(box)
                .setPositiveButton("应用", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String pre = prefix.getText().toString();
                        String suf = suffix.getText().toString();
                        int ok = 0;
                        for (FileItem item : items) {
                            String name = item.name;
                            String base = name;
                            String ext = "";
                            if (!item.isDir) {
                                int dot = name.lastIndexOf('.');
                                if (dot > 0) {
                                    base = name.substring(0, dot);
                                    ext = name.substring(dot);
                                }
                            }
                            String newName = pre + base + suf + ext;
                            if (newName.contains("/") || newName.length() == 0) continue;
                            File src = item.file();
                            File dst = new File(src.getParentFile(), newName);
                            if (dst.exists()) continue;
                            boolean renamed = src.renameTo(dst);
                            if (!renamed && rootAvailable) {
                                renamed = RootShell.rename(src.getAbsolutePath(), dst.getAbsolutePath());
                            }
                            if (renamed) ok++;
                        }
                        UiUtils.toast(MainActivity.this, "已重命名 " + ok + " 项");
                        exitSelection();
                        reload();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void compressSelection() {
        final List<FileItem> items = adapter.getSelectedItems();
        if (items.isEmpty()) return;
        String base = items.size() == 1 ? items.get(0).name : "压缩包";
        final String name = FileUtils.uniqueName(currentDir, base + ".zip");
        final UiUtils.ProgressHandle progress = UiUtils.progress(this, "正在压缩");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final File zip = new File(currentDir, name);
                boolean ok = FileUtils.zip(items, zip, new FileUtils.Cancel() {
                    @Override
                    public boolean isCancelled() {
                        return progress.cancelled;
                    }
                }, new FileUtils.Progress() {
                    @Override
                    public void onProgress(String current, long d, long t) {
                        updateProgress(progress, current, d, t);
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(progress);
                        UiUtils.toast(MainActivity.this, ok ? "已创建 " + name : "压缩失败");
                        exitSelection();
                        reload();
                    }
                });
            }
        });
    }

    private void shareSelection() {
        final List<FileItem> items = adapter.getSelectedItems();
        if (items.isEmpty()) return;
        List<Uri> uris = new ArrayList<>();
        ArrayList<String> mimes = new ArrayList<>();
        for (FileItem item : items) {
            if (item.isDir) continue;
            try {
                uris.add(UiUtils.uriFor(this, item.file()));
                mimes.add(item.mime());
            } catch (Exception ignored) {
            }
        }
        if (uris.isEmpty()) {
            UiUtils.toast(this, "文件夹无法直接分享");
            return;
        }
        try {
            Intent intent;
            if (uris.size() == 1) {
                intent = new Intent(Intent.ACTION_SEND);
                intent.putExtra(Intent.EXTRA_STREAM, uris.get(0));
                intent.setType(mimes.get(0));
            } else {
                intent = new Intent(Intent.ACTION_SEND_MULTIPLE);
                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, new ArrayList<>(uris));
                intent.setType("*/*");
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "分享"));
        } catch (Exception e) {
            UiUtils.toast(this, "分享失败");
        }
    }

    private void showProperties() {
        final List<FileItem> items = adapter.getSelectedItems();
        if (items.size() != 1) {
            UiUtils.toast(this, "请选择单个项目查看属性");
            return;
        }
        final FileItem item = items.get(0);
        final UiUtils.ProgressHandle progress = UiUtils.progress(this, "正在读取属性");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final long size = item.isDir ? FileUtils.sizeOf(item.file()) : item.size;
                final int count = item.isDir ? FileUtils.countFiles(item.file()) : 1;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(progress);
                        StringBuilder sb = new StringBuilder();
                        sb.append("名称: ").append(item.name).append('\n');
                        sb.append("路径: ").append(item.path).append('\n');
                        sb.append("类型: ").append(item.typeLabel()).append('\n');
                        sb.append("大小: ").append(FileUtils.formatSize(size)).append('\n');
                        if (item.isDir) sb.append("文件数: ").append(count).append('\n');
                        sb.append("修改: ").append(FileUtils.formatDate(item.modified)).append('\n');
                        sb.append("权限: ").append(FileUtils.readPermission(item.file()));
                        if (rootAvailable) sb.append(" · 已启用 Root");
                        new AlertDialog.Builder(MainActivity.this, R.style.Theme_ZrinFile_Dialog)
                                .setTitle("属性")
                                .setMessage(sb.toString())
                                .setPositiveButton("关闭", null)
                                .setNeutralButton("校验值", new android.content.DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(android.content.DialogInterface dialog, int which) {
                                        computeChecksum(item);
                                    }
                                })
                                .show();
                    }
                });
            }
        });
    }

    private void computeChecksum(final FileItem item) {
        if (item.isDir) {
            UiUtils.toast(this, "文件夹不支持校验值");
            return;
        }
        final UiUtils.ProgressHandle progress = UiUtils.progress(this, "正在计算校验值");
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final String md5 = FileUtils.checksum(item.file(), "MD5");
                final String sha1 = FileUtils.checksum(item.file(), "SHA-1");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        UiUtils.dismiss(progress);
                        new AlertDialog.Builder(MainActivity.this, R.style.Theme_ZrinFile_Dialog)
                                .setTitle("校验值")
                                .setMessage("MD5:\n" + md5 + "\n\nSHA-1:\n" + sha1)
                                .setPositiveButton("关闭", null)
                                .show();
                    }
                });
            }
        });
    }

    private void bookmarkSelection() {
        List<FileItem> items = adapter.getSelectedItems();
        for (FileItem item : items) {
            if (item.isDir) Prefs.addBookmark(this, item.path);
        }
        UiUtils.toast(this, "已收藏文件夹");
        rebuildBookmarks();
        exitSelection();
    }

    private void toggleBookmark() {
        if (currentDir == null) return;
        String path = currentDir.getAbsolutePath();
        if (Prefs.isBookmarked(this, path)) {
            Prefs.removeBookmark(this, path);
            UiUtils.toast(this, "已取消收藏");
        } else {
            Prefs.addBookmark(this, path);
            UiUtils.toast(this, "已添加到收藏");
        }
        rebuildBookmarks();
    }

    private void openTerminalHere() {
        Intent i = new Intent(this, TerminalActivity.class);
        if (currentDir != null) i.putExtra("cwd", currentDir.getAbsolutePath());
        startActivity(i);
    }

    private void jumpToPath() {
        UiUtils.showInput(this, "跳转到路径", "绝对路径", currentDir.getAbsolutePath(), null, new UiUtils.InputCallback() {
            @Override
            public void onInput(String text) {
                File f = new File(text);
                if (!f.exists() || !f.isDirectory()) {
                    UiUtils.toast(MainActivity.this, "目录不存在");
                    return;
                }
                openDir(f);
                drawer.closeDrawers();
            }
        });
    }

    private boolean hasStoragePermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestStorage() {
        ActivityCompat.requestPermissions(this, new String[]{
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, REQ_STORAGE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                UiUtils.toast(this, "已获得存储权限");
                startHome();
                maybeAskBattery();
                maybeCheckUpdate();
            } else {
                loadDir(new File(homePath()));
                emptyView.setVisibility(View.VISIBLE);
                emptyText.setText("没有存储权限\n部分功能无法使用");
                emptyButton.setText("授予权限");
                emptyButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void maybeAskBattery() {
        if (Build.VERSION.SDK_INT < 23 || Prefs.batteryPromptShown(this)) return;
        Prefs.setBatteryPromptShown(this);
        UiUtils.confirm(this, "后台稳定性", "将本应用加入电池优化白名单，可避免长时间传输文件时被系统中断。",
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            i.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(i);
                        } catch (Exception ignored) {
                        }
                    }
                });
    }

    private void maybeCheckUpdate() {
        final android.content.SharedPreferences sp = Prefs.get(this);
        long last = sp.getLong("update_check_time", 0L);
        if (System.currentTimeMillis() - last < 6L * 60 * 60 * 1000) return;
        sp.edit().putLong("update_check_time", System.currentTimeMillis()).apply();
        fileList.postDelayed(new Runnable() {
            @Override
            public void run() {
                UpdateChecker.checkAsync(MainActivity.this, new UpdateChecker.Callback() {
                    @Override
                    public void onResult(UpdateInfo info, String error) {
                        if (isFinishing() || info == null) return;
                        if (!UpdateChecker.isNewer(info.versionName, com.zrinfile.app.BuildConfig.VERSION_NAME)) return;
                        String skipped = Prefs.get(MainActivity.this).getString("skipped_update", "");
                        if (info.versionName.equals(skipped)) return;
                        startActivity(UpdateActivity.intent(MainActivity.this, info));
                    }
                });
            }
        }, 1500);
    }

    private String homePath() {
        File f = Environment.getExternalStorageDirectory();
        return f != null ? f.getAbsolutePath() : "/";
    }

    private String secondaryStorage() {
        try {
            File[] dirs = getExternalFilesDirs(null);
            if (dirs != null && dirs.length > 1 && dirs[1] != null) {
                String p = dirs[1].getAbsolutePath();
                int idx = p.indexOf("/Android/");
                if (idx > 0) return p.substring(0, idx);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void checkRoot() {
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final boolean ok = RootShell.available();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        rootAvailable = ok;
                        rootBadge.setVisibility(ok ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String browse = intent.getStringExtra("browse");
        if (browse != null) {
            File f = new File(browse);
            if (f.exists() && f.isDirectory()) openDir(f);
        }
        String open = intent.getStringExtra("open");
        if (open == null) return;
        if ("terminal".equals(open)) openTerminalHere();
        else if ("trash".equals(open)) startActivity(new Intent(this, TrashActivity.class));
        else if ("search".equals(open)) {
            Intent i = new Intent(this, SearchActivity.class);
            i.putExtra("path", homePath());
            startActivity(i);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null && !selectionMode) {
            boolean grid = Prefs.grid(this);
            if (grid != adapter.isGrid()) {
                adapter.setGrid(grid);
                applyLayoutManager();
                updateViewButton();
            }
            if (Prefs.showHidden(this) != loadedShowHidden
                    || Prefs.sortBy(this) != loadedSortBy
                    || Prefs.sortAsc(this) != loadedSortAsc
                    || Prefs.foldersFirst(this) != loadedFoldersFirst) {
                reload();
            } else if (adapter.getItemCount() > 0) {
                adapter.notifyDataSetChanged();
            }
            refreshBottomPaste();
            updateStorageCard();
            rebuildBookmarks();
        }
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawers();
            return;
        }
        if (selectionMode) {
            exitSelection();
            return;
        }
        if (currentDir != null && currentDir.getParentFile() != null
                && !currentDir.getAbsolutePath().equals(homePath())
                && !currentDir.getAbsolutePath().equals("/")) {
            openDir(currentDir.getParentFile());
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBack < 1800) {
            super.onBackPressed();
        } else {
            lastBack = now;
            UiUtils.toast(this, "再按一次返回键退出");
        }
    }

    private void tintMenuIcons(Menu menu) {
        int color = UiUtils.color(this, R.color.icon);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            int res = iconResForId(item.getItemId());
            if (res != 0 && item.getIcon() != null) {
                item.setIcon(UiUtils.tint(this, res, color));
            }
            if (item.hasSubMenu()) tintMenuIcons(item.getSubMenu());
        }
    }

    private int iconResForId(int id) {
        if (id == R.id.action_search) return R.drawable.ic_search;
        if (id == R.id.action_sort) return R.drawable.ic_sort;
        if (id == R.id.action_more) return R.drawable.ic_more_vert;
        if (id == R.id.sel_copy) return R.drawable.ic_copy;
        if (id == R.id.sel_cut) return R.drawable.ic_cut;
        if (id == R.id.sel_delete) return R.drawable.ic_delete;
        if (id == R.id.sel_more) return R.drawable.ic_more_vert;
        return 0;
    }

    private class DividerDecoration extends RecyclerView.ItemDecoration {
        private final Paint paint = new Paint();

        DividerDecoration() {
            paint.setColor(UiUtils.color(MainActivity.this, R.color.divider));
            paint.setStrokeWidth(1f);
        }

        @Override
        public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            if (adapter.isGrid()) return;
            int left = parent.getPaddingLeft() + UiUtils.dp(MainActivity.this, 62);
            int right = parent.getWidth() - parent.getPaddingRight();
            for (int i = 0; i < parent.getChildCount(); i++) {
                View child = parent.getChildAt(i);
                int pos = parent.getChildAdapterPosition(child);
                if (pos < 0 || pos >= adapter.getItemCount() - 1) continue;
                c.drawRect(left, child.getBottom(), right, child.getBottom() + 1, paint);
            }
        }
    }
}
