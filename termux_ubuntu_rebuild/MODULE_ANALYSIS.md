# Termux 与 Termux:X11 模块分析

## 结论

官方仓库已经提供单 APK 集成补丁，方向可行。
主工程应基于 Termux，而不是继续修改反编译 APK。
原生 Linux 终端仅作为界面参考。

## Termux 模块

### `app`

- 主 Android 应用，包名 `com.termux`
- 包含 `TermuxActivity`、`TermuxService`、bootstrap 安装器
- 管理会话、通知、文件访问和命令执行
- 最终需将 `TermuxActivity` 改造成原生终端风格

### `terminal-emulator`

- 纯终端状态机与 PTY 会话
- `TerminalSession` 通过 JNI 创建子进程和伪终端
- 这是标签页后端的核心

### `terminal-view`

- 终端绘制、键盘、选择、手势
- `TerminalView` 可直接嵌入自定义 Activity

### `termux-shared`

- Termux 路径、配置、插件协议和会话封装
- PRoot、bootstrap 与外部插件兼容依赖此模块

## Termux:X11 模块

### `lorie`

- Android Library
- 包含 X11 Activity、LorieView、CmdEntryPoint
- 包含 NDK/CMake X server
- 可直接作为 Termux `app` 的依赖嵌入

### `lorie-app`

- 独立 Termux:X11 APK 壳
- 单 APK方案不需要此模块

### `shell-loader`

- 独立应用模式的加载器
- 单 APK方案只保留 `shell-loader:stub`

## 官方集成补丁

仓库自带：

`.github/termux-app-integration/termux-app.patch`

补丁明确执行：

1. Termux `app` 添加 `implementation project(':lorie')`
2. Termux 工程添加 `:lorie`
3. 只引入 `shell-loader:stub`
4. minSdk 提升到 26
5. 调整 JNI 打包方式

## 推荐架构

- 包名：`com.termux`
- 主界面：复刻原生 Linux 终端
- 终端后端：`terminal-emulator` + `TermuxService`
- 文件系统：Termux bootstrap
- Ubuntu：PRoot-Distro 24.04 arm64
- 桌面：内嵌 `lorie` Activity
- 图形：Ubuntu 内配置 Turnip、Mesa、Zink

## 风险

- Termux 路径硬编码为 `/data/data/com.termux/files`
- 改包名会破坏大量兼容逻辑
- X11 全量构建需要完整子模块和 NDK 29
- PRoot 不提供 KVM；图形加速依赖 Mesa 驱动链
- 现有 Termux 插件必须与最终 APK 签名兼容