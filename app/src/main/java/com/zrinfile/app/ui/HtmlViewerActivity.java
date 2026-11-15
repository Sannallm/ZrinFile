package com.zrinfile.app.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

/** HTML / SVG 预览，使用系统 WebView。 */
public class HtmlViewerActivity extends AppCompatActivity {

    private WebView web;
    private File file;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_html_viewer);
        Toolbar toolbar = findViewById(R.id.toolbar);
        web = findViewById(R.id.html_web);

        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        file = new File(path);
        toolbar.setTitle(file.getName());
        toolbar.setSubtitle(FileUtils.formatSize(file.length()));
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (web != null && web.canGoBack()) web.goBack();
                else finish();
            }
        });
        toolbar.inflateMenu(R.menu.menu_html);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.action_html_edit) {
                    Intent i = new Intent(HtmlViewerActivity.this, HtmlEditorActivity.class);
                    i.putExtra("path", file.getAbsolutePath());
                    startActivity(i);
                    return true;
                }
                if (item.getItemId() == R.id.action_html_browser) {
                    UiUtils.openExternally(HtmlViewerActivity.this, file, "text/html");
                    return true;
                }
                return false;
            }
        });

        try {
            WebSettings s = web.getSettings();
            s.setJavaScriptEnabled(true);
            s.setAllowFileAccess(true);
            s.setAllowContentAccess(true);
            try {
                s.setAllowFileAccessFromFileURLs(true);
                s.setAllowUniversalAccessFromFileURLs(true);
            } catch (Exception ignored) {
            }
            s.setSupportZoom(true);
            s.setBuiltInZoomControls(true);
            s.setDisplayZoomControls(false);
            s.setLoadWithOverviewMode(true);
            s.setUseWideViewPort(true);
            if (Build.VERSION.SDK_INT >= 21) s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        } catch (Exception ignored) {
        }
        web.setBackgroundColor(UiUtils.color(this, R.color.window_bg));
        web.setWebViewClient(new WebViewClient());
        try {
            web.loadUrl("file://" + file.getAbsolutePath());
        } catch (Exception e) {
            UiUtils.toast(this, "无法打开此文件");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            try {
                web.stopLoading();
                web.loadUrl("about:blank");
                web.destroy();
            } catch (Exception ignored) {
            }
            web = null;
        }
        super.onDestroy();
    }
}
