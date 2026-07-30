package com.doro.terminal.shell.linux

object VendorAppsRepairScript {
    fun build(): String =
        "export DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1 NEEDRESTART_MODE=a; " +
            "apt-get update; " +
            "apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold -f install; " +
            "apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold install libx11-xcb1 libasound2t64 libgtk-3-0t64 libnotify4 libnss3 libxss1 libxtst6 libatspi2.0-0t64 libsecret-1-0 fonts-noto-cjk; " +
            "dpkg --force-confdef --force-confold --configure -a; " +
            "dpkg -C"

    fun check(): String =
        "command -v firefox >/dev/null || { echo '缺少 firefox 命令'; exit 1; }; " +
            "test -f /usr/share/applications/firefox.desktop || { echo '缺少 Firefox 桌面入口'; exit 1; }; " +
            "doro_missing=$(LD_LIBRARY_PATH=/opt/firefox ldd /opt/firefox/firefox-bin /opt/firefox/libxul.so 2>&1 | grep -E 'not found|version .* not found' || true); " +
            "test -z \"${'$'}doro_missing\" || { echo \"Firefox 缺少运行库:\n${'$'}doro_missing\"; exit 1; }"
}