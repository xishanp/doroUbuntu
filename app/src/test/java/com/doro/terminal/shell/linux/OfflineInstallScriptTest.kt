package com.doro.terminal.shell.linux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineInstallScriptTest {
    @Test
    fun installsDependencyPackagesBeforeMainPackages() {
        val script = OfflineInstallScript.build()
        assertTrue(script.contains("-i /var/cache/doro-offline/deps/*.deb"))
        assertTrue(script.indexOf("deps/*.deb") < script.indexOf("packages/*.deb"))
    }

    @Test
    fun neverRunsOnlineFixDuringOfflineStage() {
        val script = OfflineInstallScript.build()
        assertFalse(script.contains("apt-get -f install"))
    }

    @Test
    fun suppressesAllConfigurationPrompts() {
        val script = OfflineInstallScript.build()
        assertTrue(script.contains("DEBIAN_FRONTEND=noninteractive"))
        assertTrue(script.contains("--force-confdef --force-confold"))
        assertTrue(script.contains("UCF_FORCE_CONFFOLD=1"))
    }

    @Test
    fun streamsDpkgOutputToWatchdog() {
        val script = OfflineInstallScript.build()
        assertTrue(script.contains("| tee /tmp/doro-deps.log"))
        assertTrue(script.contains("| tee /tmp/doro-packages.log"))
        assertFalse(script.contains(">/tmp/doro-deps.log 2>&1"))
    }
}