package com.brycewg.pinme

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.brycewg.pinme.capture.AccessibilityCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.ui.layouts.AppSettings
import com.brycewg.pinme.ui.layouts.ExtractHome
import com.brycewg.pinme.ui.layouts.MarketScreen
import com.brycewg.pinme.ui.theme.StarScheduleTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // 无论用户是否授权，都继续运行应用
        // 用户可以稍后在设置中手动授权
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(this)
        enableEdgeToEdge()

        // 请求通知权限 (Android 13+)
        requestNotificationPermissionIfNeeded()

        // 检查无障碍截图模式是否需要引导用户开启无障碍服务
        checkAccessibilityServiceIfNeeded()

        setContent {
            StarScheduleTheme {
                AppRoot()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }

    private fun checkAccessibilityServiceIfNeeded() {
        lifecycleScope.launch {
            val useAccessibilityCapture = withContext(Dispatchers.IO) {
                val dao = DatabaseProvider.dao()
                dao.getPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE)?.toBoolean() ?: false
            }

            // 如果开启了无障碍截图模式，但无障碍服务未启用，则跳转到设置页面
            if (useAccessibilityCapture && !AccessibilityCaptureService.isServiceEnabled(this@MainActivity)) {
                AccessibilityCaptureService.openAccessibilitySettings(this@MainActivity)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AppRoot() {
    var selected by remember { mutableIntStateOf(0) }
    val title = when (selected) {
        0 -> "PinMe"
        1 -> "市场"
        else -> "设置"
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(title) }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selected == 0,
                    onClick = { selected = 0 },
                    icon = { Icon(Icons.Rounded.History, contentDescription = null) },
                    label = { Text("记录") }
                )
                NavigationBarItem(
                    selected = selected == 1,
                    onClick = { selected = 1 },
                    icon = { Icon(Icons.Rounded.Storefront, contentDescription = null) },
                    label = { Text("市场") }
                )
                NavigationBarItem(
                    selected = selected == 2,
                    onClick = { selected = 2 },
                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selected) {
                0 -> ExtractHome()
                1 -> MarketScreen()
                else -> AppSettings()
            }
        }
    }
}
