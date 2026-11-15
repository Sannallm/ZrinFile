package com.zrinfile.app.ui;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

/** PDF 逐页渲染预览。 */
public class PdfViewerActivity extends AppCompatActivity {

    private ParcelFileDescriptor descriptor;
    private PdfRenderer renderer;
    private volatile boolean cancelled;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf);
        Toolbar toolbar = findViewById(R.id.toolbar);
        final LinearLayout pages = findViewById(R.id.pdf_pages);
        final ProgressBar progress = findViewById(R.id.pdf_progress);

        String path = getIntent().getStringExtra("path");
        if (path == null) {
            finish();
            return;
        }
        final File file = new File(path);
        toolbar.setTitle(file.getName());
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        if (Build.VERSION.SDK_INT < 21) {
            UiUtils.toast(this, "当前系统不支持 PDF 预览");
            finish();
            return;
        }
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(descriptor);
            toolbar.setSubtitle(renderer.getPageCount() + " 页 · " + FileUtils.formatSize(file.length()));
        } catch (Exception e) {
            UiUtils.toast(this, "无法打开 PDF");
            finish();
            return;
        }

        final int width = getResources().getDisplayMetrics().widthPixels - UiUtils.dp(this, 20);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (int i = 0; i < renderer.getPageCount(); i++) {
                        if (cancelled) break;
                        final Bitmap bitmap = renderPage(i, width);
                        if (bitmap == null) continue;
                        final int index = i;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (isFinishing() || cancelled) {
                                    bitmap.recycle();
                                    return;
                                }
                                ImageView iv = new ImageView(PdfViewerActivity.this);
                                iv.setImageBitmap(bitmap);
                                iv.setAdjustViewBounds(true);
                                iv.setBackgroundColor(Color.WHITE);
                                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        LinearLayout.LayoutParams.WRAP_CONTENT);
                                lp.bottomMargin = UiUtils.dp(PdfViewerActivity.this, 10);
                                pages.addView(iv, lp);
                                toolbar.setSubtitle((index + 1) + " / " + renderer.getPageCount());
                            }
                        });
                    }
                } catch (Exception ignored) {
                } finally {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.setVisibility(View.GONE);
                        }
                    });
                }
            }
        }, "zrinfile-pdf").start();
    }

    private Bitmap renderPage(int index, int targetWidth) {
        PdfRenderer.Page page = null;
        try {
            page = renderer.openPage(index);
            int w = Math.max(1, targetWidth);
            int h = (int) ((long) w * page.getHeight() / Math.max(1, page.getWidth()));
            Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            return bitmap;
        } catch (Exception e) {
            return null;
        } finally {
            if (page != null) {
                try {
                    page.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        try {
            if (renderer != null) renderer.close();
        } catch (Exception ignored) {
        }
        try {
            if (descriptor != null) descriptor.close();
        } catch (Exception ignored) {
        }
        super.onDestroy();
    }
}
