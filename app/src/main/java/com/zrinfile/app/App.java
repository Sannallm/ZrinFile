package com.zrinfile.app;

import android.app.Application;
import android.os.Environment;

import androidx.appcompat.app.AppCompatDelegate;

import com.zrinfile.app.fs.TrashManager;
import com.zrinfile.app.util.Prefs;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class App extends Application {

    private static App instance;
    private static final ExecutorService IO = Executors.newFixedThreadPool(3);

    public static App get() {
        return instance;
    }

    public static ExecutorService io() {
        return IO;
    }

    public static File homeDir() {
        File f = Environment.getExternalStorageDirectory();
        return f != null ? f : new File("/");
    }

    public static File appRoot() {
        File f = instance.getExternalFilesDir(null);
        if (f == null) f = instance.getFilesDir();
        return f;
    }

    public static File thumbCache() {
        File f = new File(instance.getCacheDir(), "thumbs");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        applyTheme();
        com.zrinfile.app.util.TlsCompat.install(this);
        File root = appRoot();
        if (root != null) {
            new File(root, "home").mkdirs();
        }
        IO.execute(new Runnable() {
            @Override
            public void run() {
                TrashManager.purgeExpired(App.this, Prefs.trashDays(App.this));
            }
        });
    }

    public void applyTheme() {
        int mode = Prefs.theme(this);
        int night;
        if (mode == Prefs.THEME_DARK) night = AppCompatDelegate.MODE_NIGHT_YES;
        else if (mode == Prefs.THEME_LIGHT) night = AppCompatDelegate.MODE_NIGHT_NO;
        else night = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(night);
    }
}
