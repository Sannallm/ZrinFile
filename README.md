# ZrinFile

Android 文件管理器，原生 Java 实现，界面为自绘的深色 Unity 风格。最低支持 Android 5.0（API 21），单个 APK 包含 armeabi-v7a、arm64-v8a、x86、x86_64 四种架构。

## 功能

文件管理

- 列表 / 网格视图，多标签页，侧边栏快捷入口
- 多选：复制、剪切、粘贴、删除、批量重命名、分享、属性
- 新建文件夹、新建文件（可指定后缀）、重命名、排序、显示隐藏文件
- 回收站：删除进入应用内回收站，可还原或彻底删除
- 搜索、书签、存储空间分析、压缩包查看与解压
- 根目录浏览：只读目录直接访问，受系统保护的目录在设备已 Root 时通过 `su` 读取

预览与打开

- 图片：双指缩放、双击放大、拖动
- 文本：UTF-8 / GBK 等编码识别
- HTML：预览（系统 WebView）或源码编辑，SVG 同样可预览
- PDF：内置逐页渲染
- 音频 / 视频：应用内播放；音频自动读取封面与歌词（同名 .lrc 或内嵌歌词），歌词按时间滚动
- APK：包名、版本、签名、权限信息
- 其他类型可交给系统或其他应用打开

文件服务器

- HTTP 文件共享：浏览器浏览、下载、断点续传、上传
- 网页服务器：直接托管目录中的 index.html 与静态资源
- FTP：目录浏览、下载、上传、重命名、删除
- 以前台服务运行，从最近任务划掉应用后继续运行，通知栏或应用内停止

内存清理

- 一键清理本应用缓存、缩略图并结束后台进程
- 扫描已安装应用与存储中的 APK，检测无法解析的安装包，可直接安装
- 扫描大于 50MB 的文件，可打开或删除

内置终端

- 系统 Shell 模式：直接调用 `/system/bin/sh`，适配所有系统版本
- Termux 模式：随安装包内置官方 Termux bootstrap，通过 proot 映射 `/data/data/com.termux/files/usr` 前缀，不需要 Root
  - 完整的 apt、bash、coreutils、nano、ssh 等命令
  - 原生 PTY（`libzrinpty.so`，四种架构），支持交互式命令、Ctrl+C、方向键
  - 首次进入自动解压环境，之后直接启动
  - 需要 Android 7.0 及以上；Android 5/6 自动回退到系统 Shell
- 命令补全气泡、终端字体（JetBrains Mono、Fira Code、Source Code Pro、Ubuntu Mono）、字号、配色
- 多会话、右侧会话抽屉
- `zrin`（别名 `fm`）桥接命令，可在终端中操作本应用

版本检测

- 启动时静默检查 GitHub Releases，有新版本才提示
- 展示更新内容，多线程分段下载，显示进度，SHA256 校验后调用系统安装器
- GitHub 直连失败时自动切换备用镜像

## 下载

在 [Releases](https://github.com/Sannallm/ZrinFile/releases) 页面下载 `ZrinFile-<版本>-universal.apk`。

`update.json` 为应用内更新检查使用的清单，包含版本号、APK 地址与 SHA256。

## 构建

环境：JDK 17、Android SDK（compileSdk 34）、NDK 28、CMake 3.22。

```bash
./gradlew assembleRelease
```

输出：`app/build/outputs/apk/release/ZrinFile-<版本>-universal.apk`

签名读取根目录 `keystore.properties`：

```properties
storeFile=keystore/zrinfile-release.jks
storePassword=******
keyAlias=zrinfile
keyPassword=******
```

该文件与 `keystore/` 不纳入版本控制；缺少时使用 debug 签名，仅用于本地测试。

Termux bootstrap 与 proot 二进制位于 `app/src/main/assets/termux/`，按架构分目录。

## 终端桥接命令

```text
zrin ls [路径]              列出目录
zrin cd <路径>              切换目录
zrin mkdir <路径>           新建目录
zrin touch <文件>           新建空文件
zrin rm <路径>              删除到回收站
zrin open <文件>            用内置预览打开
zrin info <文件>            查看文件信息
zrin find <目录> <关键字>   查找文件
zrin df                     查看存储空间
zrin trash                  查看回收站
zrin zip <目录> <输出.zip>  压缩
zrin unzip <压缩包> [目录]  解压
zrin bookmark <路径>        添加书签
```

## 许可

应用代码以 MIT 许可证发布，见 [LICENSE](LICENSE)。第三方组件与字体许可见 [LICENSES.md](LICENSES.md) 及 `app/src/main/assets/licenses/`。Termux bootstrap 内各软件包的许可证位于安装后的 `usr/share/LICENSES`。
