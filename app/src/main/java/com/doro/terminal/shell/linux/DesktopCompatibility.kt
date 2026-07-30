package com.doro.terminal.shell.linux

internal object DesktopCompatibility {
    fun systemServicesScript(): String =
        "mkdir -p /run/dbus; if ! test -S /run/dbus/system_bus_socket; then " +
            "doro_dbus_error=\$(dbus-daemon --system --fork --nopidfile 2>&1) || { " +
            "printf '%s\\n' \"\$doro_dbus_error\" | tee /tmp/doro-system-dbus.log; exit 1; }; " +
            "printf '%s\\n' \"\$doro_dbus_error\" > /tmp/doro-system-dbus.log; fi"

    fun wallpaperScript(): String = """#!/bin/sh
wallpaper=/usr/share/backgrounds/xfce/doro-ubuntu.png
channel=xfce4-desktop
attempt=0
while [ "${'$'}attempt" -lt 20 ]; do
    properties="${'$'}(xfconf-query -c "${'$'}channel" -l 2>/dev/null)"
    [ -n "${'$'}properties" ] && break
    attempt=${'$'}((attempt + 1))
    sleep 1
done
last_image=/backdrop/screen0/monitor0/workspace0/last-image
image_style=/backdrop/screen0/monitor0/workspace0/image-style
xfconf-query -c "${'$'}channel" -p "${'$'}last_image" -s "${'$'}wallpaper" 2>/dev/null || \
    xfconf-query -c "${'$'}channel" -p "${'$'}last_image" -n -t string -s "${'$'}wallpaper" || exit 1
xfconf-query -c "${'$'}channel" -p "${'$'}image_style" -s 5 2>/dev/null || \
    xfconf-query -c "${'$'}channel" -p "${'$'}image_style" -n -t int -s 5 || exit 1
properties="${'$'}(xfconf-query -c "${'$'}channel" -l 2>/dev/null)"
printf '%s\n' "${'$'}properties" | while IFS= read -r property; do
    case "${'$'}property" in
        */last-image|*/image-path) xfconf-query -c "${'$'}channel" -p "${'$'}property" -s "${'$'}wallpaper" ;;
        */image-style) xfconf-query -c "${'$'}channel" -p "${'$'}property" -s 5 ;;
    esac
done
applied="${'$'}(xfconf-query -c "${'$'}channel" -p "${'$'}last_image" 2>/dev/null)"
[ "${'$'}applied" = "${'$'}wallpaper" ] || exit 1
"""

    fun sessionScript(display: String, username: String): String =
        "export DISPLAY=${quote(display)}; " +
            "if locale -a 2>/dev/null | grep -qi '^zh_CN\\.utf8$'; then " +
            "export LANG=zh_CN.UTF-8 LC_ALL=zh_CN.UTF-8 LANGUAGE=zh_CN:zh; " +
            "else export LANG=C.UTF-8 LC_ALL=C.UTF-8; unset LANGUAGE; fi; " +
            "export XDG_RUNTIME_DIR=/tmp/runtime-${quote(username)}; " +
            "mkdir -p \"\$XDG_RUNTIME_DIR\"; chmod 700 \"\$XDG_RUNTIME_DIR\"; " +
            "export PATH=/usr/local/lib/doro-software-bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games; " +
            "if [ \"\$(cat /etc/doro-gpu-enabled 2>/dev/null)\" = \"1\" ]; then " +
            "export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink; " +
            "export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json; " +
            "export VK_DRIVER_FILES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json; " +
            "unset LIBGL_ALWAYS_SOFTWARE LIBGL_DRI3_DISABLE; " +
            "else unset MESA_LOADER_DRIVER_OVERRIDE GALLIUM_DRIVER VK_ICD_FILENAMES VK_DRIVER_FILES; export LIBGL_ALWAYS_SOFTWARE=1; fi; " +
            "export XMODIFIERS=@im=fcitx GTK_IM_MODULE=fcitx QT_IM_MODULE=fcitx; " +
            "export MOZ_ENABLE_WAYLAND=0; " +
            "export NO_AT_BRIDGE=1; " +
            "unset DBUS_SESSION_BUS_ADDRESS DBUS_SESSION_BUS_PID SESSION_MANAGER; " +
            "pkill -9 -x xfce4-session 2>/dev/null || true; pkill -9 -x startxfce4 2>/dev/null || true; " +
            "pkill -9 -x xfce4-panel 2>/dev/null || true; pkill -9 -x xfdesktop 2>/dev/null || true; pkill -9 -x xfwm4 2>/dev/null || true; " +
            "pkill -9 -x xfce4-power-manager 2>/dev/null || true; pkill -9 -x upowerd 2>/dev/null || true; sleep 2; " +
            "exec dbus-launch --exit-with-session /bin/sh -c '" +
            "command -v fcitx5 >/dev/null 2>&1 && fcitx5 -d; " +
            "xfconf-query -c xsettings -p /Net/ThemeName -s WhiteSur-Dark 2>/dev/null || true; " +
            "xfconf-query -c xsettings -p /Net/IconThemeName -s WhiteSur 2>/dev/null || true; " +
            "(sleep 5; doro-apply-wallpaper >/tmp/doro-wallpaper.log 2>&1; xfdesktop --reload >/dev/null 2>&1 || true) & " +
            "exec startxfce4'"

    fun patchDesktop(content: String, name: String): String {
        val gpuPrefix = "/usr/local/bin/doro-gpu-run "
        return content.lineSequence().joinToString("\n") { originalLine ->
            val trimmed = originalLine.trim()
            val repairedLine = if (
                trimmed.isNotEmpty() &&
                !trimmed.startsWith("#") &&
                !trimmed.startsWith("[") &&
                !trimmed.contains('=')
            ) "Exec=$trimmed" else originalLine
            if (!repairedLine.startsWith("Exec=")) return@joinToString repairedLine
            var command = repairedLine.removePrefix("Exec=").removePrefix(gpuPrefix)
            command = when (name) {
                "qq.desktop" -> command.replace("/opt/QQ/qq --no-sandbox", "/opt/QQ/qq")
                    .replace("/opt/QQ/qq", "/opt/QQ/qq --no-sandbox")
                "firefox.desktop" -> command
                    .replace("/opt/firefox/firefox --no-remote", "/opt/firefox/firefox")
                    .replace("/opt/firefox/firefox", "/usr/local/bin/doro-firefox")
                else -> command
            }
            "Exec=$gpuPrefix$command"
        } + if (content.endsWith('\n')) "\n" else ""
    }

    private fun quote(value: String) = "'${value.replace("'", "'\\''")}'"
}