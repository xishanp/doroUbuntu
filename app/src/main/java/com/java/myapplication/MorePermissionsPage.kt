package com.java.myapplication

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import rikka.shizuku.Shizuku

@Composable
fun MorePermissionsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val shizukuListener = remember {
        Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
            if (requestCode == SHIZUKU_REQUEST_CODE) refresh++
        }
    }
    val binderListener = remember { Shizuku.OnBinderReceivedListener { refresh++ } }
    val binderDeadListener = remember { Shizuku.OnBinderDeadListener { refresh++ } }
    DisposableEffect(Unit) {
        Shizuku.addRequestPermissionResultListener(shizukuListener)
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        onDispose {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
            Shizuku.removeBinderReceivedListener(binderListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
        }
    }

    val shizukuRunning = refresh.let { runCatching { Shizuku.pingBinder() }.getOrDefault(false) }
    val shizukuGranted = shizukuRunning && runCatching {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
    val storageGranted = if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    val notificationGranted = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val batteryGranted = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(context.packageName)
    val pictureInPictureGranted = if (Build.VERSION.SDK_INT >= 26) {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_PICTURE_IN_PICTURE,
            android.os.Process.myUid(),
            context.packageName
        ) == AppOpsManager.MODE_ALLOWED
    } else true

    Box(Modifier.fillMaxSize().background(XBackground), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxSize().widthIn(max = 720.dp).padding(top = 48.dp)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("‹", Modifier.clickable(onClick = onBack).padding(12.dp), color = XAccent, fontSize = 38.sp)
            Text("更多权限", color = XText, fontSize = 25.sp)
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PermissionItem(
                "Shizuku授权",
                when {
                    shizukuGranted -> "已授权"
                    shizukuRunning -> "服务运行中，尚未授权"
                    else -> "未安装或服务未运行"
                }
            ) {
                when {
                    shizukuGranted -> Unit
                    shizukuRunning -> runCatching { Shizuku.requestPermission(SHIZUKU_REQUEST_CODE) }
                    else -> openShizuku(context)
                }
            }
            PermissionItem("所有文件访问", if (storageGranted) "已授权" else "未授权") {
                if (Build.VERSION.SDK_INT >= 30) openSetting(context, Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                else activity?.requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 2002)
            }
            PermissionItem("通知权限", if (notificationGranted) "已授权" else "未授权") {
                if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else openAppDetails(context)
            }
            PermissionItem("忽略电池优化", if (batteryGranted) "已授权" else "未授权") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:${context.packageName}")))
                }.onFailure { openAppDetails(context) }
            }
            PermissionItem("画中画权限", if (pictureInPictureGranted) "已授权" else "未授权") {
                if (Build.VERSION.SDK_INT >= 26) openSetting(context, "android.settings.PICTURE_IN_PICTURE_SETTINGS")
                else openAppDetails(context)
            }
            Button(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("刷新权限状态") }
            Spacer(Modifier.height(28.dp))
        }
        }
    }
}

@Composable
private fun PermissionItem(title: String, status: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = XText, fontSize = 17.sp)
            Text(status, color = if (status == "已授权") Color(0xFF2E7D32) else XAccent, fontSize = 14.sp)
        }
        Text("›", color = XAccent, fontSize = 28.sp)
    }
}

private fun openShizuku(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (launch != null) context.startActivity(launch)
    else context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://shizuku.rikka.app/zh-hans/download/")))
}

private fun openSetting(context: Context, action: String) {
    runCatching { context.startActivity(Intent(action, Uri.parse("package:${context.packageName}"))) }
        .onFailure { openAppDetails(context) }
}

private fun openAppDetails(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
}

private const val SHIZUKU_REQUEST_CODE = 2001
