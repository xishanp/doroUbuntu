package com.doro.terminal.shell.x11

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

class X11SurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    var runtime: X11Runtime? = null
        set(value) {
            field?.detachSurface()
            field = value
            if (holder.surface.isValid) attach(holder)
        }

    init {
        holder.addCallback(this)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun surfaceCreated(holder: SurfaceHolder) = attach(holder)

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        runtime?.resize(width, height, resources.displayMetrics.densityDpi)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        runtime?.detachSurface()
    }

    private fun attach(holder: SurfaceHolder) {
        if (!holder.surface.isValid) return
        runtime?.attachSurface(
            holder.surface,
            width.coerceAtLeast(1),
            height.coerceAtLeast(1),
            resources.displayMetrics.densityDpi
        )
    }
}