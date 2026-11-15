package com.zrinfile.app.fs;

import android.text.TextUtils;

import com.zrinfile.app.util.Prefs;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class FileUtils {

    public static final int CONFLICT_OVERWRITE = 0;
    public static final int CONFLICT_RENAME = 1;
    public static final int CONFLICT_SKIP = 2;

    private FileUtils() {
    }

    public interface Cancel {
        boolean isCancelled();
    }

    public interface Progress {
        void onProgress(String current, long done, long total);
    }

    public interface Conflict {
        int resolve(File target);
    }

    public interface FileVisitor {
        void onFile(FileItem item);
    }

    public static final Cancel NEVER = new Cancel() {
        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    public static List<FileItem> list(File dir, boolean showHidden) {
        List<FileItem> out = new ArrayList<>();
        File[] files = null;
        try {
            files = dir.listFiles();
        } catch (Exception ignored) {
        }
        if (files != null) {
            for (File f : files) {
                if (!showHidden && f.getName().startsWith(".")) continue;
                FileItem item = FileItem.fromFile(f);
                item.rootOnly = false;
                out.add(item);
            }
            if (out.isEmpty() && dir.getAbsolutePath().startsWith("/") && !dir.canRead()) {
                out.addAll(RootShell.list(dir.getAbsolutePath()));
            }
            return out;
        }
        if (RootShell.available()) {
            out.addAll(RootShell.list(dir.getAbsolutePath()));
            if (!showHidden) {
                List<FileItem> filtered = new ArrayList<>();
                for (FileItem i : out) if (!i.name.startsWith(".")) filtered.add(i);
                return filtered;
            }
        }
        return out;
    }

    public static void sort(List<FileItem> list, int sortBy, final boolean asc, final boolean foldersFirst) {
        final SimpleDateFormat ignored = null;
        Collections.sort(list, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                if (foldersFirst && a.isDir != b.isDir) return a.isDir ? -1 : 1;
                int r;
                switch (sortBy) {
                    case Prefs.SORT_SIZE:
                        r = Long.compare(a.size, b.size);
                        break;
                    case Prefs.SORT_DATE:
                        r = Long.compare(a.modified, b.modified);
                        break;
                    case Prefs.SORT_TYPE:
                        r = Integer.compare(a.type(), b.type());
                        if (r == 0) r = a.name.compareToIgnoreCase(b.name);
                        break;
                    default:
                        r = a.name.compareToIgnoreCase(b.name);
                }
                return asc ? r : -r;
            }
        });
    }

    public static String formatSize(long bytes) {
        if (bytes < 0) return "-";
        if (bytes < 1024) return bytes + " B";
        double v = bytes;
        String[] units = {"KB", "MB", "GB", "TB", "PB"};
        int i = -1;
        while (v >= 1024 && i < units.length - 1) {
            v /= 1024;
            i++;
        }
        if (v >= 100) return String.format(Locale.US, "%.0f %s", v, units[i]);
        if (v >= 10) return String.format(Locale.US, "%.1f %s", v, units[i]);
        return String.format(Locale.US, "%.2f %s", v, units[i]);
    }

    public static String formatDate(long time) {
        if (time <= 0) return "—";
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date(time));
    }

    public static String formatTimeShort(long ms) {
        if (ms < 0) ms = 0;
        long total = ms / 1000;
        long h = total / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        if (h > 0) return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        return String.format(Locale.US, "%02d:%02d", m, s);
    }

    public static String uniqueName(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return name;
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 9999; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) return candidate;
        }
        return base + "_" + System.currentTimeMillis() + ext;
    }

    public static File uniqueFile(File dir, String name) {
        return new File(dir, uniqueName(dir, name));
    }

    public static long sizeOf(File f) {
        if (f == null ) return 0;
        if (f.isFile()) return f.length();
        if (!f.isDirectory()) return 0;
        long total = 0;
        File[] files = null;
        try {
            files = f.listFiles();
        } catch (Exception ignored) {
        }
        if (files == null) return 0;
        for (File c : files) {
            if (c.isDirectory()) total += sizeOf(c);
            else total += c.length();
        }
        return total;
    }

    public static int countFiles(File f) {
        if (f == null || !f.isDirectory()) return f != null && f.isFile() ? 1 : 0;
        int n = 0;
        File[] files;
        try {
            files = f.listFiles();
        } catch (Exception e) {
            return 0;
        }
        if (files == null) return 0;
        for (File c : files) n += c.isDirectory() ? countFiles(c) : 1;
        return n;
    }

    public static boolean deleteRecursive(File f) {
        if (f == null || !f.exists()) return true;
        if (f.isDirectory()) {
            File[] files = null;
            try {
                files = f.listFiles();
            } catch (Exception ignored) {
            }
            if (files != null) {
                for (File c : files) {
                    if (!deleteRecursive(c)) return false;
                }
            }
        }
        return f.delete();
    }

    public static boolean copyFile(File src, File dst) {
        InputStream in = null;
        OutputStream out = null;
        try {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            in = new BufferedInputStream(new FileInputStream(src), 65536);
            out = new BufferedOutputStream(new FileOutputStream(dst), 65536);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    public static boolean copyRecursive(File src, File dst, Cancel cancel, Progress progress,
                                        Conflict conflict, long[] counter) {
        if (cancel.isCancelled()) return false;
        if (src.isDirectory()) {
            if (!dst.exists() && !dst.mkdirs()) return false;
            File[] files = null;
            try {
                files = src.listFiles();
            } catch (Exception ignored) {
            }
            if (files == null) return false;
            for (File c : files) {
                if (cancel.isCancelled()) return false;
                File target = new File(dst, c.getName());
                if (target.exists()) {
                    int r = conflict.resolve(target);
                    if (r == CONFLICT_SKIP) continue;
                    if (r == CONFLICT_RENAME) target = uniqueFile(dst, c.getName());
                }
                if (!copyRecursive(c, target, cancel, progress, conflict, counter)) return false;
            }
            dst.setLastModified(src.lastModified());
            return true;
        } else {
            if (dst.exists()) {
                int r = conflict.resolve(dst);
                if (r == CONFLICT_SKIP) return true;
                if (r == CONFLICT_RENAME) dst = uniqueFile(dst.getParentFile(), dst.getName());
            }
            boolean ok = copyFile(src, dst);
            if (ok) {
                dst.setLastModified(src.lastModified());
                counter[0] += src.length();
                if (progress != null) progress.onProgress(src.getName(), counter[0], counter[1]);
            }
            return ok;
        }
    }

    public static boolean moveRecursive(File src, File dst, Cancel cancel, Progress progress,
                                        Conflict conflict, long[] counter) {
        if (cancel.isCancelled()) return false;
        if (dst.exists()) {
            int r = conflict.resolve(dst);
            if (r == CONFLICT_SKIP) return true;
            if (r == CONFLICT_RENAME) dst = uniqueFile(dst.getParentFile(), dst.getName());
            else deleteRecursive(dst);
        }
        try {
            File parent = dst.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            if (src.renameTo(dst)) {
                counter[0] += sizeOf(src);
                if (progress != null) progress.onProgress(src.getName(), counter[0], counter[1]);
                return true;
            }
        } catch (Exception ignored) {
        }
        boolean ok = copyRecursive(src, dst, cancel, progress, conflict, counter);
        if (ok) deleteRecursive(src);
        return ok;
    }

    public static long[] totalSize(List<FileItem> items) {
        long total = 0;
        for (FileItem i : items) total += i.isDir ? sizeOf(i.file()) : i.size;
        return new long[]{total};
    }

    public static boolean zip(List<FileItem> items, File zipFile, Cancel cancel, Progress progress) {
        ZipOutputStream zos = null;
        try {
            long[] total = totalSize(items);
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile), 65536));
            zos.setLevel(6);
            long[] done = new long[]{0};
            for (FileItem item : items) {
                if (!addZipEntry(zos, item.file(), item.name, cancel, progress, done, total[0])) {
                    closeQuietly(zos);
                    zipFile.delete();
                    return false;
                }
            }
            zos.finish();
            return true;
        } catch (Exception e) {
            zipFile.delete();
            return false;
        } finally {
            closeQuietly(zos);
        }
    }

    private static boolean addZipEntry(ZipOutputStream zos, File f, String entryName, Cancel cancel,
                                       Progress progress, long[] done, long total) throws IOException {
        if (cancel.isCancelled()) return false;
        if (f.isDirectory()) {
            File[] children = f.listFiles();
            if (children == null) return true;
            if (children.length == 0) {
                zos.putNextEntry(new ZipEntry(entryName + "/"));
                zos.closeEntry();
                return true;
            }
            for (File c : children) {
                if (!addZipEntry(zos, c, entryName + "/" + c.getName(), cancel, progress, done, total)) return false;
            }
            return true;
        }
        zos.putNextEntry(new ZipEntry(entryName));
        InputStream in = null;
        try {
            in = new BufferedInputStream(new FileInputStream(f), 65536);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancel.isCancelled()) return false;
                zos.write(buf, 0, n);
                done[0] += n;
                if (progress != null) progress.onProgress(f.getName(), done[0], total);
            }
        } finally {
            closeQuietly(in);
            zos.closeEntry();
        }
        return true;
    }

    public static int unzip(File zipFile, File destDir, Cancel cancel, Progress progress) {
        int count = 0;
        ZipFile zf = null;
        try {
            if (!destDir.exists()) destDir.mkdirs();
            zf = new ZipFile(zipFile);
            String base = destDir.getCanonicalPath();
            long total = zipFile.length();
            long[] done = new long[]{0};
            java.util.Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                if (cancel.isCancelled()) return count;
                ZipEntry entry = en.nextElement();
                File out = new File(destDir, entry.getName());
                String canonical = out.getCanonicalPath();
                if (!canonical.equals(base) && !canonical.startsWith(base + File.separator)) {
                    continue;
                }
                if (entry.isDirectory()) {
                    out.mkdirs();
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                InputStream in = null;
                OutputStream os = null;
                try {
                    in = new BufferedInputStream(zf.getInputStream(entry), 65536);
                    os = new BufferedOutputStream(new FileOutputStream(out), 65536);
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        done[0] += n;
                        if (progress != null) progress.onProgress(entry.getName(), done[0], total);
                    }
                    os.flush();
                } finally {
                    closeQuietly(in);
                    closeQuietly(os);
                }
                count++;
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (zf != null) zf.close();
            } catch (Exception ignored) {
            }
        }
        return count;
    }

    public static List<String> zipEntries(File zipFile) {
        List<String> out = new ArrayList<>();
        ZipFile zf = null;
        try {
            zf = new ZipFile(zipFile);
            java.util.Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry e = en.nextElement();
                if (!e.isDirectory()) out.add(e.getName());
            }
        } catch (Exception ignored) {
        } finally {
            try {
                if (zf != null) zf.close();
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static TextResult readText(File f, int maxBytes) {
        TextResult result = new TextResult();
        result.fileSize = f.length();
        RandomAccessFile raf = null;
        try {
            raf = new RandomAccessFile(f, "r");
            int len = (int) Math.min(maxBytes, raf.length());
            byte[] data = new byte[len];
            raf.readFully(data);
            result.truncated = raf.length() > len;

            String enc = "UTF-8";
            int offset = 0;
            if (data.length >= 3 && (data[0] & 0xFF) == 0xEF && (data[1] & 0xFF) == 0xBB && (data[2] & 0xFF) == 0xBF) {
                offset = 3;
            } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xFE) {
                enc = "UTF-16LE";
                offset = 2;
            } else if (data.length >= 2 && (data[0] & 0xFF) == 0xFE && (data[1] & 0xFF) == 0xFF) {
                enc = "UTF-16BE";
                offset = 2;
            }
            String text = null;
            if ("UTF-8".equals(enc)) {
                try {
                    CharsetDecoder dec = Charset.forName("UTF-8").newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);
                    text = dec.decode(ByteBuffer.wrap(data, offset, data.length - offset)).toString();
                } catch (CharacterCodingException e) {
                    try {
                        text = new String(data, offset, data.length - offset, Charset.forName("GBK"));
                        enc = "GBK";
                    } catch (Exception e2) {
                        text = new String(data, offset, data.length - offset, Charset.forName("ISO-8859-1"));
                        enc = "ISO-8859-1";
                    }
                }
            } else {
                text = new String(data, offset, data.length - offset, Charset.forName(enc));
            }
            if (text == null) text = new String(data, offset, data.length - offset);
            result.text = text;
            result.encoding = enc;
        } catch (Exception e) {
            result.text = "";
            result.encoding = "UTF-8";
            result.error = e.getMessage();
        } finally {
            try {
                if (raf != null) raf.close();
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    public static class TextResult {
        public String text;
        public String encoding;
        public boolean truncated;
        public long fileSize;
        public String error;
    }

    public static String checksum(File f, String algorithm) {
        InputStream in = null;
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            in = new BufferedInputStream(new FileInputStream(f), 65536);
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format(Locale.US, "%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            closeQuietly(in);
        }
    }

    public static void search(File dir, String query, boolean showHidden, Cancel cancel,
                              FileVisitor visitor, int maxResults) {
        if (cancel.isCancelled() || visitor == null) return;
        File[] files = null;
        try {
            files = dir.listFiles();
        } catch (Exception ignored) {
        }
        if (files == null) return;
        String q = query.toLowerCase(Locale.US);
        for (File f : files) {
            if (cancel.isCancelled()) return;
            if (!showHidden && f.getName().startsWith(".")) continue;
            if (f.getName().toLowerCase(Locale.US).contains(q)) {
                visitor.onFile(FileItem.fromFile(f));
                maxResults--;
                if (maxResults <= 0) return;
            }
            if (f.isDirectory()) {
                search(f, query, showHidden, cancel, visitor, maxResults);
                if (maxResults <= 0) return;
            }
        }
    }

    public static String readPermission(File f) {
        return (f.canRead() ? "r" : "-") + (f.canWrite() ? "w" : "-") + (f.canExecute() ? "x" : "-");
    }

    public static String parentPath(String path) {
        if (TextUtils.isEmpty(path)) return "/";
        File f = new File(path);
        String p = f.getParent();
        if (p == null) return "/";
        return p;
    }

    public static String join(String dir, String name) {
        if (dir.endsWith("/")) return dir + name;
        return dir + "/" + name;
    }

    public static long availableSpace(String path) {
        try {
            android.os.StatFs stat = new android.os.StatFs(path);
            return stat.getAvailableBlocksLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            return 0;
        }
    }

    public static long totalSpace(String path) {
        try {
            android.os.StatFs stat = new android.os.StatFs(path);
            return stat.getBlockCountLong() * stat.getBlockSizeLong();
        } catch (Exception e) {
            return 0;
        }
    }

    public static void closeQuietly(java.io.Closeable c) {
        try {
            if (c != null) c.close();
        } catch (Exception ignored) {
        }
    }
}
