package com.zrinfile.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.zrinfile.app.App;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

public class ImageViewerActivity extends AppCompatActivity {

    private Toolbar toolbar;
    private ZoomImageView image;
    private ProgressBar progress;
    private TextView name;
    private TextView meta;
    private File file;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_viewer);
        toolbar = findViewById(R.id.toolbar);
        image = findViewById(R.id.zoom_image);
        progress = findViewById(R.id.image_progress);
        name = findViewById(R.id.image_name);
        meta = findViewById(R.id.image_meta);
        toolbar.setTitleTextColor(Color.WHITE);
        toolbar.setSubtitleTextColor(0xFF8B9098);
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, 0xFFE6E7EA));
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
        name.setText(file.getName());
        toolbar.setTitle(file.getName());
        toolbar.setSubtitle(FileUtils.formatSize(file.length()));
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
                final int width = bounds.outWidth;
                final int height = bounds.outHeight;
                int sample = 1;
                int target = 2400;
                while (width / (sample * 2) >= target && height / (sample * 2) >= target) sample *= 2;
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = sample;
                opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                final Bitmap bitmap;
                try {
                    bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                } catch (OutOfMemoryError e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            progress.setVisibility(View.GONE);
                            UiUtils.toast(ImageViewerActivity.this, "图片过大，无法加载");
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progress.setVisibility(View.GONE);
                        if (bitmap == null) {
                            UiUtils.toast(ImageViewerActivity.this, "无法解码此图片");
                            return;
                        }
                        image.setImageBitmap(bitmap);
                        meta.setText(width + " × " + height + " · " + FileUtils.formatSize(file.length()));
                        toolbar.setSubtitle(width + " × " + height + " · " + FileUtils.formatSize(file.length()));
                    }
                });
            }
        });
    }
}
