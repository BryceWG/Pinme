package com.brycewg.pinme.capture

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

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
        permissionLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
    }
}
