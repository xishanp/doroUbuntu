# Contributing to LinuxHub

感谢你参与 LinuxHub。

## 提交方式

1. Fork 仓库。
2. 从主分支创建功能分支。
3. 一次提交只处理一个模块。
4. 修改前先定位相关源码。
5. 为行为变更补充测试。
6. 确保 Debug 构建通过。
7. 提交 Pull Request。

## 分支命名

- `feature/<name>`：新功能
- `fix/<name>`：问题修复
- `docs/<name>`：文档
- `refactor/<name>`：重构

## 提交信息

推荐使用简洁的 Conventional Commits：

```text
feat: add input shortcut
fix: correct evdev backspace mapping
docs: update GPU architecture
```

## 开发要求

- 不得静默回退到伪硬件加速。
- GPU 后端必须经过实际能力检测。
- 不得破坏 PRoot、Wayland 或终端现有流程。
- 输入修改需检查软键盘和物理键盘。
- 原生层修改需检查缓冲区与文件描述符生命周期。
- 公共行为变化需同步更新文档。

## 构建

```bash
./gradlew :app:assembleDebug --no-daemon
```

## 测试重点

- 首次 RootFS 部署
- 已安装环境再次启动
- 终端输入与删除
- 中文组合输入
- 横屏桌面进入和退出
- KGSL 与 VirGL 后端选择
- Surface 重建
- 外接鼠标和键盘

## Pull Request

PR 描述应包含：

- 修改目标
- 实现方式
- 测试设备
- Android 版本
- GPU 型号
- 构建结果
- 截图或日志（适用时）

## License

提交代码即表示你有权贡献该内容，并同意贡献内容按 GNU GPL v3.0 发布。

---

# English

Thank you for contributing to LinuxHub.

1. Fork the repository.
2. Create a focused branch.
3. Keep each change limited to one module.
4. Add tests for behavior changes.
5. Build with `./gradlew :app:assembleDebug --no-daemon`.
6. Open a pull request with device, Android, GPU, and test details.

Contributions are accepted under GNU GPL v3.0.