package com.zrinfile.app.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.zrinfile.app.App;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

/** 轻量缩略图加载，限制内存与并发，避免拖慢老设备。 */
public final class ThumbLoader {

    private static final LruCache<String, Bitmap> CACHE;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    static {
        int max = (int) Math.min(Runtime.getRuntime().maxMemory() / 8, 16 * 1024 * 1024);
        CACHE = new LruCache<String, Bitmap>(max) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
    }

    private ThumbLoader() {
    }

    public static void clear() {
        CACHE.evictAll();
    }

    public static void load(final File file, final ImageView view, final int sizePx, final int fallbackRes,
                            final int fallbackColor) {
        final String key = file.getAbsolutePath() + "@" + sizePx;
        Bitmap cached = CACHE.get(key);
        view.setTag(key);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(UiUtils.tint(view.getContext(), fallbackRes, fallbackColor));
        App.io().execute(new Runnable() {
            @Override
            public void run() {
                final Bitmap bmp = decode(file, sizePx);
                if (bmp == null) return;
                CACHE.put(key, bmp);
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        if (key.equals(view.getTag())) view.setImageBitmap(bmp);
                    }
                });
            }
        });
    }

    private static Bitmap decode(File file, int sizePx) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            int target = Math.max(1, sizePx) * 2;
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Throwable e) {
            return null;
        }
    }
}
