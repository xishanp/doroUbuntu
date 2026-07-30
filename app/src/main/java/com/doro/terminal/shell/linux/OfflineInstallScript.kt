package com.doro.terminal.shell.linux

object OfflineInstallScript {
    fun build(): String =
        "export DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1 NEEDRESTART_MODE=a; " +
            "dpkg_opts='--force-confdef --force-confold'; " +
            "if ls /var/cache/doro-offline/deps/*.deb >/dev/null 2>&1; then " +
            "dpkg ${'$'}dpkg_opts -i /var/cache/doro-offline/deps/*.deb 2>&1 | tee /tmp/doro-deps.log || true; fi; " +
            "if ls /var/cache/doro-offline/packages/*.deb >/dev/null 2>&1; then " +
            "dpkg ${'$'}dpkg_opts -i /var/cache/doro-offline/packages/*.deb 2>&1 | tee /tmp/doro-packages.log || true; " +
            "elif ls /var/cache/doro-offline/*.deb >/dev/null 2>&1; then " +
            "dpkg ${'$'}dpkg_opts -i /var/cache/doro-offline/*.deb 2>&1 | tee /tmp/doro-packages.log || true; fi; " +
            "dpkg ${'$'}dpkg_opts --configure -a; dpkg -C"
}