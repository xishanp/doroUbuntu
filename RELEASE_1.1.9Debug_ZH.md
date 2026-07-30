# doroUbuntu 1.1.9Debug

## 发布状态

本版本已完成清空数据后的全新安装与离线初始化验收，定位为公开测试版。

## 版本亮点

- Ubuntu 24.04 离线环境
- XFCE 与 Termux:X11 集成
- PulseAudio 声音桥可用
- 桌面默认强制横屏
- 启动画面显示 Ubuntu 图标
- 内置终端显示欢迎页
- 桌面终端隐藏欢迎页
- 六个桌面入口已修复

## GPU 方案

- 桌面壳使用软件渲染
- 应用入口使用 Zink
- Turnip 驱动保持不变
- Mesa 环境保持不变
- 不使用全局强制 Zink
- 包装器：`/usr/local/bin/doro-gpu-run`

## 已知限制

- 当前仍为 Debug 构建
- 其他 GPU 尚未完整验证
- Firefox 保持软件视频解码
- 不包含实验性硬解功能
- APK 与离线资源体积较大