# ZrinFile 文件管理 · 开源组件与许可证

本项目源代码以 **MIT** 许可证发布，完整文本见 `LICENSE`。
安装包内的「开源许可」页面同样提供下列许可证全文。

## 组件列表

| 组件 | 用途 | 许可证 | 全文 |
| --- | --- | --- | --- |
| ZrinFile 文件管理 | 应用源代码与界面实现 | MIT | `LICENSE` / `app/src/main/assets/licenses/MIT.txt` |
| AndroidX AppCompat · Core · RecyclerView · DrawerLayout | Android 基础 UI 组件 | Apache-2.0 | `app/src/main/assets/licenses/Apache-2.0.txt` |
| Material Design Icons | 界面图标路径 | Apache-2.0 | `app/src/main/assets/licenses/Apache-2.0.txt` |
| Android Shell / Toybox | 未启用 Termux 时使用的系统命令行运行时 | 系统组件（随设备） | — |
| Termux bootstrap | 随安装包分发的官方 Termux 基础环境（bash、apt、coreutils 等），包内附各软件包许可证 | GPL-3.0 | `app/src/main/assets/licenses/GPL-3.0.txt` |
| proot | 无 Root 环境下映射 Termux 路径前缀 | GPL-2.0 | `app/src/main/assets/licenses/GPL-2.0.txt` |
| libtalloc | proot 依赖库 | LGPL-3.0 | `app/src/main/assets/licenses/LGPL-3.0.txt` |
| libandroid-shmem | proot 依赖库 | Apache-2.0 | `app/src/main/assets/licenses/Apache-2.0.txt` |
| JetBrains Mono | 命令行等宽字体 | SIL Open Font License 1.1 | `app/src/main/assets/licenses/OFL-1.1.txt` |
| Fira Code | 命令行等宽字体 | SIL Open Font License 1.1 | `app/src/main/assets/licenses/OFL-1.1.txt` |
| Source Code Pro | 命令行等宽字体 | SIL Open Font License 1.1 | `app/src/main/assets/licenses/OFL-1.1.txt` |
| Ubuntu Mono | 命令行等宽字体 | Ubuntu Font License 1.0 | `app/src/main/assets/licenses/UFL-1.0.txt` |

## 说明

- 应用不收集账号、不上传文件，不包含广告或付费功能。
- Termux 运行时使用官方 bootstrap 包，未修改其二进制内容；bootstrap 内各软件包自带的许可证文本位于安装后的 `usr/share/LICENSES`。
- Termux 运行时需要 Android 7.0 及以上；Android 5/6 自动使用系统 Shell。
- Root 浏览仅在设备已授权 `su` 时可用；未 Root 设备受 Android 沙箱限制，部分系统目录不可读，这是系统安全机制而非应用限制。
- 字体文件位于 `app/src/main/assets/fonts/`，其许可证全文随安装包一同分发。
