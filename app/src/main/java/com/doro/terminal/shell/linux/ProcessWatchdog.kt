package com.doro.terminal.shell.linux

internal class ProcessWatchdog(
    private val timeoutMs: Long,
    private val idleTimeoutMs: Long,
    private val startedAtMs: Long = System.currentTimeMillis()
) {
    @Volatile private var lastActivityMs: Long = startedAtMs

    fun recordActivity(nowMs: Long = System.currentTimeMillis()) {
        lastActivityMs = nowMs
    }

    fun isTotalTimedOut(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - startedAtMs >= timeoutMs

    fun isIdleTimedOut(nowMs: Long = System.currentTimeMillis()): Boolean =
        idleTimeoutMs > 0L && nowMs - lastActivityMs >= idleTimeoutMs
}