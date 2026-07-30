package com.doro.terminal.shell.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCatalogTest {
    @Test
    fun bundledAppsAreNotShownInSetup() {
        val visible = SetupCatalog.optionalComponents
        assertFalse(InstallComponent.QQ in visible)
        assertFalse(InstallComponent.WECHAT in visible)
        assertFalse(InstallComponent.FIREFOX in visible)
    }

    @Test
    fun bundledDesktopIsNotShownInSetup() {
        val visible = SetupCatalog.optionalComponents
        assertFalse(InstallComponent.DESKTOP in visible)
        assertFalse(InstallComponent.XFCE in visible)
        assertFalse(InstallComponent.X11 in visible)
        assertFalse(InstallComponent.PULSEAUDIO in visible)
        assertFalse(InstallComponent.FILE_MANAGER in visible)
    }
}