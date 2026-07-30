package com.doro.terminal.shell.linux

import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopCompatibilityTest {
    @Test fun sessionScriptStartsSystemAndSessionDbus() {
        val script = DesktopCompatibility.sessionScript(":0", "doro")
        assertTrue(DesktopCompatibility.systemServicesScript().contains("dbus-daemon --system"))
        assertTrue(script.contains("dbus-launch --exit-with-session"))
        assertTrue(script.contains("MOZ_DISABLE_CONTENT_SANDBOX=1"))
        assertTrue(script.contains("LIBGL_ALWAYS_SOFTWARE=1"))
    }

    @Test fun desktopLaunchersKeepNativeCommands() {
        val firefox = DesktopCompatibility.patchDesktop("Exec=/usr/local/bin/doro-gpu-run firefox %u", "firefox.desktop")
        val qq = DesktopCompatibility.patchDesktop("Exec=/usr/local/bin/doro-gpu-run /opt/QQ/qq %U", "qq.desktop")
        val thunar = DesktopCompatibility.patchDesktop("Exec=/usr/local/bin/doro-gpu-run thunar %F", "thunar.desktop")
        assertTrue(firefox == "Exec=firefox %u")
        assertTrue(qq == "Exec=/opt/QQ/qq --no-sandbox %U")
        assertTrue(thunar == "Exec=thunar %F")
        assertTrue(DesktopCompatibility.patchDesktop(thunar, "thunar.desktop") == thunar)
    }
}