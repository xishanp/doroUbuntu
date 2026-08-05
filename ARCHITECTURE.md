# LinuxHub Architecture

[中文](#中文) | [English](#english)

## 中文

### 总览

LinuxHub 将 Android 原生应用、PRoot Linux 用户空间、KDE Plasma、Wayland 合成器与硬件图形驱动连接为一套移动 Linux 桌面系统。

```text
Android App / Jetpack Compose
          │
          ├── Terminal UI ── Termux Terminal Core
          │
          ├── Runtime Manager
          │       ├── RootFS 部署
          │       ├── PRoot 生命周期
          │       ├── 用户与环境配置
          │       └── 桌面服务控制
          │
          └── Anland SurfaceView
                  │
             Native Consumer
                  │ DMA-BUF / Fence / IPC
             Anland Producer
                  │
            KWin Wayland Backend
                  │
      Freedreno / Turnip / KGSL / VirGL
```

### Android 应用层

Android 应用层负责首次部署、终端会话、桌面入口、横屏显示、输入法连接、触控映射和生命周期管理。

主要职责：

- Jetpack Compose 页面与状态管理
- Ubuntu 初始化进度与断点状态
- Android Surface 创建和销毁
- 终端标签与会话管理
- Android IME 与 Linux 输入桥接
- 系统栏、横屏与沉浸模式管理

### Linux 容器运行时

LinuxHub 使用 PRoot 提供 ARM64 Linux 用户空间。

运行时负责：

- RootFS 完整性检查与部署
- Android 目录到 Linux 的绑定挂载
- `/dev`、`/proc`、`/sys` 与共享存储映射
- 用户、sudo、语言环境和时区配置
- D-Bus、PulseAudio 和 KDE 会话启动
- 进程清理与异常恢复

### Wayland 与 Anland

Anland 是 Android 与 Linux Wayland 桌面之间的显示桥。

消费者位于 Android 进程，生产者连接 KWin 后端。双方通过 Unix Socket 与共享图形缓冲区交换状态、输入和帧。

核心能力：

- 自定义显示分辨率
- DMA-BUF 缓冲区传输
- Acquire/Release Fence 同步
- 多缓冲帧呈现
- 触摸、键盘与鼠标事件
- 剪贴板同步
- 刷新率信息传递

### GPU 后端

高通设备优先使用 KGSL 图形链：

```text
KWin → Freedreno OpenGL → KGSL → Adreno GPU
应用 → Turnip Vulkan → KGSL → Adreno GPU
```

运行时检测 `/dev/kgsl-3d0`、Mesa 驱动和 Vulkan ICD，并选择真实可用的后端。

兼容设备可以使用 VirGL Guest 驱动。软件渲染回退被主动约束，避免把 LLVM 渲染误报为硬件加速。

### 输入系统

输入系统将 Android 事件转换为 Linux evdev 语义：

- 触摸板式指针移动
- 左键、右键与拖拽
- 双指滚动
- 外接鼠标相对移动与按键
- Android KeyCode 到 evdev KeyCode
- UTF-8 文本提交
- 中文 composing 文本增量替换
- Backspace、Delete 与方向键

### 音频

PulseAudio 在 Linux 用户空间运行。Android 音频桥负责播放与采集数据通道，并将 Linux 桌面音频接入 Android 设备。

### 终端

终端基于 Termux Terminal Emulator 与 TerminalView 核心，提供 PTY 会话、真彩色、Unicode、复制粘贴、标签页和软键盘输入。

### 数据与边界

- 应用私有目录保存 RootFS 与运行时数据
- `/sdcard` 按需映射到 Linux
- RootFS 不修改 Android 系统分区
- 用户安装的软件保存在 Linux RootFS 内

## English

### Overview

LinuxHub connects an Android application, a PRoot Linux userspace, KDE Plasma, a Wayland compositor, and hardware graphics drivers into a mobile Linux desktop stack.

```text
Android App / Jetpack Compose
          │
          ├── Terminal UI ── Termux Terminal Core
          │
          ├── Runtime Manager
          │       ├── RootFS Deployment
          │       ├── PRoot Lifecycle
          │       ├── User and Environment Setup
          │       └── Desktop Service Control
          │
          └── Anland SurfaceView
                  │
             Native Consumer
                  │ DMA-BUF / Fence / IPC
             Anland Producer
                  │
            KWin Wayland Backend
                  │
      Freedreno / Turnip / KGSL / VirGL
```

### Android Layer

The Android layer manages initial deployment, terminal sessions, desktop launch, landscape presentation, IME integration, touch mapping, and lifecycle coordination.

### Linux Runtime

LinuxHub uses PRoot to provide an ARM64 Linux userspace without modifying the Android system partition. It manages RootFS deployment, bind mounts, users, locale, D-Bus, PulseAudio, KDE startup, cleanup, and recovery.

### Wayland and Anland

Anland bridges Android and the Linux Wayland desktop. Its Android consumer and KWin-side producer exchange frames, state, and input through Unix sockets and shared graphics buffers.

It supports DMA-BUF transfer, synchronization fences, multi-buffer presentation, dynamic resolution, refresh-rate reporting, input forwarding, and clipboard synchronization.

### GPU Backends

Supported Qualcomm devices use the KGSL graphics path:

```text
KWin → Freedreno OpenGL → KGSL → Adreno GPU
Apps → Turnip Vulkan → KGSL → Adreno GPU
```

The runtime validates the KGSL device, Mesa drivers, and Vulkan ICD before selecting acceleration. VirGL is available as a compatibility backend.

### Input, Audio, and Terminal

Android touch, mouse, keyboard, and IME events are translated into Linux input semantics. PulseAudio is connected through an Android audio bridge. The terminal is based on the Termux terminal emulator core and supports PTY sessions, Unicode, true color, tabs, clipboard operations, and soft-keyboard input.