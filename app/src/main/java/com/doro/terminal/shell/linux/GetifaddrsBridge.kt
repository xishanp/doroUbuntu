package com.doro.terminal.shell.linux

import java.io.File

object GetifaddrsBridge {
    const val CONTAINER_LIBRARY = "/usr/local/lib/doro/libgetifaddrs_bridge.so"
    const val CONTAINER_SOCKET = "/tmp/.getifaddrs-bridge"

    fun preloadExpression(existing: String): String =
        listOf(CONTAINER_LIBRARY, existing).filter { it.isNotBlank() }.joinToString(":")

    fun installClient(rootfsDir: File, source: File) {
        check(source.isFile) { "getifaddrs客户端库缺失" }
        val target = File(rootfsDir, CONTAINER_LIBRARY.removePrefix("/"))
        target.parentFile?.mkdirs()
        source.inputStream().use { input -> target.outputStream().use(input::copyTo) }
        target.setReadable(true, false)
        target.setExecutable(true, false)
    }
}