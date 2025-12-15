package com.brycewg.pinme.capture

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.brycewg.pinme.Constants
import com.brycewg.pinme.db.DatabaseProvider
import kotlinx.coroutines.runBlocking

class CaptureActivity : ComponentActivity() {

    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null) {
                Toast.makeText(this, "未授予截屏权限", Toast.LENGTH_SHORT).show()
                finishAndRemoveTask()
                return@registerForActivityResult
            }

            // 启动前台服务执行截屏 (Android 14+ 要求)
            ScreenCaptureService.start(this, result.resultCode, data)
            finishAndRemoveTask()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)  // 禁用退出动画
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查是否开启了无障碍截图模式
        val useAccessibility = runBlocking {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(this@CaptureActivity)
            }
            DatabaseProvider.dao().getPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE) == "true"
        }

        // 如果开启了无障碍模式且服务已启用，使用无障碍截图
        if (useAccessibility && AccessibilityCaptureService.isServiceEnabled(this)) {
            AccessibilityCaptureService.requestCapture()
            finishAndRemoveTask()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
            return
        }

        // 否则使用 MediaProjection 方式
        permissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}
