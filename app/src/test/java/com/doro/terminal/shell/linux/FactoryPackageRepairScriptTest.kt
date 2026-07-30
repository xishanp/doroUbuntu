package com.doro.terminal.shell.linux

import org.junit.Assert.assertTrue
import org.junit.Test

class FactoryPackageRepairScriptTest {
    @Test
    fun repairsMissingDependenciesBeforeAudit() {
        val script = FactoryPackageRepairScript.build()
        assertTrue(script.contains("apt-get update"))
        assertTrue(script.contains("apt-get -y -f install"))
        assertTrue(script.indexOf("apt-get -y -f install") < script.indexOf("dpkg -C"))
    }
}