package com.doro.terminal.shell.x11

import android.content.Context

/**
 * 全应用唯一的 X11 生命周期管理器。
 * 切换终端、桌面页面不会重建 X server。
 */
class X11RuntimeManager private constructor(context: Context) {
    val runtime: X11Runtime = LorieX11Runtime(context, displayNumber = 0)

    @Synchronized
    fun ensureStarted(): X11Runtime {
        if (runtime.state == X11State.STOPPED || runtime.state == X11State.FAILED) {
            runtime.start()
        }
        return runtime
    }

    fun shutdown() = runtime.stop()

    companion object {
        @Volatile
        private var instance: X11RuntimeManager? = null

        fun get(context: Context): X11RuntimeManager {
            requireNotNull(context.applicationContext)
            return instance ?: synchronized(this) {
                instance ?: X11RuntimeManager(context.applicationContext).also { instance = it }
            }
        }
    }
}