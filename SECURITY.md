# Security Policy

## Supported version

Security fixes are provided for the latest source revision and latest published release.

## Reporting a vulnerability

请不要在公开 Issue 中披露尚未修复的安全问题。

Do not disclose an unpatched vulnerability in a public issue.

请通过 GitHub Security Advisory 私密报告，并提供：

- 受影响版本
- Android 与设备信息
- 复现步骤
- 影响范围
- 日志或概念验证
- 建议修复（如有）

Please use GitHub Security Advisories and include the affected version, device details, reproduction steps, impact, logs or proof of concept, and any suggested fix.

## Security boundaries

LinuxHub uses PRoot userspace isolation. PRoot is not a hardware virtual machine and must not be treated as a hostile-code security sandbox.

用户在 Linux 环境执行的程序可能访问已绑定的 Android 路径。请仅安装可信软件，并谨慎映射个人文件。

Programs running in the Linux environment may access Android paths explicitly bind-mounted into the RootFS. Install trusted software and protect personal files.

## Sensitive data

提交日志前请删除用户名、文件路径、网络地址、令牌及个人信息。

Remove usernames, paths, network addresses, tokens, and personal information before publishing logs.