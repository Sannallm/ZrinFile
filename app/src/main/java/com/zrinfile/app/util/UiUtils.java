package com.zrinfile.app.util;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.drawable.DrawableCompat;

import com.zrinfile.app.BuildConfig;
import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;

import java.io.File;

public final class UiUtils {

    private UiUtils() {
    }

    public interface InputCallback {
        void onInput(String text);
    }

    public static int dp(Context c, float v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    public static int color(Context c, int res) {
        return ContextCompat.getColor(c, res);
    }

    public static void toast(Context c, String msg) {
        if (c == null || TextUtils.isEmpty(msg)) return;
        Toast.makeText(c, msg, Toast.LENGTH_SHORT).show();
    }

    public static void toastLong(Context c, String msg) {
        if (c == null || TextUtils.isEmpty(msg)) return;
        Toast.makeText(c, msg, Toast.LENGTH_LONG).show();
    }

    public static Drawable tint(Context c, int res, int color) {
        Drawable d = ContextCompat.getDrawable(c, res);
        if (d == null) return null;
        d = DrawableCompat.wrap(d.mutate());
        DrawableCompat.setTint(d, color);
        return d;
    }

    public static void tintImage(ImageView v, int color) {
        if (v.getDrawable() != null) {
            Drawable d = DrawableCompat.wrap(v.getDrawable().mutate());
            DrawableCompat.setTint(d, color);
            v.setImageDrawable(d);
        }
    }

    public static int typeColor(Context c, int type) {
        switch (type) {
            case FileItem.TYPE_FOLDER: return color(c, R.color.folder);
            case FileItem.TYPE_IMAGE: return color(c, R.color.type_image);
            case FileItem.TYPE_VIDEO: return color(c, R.color.type_video);
            case FileItem.TYPE_AUDIO: return color(c, R.color.type_audio);
            case FileItem.TYPE_ARCHIVE: return color(c, R.color.type_archive);
            case FileItem.TYPE_APK: return color(c, R.color.type_apk);
            case FileItem.TYPE_TEXT: return color(c, R.color.type_text);
            case FileItem.TYPE_PDF: return color(c, R.color.type_pdf);
            default: return color(c, R.color.type_unknown);
        }
    }

    public static int iconFor(int type) {
        switch (type) {
            case FileItem.TYPE_FOLDER: return R.drawable.ic_folder;
            case FileItem.TYPE_IMAGE: return R.drawable.ic_image;
            case FileItem.TYPE_VIDEO: return R.drawable.ic_video;
            case FileItem.TYPE_AUDIO: return R.drawable.ic_audio;
            case FileItem.TYPE_ARCHIVE: return R.drawable.ic_archive;
            case FileItem.TYPE_APK: return R.drawable.ic_apk;
            case FileItem.TYPE_TEXT: return R.drawable.ic_text;
            case FileItem.TYPE_PDF: return R.drawable.ic_pdf;
            default: return R.drawable.ic_file;
        }
    }

    public static void showInput(Activity a, String title, String label, String initial,
                                 String hint, final InputCallback callback) {
        View view = LayoutInflater.from(a).inflate(R.layout.dialog_input, null);
        final EditText edit = view.findViewById(R.id.input_edit);
        TextView labelView = view.findViewById(R.id.input_label);
        TextView hintView = view.findViewById(R.id.input_hint);
        if (TextUtils.isEmpty(label)) {
            labelView.setVisibility(View.GONE);
        } else {
            labelView.setText(label);
        }
        if (!TextUtils.isEmpty(initial)) {
            edit.setText(initial);
            edit.setSelection(initial.length());
        }
        if (!TextUtils.isEmpty(hint)) {
            hintView.setText(hint);
            hintView.setVisibility(View.VISIBLE);
        }
        AlertDialog d = new AlertDialog.Builder(a, R.style.Theme_ZrinFile_Dialog)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.action_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String text = edit.getText().toString().trim();
                        if (text.length() > 0 && callback != null) callback.onInput(text);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        d.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                edit.requestFocus();
                edit.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        InputMethodManager imm = (InputMethodManager) edit.getContext()
                                .getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
                    }
                }, 120);
            }
        });
        d.show();
    }

    public static void confirm(Context c, String title, String message, final Runnable ok) {
        new AlertDialog.Builder(c, R.style.Theme_ZrinFile_Dialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (ok != null) ok.run();
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    public static void alert(Context c, String title, String message) {
        new AlertDialog.Builder(c, R.style.Theme_ZrinFile_Dialog)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }

    public static void hideKeyboard(Activity a) {
        View v = a.getCurrentFocus();
        if (v != null) {
            InputMethodManager imm = (InputMethodManager) a.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        }
    }

    public static Uri uriFor(Context c, File f) {
        return FileProvider.getUriForFile(c, BuildConfig.APPLICATION_ID + ".fileprovider", f);
    }

    public static void openExternally(Context c, File f, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uriFor(c, f), mime);
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(Intent.createChooser(i, "打开方式"));
        } catch (Exception e) {
            toast(c, "没有找到可打开此文件的应用");
        }
    }

    public static void installApk(Context c, File f) {
        if (Build.VERSION.SDK_INT >= 26) {
            if (!c.getPackageManager().canRequestPackageInstalls()) {
                toastLong(c, "请允许本应用安装未知来源的应用后重试");
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES);
                    i.setData(Uri.parse("package:" + c.getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    c.startActivity(i);
                } catch (Exception ignored) {
                }
                return;
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            Uri uri;
            if (Build.VERSION.SDK_INT >= 24) {
                uri = uriFor(c, f);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                // Android 5/6 的安装器只接受 file:// 且需要能直接读取文件，
                // 因此把安装包复制到公共下载目录。
                uri = Uri.fromFile(copyForLegacyInstaller(f));
            }
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            c.startActivity(i);
        } catch (Exception e) {
            toast(c, "无法启动安装程序");
        }
    }

    private static File copyForLegacyInstaller(File src) {
        try {
            File dir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dir == null) return src;
            if (!dir.exists() && !dir.mkdirs()) return src;
            File[] old = dir.listFiles();
            if (old != null) {
                for (File o : old) {
                    if (o.getName().startsWith("ZrinFile-update-")) o.delete();
                }
            }
            File dst = new File(dir, "ZrinFile-update-" + src.getName());
            java.io.InputStream in = new java.io.FileInputStream(src);
            java.io.OutputStream out = new java.io.FileOutputStream(dst);
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            in.close();
            out.close();
            return dst;
        } catch (Exception e) {
            return src;
        }
    }

    public static void shareFile(Context c, File f, String mime) {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType(mime);
            i.putExtra(Intent.EXTRA_STREAM, uriFor(c, f));
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            c.startActivity(Intent.createChooser(i, "分享"));
        } catch (Exception e) {
            toast(c, "分享失败");
        }
    }

    public static class ProgressHandle {
        public AlertDialog dialog;
        public TextView text;
        public boolean cancelled;
    }

    public static ProgressHandle progress(Activity a, String title) {
        View view = LayoutInflater.from(a).inflate(R.layout.dialog_progress, null);
        final ProgressHandle h = new ProgressHandle();
        h.text = view.findViewById(R.id.progress_text);
        h.text.setText("");
        h.dialog = new AlertDialog.Builder(a, R.style.Theme_ZrinFile_Dialog)
                .setTitle(title)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        h.cancelled = true;
                    }
                })
                .setCancelable(false)
                .create();
        try {
            h.dialog.show();
        } catch (Exception ignored) {
        }
        return h;
    }

    public static void dismiss(ProgressHandle h) {
        if (h == null) return;
        try {
            if (h.dialog != null && h.dialog.isShowing()) h.dialog.dismiss();
        } catch (Exception ignored) {
        }
    }

    public static void setMargins(View v, int l, int t, int r, int b) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.setMargins(l, t, r, b);
            v.setLayoutParams(p);
        }
    }
}
