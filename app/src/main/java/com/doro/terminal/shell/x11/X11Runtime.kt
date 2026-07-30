package com.doro.terminal.shell.x11

import android.view.Surface

/**
 * 应用内部 X11 Runtime 的稳定边界。
 * Linux 容器、桌面会话和 UI 只能依赖此接口。
 * Native 实现采用 Lorie/Termux:X11 的 X server 核心。
 */
interface X11Runtime {
    val state: X11State
    val display: String

    fun start()
    fun attachSurface(surface: Surface, width: Int, height: Int, densityDpi: Int)
    fun resize(width: Int, height: Int, densityDpi: Int)
    fun detachSurface()
    fun stop()
}

enum class X11State {
    STOPPED,
    STARTING,
    RUNNING_HEADLESS,
    ATTACHING,
    RUNNING_ATTACHED,
    DETACHING,
    FAILED
}