package com.java.myapplication

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.doro.terminal.shell.linux.LinuxContainerManager
import com.doro.terminal.shell.x11.LorieX11Runtime
import com.doro.terminal.shell.x11.X11RuntimeManager
import com.termux.x11.LorieView
import com.termux.x11.input.EmbeddedInputController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

internal val XAccent = Color(0xFF6C4EB3)
internal val XBackground = Color(0xFFFFF8FF)
internal val XText = Color(0xFF49454F)

@Composable
fun TerminalSettingsPage(onBack: () -> Unit, onMorePermissions: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { android.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    Box(Modifier.fillMaxSize().background(XBackground), contentAlignment = Alignment.TopCenter) {
    Column(
        Modifier.fillMaxSize().widthIn(max = 720.dp).padding(top = 48.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", Modifier.clickable(onClick = onBack).padding(12.dp), color = XAccent, fontSize = 38.sp)
            Text("终端设置", color = XText, fontSize = 25.sp)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        ) {
            SettingSection("GPU 加速")
            SwitchSetting("Turnip + Zink GPU 加速", "gpuAcceleration", true, prefs)

            SettingSection("X11 显示输出")
            ChoiceSetting("分辨率模式", "displayResolutionMode", listOf("原生" to "native", "缩放" to "scaled", "精确" to "exact", "自定义" to "custom"), prefs)
            SliderSetting("显示缩放", "displayScale", 30f, 300f, 100f, prefs)
            ChoiceSetting("精确分辨率", "displayResolutionExact", listOf("1280×720" to "1280x720", "1280×1024" to "1280x1024", "1600×900" to "1600x900", "1920×1080" to "1920x1080"), prefs)
            TextSetting("自定义分辨率", "displayResolutionCustom", "1280x1024", prefs)
            ChoiceSetting("过滤模式", "displayFilteringMode", listOf("最近邻" to "nearest", "双线性" to "bilinear"), prefs)
            SwitchSetting("自动调整分辨率", "adjustResolution", false, prefs)
            SwitchSetting("拉伸显示", "displayStretch", false, prefs)
            SwitchSetting("重新播种画面", "Reseed", true, prefs)
            SwitchSetting("画中画", "PIP", false, prefs)
            SwitchSetting("全屏", "fullscreen", true, prefs)
            ChoiceSetting("强制方向", "forceOrientation", listOf("自动" to "auto", "横屏" to "landscape", "竖屏" to "portrait"), prefs)
            SwitchSetting("延伸到挖孔区域", "hideCutout", true, prefs)
            SwitchSetting("保持屏幕常亮", "keepScreenOn", true, prefs)

            SettingSection("指针输入")
            ChoiceSetting("触摸模式", "touchMode", listOf("触摸板" to "1", "模拟触摸" to "2", "直接触摸" to "3"), prefs)
            SwitchSetting("缩放触摸板", "scaleTouchpad", true, prefs)
            SwitchSetting("显示手写笔按键", "showStylusClickOverride", false, prefs)
            SwitchSetting("手写笔作为鼠标", "stylusIsMouse", false, prefs)
            SwitchSetting("显示鼠标助手", "showMouseHelper", false, prefs)
            SwitchSetting("捕获鼠标指针", "pointerCapture", false, prefs)
            ChoiceSetting("捕获指针变换", "transformCapturedPointer", listOf("无" to "no", "顺时针" to "c", "逆时针" to "cc", "倒置" to "ud", "自动" to "at"), prefs)
            SliderSetting("指针速度", "capturedPointerSpeedFactor", 1f, 300f, 100f, prefs)
            SwitchSetting("轻触移动", "tapToMove", false, prefs)
            SwitchSetting("忽略游戏手柄", "ignoreGamepadEvents", false, prefs)

            SettingSection("键盘输入")
            SwitchSetting("显示附加键盘", "showAdditionalKbd", true, prefs)
            SwitchSetting("外接键盘时显示输入法", "showIMEWhileExternalConnected", true, prefs)
            SwitchSetting("优先使用扫描码", "preferScancodes", false, prefs)
            SwitchSetting("硬件键盘兼容模式", "hardwareKbdScancodesWorkaround", true, prefs)
            SwitchSetting("捕获 DeX Meta 键", "dexMetaKeyCapture", false, prefs)
            SwitchSetting("Esc 暂停按键拦截", "pauseKeyInterceptingWithEsc", false, prefs)
            SwitchSetting("过滤 Windows 键", "filterOutWinkey", false, prefs)
            SwitchSetting("强制字符输入", "enforceCharBasedInput", false, prefs)

            SettingSection("其他行为")
            SwitchSetting("同步剪贴板", "clipboardEnable", true, prefs)
            SwitchSetting("分开保存副屏设置", "storeSecondaryDisplayPreferencesSeparately", false, prefs)
            SwitchSetting("附加键适配高度", "adjustHeightForEK", true, prefs)
            SwitchSetting("使用 Termux 附加键行为", "useTermuxEKBarBehaviour", false, prefs)
            SliderSetting("附加键透明度", "opacityEKBar", 1f, 100f, 100f, prefs)
            ChoiceSetting("上滑动作", "swipeUpAction", listOf("无动作" to "no action", "显示键盘" to "toggle soft keyboard", "切换附加键" to "toggle additional key bar"), prefs)
            ChoiceSetting("下滑动作", "swipeDownAction", listOf("无动作" to "no action", "显示键盘" to "toggle soft keyboard", "切换附加键" to "toggle additional key bar"), prefs)
            ChoiceSetting("返回键动作", "backButtonAction", listOf("显示键盘" to "toggle soft keyboard", "退出桌面" to "exit", "无动作" to "no action"), prefs)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onMorePermissions, modifier = Modifier.fillMaxWidth()) {
                Text("更多权限")
            }
            Spacer(Modifier.height(28.dp))
        }
    }
    }
}

@Composable
private fun SettingSection(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, color = XAccent, fontSize = 19.sp)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun SwitchSetting(
    title: String,
    key: String,
    default: Boolean,
    prefs: android.content.SharedPreferences,
    enabled: Boolean = true
) {
    var checked by remember(key) { mutableStateOf(prefs.getBoolean(key, default)) }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) {
            checked = !checked
            prefs.edit().putBoolean(key, checked).apply()
        }.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, enabled = enabled, onCheckedChange = {
            checked = it
            prefs.edit().putBoolean(key, it).apply()
        })
        Text(title, color = XText, fontSize = 15.sp)
    }
}

@Composable
private fun ChoiceSetting(
    title: String,
    key: String,
    choices: List<*>,
    prefs: android.content.SharedPreferences
) {
    val options = choices.map { option ->
        if (option is Pair<*, *>) option.first.toString() to option.second.toString()
        else option.toString() to option.toString()
    }
    var value by remember(key) { mutableStateOf(prefs.getString(key, options.first().second) ?: options.first().second) }
    Row(
        Modifier.fillMaxWidth().clickable {
            val index = options.indexOfFirst { it.second == value }.coerceAtLeast(0)
            value = options[(index + 1) % options.size].second
            prefs.edit().putString(key, value).apply()
        }.padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(title, color = XText, fontSize = 15.sp)
        Text(options.firstOrNull { it.second == value }?.first ?: value, color = XAccent, fontSize = 15.sp)
    }
}

@Composable
private fun TextSetting(
    title: String,
    key: String,
    default: String,
    prefs: android.content.SharedPreferences
) {
    var value by remember(key) { mutableStateOf(prefs.getString(key, default) ?: default) }
    OutlinedTextField(
        value = value,
        onValueChange = { next ->
            value = next
            if (Regex("^[1-9]\\d{1,4}x[1-9]\\d{1,4}$").matches(next)) {
                prefs.edit().putString(key, next).apply()
            }
        },
        label = { Text(title) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    )
}

@Composable
private fun SliderSetting(
    title: String,
    key: String,
    min: Float,
    max: Float,
    default: Float,
    prefs: android.content.SharedPreferences
) {
    var value by remember(key) { mutableFloatStateOf(prefs.getInt(key, default.toInt()).toFloat()) }
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title, color = XText, fontSize = 15.sp)
            Text(value.toInt().toString(), color = XAccent, fontSize = 15.sp)
        }
        Slider(value, { value = it }, valueRange = min..max, onValueChangeFinished = {
            prefs.edit().putInt(key, value.toInt()).apply()
        })
    }
}

@Composable
fun X11DesktopPage(autoStartDesktop: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val runtime = remember { X11RuntimeManager.get(context).runtime as LorieX11Runtime }
    val container = remember { LinuxContainerManager(context) }
    val prefs = remember { android.preference.PreferenceManager.getDefaultSharedPreferences(context) }
    if (!prefs.getBoolean("doroTrueFullscreenV2", false)) {
        prefs.edit()
            .putBoolean("fullscreen", true)
            .putBoolean("hideCutout", true)
            .putString("forceOrientation", "landscape")
            .putBoolean("doroTrueFullscreenV2", true)
            .apply()
    }
    val activity = context as? MainActivity
    var lorieView by remember { mutableStateOf<LorieView?>(null) }
    var inputController by remember { mutableStateOf<EmbeddedInputController?>(null) }
    var desktopProcess by remember { mutableStateOf<Process?>(null) }
    var desktopStarting by remember { mutableStateOf(false) }
    var serverReady by remember { mutableStateOf(false) }
    var showBootSplash by remember { mutableStateOf(autoStartDesktop) }
    var error by remember { mutableStateOf<String?>(null) }

    val handleDesktopBack: () -> Unit = {
        when (prefs.getString("backButtonAction", "toggle soft keyboard")) {
            "exit" -> onBack()
            "no action" -> Unit
            else -> lorieView?.let { view ->
                view.requestFocus()
                context.getSystemService(InputMethodManager::class.java)
                    ?.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    BackHandler { handleDesktopBack() }

    LaunchedEffect(runtime) {
        try {
            withContext(Dispatchers.IO) { runtime.start() }
            serverReady = true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            error = failure.stackTraceToString()
        }
    }

    LaunchedEffect(autoStartDesktop) {
        if (autoStartDesktop) {
            delay(2500)
            showBootSplash = false
        }
    }

    LaunchedEffect(lorieView, serverReady, autoStartDesktop) {
        if (!autoStartDesktop || !serverReady || lorieView == null || desktopProcess != null || desktopStarting) return@LaunchedEffect
        desktopStarting = true
        try {
            val process = withContext(Dispatchers.IO) {
                container.startDesktop(runtime.display, prefs.getBoolean("gpuAcceleration", true))
            }
            desktopProcess = process
            val output = withContext(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val exitCode = withContext(Dispatchers.IO) { process.waitFor() }
            if (desktopProcess === process) {
                error = if (output.isBlank()) {
                    "XFCE会话已退出，退出码：$exitCode"
                } else {
                    "XFCE会话已退出，退出码：$exitCode\n$output"
                }
                desktopProcess = null
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            desktopStarting = false
            error = failure.message ?: "XFCE桌面启动失败"
        }
    }

    DisposableEffect(runtime, prefs) {
        activity?.setDesktopBackAction(handleDesktopBack)
        val applyWindowPreferences = {
            val window = activity?.window
            activity?.updateDesktopWindowState(true, prefs.getBoolean("PIP", false))
            window?.setSoftInputMode(
                if (prefs.getBoolean("Reseed", true)) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                else WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            )
            if (prefs.getBoolean("keepScreenOn", true)) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window?.attributes = window?.attributes?.also {
                    it.layoutInDisplayCutoutMode = if (prefs.getBoolean("hideCutout", true))
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    else WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                }
            }
            val fullscreen = prefs.getBoolean("fullscreen", true)
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, !fullscreen)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (fullscreen) hide(WindowInsetsCompat.Type.systemBars())
                    else show(WindowInsetsCompat.Type.systemBars())
                }
            }
            when (prefs.getString("forceOrientation", "landscape")) {
                "landscape" -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "portrait" -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                else -> activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }

        }
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            inputController?.reloadPreferences()
            applyWindowPreferences()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        applyWindowPreferences()
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            activity?.setDesktopBackAction(null)
            activity?.updateDesktopWindowState(false, false)
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, true)
                WindowCompat.getInsetsController(window, window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                activity?.window?.attributes = activity?.window?.attributes?.also {
                    it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            desktopProcess?.destroy()
            desktopProcess = null
            container.stopDesktopServices()
            inputController?.close()
            inputController = null
            runtime.detachSurface()
            lorieView = null
        }
    }
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (error != null) {
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text(error.orEmpty(), color = Color.White, fontSize = 12.sp)
                Spacer(Modifier.height(18.dp))
                Button(onClick = onBack) { Text("返回终端") }
            }
        } else if (!serverReady) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("正在启动X11…", color = Color.White, fontSize = 17.sp)
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    LorieView(ctx).also { view ->
                        lorieView = view
                        runCatching {
                            val hostActivity = requireNotNull(activity) { "X11输入需要Activity宿主" }
                            inputController = EmbeddedInputController(hostActivity, view)
                            runtime.bindView(view)
                            inputController?.reloadPreferences()
                            view.requestFocus()
                        }.onFailure { error = it.message ?: "X11 启动失败" }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            if (showBootSplash) UbuntuBootSplash()
        }
    }
}

@Composable
private fun UbuntuBootSplash() {
    Column(
        Modifier.fillMaxSize().background(Color.Black),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(com.doro.terminal.shell.R.drawable.ic_ubuntu_large),
            contentDescription = "Ubuntu",
            modifier = Modifier.size(112.dp)
        )
        Spacer(Modifier.height(22.dp))
        Text("Ubuntu", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        Text("正在启动桌面", color = Color.White.copy(alpha = 0.72f), fontSize = 15.sp)
    }
}