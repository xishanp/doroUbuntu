package com.doro.terminal.shell.linux

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.Closeable

internal class ContainerAudioBridge(private val pipe: File) : Closeable {
    @Volatile private var running = false
    private var worker: Thread? = null

    @Synchronized
    fun start() {
        if (running && worker?.isAlive == true) return
        running = false
        worker = null
        pipe.parentFile?.mkdirs()
        if (!pipe.exists()) {
            try {
                Os.mkfifo(pipe.path, 0x1B6)
            } catch (error: ErrnoException) {
                if (error.errno != OsConstants.EEXIST) throw error
            }
        }
        running = true
        worker = Thread(::playLoop, "doro-container-audio").apply {
            isDaemon = true
            start()
        }
    }

    @Synchronized
    override fun close() {
        running = false
        worker?.interrupt()
        runCatching { FileOutputStream(pipe).use { it.write(ByteArray(4)) } }
        worker?.join(1_500L)
        worker = null
        runCatching { pipe.delete() }
    }

    private fun playLoop() {
        val sampleRate = 44100
        val minimum = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .build()
            )
            .setBufferSizeInBytes(minimum * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val buffer = ByteArray(minimum)
        try {
            track.play()
            while (running) {
                FileInputStream(pipe).use { input ->
                    while (running) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) track.write(buffer, 0, count, AudioTrack.WRITE_BLOCKING)
                    }
                }
            }
        } finally {
            track.pause()
            track.flush()
            track.release()
        }
    }
}
