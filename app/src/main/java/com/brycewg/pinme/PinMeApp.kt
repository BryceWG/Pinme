package com.brycewg.pinme

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import com.brycewg.pinme.widget.PinMeWidget
import com.brycewg.pinme.widget.WidgetAutoUpdateReceiver
import com.brycewg.pinme.widget.WidgetUpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PinMeApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var nightMode = Configuration.UI_MODE_NIGHT_UNDEFINED

    private val screenOnReceiver = WidgetAutoUpdateReceiver()

    private val configCallback =
        object : ComponentCallbacks2 {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val newNight = newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (newNight == nightMode) return
                nightMode = newNight
                appScope.launch {
                    PinMeWidget.updateWidgetContent(this@PinMeApp)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onLowMemory() = Unit

            override fun onTrimMemory(level: Int) = Unit
        }

    override fun onCreate() {
        super.onCreate()
        nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        registerReceiver(
            screenOnReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        registerComponentCallbacks(configCallback)
        WidgetUpdateScheduler.schedule(this)
    }
}
