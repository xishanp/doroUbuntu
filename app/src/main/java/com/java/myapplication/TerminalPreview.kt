package com.java.myapplication

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TerminalPreviewPage(username: String) {
    var tabs by remember { mutableStateOf(listOf(1)) }
    var active by remember { mutableIntStateOf(1) }
    Column(Modifier.fillMaxSize().background(Color(0xFF111111)).padding(top = 48.dp)) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).background(Color(0xFFFFF8FF)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(Modifier.weight(1f)) {
                tabs.forEach { id ->
                    Row(
                        Modifier.weight(1f).height(72.dp)
                            .background(if (id == active) Color(0xFFE6DEE7) else Color(0xFFFFF8FF))
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("标签页 $id", color = Color(0xFF49454F), fontSize = 18.sp)
                        Text("×", color = Color(0xFF6C4EB3), fontSize = 30.sp)
                    }
                }
            }
            IconButton(onClick = {
                val next = (tabs.maxOrNull() ?: 0) + 1
                tabs = tabs + next
                active = next
            }, modifier = Modifier.size(58.dp)) {
                Text("+", color = Color(0xFF6C4EB3), fontSize = 36.sp)
            }
            Spacer(Modifier.size(14.dp))
            Text("⚙", color = Color(0xFF6C4EB3), fontSize = 30.sp)
            Spacer(Modifier.size(24.dp))
            Text("▣", color = Color(0xFF6C4EB3), fontSize = 30.sp)
            Spacer(Modifier.size(18.dp))
        }
        Text(
            "预览模式\n$username@ubuntu:~$ ",
            Modifier.fillMaxSize().padding(18.dp),
            color = Color(0xFFEAEAEA),
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp
        )
    }
}