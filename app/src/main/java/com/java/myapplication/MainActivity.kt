package com.java.myapplication

import android.Manifest
import android.content.Intent
import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Rational
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
// X11 remains embedded in the Compose desktop page.

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.doro.terminal.shell.R
import com.doro.terminal.shell.linux.InitializationProgress
import com.doro.terminal.shell.linux.InstallComponent
import com.doro.terminal.shell.linux.LinuxAccount
import com.doro.terminal.shell.linux.LinuxContainerManager
import com.doro.terminal.shell.linux.LinuxProcessSession
import com.doro.terminal.shell.linux.SetupCatalog
import com.doro.terminal.shell.linux.TerminalLaunchSpec
// X11 runtime imports live in X11Screens.kt.

import com.java.myapplication.ui.theme.MyApplicationTheme
import com.termux.x11.LorieView
// No external X11 activity launch dependencies.

private val Accent = Color(0xFF6C4EB3)
private val PageBackground = Color(0xFFFFF8FF)
private val MainText = Color(0xFF49454F)

private enum class SetupPage { PERMISSIONS, WELCOME, ACCOUNT, INSTALLING, COMPLETE, TERMINAL, SETTINGS, MORE_PERMISSIONS, DESKTOP }

class MainActivity : ComponentActivity() {
    private lateinit var containerManager: LinuxContainerManager
    @Volatile private var desktopVisible = false
    @Volatile private var pipEnabled = false

    @Volatile private var desktopBackAction: (() -> Unit)? = null

    fun setDesktopBackAction(action: (() -> Unit)?) {
        desktopBackAction = action
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (desktopVisible && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                desktopBackAction?.invoke()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    fun updateDesktopWindowState(visible: Boolean, allowPip: Boolean) {
        desktopVisible = visible
        pipEnabled = allowPip
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder
                    .setAutoEnterEnabled(visible && allowPip)
                    .setSeamlessResizeEnabled(true)
            }
            setPictureInPictureParams(builder.build())
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (desktopVisible && pipEnabled && Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        containerManager = LinuxContainerManager(this)
        setContent {
            MyApplicationTheme(dynamicColor = false) {
                SetupApp(
                    initialized = containerManager.isInitialized(),
                    savedUsername = containerManager.loadConfig()?.username
                        ?: containerManager.loadInitializationState()?.username.orEmpty(),
                    interruptedMessage = containerManager.loadInitializationState()?.let {
                        it.failedMessage ?: "检测到未完成初始化，请输入密码后继续"
                    },
                    initialize = ::initializeLinux
                )
            }
        }
    }

    private fun initializeLinux(
        username: String,
        password: String,
        components: Set<InstallComponent>,
        onProgress: (InitializationProgress) -> Unit,
        onComplete: () -> Unit,
        onFailure: (String) -> Unit
    ) {
        Thread {
            runCatching {
                containerManager.initialize(username, password.toCharArray(), components) { progress ->
                    runOnUiThread { onProgress(progress) }
                }
            }.onSuccess {
                runOnUiThread(onComplete)
            }.onFailure { error ->
                runOnUiThread { onFailure(error.message ?: "初始化失败") }
            }
        }.start()
    }
}

@Composable
private fun SetupApp(
    initialized: Boolean,
    savedUsername: String,
    interruptedMessage: String?,
    initialize: (
        String,
        String,
        Set<InstallComponent>,
        (InitializationProgress) -> Unit,
        () -> Unit,
        (String) -> Unit
    ) -> Unit
) {
    val context = LocalContext.current
    val hasFileAccess = remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
            else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasFileAccess.value = result.values.all { it }
    }
    var page by remember {
        mutableStateOf(
            when {
                initialized -> SetupPage.TERMINAL
                interruptedMessage != null -> SetupPage.ACCOUNT
                else -> SetupPage.WELCOME
            }
        )
    }
    var username by remember { mutableStateOf(savedUsername) }
    var password by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var progressMessage by remember { mutableStateOf("准备初始化") }
    var error by remember { mutableStateOf(interruptedMessage) }
    val terminalWorkspace = remember { TerminalWorkspace() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, page) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && page == SetupPage.PERMISSIONS) {
                hasFileAccess.value = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
                else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (hasFileAccess.value) page = SetupPage.ACCOUNT
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = PageBackground
    ) {
        when (page) {
            SetupPage.PERMISSIONS -> PermissionPage(
                onRequest = {
                    if (Build.VERSION.SDK_INT >= 30) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    } else {
                        storagePermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            )
                        )
                    }
                },
                onCheck = {
                    hasFileAccess.value = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
                    else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasFileAccess.value) page = SetupPage.ACCOUNT
                }
            )
            SetupPage.WELCOME -> WelcomePage {
                page = if (hasFileAccess.value) SetupPage.ACCOUNT else SetupPage.PERMISSIONS
            }
            SetupPage.ACCOUNT -> AccountPage(
                initialUsername = username,
                error = error,
                onStart = { user, pass ->
                    username = user
                    password = pass
                    error = null
                progress = 5
                progressMessage = "正在准备 Ubuntu 24.04"
                page = SetupPage.INSTALLING
                initialize(
                    username,
                    password,
                    SetupCatalog.resolveSelection(emptySet()),
                    { state ->
                        progress = state.percent
                        progressMessage = state.message
                    },
                    {
                        password = ""
                        progress = 100
                        page = SetupPage.COMPLETE
                    },
                    { message ->
                        password = ""
                        error = message
                        page = SetupPage.ACCOUNT
                    }
                )
                }
            )
SetupPage.INSTALLING -> InstallingPage(progress, progressMessage)
            SetupPage.COMPLETE -> CompletePage { page = SetupPage.TERMINAL }
            SetupPage.TERMINAL -> TerminalPage(
                username = username.ifBlank { "ubuntu" },
                manager = LinuxContainerManager(LocalContext.current),
                workspace = terminalWorkspace,
                onSettings = { page = SetupPage.SETTINGS },
                onOpenDisplay = { page = SetupPage.DESKTOP }
            )
            SetupPage.SETTINGS -> TerminalSettingsPage(
                onBack = { page = SetupPage.TERMINAL },
                onMorePermissions = { page = SetupPage.MORE_PERMISSIONS }
            )
            SetupPage.MORE_PERMISSIONS -> MorePermissionsPage { page = SetupPage.SETTINGS }
            SetupPage.DESKTOP -> X11DesktopPage(autoStartDesktop = true) { page = SetupPage.TERMINAL }
        }
            }
}

@Composable
private fun PageFrame(
    currentStep: Int,
    compact: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val shortScreen = maxHeight < 700.dp
        val tablet = maxWidth >= 600.dp
        val landscape = maxWidth > maxHeight
        val horizontalPadding = if (tablet) 40.dp else 24.dp
        val contentWidth = when {
            maxWidth >= 1000.dp -> 720.dp
            tablet -> 600.dp
            else -> maxWidth
        }
        val topPadding = when {
            landscape -> maxHeight * 0.06f
            tablet -> maxHeight * 0.10f
            shortScreen -> maxHeight * 0.10f
            else -> maxHeight * 0.14f
        }
        val bottomPadding = if (shortScreen) 18.dp else 28.dp
        val headerGap = if (shortScreen) 18.dp else 26.dp
        Column(
            modifier = Modifier
                .width(contentWidth)
                .height(maxHeight)
                .align(Alignment.TopCenter)
                .padding(horizontal = horizontalPadding)
                .padding(top = topPadding, bottom = bottomPadding),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = ">_",
                modifier = Modifier.fillMaxWidth(),
                color = Accent,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = if (shortScreen) 44.sp else 54.sp,
                textAlign = TextAlign.Start
            )
            Spacer(Modifier.height(headerGap))
            content()
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(5) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(
                        color = if (index <= currentStep) Accent else Color(0xFFE9DDF7),
                        shape = RoundedCornerShape(50)
                    )
            )
        }
    }
}

@Composable
private fun PermissionPage(onRequest: () -> Unit, onCheck: () -> Unit) {
    PageFrame(currentStep = 1) {
        Text("获取文件权限", fontSize = 30.sp, color = MainText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Text(
            "这是部署前的必要步骤。\n\n授权后才能准备系统文件，并将 /sdcard 挂载到 Ubuntu。\n应用不会主动读取无关文件。",
            color = MainText,
            fontSize = 18.sp,
            lineHeight = 28.sp
        )
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onCheck, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Accent)) {
                Text("检查授权")
            }
            Button(onClick = onRequest, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                Text("授权所有文件")
            }
        }
    }
}

@Composable
private fun WelcomePage(onContinue: () -> Unit) {
    PageFrame(currentStep = 0, compact = true) {
        Text(
            "doroUbuntu",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 32.sp,
            color = MainText,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Android 上的 Ubuntu 24.04 桌面",
            modifier = Modifier.fillMaxWidth(),
            color = MainText.copy(alpha = 0.78f),
            fontSize = 17.sp,
            lineHeight = 24.sp,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(30.dp))
        Text(
            "XFCE 桌面  ·  Turnip + Zink  ·  开发环境",
            modifier = Modifier.fillMaxWidth(),
            color = Accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 24.sp,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "离线一键部署，首次安装约需 30 分钟。\n请预留至少 10 GB 可用空间。",
            modifier = Modifier.fillMaxWidth(),
            color = MainText,
            fontSize = 16.sp,
            lineHeight = 25.sp,
            textAlign = TextAlign.Start
        )
        Spacer(Modifier.weight(1f))
        PrimaryButton("开始部署", onContinue)
    }
}

@Composable
private fun AccountPage(
    initialUsername: String,
    error: String?,
    onStart: (String, String) -> Unit
) {
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    PageFrame(currentStep = 1) {
        Text("创建 Linux 用户", fontSize = 30.sp, color = MainText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(24.dp))
        Text("该用户将作为默认登录用户。", color = MainText, fontSize = 17.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it.trim() },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("用户名") },
            singleLine = true
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = confirmation,
            onValueChange = { confirmation = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("确认密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        val shownError = localError ?: error
        if (shownError != null) {
            Spacer(Modifier.height(12.dp))
            Text(shownError, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("下一步") {
            localError = when {
                !LinuxAccount.isValidUsername(username) -> "用户名格式无效"
                password.length < 6 -> "密码至少需要六位"
                password != confirmation -> "两次输入的密码不同"
                else -> null
            }
            if (localError == null) onStart(username, password)
        }
    }
}
@Composable
private fun InstallingPage(progress: Int, message: String) {
    val view = LocalView.current
    DisposableEffect(view) {
        val previous = view.keepScreenOn
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = previous }
    }
    PageFrame(currentStep = 3) {
        Text("正在部署系统", fontSize = 30.sp, color = MainText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(26.dp))
        Text(
            "完整部署通常需要约 30 分钟，具体时间取决于设备性能。",
            color = MainText,
            fontSize = 19.sp,
            lineHeight = 29.sp
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "期间请保持应用在前台，不要清理后台、锁屏或强制退出。系统会持续监测安装任务；若长时间没有响应，将自动停止并返回明确错误。",
            color = MainText,
            fontSize = 17.sp,
            lineHeight = 27.sp
        )
        Spacer(Modifier.height(30.dp))
        Text("当前任务", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MainText, fontSize = 17.sp, lineHeight = 26.sp)
        Spacer(Modifier.weight(1f))
        Text("正在安装，请耐心等待", color = MainText, fontSize = 15.sp)
    }
}

@Composable
private fun CompletePage(onEnter: () -> Unit) {
    PageFrame(currentStep = 4) {
        Text("完成", fontSize = 30.sp, color = MainText, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(26.dp))
        Text("Ubuntu 已部署完成。", color = MainText, fontSize = 19.sp)
        Spacer(Modifier.height(18.dp))
        Text("XFCE 与已选组件已配置。", color = MainText, fontSize = 17.sp)
        Spacer(Modifier.weight(1f))
        PrimaryButton("进入 Ubuntu", onEnter)
    }
}

@Composable
private fun TerminalPage(
    username: String,
    manager: LinuxContainerManager,
    workspace: TerminalWorkspace,
    onSettings: () -> Unit,
    onOpenDisplay: () -> Unit
) {
    val context = LocalContext.current
    val sessions = workspace.sessions
    val activeSession = workspace.activeSession
    val terminalViews = workspace.terminalViews
    val launchSpec = remember(manager) { runCatching { manager.terminalLaunchSpec() } }
    val darkTheme = isSystemInDarkTheme()
    val toolbarColor = if (darkTheme) Color(0xFF111111) else Color.White
    val activeTabColor = if (darkTheme) Color(0xFF292929) else Color(0xFFE5E5E5)
    val toolbarTextColor = if (darkTheme) Color(0xFFF2F2F2) else Color(0xFF3C3C3C)
 
    fun createTerminal(id: Int): DoroTerminalPane {
        return terminalViews.getOrPut(id) {
            val spec = launchSpec.getOrThrow()
            DoroTerminalPane(
                context,
                spec.shellPath,
                spec.workingDirectory,
                spec.arguments,
                spec.environment
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(toolbarColor)
            .statusBarsPadding()
            .imePadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(toolbarColor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sessions.forEach { session ->
                    Row(
                        modifier = Modifier
                            .width(132.dp)
                            .height(42.dp)
                            .background(
                                if (session == activeSession) activeTabColor
                                else Color.Transparent,
                                RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                            )
                            .clickable { workspace.activeSession = session }
                            .padding(horizontal = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "$username@localhost: ~",
                            modifier = Modifier.weight(1f),
                            color = toolbarTextColor,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "×",
                            modifier = Modifier
                                .clickable(enabled = sessions.size > 1) {
                                    terminalViews.remove(session)?.close()
                                    workspace.sessions = sessions.filterNot { it == session }
                                    if (activeSession == session) workspace.activeSession = workspace.sessions.first()
                                }
                                .padding(7.dp),
                            color = if (sessions.size > 1) Accent else Accent.copy(alpha = 0.45f),
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Light
                        )
                    }
                }
                IconButton(
                    onClick = {
                        val next = (sessions.maxOrNull() ?: 0) + 1
                        workspace.sessions = sessions + next
                        workspace.activeSession = next
                    },
                    modifier = Modifier.size(32.dp)
                ) { Text("+", color = Accent, fontSize = 23.sp, fontWeight = FontWeight.Light) }
            }
            Spacer(Modifier.width(4.dp))
            Row(
                modifier = Modifier.padding(end = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings_original),
                        contentDescription = "设置",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onOpenDisplay, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_display_original),
                        contentDescription = "桌面",
                        tint = Color.Unspecified,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
        if (launchSpec.isSuccess) {
            androidx.compose.runtime.key(activeSession) {
                AndroidView(
                    factory = { createTerminal(activeSession) },
                    modifier = Modifier.weight(1f).fillMaxWidth().background(Color(0xFF232323))
                )
            }
        } else {
            Box(
                Modifier.fillMaxSize().background(Color(0xFF232323)).padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Ubuntu 启动失败：${launchSpec.exceptionOrNull()?.message}",
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.End)
            .height(54.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Accent),
        shape = RoundedCornerShape(28.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 20.dp), fontSize = 16.sp)
    }
}
