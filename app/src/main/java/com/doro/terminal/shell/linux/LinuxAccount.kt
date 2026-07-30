package com.doro.terminal.shell.linux

object LinuxAccount {
    private val usernamePattern = Regex("^[a-z_][a-z0-9_-]{0,31}$")

    fun isValidUsername(username: String): Boolean =
        username != "root" && usernamePattern.matches(username)

    fun validate(username: String, password: String, confirmation: String): String? = when {
        !isValidUsername(username) -> "用户名格式无效"
        password.length < 6 -> "密码至少需要六位"
        password != confirmation -> "两次密码不一致"
        else -> null
    }
}