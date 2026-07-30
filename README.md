# doroUbuntu

[English](README_EN.md) | 简体中文

## 项目介绍

doroUbuntu 是安卓离线 Ubuntu 桌面环境，集成 Ubuntu 24.04、XFCE、Termux:X11 与 PulseAudio。

## 当前版本

- 版本：`1.1.9Debug`
- 状态：已完成全新安装验收
- 架构：ARM64

## 主要特性

- Ubuntu 24.04 离线初始化
- XFCE 桌面环境
- 内嵌 Termux:X11
- PulseAudio 声音桥
- Turnip 与 Zink 图形栈
- fastfetch 终端欢迎页
- 桌面默认横屏
- 启动画面保留 2.5 秒

## GPU 方案

桌面壳使用软件渲染。应用入口通过 `/usr/local/bin/doro-gpu-run` 使用 Zink。Turnip 与 Mesa 保持不变，不全局强制 Zink，避免桌面黑屏。

```bash
doro-gpu-run glxinfo -B
```

已验证 Zink Vulkan、Turnip Adreno 740 与硬件加速。

## 桌面入口

Firefox、星火应用商店、VLC、LibreOffice、GIMP 与 Thunar 支持入口恢复，并使用 GPU 包装器启动。

## 安装说明

1. 至少预留 10 GB 空间。
2. 安装发布页提供的 APK。
3. 打开应用并启动初始化。
4. 等待离线任务全部完成。
5. 按提示进入 XFCE 桌面。

部署期间不要清理后台，也不要强制关闭应用。

## 兼容性与限制

当前已验证 Qualcomm Adreno 740。其他 GPU 和系统尚未完整验证。Firefox 保持软件视频解码，本版本不含实验性 MediaCodec 硬解桥。

## 项目截图

部署流程、终端、XFCE 桌面和 GPU 验证截图将保存在 `assets/screenshots/`。

## 许可证

许可证信息整理中。使用和再分发前，请遵守代码、主题、图标、驱动及预装软件的各自授权条款。