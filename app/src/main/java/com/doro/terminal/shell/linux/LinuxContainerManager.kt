package com.doro.terminal.shell.linux

import android.content.Context
import android.os.Build
import android.os.BatteryManager
import java.io.File
import java.security.MessageDigest
import java.util.Properties

class LinuxContainerManager(private val context: Context) {
    private val paths = ContainerPaths(context)
    private val audioBridge = ContainerAudioBridge(paths.audioPipe)
    // Bridge process state is shared in the companion object below.


    fun isInitialized(): Boolean = loadConfig()?.initialized == true

    fun loadConfig(): ContainerConfig? {
        if (!paths.configFile.isFile) return null
        return runCatching {
            val values = Properties().apply { paths.configFile.inputStream().use(::load) }
            ContainerConfig(
                distribution = values.getProperty("distribution", "ubuntu"),
                version = values.getProperty("version", "24.04"),
                username = values.getProperty("username"),
                initialized = values.getProperty("initialized").toBoolean()
            )
        }.getOrNull()
    }

    fun initialize(
        username: String,
        password: CharArray,
        components: Set<InstallComponent>,
        onProgress: (InitializationProgress) -> Unit = {}
    ): ContainerConfig {
        require(LinuxAccount.isValidUsername(username)) { "用户名格式无效" }
        require(password.size >= 6) { "密码至少需要六位" }
        check(Build.SUPPORTED_64_BIT_ABIS.contains("arm64-v8a")) { "当前仅支持 ARM64 设备" }
        check(!isInitialized()) { "Ubuntu 已完成初始化" }
        val requestedComponents = components.filter { it.supported }.toSet()
        var state = loadInitializationState()?.takeIf { it.username == username }
            ?: InitializationState(username, requestedComponents)
        val selected = state.components
        saveInitializationState(state)
        prepareLog()

        try {
            fun execute(step: InitializationStep, message: String, action: () -> Unit) {
                if (state.completedStep != null && state.completedStep!!.ordinal >= step.ordinal) return
                onProgress(progress(step, message))
                appendLog("START ${step.name}: $message")
                action()
                state = state.copy(completedStep = step, failedMessage = null)
                saveInitializationState(state)
                appendLog("DONE ${step.name}")
            }

            execute(InitializationStep.EXTRACT_SYSTEM, "正在解压系统") {
                installRuntime()
                if (!paths.rootfsReadyMarker.isFile) {
                    paths.rootfsDir.deleteRecursively()
                    verifyAsset(ROOTFS_ASSET, ROOTFS_SHA256)
                    context.assets.open(ROOTFS_ASSET).use { TarGzExtractor.extract(it, paths.rootfsDir) }
                    File(paths.rootfsDir, "etc/dpkg/dpkg.cfg.d/PaxHeaders").deleteRecursively()
                    File(paths.rootfsDir, "etc/dpkg/dpkg.cfg.d/PaxHeaders.0").deleteRecursively()
                    val x11Share = File(paths.rootfsDir, "usr/share/X11")
                    verifyAsset(XKB_ASSET, XKB_SHA256)
                    context.assets.open(XKB_ASSET).use { TarGzExtractor.extract(it, x11Share) }
                    paths.rootfsReadyMarker.writeText("ubuntu-24.04\n")
                }
            }
            execute(InitializationStep.CREATE_CONTAINER, "正在安装离线开发核心") {
                writeContainerFiles()
                installOfflineCore(onProgress)
                installCliExtras(onProgress)
                installFastfetch(onProgress)
                installFactoryApps(onProgress)
                installMesaGpuRuntime()
                installGpuTools(onProgress)
                installSparkStore(onProgress)
                installVendorApps(onProgress)
            }
            execute(InitializationStep.CREATE_USER, "正在创建用户") {
                runAsRoot(userCreationScript(username, password), "create-user")
            }
            execute(InitializationStep.CONFIGURE_SUDO, "正在配置 sudo") {
                runAsRoot("getent group sudo >/dev/null || groupadd sudo; usermod -aG sudo ${shell(username)} && mkdir -p /etc/sudoers.d && printf '%s ALL=(ALL:ALL) ALL\\n' ${shell(username)} > /etc/sudoers.d/${shell(username)} && chmod 440 /etc/sudoers.d/${shell(username)}", "configure-sudo")
            }
            execute(InitializationStep.INITIALIZE_ENVIRONMENT, "正在初始化环境") {
                runAsRoot(environmentScript(username), "initialize-environment")
            }
            execute(InitializationStep.SAVE_CONFIGURATION, "正在保存配置") { }
            return ContainerConfig(username = username).also {
                saveConfig(it)
                paths.initializationStateFile.delete()
                appendLog("INITIALIZATION COMPLETE")
            }
        } catch (failure: Throwable) {
            state = state.copy(failedMessage = failure.message)
            saveInitializationState(state)
            appendLog("FAILED: ${failure.stackTraceToString()}")
            throw IllegalStateException("${failure.message ?: "初始化失败"}\n日志：${paths.initializationLogFile.path}", failure)
        } finally {
            password.fill('\u0000')
        }
    }
    private fun ensureStartupMaintenance(username: String) {
        ensureFetchScript()
        if (!File(paths.rootfsDir, "usr/bin/fastfetch").isFile) {
            val packageFile = File(paths.rootfsDir, "tmp/$FASTFETCH_FILE")
            packageFile.parentFile?.mkdirs()
            verifyAsset(FASTFETCH_ASSET, FASTFETCH_SHA256)
            copyAsset(FASTFETCH_ASSET, packageFile)
            runAsRoot(
                "export DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1; dpkg --force-confdef --force-confold -i /tmp/$FASTFETCH_FILE && command -v fastfetch >/dev/null",
                "startup-fastfetch",
                timeoutMs = COMMAND_TIMEOUT_MS
            )
            packageFile.delete()
            paths.fastfetchReadyMarker.writeText("fastfetch-2.66.0\n")
        }
        ensureTerminalProfile(username)
    }

    fun loginCommand(config: ContainerConfig = requireNotNull(loadConfig())): List<String> {
        installRuntime()
        ensureGetifaddrsBridge()
        ensureStartupMaintenance(config.username)
        val script = "for gid in \$(id -G); do getent group \"\$gid\" >/dev/null || " +
            "printf 'android_gid_%s:x:%s:\\n' \"\$gid\" \"\$gid\" >> /etc/group; done; " +
            "exec /usr/bin/su - ${shell(config.username)}"
        return prootCommand("/bin/sh", "-c", script)
    }

    fun terminalLaunchSpec(config: ContainerConfig = requireNotNull(loadConfig())): TerminalLaunchSpec {
        val command = loginCommand(config)
        return TerminalLaunchSpec(
            shellPath = command.first(),
            workingDirectory = sessionWorkingDirectory(),
            arguments = command.toTypedArray(),
            environment = loginEnvironment()
                .map { "${it.key}=${it.value}" }
                .toTypedArray()
        )
    }

    fun loginEnvironment(): Map<String, String> {
        paths.prootTmpDir.mkdirs()
        return mapOf(
            "LD_LIBRARY_PATH" to paths.libraryDir.path,
            "PROOT_LOADER" to paths.loader.path,
            "PROOT_TMP_DIR" to paths.prootTmpDir.path,
            "TMPDIR" to paths.prootTmpDir.path,
            "TERM" to "xterm-256color"
        )
    }

    fun sessionWorkingDirectory(): String = context.filesDir.path

    private fun ensureNetworkConfig() {
        File(paths.rootfsDir, "etc/resolv.conf").writeText(
            "nameserver 223.5.5.5\n" +
                "nameserver 119.29.29.29\n" +
                "nameserver 8.8.8.8\n" +
                "options timeout:2 attempts:3 rotate\n"
        )
        File(paths.rootfsDir, "etc/gai.conf").apply {
            parentFile?.mkdirs()
            writeText("precedence ::ffff:0:0/96  100\n")
        }
        File(paths.rootfsDir, "etc/wgetrc").writeText(
            "prefer-family = IPv4\ntimeout = 20\ntries = 3\n"
        )
        File(paths.rootfsDir, "etc/curlrc").writeText(
            "--ipv4\n--connect-timeout 20\n--retry 3\n--retry-delay 2\n"
        )
        File(paths.rootfsDir, "etc/apt/apt.conf.d/99doro-network").apply {
            parentFile?.mkdirs()
            writeText(
                "Acquire::ForceIPv4 \"true\";\n" +
                    "Acquire::Retries \"3\";\n" +
                    "Acquire::http::Timeout \"20\";\n" +
                    "Acquire::https::Timeout \"20\";\n"
            )
        }
    }

    private fun ensureTunaAptMirror() {
        val aptDir = File(paths.rootfsDir, "etc/apt").apply { mkdirs() }
        File(aptDir, "sources.list").writeText(
            "deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble main restricted universe multiverse\n" +
                "deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-updates main restricted universe multiverse\n" +
                "deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-security main restricted universe multiverse\n" +
                "deb https://mirrors.tuna.tsinghua.edu.cn/ubuntu-ports/ noble-backports main restricted universe multiverse\n"
        )
        File(aptDir, "sources.list.d/ubuntu.sources").delete()
    }

    private fun ensureHostnameResolution() {
        File(paths.rootfsDir, "etc/hostname").writeText("localhost\n")
        val hosts = File(paths.rootfsDir, "etc/hosts")
        val retained = if (hosts.isFile) {
            hosts.readLines().filterNot { line ->
                val value = line.trim()
                value.startsWith("127.0.0.1") || value.startsWith("127.0.1.1")
            }
        } else emptyList()
        hosts.writeText(
            buildString {
                append("127.0.0.1 localhost\n")
                append("127.0.1.1 localhost\n")
                retained.forEach { append(it).append('\n') }
            }
        )
    }

    private fun ensureFetchScript() {
        val target = File(paths.rootfsDir, "usr/local/bin/doro-fetch")
        target.parentFile?.mkdirs()
        copyAsset(FETCH_SCRIPT_ASSET, target)
        target.setExecutable(true, false)
    }

    private fun ensureTerminalProfile(username: String) {
        val bashrc = File(paths.rootfsDir, "home/$username/.bashrc")
        if (!bashrc.isFile) return
        val marker = "# DORO_TERMINAL_PROFILE"
        var content = bashrc.readText()
        if (!content.contains(marker)) {
            bashrc.appendText(
                "\n$marker\n" +
                    "export LANG=zh_CN.UTF-8\n" +
                    "export LC_ALL=zh_CN.UTF-8\n" +
                    "export LANGUAGE=zh_CN:zh\n" +
                    "unset LD_PRELOAD\n" +
                    "export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games\n" +
                    "export TERM=xterm-256color\n" +
                    "export COLORTERM=truecolor\n" +
                    "export PS1='\\[\\e[1;32m\\]\\u@localhost\\[\\e[0m\\]:\\[\\e[1;34m\\]\\w\\[\\e[0m\\]\\$ '\n"
            )
        }
        content = bashrc.readText()
        if (!content.contains("export TERM=xterm-256color")) {
            bashrc.appendText("\nexport TERM=xterm-256color\nexport COLORTERM=truecolor\n")
        }
        val bannerMarker = "# DORO_UBUNTU_BANNER_V10"
        val bannerEndMarker = "# DORO_UBUNTU_BANNER_END"
        val current = bashrc.readText()
        val bannerStart = current.indexOf("# DORO_UBUNTU_BANNER_")
        val bannerEnd = current.indexOf(bannerEndMarker, bannerStart.coerceAtLeast(0))
        if (bannerStart >= 0 && bannerEnd >= bannerStart) {
            bashrc.writeText(current.removeRange(bannerStart, bannerEnd + bannerEndMarker.length))
        }
        bashrc.appendText(
            "\n$bannerMarker\n" +
                "if [ \"\$DORO_DESKTOP_TERMINAL\" != \"1\" ] && command -v fastfetch >/dev/null 2>&1; then\n" +
                "  doro-fetch\n" +
                "fi\n" +
                "$bannerEndMarker\n"
        )
    }

    private fun ensureDesktopWallpaper(username: String) {
        @Suppress("UNUSED_VARIABLE") val ignored = username
        val xfceBackgrounds = File(paths.rootfsDir, "usr/share/backgrounds/xfce").apply { mkdirs() }
        copyAsset(DESKTOP_WALLPAPER_ASSET, File(xfceBackgrounds, "doro-ubuntu.png"))
    }

    private fun ensureDesktopShortcuts(username: String) {
        val home = File(paths.rootfsDir, "home/$username")
        if (!home.isDirectory) return
        installDesktopCompatibility()
        installWhiteSurDesktopTheme()
        removeConflictingNotificationArea(username)
        configureStablePanel(username)
        configureMacAppearance(username)
        configurePlank(username)
        val desktop = File(home, "Desktop").apply { mkdirs() }
        val applications = File(paths.rootfsDir, "usr/share/applications")
        val firefoxDesktop = File(applications, "firefox.desktop")
        if (!firefoxDesktop.isFile || firefoxDesktop.length() == 0L) {
            firefoxDesktop.writeText(
                "[Desktop Entry]\n" +
                    "Name=Firefox\nName[zh_CN]=Firefox 浏览器\n" +
                    "Exec=/opt/firefox/firefox %u\nIcon=/opt/firefox/browser/chrome/icons/default/default128.png\n" +
                    "Type=Application\nCategories=Network;WebBrowser;\nTerminal=false\n"
            )
        }
        val launchers = listOf(
            "spark-store.desktop",
            "firefox.desktop",
            "thunar.desktop",
            "libreoffice-startcenter.desktop",
            "vlc.desktop",
            "gimp.desktop"

        )
        applications.listFiles { file -> file.isFile && file.extension == "desktop" }?.forEach { source ->
            val patched = DesktopCompatibility.patchDesktop(source.readText(), source.name)
            source.writeText(patched)
        }
        launchers.forEach { name ->
            val source = File(applications, name)
            if (source.isFile && source.length() > 0L) {
                val target = File(desktop, name)
                target.writeText(source.readText())
                target.setExecutable(true, false)
            }
        }
        installNewApplicationDesktopSync(username)
    }

    private fun installNewApplicationDesktopSync(username: String) {
        val state = File(paths.rootfsDir, "home/$username/.local/share/doro-known-apps")
        if (!state.exists()) {
            state.parentFile?.mkdirs()
            val applications = File(paths.rootfsDir, "usr/share/applications")
            state.writeText(
                applications.listFiles { file -> file.isFile && file.extension == "desktop" }
                    ?.map { it.name }
                    ?.sorted()
                    ?.joinToString("\n", postfix = "\n")
                    .orEmpty()
            )
        }
        val bin = File(paths.rootfsDir, "usr/local/bin").apply { mkdirs() }
        File(bin, "doro-sync-new-apps").apply {
            writeText(
                "#!/bin/sh\n" +
                    "apps=/usr/share/applications\n" +
                    "desktop=\"/home/$username/Desktop\"\n" +
                    "state=\"/home/$username/.local/share/doro-known-apps\"\n" +
                    "mkdir -p \"\$desktop\" \"\$(dirname \"\$state\")\"\n" +
                    "touch \"\$state\"\n" +
                    "find \"\$apps\" -maxdepth 1 -type f -name '*.desktop' -print | sort | while IFS= read -r source; do\n" +
                    "  name=\$(basename \"\$source\")\n" +
                    "  grep -qxF \"\$name\" \"\$state\" && continue\n" +
                    "  grep -Eq '^Type=Application$' \"\$source\" || continue\n" +
                    "  grep -Eqi '^(NoDisplay|Hidden)=true$' \"\$source\" && continue\n" +
                    "  cp -f \"\$source\" \"\$desktop/\$name\"\n" +
                    "  chmod +x \"\$desktop/\$name\"\n" +
                    "  printf '%s\\n' \"\$name\" >> \"\$state\"\n" +
                    "done\n"
            )
            setExecutable(true, false)
        }
        val autostart = File(paths.rootfsDir, "home/$username/.config/autostart").apply { mkdirs() }
        File(autostart, "doro-sync-new-apps.desktop").writeText(
            "[Desktop Entry]\nType=Application\nName=Sync new applications\n" +
                "Exec=/bin/sh -c 'sleep 3; doro-sync-new-apps; while sleep 15; do doro-sync-new-apps; done'\n" +
                "NoDisplay=true\nTerminal=false\n"
        )
    }

    private fun installWhiteSurDesktopTheme() {
        val marker = File(paths.rootfsDir, "usr/share/themes/.doro-whitesur-v2-icons")
        if (marker.isFile) return
        verifyAsset(WHITESUR_THEME_ASSET, WHITESUR_THEME_SHA256)
        context.assets.open(WHITESUR_THEME_ASSET).use { input ->
            TarGzExtractor.extract(input, File(paths.rootfsDir, "usr/share"), WHITESUR_THEME_ASSET_SIZE)
        }
        marker.parentFile?.mkdirs()
        marker.writeText("WhiteSur-Dark + WhiteSur icons\n")
    }

    private fun configureMacAppearance(username: String) {
        val gtkConfigDir = File(paths.rootfsDir, "home/$username/.config/gtk-3.0").apply { mkdirs() }
        File(gtkConfigDir, "gtk.css").writeText(
            "XfdesktopIconView.view {\n" +
                "  color: #ffffff;\n" +
                "  text-shadow: 0 1px 2px rgba(0, 0, 0, 0.95);\n" +
                "  -XfdesktopIconView-label-alpha: 90;\n" +
                "  -XfdesktopIconView-selected-label-alpha: 180;\n" +
                "}\n"
        )
        val configDir = File(
            paths.rootfsDir,
            "home/$username/.config/xfce4/xfconf/xfce-perchannel-xml"
        ).apply { mkdirs() }
        val xsettings = File(configDir, "xsettings.xml")
        if (!xsettings.exists()) {
            xsettings.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<channel name="xsettings" version="1.0">
  <property name="Net" type="empty">
    <property name="ThemeName" type="string" value="WhiteSur-Dark"/>
    <property name="IconThemeName" type="string" value="WhiteSur"/>
  </property>
  <property name="Gtk" type="empty">
    <property name="FontName" type="string" value="Noto Sans 10"/>
  </property>
</channel>
"""
            )
        }
        val xfwm = File(configDir, "xfwm4.xml")
        if (!xfwm.exists()) {
            xfwm.writeText(
                """<?xml version="1.0" encoding="UTF-8"?>
<channel name="xfwm4" version="1.0">
  <property name="general" type="empty">
    <property name="theme" type="string" value="WhiteSur-Dark"/>
    <property name="button_layout" type="string" value="CHM|"/>
    <property name="title_alignment" type="string" value="center"/>
  </property>
</channel>
"""
            )
        }
    }

    // Plank replaces the former second-panel launcher strip.

    private fun configurePlank(username: String) {
        val applications = File(paths.rootfsDir, "usr/share/applications").apply { mkdirs() }
        File(applications, "doro-terminal.desktop").writeText(
            "[Desktop Entry]\nType=Application\nName=Terminal\nName[zh_CN]=终端\n" +
                "Exec=env DORO_DESKTOP_TERMINAL=1 xfce4-terminal --disable-server\nIcon=utilities-terminal\nCategories=System;TerminalEmulator;\n"
        )
        File(applications, "doro-home.desktop").writeText(
            "[Desktop Entry]\nType=Application\nName=Home\nName[zh_CN]=主文件夹\n" +
                "Exec=thunar /home/$username\nIcon=user-home\nCategories=System;FileTools;FileManager;\n"
        )
        val launcher = File(paths.rootfsDir, "usr/local/bin/doro-start-plank")
        launcher.parentFile?.mkdirs()
        launcher.writeText(
            "#!/bin/sh\n" +
                "marker=\"\$HOME/.config/plank/doro-defaults-v4\"\n" +
                "xfconf-query -c xfwm4 -p /general/use_compositing -s true 2>/dev/null || xfconf-query -c xfwm4 -p /general/use_compositing -n -t bool -s true\n" +
                "xfconf-query -c xfce4-session -p /general/SaveOnExit -s false 2>/dev/null || xfconf-query -c xfce4-session -p /general/SaveOnExit -n -t bool -s false\n" +
                "pkill -x plank 2>/dev/null || true\n" +
                "sleep 1\n" +
                "if [ ! -f \"\$marker\" ]; then\n" +
                "  rm -f \"\$HOME/.config/plank/doro-defaults-v1\" \"\$HOME/.config/plank/doro-defaults-v2\" \"\$HOME/.config/plank/doro-defaults-v3\"\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ theme 'Transparent'\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ position 'bottom'\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ alignment 'center'\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ items-alignment 'center'\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ icon-size 64\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ zoom-enabled true\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ zoom-percent 200\n" +
                "  launchers=\"\$HOME/.config/plank/dock1/launchers\"\n" +
                "  mkdir -p \"\$launchers\"\n" +
                "  rm -f \"\$launchers\"/*.dockitem\n" +
                "  printf '[PlankDockItemPreferences]\\nLauncher=file:///usr/share/applications/firefox.desktop\\n' > \"\$launchers/firefox.dockitem\"\n" +
                "  printf '[PlankDockItemPreferences]\\nLauncher=file:///usr/share/applications/spark-store.desktop\\n' > \"\$launchers/spark-store.dockitem\"\n" +
                "  printf '[PlankDockItemPreferences]\\nLauncher=file:///usr/share/applications/doro-terminal.desktop\\n' > \"\$launchers/terminal.dockitem\"\n" +
                "  printf '[PlankDockItemPreferences]\\nLauncher=file:///usr/share/applications/doro-home.desktop\\n' > \"\$launchers/home.dockitem\"\n" +
                "  gsettings set net.launchpad.plank.dock.settings:/net/launchpad/plank/docks/dock1/ dock-items \"['firefox.dockitem', 'spark-store.dockitem', 'terminal.dockitem', 'home.dockitem']\"\n" +
                "  mkdir -p \"\$(dirname \"\$marker\")\" && touch \"\$marker\"\n" +
                "fi\n" +
                "exec plank\n"
        )
        launcher.setExecutable(true, false)
        val autostart = File(paths.rootfsDir, "home/$username/.config/autostart").apply { mkdirs() }
        val entry = File(autostart, "plank.desktop")
        val generatedEntry = !entry.exists() || entry.readText().contains("Exec=plank\n")
        if (generatedEntry) {
            entry.writeText(
                "[Desktop Entry]\n" +
                    "Type=Application\n" +
                    "Name=Plank\n" +
                    "Exec=/usr/local/bin/doro-start-plank\n" +
                    "OnlyShowIn=XFCE;\n" +
                    "X-GNOME-Autostart-enabled=true\n"
            )
        }
    }

    private fun configureStablePanel(username: String) {
        val panelFile = File(
            paths.rootfsDir,
            "home/$username/.config/xfce4/xfconf/xfce-perchannel-xml/xfce4-panel.xml"
        )
        panelFile.parentFile?.mkdirs()
        if (panelFile.exists() && panelFile.readText().contains("DORO_CENTERED_TIME_V1")) return
        panelFile.writeText(
            """<?xml version="1.0" encoding="UTF-8"?>
<!-- DORO_CENTERED_TIME_V1 -->
<channel name="xfce4-panel" version="1.0">
  <property name="configver" type="int" value="2"/>
  <property name="panels" type="array">
    <value type="int" value="1"/>
    <property name="panel-1" type="empty">
      <property name="position" type="string" value="p=6;x=0;y=0"/>
      <property name="length" type="uint" value="100"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="size" type="uint" value="30"/>
      <property name="background-style" type="uint" value="1"/>
      <property name="background-rgba" type="array">
        <value type="double" value="0.0"/><value type="double" value="0.0"/>
        <value type="double" value="0.0"/><value type="double" value="0.0"/>
      </property>
      <property name="plugin-ids" type="array">
        <value type="int" value="1"/><value type="int" value="2"/><value type="int" value="3"/>
        <value type="int" value="12"/><value type="int" value="13"/>
        <value type="int" value="8"/><value type="int" value="9"/><value type="int" value="14"/>
      </property>
    </property>
    <property name="panel-2" type="empty">
      <property name="autohide-behavior" type="uint" value="1"/>
      <property name="position" type="string" value="p=10;x=0;y=0"/>
      <property name="length" type="uint" value="46"/>
      <property name="length-adjust" type="bool" value="true"/>
      <property name="position-locked" type="bool" value="true"/>
      <property name="size" type="uint" value="52"/>
      <property name="icon-size" type="uint" value="38"/>
      <property name="background-style" type="uint" value="1"/>
      <property name="background-rgba" type="array">
        <value type="double" value="0.105"/><value type="double" value="0.117"/>
        <value type="double" value="0.145"/><value type="double" value="0.90"/>
      </property>
      <property name="plugin-ids" type="array">
        <value type="int" value="15"/><value type="int" value="16"/><value type="int" value="17"/>
        <value type="int" value="18"/><value type="int" value="19"/><value type="int" value="20"/>
        <value type="int" value="21"/><value type="int" value="22"/>
      </property>
    </property>
  </property>
  <property name="plugins" type="empty">
    <property name="plugin-1" type="string" value="applicationsmenu">
      <property name="show-button-title" type="bool" value="true"/>
      <property name="button-title" type="string" value="Applications"/>
      <property name="button-icon" type="string" value="start-here"/>
    </property>
    <property name="plugin-2" type="string" value="tasklist"/>
    <property name="plugin-3" type="string" value="separator"><property name="expand" type="bool" value="true"/></property>
    <property name="plugin-12" type="string" value="clock">
      <property name="digital-format" type="string" value="%H:%M"/>
      <property name="tooltip-format" type="string" value="%H:%M"/>
    </property>
    <property name="plugin-13" type="string" value="separator"><property name="expand" type="bool" value="true"/></property>
    <property name="plugin-8" type="string" value="pulseaudio"/>
    <property name="plugin-9" type="string" value="genmon">
      <property name="Command" type="string" value="/usr/local/bin/doro-battery-status"/>
      <property name="UpdatePeriod" type="int" value="10000"/>
      <property name="UseLabel" type="bool" value="false"/>
    </property>
    <property name="plugin-14" type="string" value="actions"/>
    <property name="plugin-15" type="string" value="showdesktop"/>
    <property name="plugin-16" type="string" value="separator"/>
    <property name="plugin-17" type="string" value="launcher"><property name="items" type="array"><value type="string" value="17853139571.desktop"/><value type="string" value="17853139591.desktop"/></property></property>
    <property name="plugin-18" type="string" value="launcher"><property name="items" type="array"><value type="string" value="17853139582.desktop"/><value type="string" value="17853139592.desktop"/></property></property>
    <property name="plugin-19" type="string" value="launcher"><property name="items" type="array"><value type="string" value="17853139583.desktop"/><value type="string" value="17853139603.desktop"/></property></property>
    <property name="plugin-20" type="string" value="launcher"><property name="items" type="array"><value type="string" value="17853139584.desktop"/><value type="string" value="17853139604.desktop"/></property></property>
    <property name="plugin-21" type="string" value="separator"/>
    <property name="plugin-22" type="string" value="directorymenu"><property name="base-directory" type="string" value="/home/$username"/></property>
  </property>
</channel>
"""
        )
        val panelDir = File(paths.rootfsDir, "home/$username/.config/xfce4/panel").apply { mkdirs() }
        File(panelDir, "genmon-9.rc").writeText(
            "Command=/usr/local/bin/doro-battery-status\n" +
                "UseLabel=0\n" +
                "UpdatePeriod=10000\n" +
                "Text=\n"
        )
    }

    private fun ensureFirefoxCompatibility(username: String) {
        val firefoxDir = File(paths.rootfsDir, "home/$username/.mozilla/firefox")
        firefoxDir.mkdirs()
        File(firefoxDir, "profiles.ini").takeIf { !it.exists() }?.writeText(
            "[Profile0]\nName=default\nIsRelative=1\nPath=doro.default\nDefault=1\n\n[General]\nStartWithLastProfile=1\nVersion=2\n"
        )
        val profiles = firefoxDir.listFiles { file ->
            file.isDirectory && (file.name.endsWith(".default") || file.name.contains("default-release"))
        }?.toList().orEmpty() + File(firefoxDir, "doro.default").apply { mkdirs() }
        profiles.distinctBy(File::getPath).forEach { profile ->
            File(profile, "sessionstore.jsonlz4").delete()
            File(profile, "sessionstore-backups").takeIf { it.exists() }?.deleteRecursively()
            File(profile, ".parentlock").delete()
            File(profile, "lock").delete()
            File(profile, "user.js").writeText(
                "user_pref(\"browser.shell.checkDefaultBrowser\", false);\n" +
                    "user_pref(\"browser.aboutwelcome.enabled\", false);\n" +
                    "user_pref(\"browser.startup.homepage_override.mstone\", \"ignore\");\n" +
                    "user_pref(\"toolkit.legacyUserProfileCustomizations.stylesheets\", true);\n" +
                    "user_pref(\"security.sandbox.content.level\", 0);\n" +
                    "user_pref(\"security.sandbox.gpu.level\", 0);\n" +
                    "user_pref(\"security.sandbox.socket.process.level\", 0);\n" +
                    "user_pref(\"media.cubeb.backend\", \"pulse\");\n" +
                    "user_pref(\"media.ffmpeg.enabled\", true);\n" +
                    "user_pref(\"media.hardware-video-decoding.enabled\", false);\n" +
                    "user_pref(\"media.rdd-ffmpeg.enabled\", true);\n" +
                    "user_pref(\"gfx.webrender.all\", true);\n" +
                    "user_pref(\"layers.acceleration.force-enabled\", true);\n" +
                    "user_pref(\"browser.sessionstore.resume_from_crash\", false);\n" +
                    "user_pref(\"browser.sessionstore.max_resumed_crashes\", -1);\n"
            )
            val chrome = File(profile, "chrome").apply { mkdirs() }
            File(chrome, "userChrome.css").writeText(
                "notification-message[value=\"sandbox\"], notification[value=\"sandbox\"] { display: none !important; }\n"
            )
        }
    }

    private fun removeConflictingNotificationArea(username: String) {
        val autostart = File(paths.rootfsDir, "home/$username/.config/autostart").apply { mkdirs() }
        listOf("indicator-application.desktop", "indicator-messages.desktop").forEach { name ->
            File(autostart, name).writeText(
                "[Desktop Entry]\nType=Application\nName=Disabled $name\nHidden=true\n"
            )
        }
        val sessions = File(paths.rootfsDir, "home/$username/.cache/sessions")
        sessions.deleteRecursively()
    }

    private fun installDesktopCompatibility() {
        val bin = File(paths.rootfsDir, "usr/local/bin").apply { mkdirs() }
        File(bin, "doro-start-xfce").delete()
        File(bin, "doro-recover-desktop").delete()
        File(bin, "doro-apply-wallpaper").apply {
            writeText(DesktopCompatibility.wallpaperScript())
            setExecutable(true, false)
        }
        File(bin, "xfce4-about").delete()
        File(bin, "doro-battery-status").apply {
            writeText(
                "#!/bin/sh\n" +
                    "value=\$(cat /run/doro-audio/battery-capacity 2>/dev/null || echo --)\n" +
                    "case \"\$value\" in ''|*[!0-9]*) icon=battery-missing-symbolic;; " +
                    "[0-9]|1[0-9]) icon=battery-empty-symbolic;; 2[0-9]|3[0-9]) icon=battery-caution-symbolic;; " +
                    "4[0-9]|5[0-9]|6[0-9]|7[0-9]) icon=battery-good-symbolic;; *) icon=battery-full-symbolic;; esac\n" +
                    "echo \"<icon>\$icon</icon><txt> \${value}%</txt><tool>电池电量：\${value}%</tool>\"\n"
            )
            setExecutable(true, false)
        }
        File(bin, "doro-gpu-run").apply {
            writeText(
                "#!/bin/sh\n" +
                    "if [ \"\$(cat /etc/doro-gpu-enabled 2>/dev/null)\" = \"1\" ]; then\n" +
                    "  export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink\n" +
                    "  export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json\n" +
                    "  export VK_DRIVER_FILES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json\n" +
                    "  unset LIBGL_ALWAYS_SOFTWARE LIBGL_DRI3_DISABLE\n" +
                    "else\n" +
                    "  unset MESA_LOADER_DRIVER_OVERRIDE GALLIUM_DRIVER VK_ICD_FILENAMES VK_DRIVER_FILES\n" +
                    "  export LIBGL_ALWAYS_SOFTWARE=1\n" +
                    "fi\n" +
                    "exec \"\$@\"\n"
            )
            setExecutable(true, false)
        }
        File(bin, "doro-firefox").apply {
            writeText(
                "#!/bin/sh\n" +
                    "unset LIBGL_ALWAYS_SOFTWARE LIBGL_DRI3_DISABLE\n" +
                    "export MESA_LOADER_DRIVER_OVERRIDE=zink GALLIUM_DRIVER=zink\n" +
                    "export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json\n" +
                    "export VK_DRIVER_FILES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json\n" +
                    "export MOZ_DISABLE_CONTENT_SANDBOX=1\n" +
                    "export MOZ_DISABLE_GMP_SANDBOX=1\n" +
                    "export MOZ_DISABLE_RDD_SANDBOX=1\n" +
                    "export MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1\n" +
                    "export MOZ_DISABLE_GPU_SANDBOX=1\n" +
                    "export MOZ_ENABLE_WAYLAND=0\n" +
                    "export PULSE_SERVER=unix:\${XDG_RUNTIME_DIR}/pulse/native\n" +
                    "export MOZ_WEBRENDER=1 MOZ_ACCELERATED=1\n" +
                    "exec /opt/firefox/firefox --no-remote \"\$@\"\n"
            )
            setExecutable(true, false)
        }
        File(bin, "doro-libreoffice").delete()
    }

    fun stopDesktopServices() {
        audioBridge.close()
    }

    fun startDesktop(display: String, gpuAcceleration: Boolean = true): Process {
        val config = requireNotNull(loadConfig()) { "Ubuntu 尚未初始化" }
        ensureNetworkConfig()
        installGpuAwareDesktopWrappers()
        File(paths.rootfsDir, "etc/doro-gpu-enabled").writeText(if (gpuAcceleration) "1\n" else "0\n")
        ensureDesktopWallpaper(config.username)
        ensureDesktopShortcuts(config.username)
        ensureFirefoxCompatibility(config.username)
        audioBridge.start()
        val audioScript = "export XDG_RUNTIME_DIR=/tmp/runtime-${shell(config.username)}; " +
            "mkdir -p \"\$XDG_RUNTIME_DIR\"; chmod 700 \"\$XDG_RUNTIME_DIR\"; " +
            "pkill -9 -x pulseaudio 2>/dev/null || true; " +
            "rm -rf \"\$XDG_RUNTIME_DIR/pulse\" /tmp/pulse-*; mkdir -p \"\$XDG_RUNTIME_DIR/pulse\"; " +
            "pulseaudio --daemonize=yes --use-pid-file=no --exit-idle-time=-1 --load='module-native-protocol-unix' " +
            "--load='module-pipe-sink sink_name=doro_android file=/run/doro-audio/pulse-output.pcm format=s16le rate=44100 channels=2' " +
            "--log-target=file:/tmp/doro-pulse.log; " +
            "doro_audio_try=0; until pactl info >/dev/null 2>&1 || [ \"\$doro_audio_try\" -ge 20 ]; do " +
            "doro_audio_try=\$((doro_audio_try+1)); sleep 0.2; done; " +
            "pactl set-default-sink doro_android >/dev/null 2>&1 || true; "
        val script = audioScript + "export DORO_DESKTOP_TERMINAL=1; " +
            DesktopCompatibility.sessionScript(display, config.username)
        val startSession = DesktopCompatibility.systemServicesScript() + "; " +
            "exec /usr/bin/su - ${shell(config.username)} -c ${shell(script)}"
        return ProcessBuilder(prootCommand("/bin/sh", "-c", startSession))
            .apply { environment().putAll(loginEnvironment()) }
            .redirectErrorStream(true)
            .start()
    }

    private fun runAsRoot(
        script: String,
        operation: String = "configure",
        timeoutMs: Long = COMMAND_TIMEOUT_MS,
        idleTimeoutMs: Long = COMMAND_IDLE_TIMEOUT_MS,
        onActivity: (String) -> Unit = {}
    ): String {
        appendLog("COMMAND $operation")
        val process = ProcessBuilder(prootCommand("/bin/sh", "-c", script))
            .apply { environment().putAll(loginEnvironment()) }
            .redirectErrorStream(true).start()
        val output = StringBuilder()
        val watchdog = ProcessWatchdog(timeoutMs, idleTimeoutMs)
val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        watchdog.recordActivity()
                        synchronized(output) { output.appendLine(line) }
                        appendLog("[$operation] $line")
                        onActivity(line)
                    }
                }
            }.onFailure { failure ->
                if (process.isAlive) appendLog("[$operation] output reader failed: ${failure.message}")
            }
        }.apply { name = "doro-$operation-output"; isDaemon = true; start() }
        var failure: String? = null
        while (true) {
            val code = runCatching { process.exitValue() }.getOrNull()
            if (code != null) break
            Thread.sleep(WATCHDOG_POLL_MS)
            failure = when {
                watchdog.isTotalTimedOut() -> "${operation}执行超时"
                watchdog.isIdleTimedOut() -> "${operation}长时间无输出"
                else -> null
            }
            if (failure != null) {
                appendLog("WATCHDOG $failure")
                process.destroy()
                Thread.sleep(PROCESS_KILL_GRACE_MS)
                break
            }
        }
        reader.join(PROCESS_KILL_GRACE_MS)
        check(failure == null) { "$failure，已终止进程。日志：${paths.initializationLogFile.path}" }
        val code = process.waitFor()
        val captured = synchronized(output) { output.toString() }
        check(code == 0) { captured.ifBlank { "Ubuntu 配置失败：$operation，退出码 $code" } }
return captured
    }
private fun prepareContainerMounts() {
        paths.x11SocketDir.mkdirs()
        paths.sharedMemoryDir.mkdirs()
        paths.audioDir.mkdirs()
        paths.sharedMemoryDir.setReadable(true, false)
        paths.sharedMemoryDir.setWritable(true, false)
        paths.sharedMemoryDir.setExecutable(true, false)
        paths.compatibilityProcDir.mkdirs()
        val procStat = runCatching { File("/proc/stat").readText() }.getOrNull()
        paths.compatibilityProcStat.writeText(
            procStat?.takeIf(String::isNotBlank)
                ?: "cpu  1 0 1 1 0 0 0 0 0 0\nbtime 0\n"
        )
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val capacity = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        val batteryDir = File(paths.compatibilityPowerSupplyDir, "BAT0").apply { mkdirs() }
        File(batteryDir, "type").writeText("Battery\n")
        File(batteryDir, "present").writeText("1\n")
        File(batteryDir, "capacity").writeText("$capacity\n")
        File(batteryDir, "status").writeText("Unknown\n")
        File(paths.audioDir, "battery-capacity").writeText("$capacity\n")
    }

    private fun prootCommand(vararg command: String): List<String> = buildList {
        add(paths.proot.path)
        prepareContainerMounts()
        addAll(listOf("--link2symlink", "-0", "-r", paths.rootfsDir.path,
            "-b", "/dev", "-b", "${paths.sharedMemoryDir.path}:/dev/shm",
            "-b", "/proc", "-b", "${paths.compatibilityProcStat.path}:/proc/stat",
            "-b", "${paths.audioDir.path}:/run/doro-audio",
            "-b", "/sys", "-b", "${paths.compatibilityPowerSupplyDir.path}:/sys/class/power_supply", "-b", "/sdcard",
            "-b", "${paths.x11SocketDir.path}:/tmp/.X11-unix",
            "-w", "/root", "/usr/bin/env", "-i", "HOME=/root", "USER=root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games",
            "LANG=C.UTF-8", "TERM=xterm-256color", "COLORTERM=truecolor"))
        addAll(command)
    }

    private fun installRuntime() {
        paths.prootTmpDir.mkdirs()
        check(paths.prootTmpDir.isDirectory && paths.prootTmpDir.canWrite()) { "PRoot临时目录不可写" }
        check(paths.proot.isFile && paths.proot.canExecute()) { "PRoot 原生运行时不可用" }
        check(paths.loader.isFile) { "PRoot Loader 不可用" }
        paths.libraryDir.mkdirs()
        copyAsset("runtime/arm64-v8a/lib/libtalloc.so.2", File(paths.libraryDir, "libtalloc.so.2"))
        copyAsset("runtime/arm64-v8a/lib/libandroid-shmem.so", File(paths.libraryDir, "libandroid-shmem.so"))
        val stagedClient = File(paths.runtimeDir, "libgetifaddrs_bridge.so")
        copyAsset(paths.getifaddrsClientAsset, stagedClient)
        GetifaddrsBridge.installClient(paths.rootfsDir, stagedClient)
        val profile = File(paths.rootfsDir, "etc/profile.d/doro.sh")
        profile.parentFile?.mkdirs()
        val preloadLine = "export LD_PRELOAD=${GetifaddrsBridge.CONTAINER_LIBRARY}"
        if (!profile.exists()) profile.writeText("$preloadLine\n")
        else if (profile.readLines().none { it.trim() == preloadLine }) profile.appendText("$preloadLine\n")
        check(paths.getifaddrsServer.isFile && paths.getifaddrsServer.canExecute()) { "getifaddrs服务端不可用" }
    }

    private fun ensureGetifaddrsBridge() {
        synchronized(bridgeLock) {
            if (bridgeProcess?.let { runCatching { it.exitValue() }.isFailure } == true && paths.getifaddrsSocket.exists()) return
            bridgeProcess?.destroy()
            paths.getifaddrsSocket.parentFile?.mkdirs()
            paths.getifaddrsSocket.delete()
            val bridgeLog = File(paths.baseDir, "logs/getifaddrs.log").also { it.parentFile?.mkdirs() }
            bridgeProcess = ProcessBuilder(
                "/system/bin/sh", "-c",
                "exec ${shell(paths.getifaddrsServer.path)} ${shell(paths.getifaddrsSocket.path)} >> ${shell(bridgeLog.path)} 2>&1"
            ).start()
            repeat(20) {
                if (paths.getifaddrsSocket.exists()) return
                Thread.sleep(25)
            }
            error("getifaddrs桥启动失败")
        }
    }

    private fun copyAsset(asset: String, target: File) {
        context.assets.open(asset).use { input -> target.outputStream().use(input::copyTo) }
    }

    private fun verifyAsset(asset: String, expectedSha256: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(asset).use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
        check(actual == expectedSha256) { "内置RootFS校验失败" }
    }

    private fun installMesaGpuRuntime() {
        if (paths.mesaGpuReadyMarker.isFile) {
            installGpuAwareDesktopWrappers()
            return
        }
        verifyAsset(MESA_GPU_ASSET, MESA_GPU_SHA256)
        context.assets.open(MESA_GPU_ASSET).use { input ->
            TarGzExtractor.extract(input, paths.rootfsDir, MESA_GPU_ASSET_SIZE)
        }
        val profile = File(paths.rootfsDir, "etc/profile.d/doro-gpu.sh")
        profile.parentFile?.mkdirs()
        profile.writeText(
            "case \$- in *i*) ;; *) return 0 ;; esac\n" +
                "[ \"\$(cat /etc/doro-gpu-enabled 2>/dev/null)\" = \"1\" ] || return 0\n" +
                "export MESA_LOADER_DRIVER_OVERRIDE=zink\n" +
                "export GALLIUM_DRIVER=zink\n" +
                "export VK_ICD_FILENAMES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json\n" +
                "export VK_DRIVER_FILES=/usr/share/vulkan/icd.d/freedreno_icd.aarch64.json\n" +
                "unset LIBGL_ALWAYS_SOFTWARE LIBGL_DRI3_DISABLE\n"
        )
        installGpuAwareDesktopWrappers()
        runAsRoot(
            "ldconfig && test -f /usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so && " +
                "test -f /usr/lib/aarch64-linux-gnu/dri/zink_dri.so && " +
                "test -f /usr/share/vulkan/icd.d/freedreno_icd.aarch64.json",
            "mesa-gpu-runtime"
        )
        paths.mesaGpuReadyMarker.writeText("mesa-26.2.0-devel-20260709\n")
    }

    private fun installGpuAwareDesktopWrappers() {
        val wrapperDir = File(paths.rootfsDir, "usr/local/lib/doro-software-bin").apply { mkdirs() }
        listOf("xfwm4", "xfdesktop", "xfce4-panel", "plank").forEach { command ->
            File(wrapperDir, command).apply {
                writeText(
                    "#!/bin/sh\n" +
                        "unset MESA_LOADER_DRIVER_OVERRIDE GALLIUM_DRIVER VK_ICD_FILENAMES VK_DRIVER_FILES\n" +
                        "export LIBGL_ALWAYS_SOFTWARE=1\n" +
                        "exec /usr/bin/$command \"\$@\"\n"
                )
                setExecutable(true, false)
            }
        }
    }

    private fun installGpuTools(onProgress: (InitializationProgress) -> Unit) {
        if (paths.gpuToolsReadyMarker.isFile) return
        paths.offlinePackagesDir.deleteRecursively()
        paths.offlinePackagesDir.mkdirs()
        verifyAsset(GPU_TOOLS_ASSET, GPU_TOOLS_SHA256)
        extractAsset(GPU_TOOLS_ASSET, paths.offlinePackagesDir, 46, 46, "正在解压GPU工具", onProgress)
        runAsRoot(OfflineInstallScript.build(), "gpu-tools", timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS)
        runAsRoot(
            "command -v glxinfo vulkaninfo >/dev/null && " +
                "test -f /usr/lib/aarch64-linux-gnu/libvulkan_freedreno.so && " +
                "test -f /usr/lib/aarch64-linux-gnu/dri/zink_dri.so && " +
                "test -f /usr/share/vulkan/icd.d/freedreno_icd.aarch64.json",
            "gpu-tools-check"
        )
        paths.offlinePackagesDir.deleteRecursively()
        paths.gpuToolsReadyMarker.writeText("gpu-tools-v1\n")
    }

    private fun installOfflineCore(onProgress: (InitializationProgress) -> Unit) {
        if (paths.offlineCoreReadyMarker.isFile) return
        paths.offlinePackagesDir.deleteRecursively()
        paths.offlinePackagesDir.mkdirs()
        verifyAsset(OFFLINE_CORE_ASSET, OFFLINE_CORE_SHA256)
        extractAsset(OFFLINE_CORE_ASSET, paths.offlinePackagesDir, 35, 38, "正在解压开发核心", onProgress)
        onProgress(createContainerProgress("正在配置开发核心", 39))
        runAsRoot(
            OfflineInstallScript.build(),
            "offline-core",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS,
            onActivity = { line -> onProgress(createContainerProgress("正在安装开发核心：${line.take(36)}", 39)) }
        )
        runAsRoot(
            "command -v sudo git python3 pip3 gcc g++ make cmake gdb ssh curl wget nano >/dev/null",
            "offline-core-check"
        )
        paths.offlinePackagesDir.deleteRecursively()
        paths.offlineCoreReadyMarker.writeText("core-dev-v1\n")
    }

    private fun installCliExtras(onProgress: (InitializationProgress) -> Unit) {
        if (paths.cliExtraReadyMarker.isFile) return
        paths.offlinePackagesDir.deleteRecursively()
        paths.offlinePackagesDir.mkdirs()
        verifyAsset(CLI_EXTRA_ASSET, CLI_EXTRA_SHA256)
        extractAsset(CLI_EXTRA_ASSET, paths.offlinePackagesDir, 39, 40, "正在解压常用命令", onProgress)
        runAsRoot(
            OfflineInstallScript.build(),
            "cli-extra",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS,
            onActivity = { line -> onProgress(createContainerProgress("正在安装常用命令：${line.take(36)}", 40)) }
        )
        runAsRoot(
            "command -v top killall ifconfig aria2c strace sl dig ping traceroute whois socat nc >/dev/null",
            "cli-extra-check"
        )
        paths.offlinePackagesDir.deleteRecursively()
        paths.cliExtraReadyMarker.writeText("cli-extra-v1\n")
    }

    private fun installFastfetch(onProgress: (InitializationProgress) -> Unit) {
        if (paths.fastfetchReadyMarker.isFile && runCatching {
                runAsRoot("command -v fastfetch >/dev/null", "fastfetch-check")
            }.isSuccess) return
        onProgress(createContainerProgress("正在安装终端欢迎页", 40))
        val packageFile = File(paths.rootfsDir, "tmp/$FASTFETCH_FILE")
        packageFile.parentFile?.mkdirs()
        verifyAsset(FASTFETCH_ASSET, FASTFETCH_SHA256)
        copyAsset(FASTFETCH_ASSET, packageFile)
        runAsRoot(
            "export DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1; dpkg --force-confdef --force-confold -i /tmp/$FASTFETCH_FILE; command -v fastfetch >/dev/null",
            "fastfetch",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS
        )
        packageFile.delete()
        paths.fastfetchReadyMarker.writeText("fastfetch-2.66.0\n")
    }

    private fun installFactoryApps(onProgress: (InitializationProgress) -> Unit) {
        val installedVersion = runCatching { paths.factoryAppsReadyMarker.readText().trim() }.getOrNull()
        if (installedVersion == FACTORY_APPS_MARKER_VERSION) return
        paths.offlinePackagesDir.deleteRecursively()
        paths.offlinePackagesDir.mkdirs()
        verifyAsset(FACTORY_APPS_ASSET, FACTORY_APPS_SHA256)
        extractAsset(FACTORY_APPS_ASSET, paths.offlinePackagesDir, 40, 44, "正在解压预装软件", onProgress)
        onProgress(createContainerProgress("正在配置预装软件", 45))
        installFactoryDependencyFix()
        runAsRoot(
            OfflineInstallScript.build(),
            "factory-apps",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS,
            onActivity = { line -> onProgress(createContainerProgress("正在安装预装软件：${line.take(36)}", 45)) }
        )
        ensureTunaAptMirror()
        ensureHostnameResolution()
        onProgress(createContainerProgress("正在补全预装软件依赖", 45))
        runAsRoot(
            FactoryPackageRepairScript.build(),
            "factory-apps-repair",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS,
            onActivity = { line -> onProgress(createContainerProgress("正在修复软件依赖：${line.take(36)}", 45)) }
        )
        runAsRoot(
            "sed -i 's/^# *zh_CN.UTF-8 UTF-8/zh_CN.UTF-8 UTF-8/' /etc/locale.gen; " +
                "locale-gen zh_CN.UTF-8; update-locale LANG=zh_CN.UTF-8 LANGUAGE=zh_CN:zh LC_ALL=zh_CN.UTF-8; " +
                "locale -a | grep -qi '^zh_CN\\.utf8$'",
            "chinese-locale"
        )
        runAsRoot(
            "doro_dpkg_audit=\$(dpkg -C 2>&1); " +
                "if [ -n \"\$doro_dpkg_audit\" ]; then printf '%s\\n' \"\$doro_dpkg_audit\"; exit 1; fi; " +
                "command -v startxfce4 fcitx5 thunar libreoffice vlc gimp plank java cargo go jupyter-notebook >/dev/null; " +
                "test -f /usr/share/applications/thunar.desktop; " +
                "test -f /usr/share/applications/libreoffice-startcenter.desktop; " +
                "test -f /usr/share/applications/vlc.desktop; " +
                "test -f /usr/share/applications/gimp.desktop",
            "factory-apps-check"
        )
        paths.offlinePackagesDir.deleteRecursively()
        paths.factoryAppsReadyMarker.writeText("$FACTORY_APPS_MARKER_VERSION\n")
    }

    private fun installFactoryDependencyFix() {
        val packageDir = File(paths.offlinePackagesDir, "packages").also { it.mkdirs() }
        FACTORY_FIX_ASSETS.forEach { fileName ->
            copyAsset("offline/factory-fix/$fileName", File(packageDir, fileName))
        }
    }

    private fun installVendorApps(onProgress: (InitializationProgress) -> Unit) {
        if (paths.vendorAppsReadyMarker.isFile) return
        paths.offlinePackagesDir.deleteRecursively()
        paths.offlinePackagesDir.mkdirs()
        verifyAsset(VENDOR_APPS_ASSET, VENDOR_APPS_SHA256)
        extractAsset(VENDOR_APPS_ASSET, paths.offlinePackagesDir, 46, 48, "正在解压Firefox浏览器", onProgress)
        onProgress(createContainerProgress("正在配置Firefox浏览器", 49))
        runAsRoot(
            "chmod 755 /var/cache/doro-offline/install-vendor.sh && /var/cache/doro-offline/install-vendor.sh",
            "vendor-apps",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS,
            onActivity = { line -> onProgress(createContainerProgress("正在安装Firefox浏览器：${line.take(30)}", 49)) }
        )
        ensureTunaAptMirror()
        ensureHostnameResolution()
        onProgress(createContainerProgress("正在补全厂商应用依赖", 49))
        runAsRoot(
            VendorAppsRepairScript.build(),
            "vendor-apps-repair",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS,
            onActivity = { line -> onProgress(createContainerProgress("正在修复厂商应用：${line.take(32)}", 49)) }
        )
        runAsRoot(VendorAppsRepairScript.check(), "vendor-apps-check")
        paths.offlinePackagesDir.deleteRecursively()
        paths.vendorAppsReadyMarker.writeText("vendor-apps-v1\n")
    }

    private fun stageFastfetchMigration(): String {
        if (paths.fastfetchReadyMarker.isFile) return ""
        val packageFile = File(paths.rootfsDir, "tmp/$FASTFETCH_FILE")
        if (!packageFile.isFile) {
            packageFile.parentFile?.mkdirs()
            verifyAsset(FASTFETCH_ASSET, FASTFETCH_SHA256)
            copyAsset(FASTFETCH_ASSET, packageFile)
        }
        return "if ! command -v fastfetch >/dev/null 2>&1; then DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1 dpkg --force-confdef --force-confold -i /tmp/$FASTFETCH_FILE >/tmp/doro-fastfetch.log 2>&1 || true; fi; " +
            "if command -v fastfetch >/dev/null 2>&1; then printf 'fastfetch-2.66.0\\n' > /.doro-fastfetch-ready; rm -f /tmp/$FASTFETCH_FILE; fi; "
    }

    private fun stageSparkStoreMigration(): String {
        if (paths.sparkStoreReadyMarker.isFile) return ""
        val packageFile = File(paths.rootfsDir, "tmp/$SPARK_STORE_FILE")
        if (!packageFile.isFile) {
            packageFile.parentFile?.mkdirs()
            verifyAsset(SPARK_STORE_ASSET, SPARK_STORE_SHA256)
            copyAsset(SPARK_STORE_ASSET, packageFile)
        }
        return "if ! dpkg-query -W -f='\${Status}' spark-store 2>/dev/null | grep -q 'install ok installed'; then " +
            "export DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1 NEEDRESTART_MODE=a; " +
            "dpkg --force-confdef --force-confold -i /tmp/$SPARK_STORE_FILE >/tmp/doro-spark-store.log 2>&1 || true; " +
            "dpkg --force-confdef --force-confold --configure -a >>/tmp/doro-spark-store.log 2>&1 || true; fi; " +
            "if dpkg-query -W -f='\${Status}' spark-store 2>/dev/null | grep -q 'install ok installed' && " +
            "command -v spark-store >/dev/null 2>&1 && test -x /opt/spark-store/bin/spark-store && " +
            "test -f /usr/share/applications/spark-store.desktop; then " +
            "printf 'spark-store-5.2.1.0\\n' > /.doro-spark-store-ready; rm -f /tmp/$SPARK_STORE_FILE; " +
            "else cat /tmp/doro-spark-store.log 2>/dev/null; exit 1; fi; "
    }

    private fun installSparkStore(onProgress: (InitializationProgress) -> Unit) {
        if (paths.sparkStoreReadyMarker.isFile) return
        onProgress(createContainerProgress("正在安装星火商店依赖", 49))
        paths.offlinePackagesDir.deleteRecursively()
        paths.offlinePackagesDir.mkdirs()
        verifyAsset(SPARK_STORE_DEPS_ASSET, SPARK_STORE_DEPS_SHA256)
        extractAsset(SPARK_STORE_DEPS_ASSET, paths.offlinePackagesDir, 49, 49, "正在解压星火依赖", onProgress)
        runAsRoot(
            OfflineInstallScript.build(),
            "spark-store-deps",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS
        )
        paths.offlinePackagesDir.deleteRecursively()
        onProgress(createContainerProgress("正在安装星火商店", 49))
        val packageFile = File(paths.rootfsDir, "tmp/$SPARK_STORE_FILE")
        packageFile.parentFile?.mkdirs()
        verifyAsset(SPARK_STORE_ASSET, SPARK_STORE_SHA256)
        copyAsset(SPARK_STORE_ASSET, packageFile)
        runAsRoot(
            "export DEBIAN_FRONTEND=noninteractive UCF_FORCE_CONFFOLD=1 NEEDRESTART_MODE=a; " +
            "dpkg --force-confdef --force-confold -i /tmp/$SPARK_STORE_FILE; " +
            "dpkg --force-confdef --force-confold --configure -a; " +
                "dpkg-query -W -f='\${Status}' spark-store | grep -q 'install ok installed'; " +
                "command -v spark-store >/dev/null; " +
                "test -x /opt/spark-store/bin/spark-store; " +
                "test -f /usr/share/applications/spark-store.desktop",
            "spark-store",
            timeoutMs = OFFLINE_INSTALL_TIMEOUT_MS,
            idleTimeoutMs = OFFLINE_INSTALL_IDLE_TIMEOUT_MS,
            onActivity = { line -> onProgress(createContainerProgress("正在安装星火商店：${line.take(34)}", 49)) }
        )
        packageFile.delete()
        paths.sparkStoreReadyMarker.writeText("spark-store-5.2.1.0\n")
    }

    private fun extractAsset(
        asset: String,
        destination: File,
        startPercent: Int,
        endPercent: Int,
        message: String,
        onProgress: (InitializationProgress) -> Unit
    ) {
        val assetSize = when (asset) {
            OFFLINE_CORE_ASSET -> 143107937L
            FACTORY_APPS_ASSET -> 1124894720L
            VENDOR_APPS_ASSET -> 291635200L
            else -> 0L
        }
        context.assets.open(asset).use { input ->
            TarGzExtractor.extract(input, destination, assetSize) { archivePercent ->
                val percent = startPercent + ((endPercent - startPercent) * archivePercent / 100)
                onProgress(createContainerProgress("$message $archivePercent%", percent))
            }
        }
    }

    private fun createContainerProgress(message: String, percent: Int) =
        InitializationProgress(InitializationStep.CREATE_CONTAINER, message, percent)

    private fun writeContainerFiles() {
        ensureNetworkConfig()
        File(paths.rootfsDir, "etc/hostname").writeText("ubuntu\n")
        val hosts = File(paths.rootfsDir, "etc/hosts")
        val hostnameLine = "127.0.1.1 ubuntu"
        if (!hosts.readText().lineSequence().any { it.trim() == hostnameLine }) hosts.appendText("$hostnameLine\n")
    }

    private fun userCreationScript(username: String, password: CharArray): String {
        val credentials = "$username:${password.concatToString()}"
        return "id -u ${shell(username)} >/dev/null 2>&1 || useradd -m -s /bin/bash ${shell(username)}; " +
            "mkdir -p /home/${shell(username)}; printf '%s\\n' ${shell(credentials)} | chpasswd"
    }

    private fun environmentScript(username: String): String =
        "printf '%s\\n' 'export LANG=zh_CN.UTF-8' 'export LC_ALL=zh_CN.UTF-8' 'export LANGUAGE=zh_CN:zh' 'export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:/usr/games' 'export LD_PRELOAD=${GetifaddrsBridge.CONTAINER_LIBRARY}' > /etc/profile.d/doro.sh && " +
            "ln -snf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && " +
            "printf '%s\\n' 'Asia/Shanghai' > /etc/timezone && " +
            "touch /home/${shell(username)}/.bashrc && " +
            "grep -qF 'export PS1=\\u@ubuntu:\\w\\$ ' /home/${shell(username)}/.bashrc || " +
            "printf '%s\\n' 'export PS1=\"\\[\\033[01;32m\\]\\u@\\h\\[\\033[00m\\]:\\[\\033[01;34m\\]\\w\\[\\033[00m\\]\\$ \"' >> /home/${shell(username)}/.bashrc; " +
            "chown -R ${shell(username)}:${shell(username)} /home/${shell(username)}"

    fun loadInitializationState(): InitializationState? {
        if (!paths.initializationStateFile.isFile) return null
        return runCatching {
            val values = Properties().apply { paths.initializationStateFile.inputStream().use(::load) }
            InitializationState(
                username = values.getProperty("username"),
                components = values.getProperty("components", "").split(',').filter(String::isNotBlank)
                    .mapNotNull { runCatching { InstallComponent.valueOf(it) }.getOrNull() }.toSet(),
                completedStep = values.getProperty("completedStep")?.takeIf(String::isNotBlank)
                    ?.let { InitializationStep.valueOf(it) },
                failedMessage = values.getProperty("failedMessage")?.takeIf(String::isNotBlank)
            )
        }.getOrNull()
    }

    private fun saveInitializationState(state: InitializationState) {
        paths.initializationStateFile.parentFile?.mkdirs()
        val values = Properties().apply {
            setProperty("username", state.username)
            setProperty("components", state.components.joinToString(",") { it.name })
            setProperty("completedStep", state.completedStep?.name.orEmpty())
            setProperty("failedMessage", state.failedMessage.orEmpty())
        }
        val temporary = File(paths.initializationStateFile.path + ".tmp")
        temporary.outputStream().use { values.store(it, "Doro initialization checkpoint") }
        check(temporary.renameTo(paths.initializationStateFile)) { "初始化检查点保存失败" }
    }

    private fun prepareLog() {
        paths.initializationLogFile.parentFile?.mkdirs()
        if (paths.initializationLogFile.length() > MAX_LOG_BYTES) {
            File(paths.initializationLogFile.path + ".old").also { old ->
                old.delete()
                paths.initializationLogFile.renameTo(old)
            }
        }
    }

    @Synchronized
    private fun appendLog(message: String) {
        prepareLog()
        paths.initializationLogFile.appendText("${System.currentTimeMillis()} $message\n")
    }

    fun initializationLogPath(): String = paths.initializationLogFile.path

    private fun saveConfig(config: ContainerConfig) {
        paths.baseDir.mkdirs()
        val values = Properties().apply {
            setProperty("distribution", config.distribution)
            setProperty("version", config.version)
            setProperty("username", config.username)
            setProperty("initialized", config.initialized.toString())
        }
        paths.configFile.outputStream().use { values.store(it, "Doro Linux container") }
    }

    private fun progress(step: InitializationStep, message: String) = InitializationProgress(step, message)

    private fun shell(value: String) = "'${value.replace("'", "'\\''")}'"

    companion object {
        private val bridgeLock = Any()
        @Volatile private var bridgeProcess: Process? = null
        private const val ROOTFS_ASSET = "rootfs/ubuntu-base-24.04.4-base-arm64.tar"
        private const val XKB_ASSET = "x11/xkb-data.tar"
        private const val ROOTFS_SHA256 = "0ce61ffe898bab0d0b35231c3daf1751d95c34a1963b507549c9d7dc97543e48"
        private const val XKB_SHA256 = "ef3e89f59155df159d528531fe7f61dd141ffbe4b91b870e6f96510f0c630b58"
        private const val OFFLINE_CORE_ASSET = "offline/core-dev-arm64.tar"
        private const val OFFLINE_CORE_SHA256 = "c1a5d21dadd7a750badfb679f135f0f9dffdb3b3f5778861aad5438017bdafd6"
        private const val CLI_EXTRA_ASSET = "offline/cli-extra-arm64.tar"
        private const val CLI_EXTRA_SHA256 = "ecfafa286906656819106be66a71cc213652606a9a43d2d06c2766f2efd020f0"
        private const val FACTORY_APPS_ASSET = "offline/factory-apps-arm64.tar"
        private const val FACTORY_APPS_MARKER_VERSION = "factory-apps-v3-plank"
        private const val FACTORY_APPS_SHA256 = "f33dce8e5b140159d9e0ddcb81876469b8be9ec7e2b67daa3c6b62d61153e05d"
        private const val VENDOR_APPS_ASSET = "offline/vendor-apps-arm64.tar"
        private const val VENDOR_APPS_SHA256 = "1a555fb917a41cece2f308f23f246d621335f9c68b70a23c3037442db345505a"
        private const val MESA_GPU_ASSET = "offline/mesa-for-android-container_26.2.0-devel-20260709_ubuntu_noble_arm64.tar"
        private const val MESA_GPU_SHA256 = "56716d9db06d8b1e16b4b4be7279dea351042e4122961fa25b78d2d603f4baeb"
        private const val MESA_GPU_ASSET_SIZE = 47237120L
        private const val GPU_TOOLS_ASSET = "offline/gpu-tools-arm64.tar"
        private const val GPU_TOOLS_SHA256 = "bccec0a1e963ae830b5c6596752bb63d551267dfaa96c47d806c485c6006b3ad"
        private const val DESKTOP_WALLPAPER_ASSET = "desktop/ubuntu-wallpaper.png"
        private const val WHITESUR_THEME_ASSET = "desktop/whitesur-xfce.tar"
        private const val WHITESUR_THEME_SHA256 = "d23b218f5877ad2f0a5c881ec4111d638753d510b3e00b7cdcdb63101a60d268"
        private const val WHITESUR_THEME_ASSET_SIZE = 73728000L
        private const val FETCH_SCRIPT_ASSET = "desktop/doro-fetch.sh"
        private const val FASTFETCH_FILE = "fastfetch-linux-aarch64.deb"
        private const val FASTFETCH_ASSET = "offline/fastfetch/$FASTFETCH_FILE"
        private const val FASTFETCH_SHA256 = "e809ecce6f409dec4857a527f9406628d168313d9f6d33a87b0c19c0535d78b0"
        private const val SPARK_STORE_FILE = "spark-store_5.2.1.0_arm64.deb"
        private const val SPARK_STORE_ASSET = "offline/spark-store/$SPARK_STORE_FILE"
        private const val SPARK_STORE_SHA256 = "ff27031880ba3c56fd76ea40dcc36d41c99da7c9038934982f4fba0ce86d2af7"
        private const val SPARK_STORE_DEPS_ASSET = "offline/spark-store-deps-arm64.tar"
        private const val SPARK_STORE_DEPS_SHA256 = "53390e59042dde4fd4f447011d6fe28c4109c605ee268f6d716f73cf7f2dd789"
        private const val LOCALES_FILE = "locales_2.39-0ubuntu8.8_all.deb"
        private const val LOCALES_ASSET = "offline/factory-fix/$LOCALES_FILE"
        private val FACTORY_FIX_ASSETS = listOf(
            "dbus_1.14.10-4ubuntu4.1_arm64.deb",
            "dbus-bin_1.14.10-4ubuntu4.1_arm64.deb",
            "dbus-daemon_1.14.10-4ubuntu4.1_arm64.deb",
            "dbus-session-bus-common_1.14.10-4ubuntu4.1_all.deb",
            "dbus-system-bus-common_1.14.10-4ubuntu4.1_all.deb",
            "libpolkit-gobject-1-0_124-2ubuntu1.24.04.3_arm64.deb",
            LOCALES_FILE
        )
        private const val COMMAND_TIMEOUT_MS = 30L * 60L * 1000L
        private const val COMMAND_IDLE_TIMEOUT_MS = 5L * 60L * 1000L
        private const val OFFLINE_INSTALL_TIMEOUT_MS = 90L * 60L * 1000L
        private const val OFFLINE_INSTALL_IDLE_TIMEOUT_MS = 0L
        private const val WATCHDOG_POLL_MS = 1_000L
        private const val PROCESS_KILL_GRACE_MS = 5_000L
        private const val MAX_LOG_BYTES = 5L * 1024L * 1024L
    }
}