package com.zrinfile.app.fs;

import android.graphics.Color;

import com.zrinfile.app.util.Prefs;

import java.io.File;
import java.util.Locale;

/** 文件或目录的轻量模型。 */
public class FileItem {

    public String name;
    public String path;
    public boolean isDir;
    public long size;
    public long modified;
    public boolean hidden;
    public boolean symlink;
    public boolean rootOnly;

    private String ext;

    public FileItem() {
    }

    public static FileItem fromFile(File f) {
        FileItem item = new FileItem();
        item.name = f.getName();
        if (item.name.length() == 0) item.name = f.getAbsolutePath();
        item.path = f.getAbsolutePath();
        item.isDir = f.isDirectory();
        item.size = item.isDir ? 0 : f.length();
        item.modified = f.lastModified();
        item.hidden = item.name.startsWith(".");
        item.symlink = false;
        return item;
    }

    public static FileItem of(String path, String name, boolean dir, long size, long modified) {
        FileItem item = new FileItem();
        item.path = path;
        item.name = name;
        item.isDir = dir;
        item.size = size;
        item.modified = modified;
        item.hidden = name.startsWith(".");
        return item;
    }

    public File file() {
        return new File(path);
    }

    public String extension() {
        if (ext != null) return ext;
        int dot = name.lastIndexOf('.');
        if (dot > 0 && dot < name.length() - 1 && !isDir) {
            ext = name.substring(dot + 1).toLowerCase(Locale.US);
        } else {
            ext = "";
        }
        return ext;
    }

    public static final int TYPE_FOLDER = 0;
    public static final int TYPE_IMAGE = 1;
    public static final int TYPE_VIDEO = 2;
    public static final int TYPE_AUDIO = 3;
    public static final int TYPE_ARCHIVE = 4;
    public static final int TYPE_APK = 5;
    public static final int TYPE_TEXT = 6;
    public static final int TYPE_PDF = 7;
    public static final int TYPE_OTHER = 8;

    public int type() {
        if (isDir) return TYPE_FOLDER;
        String e = extension();
        switch (e) {
            case "jpg": case "jpeg": case "png": case "gif": case "webp": case "bmp":
            case "heic": case "heif": case "svg": case "ico": case "tiff":
                return TYPE_IMAGE;
            case "mp4": case "mkv": case "mov": case "avi": case "3gp": case "webm":
            case "flv": case "ts": case "m4v": case "wmv": case "rmvb":
                return TYPE_VIDEO;
            case "mp3": case "flac": case "wav": case "aac": case "ogg": case "opus":
            case "m4a": case "wma": case "amr": case "ape":
                return TYPE_AUDIO;
            case "zip": case "rar": case "7z": case "tar": case "gz": case "bz2":
            case "xz": case "tgz": case "jar": case "apks":
                return TYPE_ARCHIVE;
            case "apk": case "xapk":
                return TYPE_APK;
            case "txt": case "log": case "md": case "json": case "xml": case "java":
            case "kt": case "js": case "css": case "html": case "htm": case "py":
            case "c": case "cpp": case "h": case "sh": case "yml": case "yaml":
            case "ini": case "conf": case "prop": case "gradle": case "sql": case "csv":
                return TYPE_TEXT;
            case "pdf":
                return TYPE_PDF;
            default:
                return TYPE_OTHER;
        }
    }

    public static int typeColor(int type) {
        switch (type) {
            case TYPE_FOLDER: return 0xFFD6A24C;
            case TYPE_IMAGE: return 0xFF79A883;
            case TYPE_VIDEO: return 0xFFD08A5C;
            case TYPE_AUDIO: return 0xFFA98BC4;
            case TYPE_ARCHIVE: return 0xFFC2A45D;
            case TYPE_APK: return 0xFF68A99A;
            case TYPE_TEXT: return 0xFF7FA2C8;
            case TYPE_PDF: return 0xFFCE746D;
            default: return 0xFF9A9FA7;
        }
    }

    public String typeLabel() {
        if (isDir) return "文件夹";
        String e = extension();
        if (e.length() == 0) return "文件";
        return e.toUpperCase(Locale.US) + " 文件";
    }

    public String mime() {
        int t = type();
        switch (t) {
            case TYPE_IMAGE: return "image/*";
            case TYPE_VIDEO: return "video/*";
            case TYPE_AUDIO: return "audio/*";
            case TYPE_ARCHIVE: return "application/zip";
            case TYPE_APK: return "application/vnd.android.package-archive";
            case TYPE_TEXT: return "text/plain";
            case TYPE_PDF: return "application/pdf";
            default: return "application/octet-stream";
        }
    }

    @Override
    public String toString() {
        return name;
    }
}
