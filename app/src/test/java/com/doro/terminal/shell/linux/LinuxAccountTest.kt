package com.doro.terminal.shell.linux

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinuxAccountTest {
    @Test
    fun acceptsNormalUser() {
        assertTrue(LinuxAccount.isValidUsername("zk"))
        assertTrue(LinuxAccount.isValidUsername("dev_user-24"))
    }

    @Test
    fun rejectsRootAndUnsafeNames() {
        assertFalse(LinuxAccount.isValidUsername("root"))
        assertFalse(LinuxAccount.isValidUsername("Bad User"))
        assertFalse(LinuxAccount.isValidUsername("-admin"))
    }

    @Test
    fun passwordConfirmationMustMatch() {
        assertEquals(null, LinuxAccount.validate("zk", "secret12", "secret12"))
        assertEquals("两次密码不一致", LinuxAccount.validate("zk", "secret12", "other12"))
    }
}
