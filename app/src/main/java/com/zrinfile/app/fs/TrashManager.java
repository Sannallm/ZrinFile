package com.zrinfile.app.fs;

import android.content.Context;

import com.zrinfile.app.App;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class TrashManager {

    private static final String INDEX = "trash_index.json";
    private static int counter = 0;

    private TrashManager() {
    }

    public static class Entry {
        public String id;
        public String name;
        public String originalPath;
        public String trashPath;
        public boolean dir;
        public long size;
        public long time;

        public File file() {
            return new File(trashPath);
        }
    }

    public static File trashDir(Context c) {
        File f = new File(App.appRoot(), "trash");
        if (!f.exists()) f.mkdirs();
        return f;
    }

    private static File indexFile(Context c) {
        return new File(c.getFilesDir(), INDEX);
    }

    public static synchronized List<Entry> list(Context c) {
        List<Entry> out = new ArrayList<>();
        try {
            File f = indexFile(c);
            if (!f.exists()) return out;
            byte[] data = new byte[(int) f.length()];
            java.io.FileInputStream in = new java.io.FileInputStream(f);
            in.read(data);
            in.close();
            JSONArray arr = new JSONArray(new String(data, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Entry e = new Entry();
                e.id = o.optString("id");
                e.name = o.optString("name");
                e.originalPath = o.optString("original");
                e.trashPath = o.optString("trash");
                e.dir = o.optBoolean("dir");
                e.size = o.optLong("size");
                e.time = o.optLong("time");
                if (new File(e.trashPath).exists()) out.add(e);
            }
        } catch (Exception ignored) {
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Long.compare(b.time, a.time);
            }
        });
        return out;
    }

    private static synchronized void save(Context c, List<Entry> entries) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : entries) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("name", e.name);
                o.put("original", e.originalPath);
                o.put("trash", e.trashPath);
                o.put("dir", e.dir);
                o.put("size", e.size);
                o.put("time", e.time);
                arr.put(o);
            }
            File f = indexFile(c);
            FileOutputStream out = new FileOutputStream(f);
            OutputStreamWriter w = new OutputStreamWriter(out, "UTF-8");
            w.write(arr.toString());
            w.flush();
            w.close();
        } catch (Exception ignored) {
        }
    }

    public static synchronized boolean moveToTrash(Context c, List<FileItem> items) {
        List<Entry> entries = list(c);
        boolean all = true;
        for (FileItem item : items) {
            File src = item.file();
            if (!src.exists() || src.getAbsolutePath().startsWith(trashDir(c).getAbsolutePath())) {
                all = false;
                continue;
            }
            counter++;
            String id = System.currentTimeMillis() + "_" + counter;
            String safeName = item.name.replace('/', '_');
            File dst = new File(trashDir(c), id + "_" + safeName);
            FileItem copy = FileItem.fromFile(src);
            boolean ok;
            if (src.renameTo(dst)) {
                ok = true;
            } else {
                long[] counterArr = new long[]{0, item.isDir ? FileUtils.sizeOf(src) : item.size};
                ok = FileUtils.copyRecursive(src, dst, FileUtils.NEVER, null,
                        new FileUtils.Conflict() {
                            @Override
                            public int resolve(File target) {
                                return FileUtils.CONFLICT_OVERWRITE;
                            }
                        }, counterArr);
                if (ok) FileUtils.deleteRecursive(src);
            }
            if (ok) {
                Entry e = new Entry();
                e.id = id;
                e.name = item.name;
                e.originalPath = item.path;
                e.trashPath = dst.getAbsolutePath();
                e.dir = item.isDir || copy.isDir;
                e.size = item.isDir ? FileUtils.sizeOf(dst) : dst.length();
                e.time = System.currentTimeMillis();
                entries.add(e);
            } else {
                all = false;
            }
        }
        save(c, entries);
        return all;
    }

    public static synchronized boolean restore(Context c, Entry e) {
        List<Entry> entries = list(c);
        File src = e.file();
        File original = new File(e.originalPath);
        File parent = original.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        File dst = original;
        if (dst.exists()) {
            if (parent != null) dst = FileUtils.uniqueFile(parent, original.getName());
            else return false;
        }
        boolean ok;
        if (src.renameTo(dst)) {
            ok = true;
        } else {
            long[] counter = new long[]{0, e.size};
            ok = FileUtils.copyRecursive(src, dst, FileUtils.NEVER, null,
                    new FileUtils.Conflict() {
                        @Override
                        public int resolve(File target) {
                            return FileUtils.CONFLICT_RENAME;
                        }
                    }, counter);
            if (ok) FileUtils.deleteRecursive(src);
        }
        if (ok) {
            for (int i = 0; i < entries.size(); i++) {
                if (entries.get(i).id.equals(e.id)) {
                    entries.remove(i);
                    break;
                }
            }
            save(c, entries);
        }
        return ok;
    }

    public static synchronized boolean deleteForever(Context c, Entry e) {
        boolean ok = FileUtils.deleteRecursive(e.file());
        List<Entry> entries = list(c);
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id.equals(e.id)) {
                entries.remove(i);
                break;
            }
        }
        save(c, entries);
        return ok;
    }

    public static synchronized void empty(Context c) {
        File dir = trashDir(c);
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) FileUtils.deleteRecursive(f);
        }
        save(c, new ArrayList<Entry>());
    }

    public static synchronized void purgeExpired(Context c, int days) {
        if (days <= 0) return;
        long limit = System.currentTimeMillis() - days * 86400000L;
        List<Entry> entries = list(c);
        List<Entry> keep = new ArrayList<>();
        for (Entry e : entries) {
            if (e.time < limit) {
                FileUtils.deleteRecursive(e.file());
            } else {
                keep.add(e);
            }
        }
        if (keep.size() != entries.size()) save(c, keep);
    }

    public static long totalSize(Context c) {
        long total = 0;
        for (Entry e : list(c)) total += e.size;
        return total;
    }
}
