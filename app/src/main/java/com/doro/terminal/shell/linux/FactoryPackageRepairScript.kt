package com.doro.terminal.shell.linux

object FactoryPackageRepairScript {
    fun build(): String =
        "export DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1 NEEDRESTART_MODE=a; " +
            "apt-get update; " +
            "apt-get -y -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold -f install; " +
            "dpkg --force-confdef --force-confold --configure -a; " +
            "dpkg -C"
}
