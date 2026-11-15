package com.zrinfile.app.ui;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.R;
import com.zrinfile.app.fs.FileItem;
import com.zrinfile.app.fs.FileUtils;
import com.zrinfile.app.util.Prefs;
import com.zrinfile.app.util.UiUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onClick(FileItem item);

        void onLongClick(FileItem item);
    }

    private final Context context;
    private final List<FileItem> items = new ArrayList<>();
    private final Set<String> selected = new HashSet<>();
    private final Listener listener;
    private boolean grid;
    private boolean selectionMode;
    private boolean animate = true;
    private int lastAnimated = -1;

    public FileListAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.grid = Prefs.grid(context);
    }

    public void setItems(List<FileItem> data, boolean animateIn) {
        items.clear();
        if (data != null) items.addAll(data);
        selected.clear();
        animate = animateIn && Prefs.animations(context);
        lastAnimated = animate ? -1 : Integer.MAX_VALUE;
        notifyDataSetChanged();
    }

    public List<FileItem> getItems() {
        return items;
    }

    public void addItems(List<FileItem> data) {
        if (data == null || data.isEmpty()) return;
        int start = items.size();
        items.addAll(data);
        notifyItemRangeInserted(start, data.size());
    }

    public List<FileItem> getSelectedItems() {
        List<FileItem> out = new ArrayList<>();
        for (FileItem i : items) if (selected.contains(i.path)) out.add(i);
        return out;
    }

    public int getSelectedCount() {
        return selected.size();
    }

    public boolean isSelected(FileItem item) {
        return selected.contains(item.path);
    }

    public void toggle(FileItem item) {
        if (selected.contains(item.path)) selected.remove(item.path);
        else selected.add(item.path);
        notifyItemChanged(indexOf(item));
    }

    public void selectAll() {
        for (FileItem i : items) selected.add(i.path);
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selected.clear();
        notifyDataSetChanged();
    }

    public void setSelectionMode(boolean mode) {
        selectionMode = mode;
        if (!mode) selected.clear();
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setGrid(boolean g) {
        if (grid == g) return;
        grid = g;
        notifyDataSetChanged();
    }

    public boolean isGrid() {
        return grid;
    }

    private int indexOf(FileItem item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == item) return i;
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        return grid ? 1 : 0;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == 1) {
            View v = LayoutInflater.from(context).inflate(R.layout.item_file_grid, parent, false);
            return new GridHolder(v);
        }
        View v = LayoutInflater.from(context).inflate(R.layout.item_file, parent, false);
        return new ListHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        final FileItem item = items.get(position);
        if (holder instanceof ListHolder) bindList((ListHolder) holder, item);
        else bindGrid((GridHolder) holder, item);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (listener != null) listener.onClick(item);
            }
        });
        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (listener != null) listener.onLongClick(item);
                return true;
            }
        });
        if (animate && position > lastAnimated) {
            lastAnimated = position;
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(UiUtils.dp(context, 7));
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(position, 8) * 16L)
                    .setDuration(170)
                    .start();
        } else {
            holder.itemView.setAlpha(1f);
            holder.itemView.setTranslationY(0f);
        }
    }

    private void bindList(ListHolder h, FileItem item) {
        int type = item.type();
        int color = UiUtils.typeColor(context, type);
        h.icon.setTag(null);
        h.icon.setImageDrawable(UiUtils.tint(context, UiUtils.iconFor(type), color));
        h.name.setText(item.name);
        StringBuilder sub = new StringBuilder();
        sub.append(item.typeLabel());
        if (Prefs.showDate(context) && item.modified > 0) {
            sub.append(" · ").append(FileUtils.formatDate(item.modified));
        }
        h.sub.setText(sub.toString());
        if (Prefs.showSize(context) && !item.isDir && item.size > 0) {
            h.size.setText(FileUtils.formatSize(item.size));
            h.size.setVisibility(View.VISIBLE);
        } else {
            h.size.setVisibility(View.GONE);
        }
        h.chevron.setVisibility(item.isDir ? View.VISIBLE : View.GONE);
        boolean sel = selected.contains(item.path);
        h.root.setBackgroundResource(sel ? R.color.selection : R.drawable.bg_item_selectable);
        h.check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        h.check.setChecked(sel);
        h.check.setClickable(false);
        if (selectionMode) {
            h.name.setTextColor(UiUtils.color(context, sel ? R.color.accent : R.color.text_primary));
        } else {
            h.name.setTextColor(UiUtils.color(context, R.color.text_primary));
        }
    }

    private void bindGrid(GridHolder h, FileItem item) {
        int type = item.type();
        int color = UiUtils.typeColor(context, type);
        if (type == FileItem.TYPE_IMAGE) {
            ThumbLoader.load(item.file(), h.icon, UiUtils.dp(context, 34), R.drawable.ic_image, color);
        } else {
            h.icon.setTag(null);
            h.icon.setImageDrawable(UiUtils.tint(context, UiUtils.iconFor(type), color));
        }
        h.name.setText(item.name);
        if (item.isDir) {
            h.sub.setText("文件夹");
        } else {
            h.sub.setText(FileUtils.formatSize(item.size));
        }
        boolean sel = selected.contains(item.path);
        h.root.setBackgroundResource(sel ? R.color.selection : R.drawable.bg_item_selectable);
        h.check.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        h.check.setChecked(sel);
        h.check.setClickable(false);
        h.name.setTextColor(UiUtils.color(context, sel ? R.color.accent : R.color.text_primary));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ListHolder extends RecyclerView.ViewHolder {
        final View root;
        final CheckBox check;
        final ImageView icon;
        final TextView name;
        final TextView sub;
        final TextView size;
        final ImageView chevron;

        ListHolder(View v) {
            super(v);
            root = v;
            check = v.findViewById(R.id.item_check);
            icon = v.findViewById(R.id.item_icon);
            name = v.findViewById(R.id.item_name);
            sub = v.findViewById(R.id.item_sub);
            size = v.findViewById(R.id.item_size);
            chevron = v.findViewById(R.id.item_chevron);
        }
    }

    static class GridHolder extends RecyclerView.ViewHolder {
        final View root;
        final CheckBox check;
        final ImageView icon;
        final TextView name;
        final TextView sub;

        GridHolder(View v) {
            super(v);
            root = v;
            check = v.findViewById(R.id.grid_check);
            icon = v.findViewById(R.id.grid_icon);
            name = v.findViewById(R.id.grid_name);
            sub = v.findViewById(R.id.grid_sub);
        }
    }
}
