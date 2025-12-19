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

    private var captureToastEnabled = true

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode != RESULT_OK || data == null) {
                if (captureToastEnabled) {
                    Toast.makeText(this, "未授予截屏权限", Toast.LENGTH_SHORT).show()
                }
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

        // 检查截图模式偏好
        val (useRootCapture, useAccessibilityCapture) = runBlocking {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(this@CaptureActivity)
            }
            val dao = DatabaseProvider.dao()
            val rootEnabled = dao.getPreference(Constants.PREF_USE_ROOT_CAPTURE) == "true"
            val accessibility = dao.getPreference(Constants.PREF_USE_ACCESSIBILITY_CAPTURE) == "true"
            captureToastEnabled = dao.getPreference(Constants.PREF_CAPTURE_TOAST_ENABLED) != "false"
            rootEnabled to accessibility
        }

        // Root 截图（与无障碍互斥，优先级更高）
        if (useRootCapture) {
            if (RootCaptureService.isSuAvailable()) {
                RootCaptureService.start(this)
                finishAndRemoveTask()
                @Suppress("DEPRECATION")
                overridePendingTransition(0, 0)
                return
            } else {
                if (captureToastEnabled) {
                    Toast.makeText(this, "未检测到 Root，将使用传统截图方式", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 无障碍截图
        if (!useRootCapture && useAccessibilityCapture && AccessibilityCaptureService.isServiceEnabled(this)) {
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
