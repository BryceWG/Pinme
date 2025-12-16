package com.brycewg.pinme.ui.layouts

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun LogPage() {
    val context = LocalContext.current
    val logEntries = remember { mutableStateOf<List<String>>(emptyList()) }

    // 读取日志文件
    fun loadLogs() {
        val logFile = File(context.filesDir, "app.log")
        if (logFile.exists()) {
            logEntries.value = logFile.readLines()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Button(onClick = { loadLogs() }) {
            Text("刷新日志")
        }

        LazyColumn {
            items(logEntries.value) { log ->
                Text(log)
            }
        }
    }
}
