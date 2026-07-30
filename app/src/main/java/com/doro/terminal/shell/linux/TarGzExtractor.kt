package com.doro.terminal.shell.linux

import android.system.Os
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

object TarGzExtractor {
    private const val BLOCK = 512

    fun extract(
        source: InputStream,
        destination: File,
        totalBytes: Long = -1L,
        onProgress: (Int) -> Unit = {}
    ) {
        destination.mkdirs()
        var lastPercent = -1
        val counted = CountingInputStream(source) { bytesRead ->
            if (totalBytes > 0L) {
                val percent = ((bytesRead * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(percent)
                }
            }
        }
        val buffered = PushbackInputStream(BufferedInputStream(counted), 2)
        val signature = ByteArray(2)
        val count = buffered.read(signature)
        if (count > 0) buffered.unread(signature, 0, count)
        val archive: InputStream = if (
            count == 2 && signature[0] == 0x1f.toByte() && signature[1] == 0x8b.toByte()
        ) GZIPInputStream(buffered) else buffered
        archive.use { tar ->
            val header = ByteArray(BLOCK)
            while (readBlock(tar, header)) {
                if (header.all { it == 0.toByte() }) break
                val name = text(header, 0, 100)
                val prefix = text(header, 345, 155)
                val relative = if (prefix.isEmpty()) name else "$prefix/$name"
                val type = header[156].toInt().toChar()
                val size = octal(header, 124, 12)
                if (type == 'x' || type == 'g' || type == 'L' || type == 'K') {
                    skipFully(tar, size)
                    skipFully(tar, (BLOCK - size % BLOCK) % BLOCK)
                    continue
                }
                check(!relative.split('/').any { it.startsWith("PaxHeaders") || it == "PaxHeaders" }) {
                    "归档包含非法PAX路径：$relative"
                }
                val output = safeTarget(destination, relative)
                when (type) {
                    '5' -> output.mkdirs()
                    '2' -> {
                        output.parentFile?.mkdirs()
                        runCatching { Os.symlink(text(header, 157, 100), output.path) }
                        skipFully(tar, size)
                    }
                    '1' -> {
                        output.parentFile?.mkdirs()
                        val target = safeTarget(destination, text(header, 157, 100))
                        runCatching { Os.link(target.path, output.path) }
                            .getOrElse {
                                check(target.isFile) { "RootFS硬链接目标不存在：${target.path}" }
                                target.inputStream().use { input -> output.outputStream().use(input::copyTo) }
                            }
                        skipFully(tar, size)
                    }
                    else -> {
                        output.parentFile?.mkdirs()
                        output.outputStream().buffered().use { copyExactly(tar, it, size) }
                        output.setExecutable((octal(header, 100, 8) and 0b001001001) != 0L, false)
                    }
                }
                skipFully(tar, (BLOCK - size % BLOCK) % BLOCK)
            }
        }
    }

    private class CountingInputStream(
        source: InputStream,
        private val onBytesRead: (Long) -> Unit
    ) : FilterInputStream(source) {
        private var bytesRead = 0L

        override fun read(): Int = super.read().also { value ->
            if (value >= 0) report(1)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            super.read(buffer, offset, length).also { count ->
                if (count > 0) report(count.toLong())
            }

        override fun skip(count: Long): Long = super.skip(count).also { skipped ->
            if (skipped > 0) report(skipped)
        }

        private fun report(count: Long) {
            bytesRead += count
            onBytesRead(bytesRead)
        }
    }

    private fun safeTarget(root: File, relative: String): File {
        val normalized = relative
            .removePrefix("./")
            .replace('\\', '/')
        if (normalized.isBlank() || normalized == ".") return root
        check(!normalized.startsWith('/')) {
            "RootFS 包含非法路径：$relative"
        }
        val segments = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        check(segments.none { it == ".." }) { "RootFS 包含非法路径：$relative" }
        // 这里只做词法路径校验，不能 canonicalize。
        // RootFS 内含 /bin -> /usr/bin 等符号链接，解析链接会错误地逃出解压根目录。
        return segments.fold(root) { parent, segment -> File(parent, segment) }
    }

    private fun readBlock(input: InputStream, data: ByteArray): Boolean {
        var offset = 0
        while (offset < data.size) {
            val count = input.read(data, offset, data.size - offset)
            if (count < 0) return offset != 0
            offset += count
        }
        return true
    }

    private fun text(data: ByteArray, offset: Int, length: Int) =
        data.copyOfRange(offset, offset + length).takeWhile { it != 0.toByte() }
            .toByteArray().toString(Charsets.UTF_8)

    private fun octal(data: ByteArray, offset: Int, length: Int): Long =
        text(data, offset, length).trim().ifEmpty { "0" }.toLong(8)

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, bytes: Long) {
        var remaining = bytes
        val buffer = ByteArray(64 * 1024)
        while (remaining > 0) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            check(count >= 0) { "RootFS 包不完整" }
            output.write(buffer, 0, count)
            remaining -= count
        }
    }

    private fun skipFully(input: InputStream, bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) remaining -= skipped else check(input.read() >= 0) { "RootFS 包不完整" }.also { remaining-- }
        }
    }
}