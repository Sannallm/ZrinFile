package com.zrinfile.app.ui;

import android.app.Activity;
import android.content.Intent;

import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

/** 统一的内置预览分发。 */
public final class OpenHelper {

    private OpenHelper() {
    }

    public static void open(Activity a, File f) {
        if (f == null || !f.exists()) {
            UiUtils.toast(a, "文件不存在");
            return;
        }
        if (f.isDirectory()) {
            Intent i = new Intent(a, MainActivity.class);
            i.putExtra("browse", f.getAbsolutePath());
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            a.startActivity(i);
            return;
        }
        FileItem item = FileItem.fromFile(f);
        Intent i;
        switch (item.type()) {
            case FileItem.TYPE_IMAGE:
                i = new Intent(a, ImageViewerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                a.startActivity(i);
                break;
            case FileItem.TYPE_VIDEO:
            case FileItem.TYPE_AUDIO:
                i = new Intent(a, MediaPlayerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                a.startActivity(i);
                break;
            case FileItem.TYPE_ARCHIVE:
                i = new Intent(a, ZipViewerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                a.startActivity(i);
                break;
            case FileItem.TYPE_APK:
                i = new Intent(a, ApkInfoActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                a.startActivity(i);
                break;
            case FileItem.TYPE_TEXT:
                i = new Intent(a, TextViewerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                a.startActivity(i);
                break;
            default:
                UiUtils.openExternally(a, f, item.mime());
        }
    }
}
