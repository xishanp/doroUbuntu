# LinuxHub

[English](#english) | [简体中文](#简体中文)

[架构 / Architecture](ARCHITECTURE.md) · [贡献 / Contributing](CONTRIBUTING.md) · [安全 / Security](SECURITY.md) · [致谢 / Acknowledgements](ACKNOWLEDGEMENTS.md) · [更新记录 / Changelog](CHANGELOG.md) · [GPL-3.0](LICENSE)

---

## 简体中文

LinuxHub 是一款面向 Android ARM64 设备的 Linux 桌面运行环境。

它将 Ubuntu、KDE Plasma、Wayland 与 Android 原生显示能力整合在同一个应用中，提供从系统部署、终端操作到图形桌面的完整体验。

### 核心特色

- 内置 Ubuntu 26.04 ARM64 运行环境
- 集成 KDE Plasma 6 桌面
- 使用 Wayland 原生图形架构
- 支持高通 Adreno GPU 图形加速
- 支持 Freedreno、Turnip 与 KGSL
- 支持 VirGL 兼容后端
- 避免 LLVM 软件渲染伪加速
- 自动探测并选择可用 GPU 后端
- 支持跟随设备刷新率输出
- 提供 16:9 桌面显示区域
- 支持横屏沉浸式桌面

### 图形与显示

LinuxHub 使用定制的 Anland 显示链路连接 Android 与 Linux 桌面。

图形帧通过 DMA-BUF 在 Linux 合成器与 Android Surface 之间传递，减少不必要的图像复制，并保持流畅的桌面响应。

高通设备可使用以下加速链路：

```text
KDE Plasma / KWin
        ↓
Wayland + Anland Backend
        ↓
Freedreno / Turnip / KGSL
        ↓
DMA-BUF
        ↓
Android Surface
```

主要能力包括：

- Qualcomm KGSL 原生设备支持
- Freedreno OpenGL 驱动支持
- Turnip Vulkan 驱动支持
- XWayland 应用兼容
- DMA-BUF 多缓冲显示
- GPU 渲染栅栏同步
- 动态分辨率同步
- 动态刷新率传递
- Android Surface 生命周期管理

### 桌面交互

LinuxHub 针对无鼠标的移动设备进行了触控适配：

- 单指滑动移动鼠标
- 单指轻点执行左键
- 长按松开执行右键
- 长按后滑动拖拽窗口
- 双指滑动模拟滚轮
- 双指轻点执行右键
- 支持外接鼠标与键盘
- 支持 Android 软键盘
- 支持中文组合输入与候选提交
- 支持退格、Delete、Tab、Esc 和方向键

桌面右侧提供常驻快捷工具栏，可快速调用软键盘及常用按键。

### Linux 运行环境

- 使用 PRoot 启动 ARM64 Linux 用户空间
- 无需修改 Android 系统分区
- 自动部署 RootFS
- 自动创建 Linux 用户
- 自动配置 sudo
- 自动生成中文 UTF-8 环境
- 自动配置终端环境
- 支持多终端会话
- 支持文件管理器与开发工具
- 支持 `/sdcard` 文件访问
- 支持 PulseAudio 音频链路
- 支持 Android 电量信息映射

### 终端体验

- 内置 Termux Terminal Emulator 核心
- 支持真彩色终端
- 使用等宽字体
- 支持触控唤起软键盘
- 支持中文输入
- 支持复制与粘贴
- 支持多标签会话
- 支持字体缩放
- 内置 Fastfetch 环境信息展示

### 桌面组件

默认桌面环境包含：

- KDE Plasma 6
- KWin Wayland
- Dolphin 文件管理器
- Konsole 终端
- Kate 编辑器
- XWayland 兼容层
- PulseAudio

浏览器及其他第三方应用由用户按需安装。

### 开发环境

LinuxHub 可作为移动端 Linux 开发环境使用，支持按需安装：

- Git
- Python
- Node.js
- GCC
- CMake
- Java
- Rust
- Go
- Jupyter

### 系统要求

- Android ARM64 设备
- 推荐 Android 10 或更高版本
- 建议预留充足存储空间
- 高通 Adreno 设备可获得完整 KGSL 加速体验
- 其他设备可使用兼容图形后端

### 使用说明

1. 安装 LinuxHub APK。
2. 首次启动时创建 Linux 用户。
3. 等待 Ubuntu 环境部署完成。
4. 进入终端或打开 KDE 桌面。
5. 根据需要安装浏览器及其他 Linux 应用。

### 项目说明

LinuxHub 的基础框架来源于 UP 主 **iPupil**。

本项目在该框架基础上完成了 Android 应用整合、Ubuntu 环境部署、KDE Plasma 桌面适配、Anland 显示链路、GPU 加速策略、触控交互、中文输入、终端体验及兼容性优化。

感谢 iPupil 对原始框架的分享与贡献。

---

## English

LinuxHub is a Linux desktop runtime environment designed for Android ARM64 devices.

It integrates Ubuntu, KDE Plasma, Wayland, and Android's native display stack into one application, providing a complete experience from system deployment and terminal access to a full graphical desktop.

### Highlights

- Built-in Ubuntu 26.04 ARM64 environment
- Integrated KDE Plasma 6 desktop
- Native Wayland-based graphics architecture
- Qualcomm Adreno GPU acceleration
- Freedreno, Turnip, and KGSL support
- VirGL compatibility backend
- Protection against unintended LLVM software-rendering fallback
- Automatic GPU backend detection and selection
- Device refresh-rate integration
- 16:9 desktop viewport
- Immersive landscape desktop mode

### Graphics and Display

LinuxHub uses a customized Anland display pipeline to connect the Linux desktop with Android.

Frames are transferred between the Linux compositor and Android Surface through DMA-BUF, reducing unnecessary image copies and maintaining responsive desktop rendering.

On supported Qualcomm devices, the graphics pipeline is:

```text
KDE Plasma / KWin
        ↓
Wayland + Anland Backend
        ↓
Freedreno / Turnip / KGSL
        ↓
DMA-BUF
        ↓
Android Surface
```

Graphics capabilities include:

- Native Qualcomm KGSL device support
- Freedreno OpenGL support
- Turnip Vulkan support
- XWayland application compatibility
- Multi-buffer DMA-BUF presentation
- GPU render-fence synchronization
- Dynamic resolution synchronization
- Refresh-rate propagation
- Android Surface lifecycle integration

### Desktop Interaction

LinuxHub includes touch controls designed for mobile devices without a mouse:

- One-finger movement for cursor control
- Single tap for left click
- Long press and release for right click
- Long press and move for window dragging
- Two-finger scrolling
- Two-finger tap for right click
- External mouse and keyboard support
- Android soft-keyboard support
- Chinese composition and candidate input
- Backspace, Delete, Tab, Esc, and arrow-key support

A persistent shortcut toolbar is available on the right side of the desktop for quick access to the soft keyboard and common keys.

### Linux Runtime

- ARM64 Linux userspace powered by PRoot
- No Android system partition modification required
- Automatic RootFS deployment
- Automatic Linux user creation
- Automatic sudo configuration
- Chinese UTF-8 locale setup
- Automatic terminal environment setup
- Multiple terminal sessions
- File manager and development tools
- `/sdcard` storage access
- PulseAudio integration
- Android battery information mapping

### Terminal Experience

- Built on the Termux Terminal Emulator core
- True-color terminal support
- Monospace font rendering
- Touch-to-open soft keyboard
- Chinese text input
- Copy and paste support
- Multi-tab sessions
- Font scaling
- Built-in Fastfetch system overview

### Desktop Components

The default desktop environment includes:

- KDE Plasma 6
- KWin Wayland
- Dolphin file manager
- Konsole terminal
- Kate text editor
- XWayland compatibility layer
- PulseAudio

Web browsers and other third-party applications can be installed by users as needed.

### Development Environment

LinuxHub can also be used as a mobile Linux development environment. Optional tools include:

- Git
- Python
- Node.js
- GCC
- CMake
- Java
- Rust
- Go
- Jupyter

### Requirements

- Android ARM64 device
- Android 10 or newer recommended
- Sufficient free storage space
- Qualcomm Adreno devices are recommended for the complete KGSL acceleration experience
- Other devices may use a compatible graphics backend

### Getting Started

1. Install the LinuxHub APK.
2. Create a Linux user during the first launch.
3. Wait for the Ubuntu environment to finish deployment.
4. Open the terminal or launch the KDE desktop.
5. Install a browser and other Linux applications as needed.

### Credits

The foundational framework used by LinuxHub originates from content creator **iPupil**.

LinuxHub extends that foundation with Android application integration, Ubuntu deployment, KDE Plasma adaptation, the Anland display pipeline, GPU acceleration policies, touch interaction, Chinese input, terminal integration, and compatibility improvements.

Special thanks to iPupil for sharing and contributing the original framework.

---

## License and Third-Party Components

LinuxHub integrates or works with multiple open-source projects. Their respective licenses and copyrights remain with their original authors, including Ubuntu, KDE, Wayland, Mesa, Termux, PRoot, and related components.

Before redistribution, please review the licenses of all bundled binaries, libraries, assets, and RootFS content.
