# 实施顺序

1. 获取官方 Termux APK。
2. 确认 APK 来源与签名。
3. 提取终端核心、PTY 与 bootstrap 机制。
4. 建立可独立运行的本地终端。
5. 接入 PRoot-Distro。
6. 安装 Ubuntu 24.04 arm64。
7. 接入 Termux:X11。
8. 安装并启动 XFCE。
9. 配置 Turnip、Mesa 与 Zink。
10. 将设置和桌面按钮接回原生外壳。

## APK来源

优先使用 Termux 官方 GitHub Release 或 F-Droid。
不要使用 Google Play 的旧版 Termux。