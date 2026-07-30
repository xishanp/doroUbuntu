# Termux Ubuntu 外壳重建

此目录专门存放新方案资料和产物。

## 目标

- 复刻 Android 原生 Linux 终端外壳
- 使用本地 PTY 作为终端后端
- 使用 PRoot-Distro 运行 Ubuntu 24.04 arm64
- 使用 Termux:X11 运行 XFCE
- 尝试恢复 Turnip + Zink 图形加速

## 目录规划

- `references/`：截图、说明和研究资料
- `termux_apk/`：Termux 原版 APK 与提取结果
- `bootstrap/`：Termux bootstrap 与依赖
- `ubuntu/`：Ubuntu rootfs、安装脚本和配置
- `x11/`：Termux:X11、XFCE 与图形配置
- `app/`：最终外壳工程
- `builds/`：测试 APK
- `backups/`：配置备份

## 当前状态

主 Termux 已卸载。
Termux:X11 和多个 Termux 插件仍在设备上。
Ubuntu 原数据通常会随 Termux 卸载而被清除。