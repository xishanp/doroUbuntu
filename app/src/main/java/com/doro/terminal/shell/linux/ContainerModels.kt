package com.doro.terminal.shell.linux

data class TerminalLaunchSpec(
    val shellPath: String,
    val workingDirectory: String,
    val arguments: Array<String>,
    val environment: Array<String>
)

data class ContainerConfig(
    val distribution: String = "ubuntu",
    val version: String = "24.04",
    val username: String,
    val initialized: Boolean = true
)

enum class InitializationStep(val progress: Int) {
    EXTRACT_SYSTEM(10),
    CREATE_CONTAINER(35),
    CREATE_USER(50),
    CONFIGURE_SUDO(62),
    INITIALIZE_ENVIRONMENT(85),
    SAVE_CONFIGURATION(100)
}

data class InitializationProgress(
    val step: InitializationStep,
    val message: String,
    val percent: Int = step.progress
)

data class InitializationState(
    val username: String,
    val components: Set<InstallComponent>,
    val completedStep: InitializationStep? = null,
    val failedMessage: String? = null
)

enum class InstallComponent(
    val label: String,
    val category: String,
    val required: Boolean = false,
    val defaultSelected: Boolean = false,
    val supported: Boolean = true,
    val packages: List<String> = emptyList()
) {
    UBUNTU("Ubuntu 24.04", "基础组件", required = true, defaultSelected = true),
    SUDO("sudo", "基础组件", required = true, defaultSelected = true, packages = listOf("sudo")),
    APT("APT", "基础组件", required = true, defaultSelected = true),
    GIT("Git", "基础组件", defaultSelected = true, packages = listOf("git")),
    PYTHON("Python", "基础组件", defaultSelected = true, packages = listOf("python3", "python3-pip", "python3-venv")),
    CHINESE("中文语言环境", "基础组件", defaultSelected = true, packages = listOf("locales", "language-pack-zh-hans", "fonts-noto-cjk")),

    DESKTOP("XFCE 图形桌面", "桌面环境", defaultSelected = true),
    XFCE("XFCE 桌面", "内置组件", packages = listOf("xfce4", "xfce4-goodies")),
    X11("X11", "内置组件", packages = listOf("dbus-x11", "x11-xserver-utils", "xterm")),
    PULSEAUDIO("PulseAudio", "内置组件", packages = listOf("pulseaudio")),
    FILE_MANAGER("文件管理器", "内置组件", packages = listOf("thunar", "mousepad")),
    FIREFOX("Mozilla Firefox", "内置应用"),

    OLLAMA("Ollama", "AI 开发", supported = false),
    LLAMA_CPP("llama.cpp", "AI 开发", supported = false),
    PYTHON_AI("Python AI 环境", "AI 开发", packages = listOf("python3-numpy", "python3-scipy", "python3-pandas", "python3-sklearn")),
    GPU("CUDA/Turnip（支持时）", "AI 开发", supported = false),
    JUPYTER("JupyterLab", "AI 开发", packages = listOf("jupyter-notebook")),

    WPS("WPS Office", "办公软件", supported = false),
    QQ("QQ", "办公软件", supported = false),
    WECHAT("微信", "办公软件", supported = false),
    VSCODE("VS Code（code-server）", "办公软件", supported = false),

    NODE("Node.js", "开发环境", packages = listOf("nodejs", "npm")),
    JAVA("Java", "开发环境", packages = listOf("default-jdk")),
    GCC("GCC/G++", "开发环境", packages = listOf("build-essential")),
    CMAKE("CMake", "开发环境", packages = listOf("cmake")),
    RUST("Rust", "开发环境", packages = listOf("rustc", "cargo")),
    GO("Go", "开发环境", packages = listOf("golang-go"))
}