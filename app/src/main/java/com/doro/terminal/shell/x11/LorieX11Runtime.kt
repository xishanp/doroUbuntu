package com.doro.terminal.shell.x11

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.DeadObjectException
import android.os.IBinder
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.os.RemoteException
import android.view.Surface
import com.termux.x11.LorieView
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LorieX11Runtime(
    context: Context,
    private val displayNumber: Int = 0
) : X11Runtime {
    private val appContext = context.applicationContext
    private var view: LorieView? = null
    private var service: IBinder? = null
    private var bound = false
    private var connection: ParcelFileDescriptor? = null
    private var activeConnection: ServiceConnection? = null

    override val display: String = ":$displayNumber"
    override var state: X11State = X11State.STOPPED
        private set

    private fun transact(code: Int, write: (Parcel) -> Unit = {}): Parcel {
        val binder = requireNotNull(service) { "X11服务未连接" }
        check(binder.isBinderAlive && binder.pingBinder()) { "X11服务进程已退出" }
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(X11_DESCRIPTOR)
            write(data)
            check(binder.transact(code, data, reply, 0)) { "X11服务通信失败" }
            reply.readException()
            return reply
        } catch (failure: DeadObjectException) {
            reply.recycle()
            service = null
            state = X11State.FAILED
            throw IllegalStateException("X11服务启动时崩溃，请查看 X11ServerService 日志", failure)
        } catch (failure: RemoteException) {
            reply.recycle()
            service = null
            state = X11State.FAILED
            throw IllegalStateException("X11服务通信异常", failure)
        } catch (failure: Throwable) {
            reply.recycle()
            throw failure
        } finally {
            data.recycle()
        }
    }

    override fun start() {
        synchronized(this) {
            if (state == X11State.RUNNING_HEADLESS || state == X11State.RUNNING_ATTACHED) return
            check(state != X11State.STARTING) { "X11正在启动" }
            state = X11State.STARTING
        }
        val latch = CountDownLatch(1)
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = binder
                latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
                connection = null
                state = X11State.FAILED
            }
        }
        activeConnection = serviceConnection
        try {
            bound = appContext.bindService(
                Intent(appContext, X11ServerService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
            check(bound) { "无法绑定X11服务" }
            check(latch.await(5, TimeUnit.SECONDS)) { "X11服务连接超时" }
            val reply = transact(X11_TRANSACTION_START) {
                it.writeStringArray(arrayOf(display, "-ac", "-nolisten", "tcp"))
            }
            val started = reply.readInt() != 0
            reply.recycle()
            check(started) { "X11 Server启动失败" }
            state = X11State.RUNNING_HEADLESS
        } catch (failure: Throwable) {
            state = X11State.FAILED
            if (bound) runCatching { appContext.unbindService(serviceConnection) }
            bound = false
            activeConnection = null
            service = null
            throw failure
        }
    }

    @Synchronized
    fun bindView(target: LorieView) {
        if (state == X11State.STOPPED || state == X11State.FAILED) start()
        view = target
        target.setSurfaceLifecycleListener(object : LorieView.SurfaceLifecycleListener {
            override fun onSurfaceAvailable(surface: Surface, width: Int, height: Int) {
                when (state) {
                    X11State.RUNNING_HEADLESS, X11State.RUNNING_ATTACHED ->
                        attachSurface(surface, width, height, target.resources.displayMetrics.densityDpi)
                    else -> Unit
                }
            }

            override fun onSurfaceDestroyed() {
                detachSurface()
            }
        })
        if (!LorieView.connected()) {
            var descriptor: ParcelFileDescriptor? = null
            for (attempt in 0 until 40) {
                val reply = transact(X11_TRANSACTION_CONNECTION)
                descriptor = if (reply.readInt() != 0) ParcelFileDescriptor.CREATOR.createFromParcel(reply) else null
                reply.recycle()
                if (descriptor != null) break
                Thread.sleep(50)
            }
            connection = descriptor
            LorieView.connect(requireNotNull(connection) { "X11连接等待超时" }.detachFd())
        }
        check(LorieView.connected()) { "X11客户端连接失败" }
        if (target.holder.surface.isValid) {
            state = X11State.ATTACHING
            try {
                target.attachRuntimeSurface(
                    target.holder.surface,
                    target.width.coerceAtLeast(1),
                    target.height.coerceAtLeast(1)
                )
                state = X11State.RUNNING_ATTACHED
            } catch (failure: Throwable) {
                state = X11State.RUNNING_HEADLESS
                throw failure
            }
        }
    }

    @Synchronized
    override fun attachSurface(surface: Surface, width: Int, height: Int, densityDpi: Int) {
        check(state == X11State.RUNNING_HEADLESS || state == X11State.RUNNING_ATTACHED)
        state = X11State.ATTACHING
        try {
            requireNotNull(view) { "X11 SurfaceView尚未绑定" }.attachRuntimeSurface(surface, width, height)
            state = X11State.RUNNING_ATTACHED
        } catch (failure: Throwable) {
            state = X11State.RUNNING_HEADLESS
            throw failure
        }
    }
override fun resize(width: Int, height: Int, densityDpi: Int) {
        if (state != X11State.RUNNING_ATTACHED) return
        val target = view ?: return
        val surface = target.holder.surface
        if (surface.isValid) target.attachRuntimeSurface(surface, width, height)
    }

    @Synchronized
    override fun detachSurface() {
        if (state != X11State.RUNNING_ATTACHED && state != X11State.ATTACHING) return
        state = X11State.DETACHING
        try {
            view?.detachRuntimeSurface()
        } finally {
            state = X11State.RUNNING_HEADLESS
        }
    }

    @Synchronized
    override fun stop() {
        detachSurface()
        connection?.close()
        connection = null
        view?.setSurfaceLifecycleListener(null)
        view = null
        activeConnection?.let { if (bound) runCatching { appContext.unbindService(it) } }
        activeConnection = null
        bound = false
        service = null
        state = X11State.STOPPED
    }
}