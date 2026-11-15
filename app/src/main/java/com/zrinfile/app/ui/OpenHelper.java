package com.zrinfile.app.ui;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;

import androidx.appcompat.app.AlertDialog;

import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.util.UiUtils;

import java.io.File;

/** 统一的内置预览分发。 */
public final class OpenHelper {

    private OpenHelper() {
    }

    public static void open(final Activity a, final File f) {
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
        final String ext = item.extension();
        if ("html".equals(ext) || "htm".equals(ext)) {
            chooseHtml(a, f);
            return;
        }
        Intent i;
        switch (item.type()) {
            case FileItem.TYPE_IMAGE:
                if ("svg".equals(ext)) {
                    i = new Intent(a, HtmlViewerActivity.class);
                    i.putExtra("path", f.getAbsolutePath());
                    a.startActivity(i);
                } else {
                    i = new Intent(a, ImageViewerActivity.class);
                    i.putExtra("path", f.getAbsolutePath());
                    a.startActivity(i);
                }
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
            case FileItem.TYPE_PDF:
                i = new Intent(a, PdfViewerActivity.class);
                i.putExtra("path", f.getAbsolutePath());
                a.startActivity(i);
                break;
            default:
                UiUtils.openExternally(a, f, item.mime());
        }
    }

    private static void chooseHtml(final Activity a, final File f) {
        new AlertDialog.Builder(a, R.style.Theme_ZrinFile_Dialog)
                .setTitle(f.getName())
                .setItems(new String[]{"预览", "编辑源码"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent i = new Intent(a, which == 0 ? HtmlViewerActivity.class : HtmlEditorActivity.class);
                        i.putExtra("path", f.getAbsolutePath());
                        a.startActivity(i);
                    }
                })
                .show();
    }
}
