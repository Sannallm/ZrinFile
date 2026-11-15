package com.zrinfile.app.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.zrinfile.app.R;
import com.zrinfile.app.util.UiUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class LicenseActivity extends AppCompatActivity {

    private static class Component {
        String name;
        String desc;
        String tag;
        String asset;

        Component(String name, String desc, String tag, String asset) {
            this.name = name;
            this.desc = desc;
            this.tag = tag;
            this.asset = asset;
        }
    }

    private final List<Component> components = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.title_license);
        toolbar.setSubtitle("MIT · Apache-2.0 · OFL-1.1 · UFL-1.0 · GPL-2.0 · GPL-3.0 · LGPL-3.0");
        toolbar.setNavigationIcon(UiUtils.tint(this, R.drawable.ic_arrow_back, UiUtils.color(this, R.color.icon)));
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        buildList();
        RecyclerView list = findViewById(R.id.license_list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(new LicenseAdapter());
    }

    private void buildList() {
        components.add(new Component("ZrinFile 文件管理", "本应用源代码与界面实现，以 MIT 许可证发布", "MIT", "licenses/MIT.txt"));
        components.add(new Component("AndroidX AppCompat · Core · RecyclerView · DrawerLayout", "Android Jetpack 基础组件", "Apache-2.0", "licenses/Apache-2.0.txt"));
        components.add(new Component("Material Design Icons", "界面图标路径，来自 Google Material Design", "Apache-2.0", "licenses/Apache-2.0.txt"));
        components.add(new Component("Android Shell / Toybox", "未启用 Termux 时调用的系统 /system/bin/sh，由 Android 系统提供", "系统组件", null));
        components.add(new Component("Termux bootstrap", "随安装包分发的官方 Termux 基础环境（apt、bash、coreutils 等），包内附各软件包许可证", "GPL-3.0", "licenses/GPL-3.0.txt"));
        components.add(new Component("proot", "在无 Root 环境下映射 Termux 路径前缀", "GPL-2.0", "licenses/GPL-2.0.txt"));
        components.add(new Component("libtalloc", "proot 依赖的内存分配库", "LGPL-3.0", "licenses/LGPL-3.0.txt"));
        components.add(new Component("libandroid-shmem", "proot 依赖的 Android 共享内存库", "Apache-2.0", "licenses/Apache-2.0.txt"));
        components.add(new Component("JetBrains Mono", "命令行等宽字体", "OFL-1.1", "licenses/OFL-1.1.txt"));
        components.add(new Component("Fira Code", "命令行等宽字体", "OFL-1.1", "licenses/OFL-1.1.txt"));
        components.add(new Component("Source Code Pro", "命令行等宽字体", "OFL-1.1", "licenses/OFL-1.1.txt"));
        components.add(new Component("Ubuntu Mono", "命令行等宽字体", "UFL-1.0", "licenses/UFL-1.0.txt"));
        components.add(new Component("开放源代码说明", "所有第三方组件均按其原始许可证分发，完整文本可在本页查看", "说明", null));
    }

    private void showText(String title, String text) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_text, null);
        TextView tv = view.findViewById(R.id.dialog_text);
        tv.setText(text);
        new AlertDialog.Builder(this, R.style.Theme_ZrinFile_Dialog)
                .setTitle(title)
                .setView(view)
                .setPositiveButton("关闭", null)
                .show();
    }

    private String readAsset(String path) {
        InputStream in = null;
        try {
            in = getAssets().open(path);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } catch (Exception e) {
            return "许可证文本读取失败: " + e.getMessage();
        } finally {
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
        }
    }

    private class LicenseAdapter extends RecyclerView.Adapter<LicenseAdapter.Holder> {

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView desc;
            final TextView tag;

            Holder(View v) {
                super(v);
                name = v.findViewById(R.id.license_name);
                desc = v.findViewById(R.id.license_desc);
                tag = v.findViewById(R.id.license_tag);
            }
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_license, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            final Component c = components.get(position);
            h.name.setText(c.name);
            h.desc.setText(c.desc);
            h.tag.setText(c.tag);
            h.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (c.asset != null) {
                        showText(c.name + " · " + c.tag, readAsset(c.asset));
                    } else if ("系统组件".equals(c.tag)) {
                        showText(c.name, "本应用的命令行直接调用 Android 系统自带的 /system/bin/sh 与 toybox 工具。\n\n这些组件由设备厂商随系统提供，遵循其各自的开放源代码许可证（主要为 BSD 与 GPL 系列）。本应用不重新分发这些二进制文件。");
                    } else {
                        showText(c.name, "本应用源代码采用 MIT 许可证：\n\n" + readAsset("licenses/MIT.txt")
                                + "\n\n第三方组件列表与许可证文本均随安装包一同提供。");
                    }
                }
            });
        }

        @Override
        public int getItemCount() {
            return components.size();
        }
    }
}
