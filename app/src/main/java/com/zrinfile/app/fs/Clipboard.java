package com.zrinfile.app.fs;

import java.util.ArrayList;
import java.util.List;

public final class Clipboard {

    public static final List<FileItem> ITEMS = new ArrayList<>();
    public static boolean cut = false;

    private Clipboard() {
    }

    public static void set(List<FileItem> items, boolean isCut) {
        ITEMS.clear();
        if (items != null) ITEMS.addAll(items);
        cut = isCut;
    }

    public static boolean isEmpty() {
        return ITEMS.isEmpty();
    }

    public static void clear() {
        ITEMS.clear();
        cut = false;
    }
}
