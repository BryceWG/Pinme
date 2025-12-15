package com.brycewg.pinme

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DatabaseProvider.init(this)
        enableEdgeToEdge()

        setContent {
            StarScheduleTheme {
                AppRoot()
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
