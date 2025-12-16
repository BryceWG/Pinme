package com.brycewg.pinme.capture

import android.accessibilityservice.AccessibilityService
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.extract.ExtractWorkflow
import com.brycewg.pinme.notification.UnifiedNotificationManager
import com.brycewg.pinme.qrcode.QrCodeDetector
import com.brycewg.pinme.widget.PinMeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class AccessibilityCaptureService : AccessibilityService() {

    companion object {
        private const val TAG = "AccessibilityCaptureService"

        @Volatile
        private var instance: AccessibilityCaptureService? = null

        /**
         * 检查无障碍服务是否已启用
         */
        fun isServiceEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, AccessibilityCaptureService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)

            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponentName) {
                    return true
                }
            }
            return false
        }

        /**
         * 请求截图
         * @return 是否成功发起截图请求
         */
        fun requestCapture(): Boolean {
            val service = instance
            if (service == null) {
                Log.w(TAG, "Service instance is null, cannot capture")
                return false
            }
            service.performCapture()
            return true
        }

        /**
         * 打开无障碍设置页面
         */
        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainExecutor = Executor { command -> mainHandler.post(command) }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要处理无障碍事件
    }

    override fun onInterrupt() {
        // 服务被中断时调用
        Log.d(TAG, "Accessibility service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        Log.d(TAG, "Accessibility service destroyed")
    }

    /**
     * 执行截图
     */
    fun performCapture() {
        Log.d(TAG, "Starting screenshot capture")

        // 延迟截图，等待控制中心/通知栏收起动画完成
        // 不同系统的动画时长可能不同，使用较长的延迟确保兼容性
        serviceScope.launch {
            delay(800)  // 等待 800ms 让控制中心收起
            doTakeScreenshot()
        }
    }

    /**
     * 实际执行截图操作
     */
    private fun doTakeScreenshot() {
        showToast("正在截图...")

        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    Log.d(TAG, "Screenshot captured successfully")
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )
                    screenshot.hardwareBuffer.close()

                    if (bitmap != null) {
                        // 转换为软件位图以便后续处理
                        val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                        bitmap.recycle()
                        processScreenshot(softwareBitmap)
                    } else {
                        showToast("截图失败：无法创建位图")
                    }
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Screenshot failed with error code: $errorCode")
                    // Error codes: 1=INTERNAL_ERROR, 2=NO_ACCESSIBILITY_ACCESS, 3=REQUEST_CANCELLED, 4=TIMED_OUT
                    val errorMessage = when (errorCode) {
                        1 -> "内部错误"
                        2 -> "无障碍权限不足"
                        3 -> "请求被取消"
                        4 -> "截图超时"
                        else -> "未知错误 ($errorCode)"
                    }
                    showToast("截图失败：$errorMessage")
                }
            }
        )
    }

    /**
     * 处理截图
     */
    private fun processScreenshot(bitmap: Bitmap) {
        serviceScope.launch {
            try {
                showToast("截图成功，正在处理")

                // 并行执行二维码检测和 LLM 识别
                val (qrResult, extract) = coroutineScope {
                    val qrDeferred = async { QrCodeDetector.detect(bitmap) }
                    val extractDeferred = async { ExtractWorkflow(this@AccessibilityCaptureService).processScreenshot(bitmap) }
                    qrDeferred.await() to extractDeferred.await()
                }

                val timeText = android.text.format.DateFormat.format("HH:mm", extract.createdAtMillis).toString()

                // 根据提取结果的 title 匹配市场类型
                val matchedItem = findMatchedMarketItem(extract.title)
                val capsuleColor = matchedItem?.capsuleColor
                val durationMinutes = matchedItem?.durationMinutes

                UnifiedNotificationManager(this@AccessibilityCaptureService)
                    .showExtractNotification(
                        title = extract.title,
                        content = extract.content,
                        timeText = timeText,
                        capsuleColor = capsuleColor,
                        emoji = matchedItem?.emoji,
                        qrBitmap = qrResult?.croppedBitmap,
                        extractId = extract.id
                    )

                // 设置定时取消通知
                if (durationMinutes != null && durationMinutes > 0) {
                    scheduleNotificationDismiss(durationMinutes, extract.id)
                }

                PinMeWidget.updateWidgetContent(this@AccessibilityCaptureService)

                val qrInfo = if (qrResult != null) " [含二维码]" else ""
                showToast("${extract.title}: ${extract.content}$qrInfo")
            } catch (e: Exception) {
                Log.e(TAG, "processScreenshot failed", e)
                showToast("模型处理失败：${e.message}")
            } finally {
                bitmap.recycle()
            }
        }
    }

    /**
     * 根据提取结果的 title 匹配市场类型
     */
    private suspend fun findMatchedMarketItem(title: String): com.brycewg.pinme.db.MarketItemEntity? {
        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(this)
        }
        val dao = DatabaseProvider.dao()
        val marketItems = dao.getEnabledMarketItems()

        // 精确匹配
        val exactMatch = marketItems.find { it.title == title }
        if (exactMatch != null) {
            return exactMatch
        }

        // 模糊匹配
        return marketItems.find {
            title.contains(it.title) || it.title.contains(title)
        }
    }

    /**
     * 设置定时取消通知
     */
    private fun scheduleNotificationDismiss(durationMinutes: Int, extractId: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationDismissReceiver::class.java).apply {
            putExtra(NotificationDismissReceiver.EXTRA_EXTRACT_ID, extractId)
        }
        // 使用 extractId 的 hashCode 作为 requestCode，确保每个通知有唯一的定时器
        val requestCode = extractId.hashCode()
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + durationMinutes * 60 * 1000L

        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled notification dismiss for extractId $extractId in $durationMinutes minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule notification dismiss", e)
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}
