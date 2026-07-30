package com.java.myapplication

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class TerminalWorkspace {
    var sessions by mutableStateOf(listOf(1))
    var activeSession by mutableIntStateOf(1)
    val terminalViews = mutableMapOf<Int, DoroTerminalPane>()

    fun closeAll() {
        terminalViews.values.forEach(DoroTerminalPane::close)
        terminalViews.clear()
    }
}
