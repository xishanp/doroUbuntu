package com.doro.terminal.shell.linux

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessWatchdogTest {
    @Test
    fun idleTimeoutTriggersAfterDeadline() {
        val watchdog = ProcessWatchdog(timeoutMs = 10_000, idleTimeoutMs = 2_000, startedAtMs = 1_000)
        watchdog.recordActivity(2_000)
        assertFalse(watchdog.isIdleTimedOut(3_999))
        assertTrue(watchdog.isIdleTimedOut(4_000))
    }

    @Test
    fun zeroIdleTimeoutDisablesIdleWatchdog() {
        val watchdog = ProcessWatchdog(timeoutMs = 10_000, idleTimeoutMs = 0, startedAtMs = 1_000)
        assertFalse(watchdog.isIdleTimedOut(100_000))
    }

    @Test
    fun totalTimeoutTriggersAfterDeadline() {
        val watchdog = ProcessWatchdog(timeoutMs = 10_000, idleTimeoutMs = 2_000, startedAtMs = 1_000)
        watchdog.recordActivity(9_500)
        assertFalse(watchdog.isTotalTimedOut(10_999))
        assertTrue(watchdog.isTotalTimedOut(11_000))
    }
}
