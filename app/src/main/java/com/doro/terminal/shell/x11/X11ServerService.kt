package com.doro.terminal.shell.x11

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.system.Os
import android.util.Log
import com.termux.x11.CmdEntryPoint
import java.io.File

class X11ServerService : Service() {
    companion object {
        private const val TAG = "X11ServerService"
    }

    private var endpoint: CmdEntryPoint? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val binder = object : Binder() {
        init { attachInterface(null, X11_DESCRIPTOR) }

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            return when (code) {
                INTERFACE_TRANSACTION -> {
                    reply?.writeString(X11_DESCRIPTOR)
                    true
                }
                X11_TRANSACTION_START -> {
                    data.enforceInterface(X11_DESCRIPTOR)
                    runCatching {
                        val args = data.createStringArray() ?: emptyArray()
                        val tmpDir = File(filesDir, "linux/tmp").apply { mkdirs() }
                        File(tmpDir, ".X11-unix").apply {
                            mkdirs()
                            setReadable(true, false)
                            setWritable(true, false)
                            setExecutable(true, false)
                        }
                        val xkbDir = File(filesDir, "linux/distributions/ubuntu-24.04/rootfs/usr/share/X11/xkb")
                        check(xkbDir.isDirectory) { "X11键盘配置缺失：${xkbDir.path}" }
                        Os.setenv("TMPDIR", tmpDir.path, true)
                        Os.setenv("XKB_CONFIG_ROOT", xkbDir.path, true)
                        if (endpoint == null) {
                            var created: CmdEntryPoint? = null
                            var failure: Throwable? = null
                            val latch = java.util.concurrent.CountDownLatch(1)
                            mainHandler.post {
                                try { created = CmdEntryPoint(args) }
                                catch (error: Throwable) { failure = error }
                                finally { latch.countDown() }
                            }
                            check(latch.await(15, java.util.concurrent.TimeUnit.SECONDS)) { "X11主线程启动超时" }
                            failure?.let { throw it }
                            endpoint = requireNotNull(created)
                        }
                        true
                    }.fold(
                        onSuccess = { started ->
                            reply?.writeNoException()
                            reply?.writeInt(if (started) 1 else 0)
                        },
                        onFailure = { failure ->
                            Log.e(TAG, "X11 server start failed", failure)
                            reply?.writeException(IllegalStateException("X11 Server启动异常：${failure.message}", failure))
                        }
                    )
                    true
                }
                X11_TRANSACTION_CONNECTION -> {
                    data.enforceInterface(X11_DESCRIPTOR)
                    val fd = endpoint?.xConnection
                    reply?.writeNoException()
                    if (fd == null) reply?.writeInt(0) else {
                        requireNotNull(reply).writeInt(1)
                        fd.writeToParcel(reply, 0)
                    }
                    true
                }
else -> super.onTransact(code, data, reply, flags)
            }
        }
    }
    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        endpoint = null
        Log.i("X11ServerService", "X11 server process stopped")
        super.onDestroy()
    }
}