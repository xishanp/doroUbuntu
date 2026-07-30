package com.doro.terminal.shell.linux

import android.os.ParcelFileDescriptor
import com.termux.terminal.JNI
import java.io.Closeable
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class LinuxProcessSession(
    private val manager: LinuxContainerManager,
    private val onOutput: (String) -> Unit,
    private val onExit: (Int) -> Unit
) : Closeable {
    private val closed = AtomicBoolean(false)
    private val processId = IntArray(1)
    private val command = manager.loginCommand()
    private val masterFd = JNI.createSubprocess(
        command.first(),
        manager.sessionWorkingDirectory(),
        command.toTypedArray(),
        manager.loginEnvironment().map { "${it.key}=${it.value}" }.toTypedArray(),
        processId,
        40,
        120,
        8,
        16
    )
    private val descriptor = ParcelFileDescriptor.adoptFd(masterFd)
    private val inputDescriptor = ParcelFileDescriptor.dup(descriptor.fileDescriptor)
    private val output = FileOutputStream(descriptor.fileDescriptor)

    init {
        Thread({
            val buffer = ByteArray(8192)
            ParcelFileDescriptor.AutoCloseInputStream(inputDescriptor).use { input ->
                while (!closed.get()) {
                    val count = runCatching { input.read(buffer) }.getOrDefault(-1)
                    if (count < 0) break
                    if (count > 0) onOutput(String(buffer, 0, count, Charsets.UTF_8))
                }
            }
        }, "ubuntu-pty-output").apply {
            isDaemon = true
            start()
        }
        Thread({
            val code = JNI.waitFor(processId[0])
            if (!closed.get()) onExit(code)
        }, "ubuntu-pty-waiter").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    fun write(command: String) {
        check(!closed.get()) { "会话已关闭" }
        output.write(command.toByteArray(Charsets.UTF_8))
        output.write('\n'.code)
        output.flush()
    }

    fun resize(rows: Int, columns: Int) {
        if (!closed.get()) JNI.setPtyWindowSize(masterFd, rows, columns, 8, 16)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { output.close() }
        runCatching { descriptor.close() }
    }
}