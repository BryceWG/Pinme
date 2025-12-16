package com.brycewg.pinme.capture

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.brycewg.pinme.R
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.extract.ExtractWorkflow
import com.brycewg.pinme.notification.UnifiedNotificationManager
import com.brycewg.pinme.widget.PinMeWidget
import com.brycewg.pinme.qrcode.QrCodeDetector
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.nio.ByteBuffer
import kotlin.coroutines.resume

class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1001
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mediaProjectionManager: MediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)

        if (resultCode != android.app.Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Invalid result: resultCode=$resultCode, data=${resultData != null}")
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        serviceScope.launch {
            // 等待CaptureActivity完全消失和系统UI恢复
            delay(500)
            performCapture(resultCode, resultData)
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "截屏服务",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于截屏识别的前台服务"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PinMe")
            .setContentText("正在截屏识别…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    private suspend fun performCapture(resultCode: Int, resultData: Intent) {
        try {
            val bitmap = captureScreen(resultCode, resultData)
            if (bitmap == null) {
                showToast("截屏失败")
                return
            }

            showToast("截图成功，正在处理")

            try {
                // 并行执行二维码检测和 LLM 识别
                val (qrResult, extracts) = coroutineScope {
                    val qrDeferred = async { QrCodeDetector.detect(bitmap) }
                    val extractDeferred = async { ExtractWorkflow(this@ScreenCaptureService).processScreenshot(bitmap) }
                    qrDeferred.await() to extractDeferred.await()
                }

                // 处理多个提取结果
                for (extract in extracts) {
                    // 如果检测到二维码，保存到数据库
                    if (qrResult != null) {
                        val qrBase64 = qrResult.croppedBitmap.toJpegBase64()
                        if (!DatabaseProvider.isInitialized()) {
                            DatabaseProvider.init(this)
                        }
                        DatabaseProvider.dao().updateExtractQrCode(extract.id, qrBase64)
                    }

                    val timeText = android.text.format.DateFormat.format("HH:mm", extract.createdAtMillis).toString()

                    // 根据提取结果的 title 匹配市场类型（获取颜色和时长）
                    val matchedItem = findMatchedMarketItem(extract.title)
                    val capsuleColor = matchedItem?.capsuleColor
                    val durationMinutes = matchedItem?.durationMinutes

                    // 优先使用 LLM 生成的 emoji，回退到类型预设的 emoji
                    val emoji = extract.emoji ?: matchedItem?.emoji
                    
                    // 为每个通知生成唯一ID
                    val notificationId = com.brycewg.pinme.notification.UnifiedNotificationManager.generateNotificationId()

                    UnifiedNotificationManager(this)
                        .showExtractNotification(
                            title = extract.title,
                            content = extract.content,
                            timeText = timeText,
                            capsuleColor = capsuleColor,
                            emoji = emoji,
                            qrBitmap = qrResult?.croppedBitmap,
                            notificationId = notificationId
                        )

                    // 设置定时取消通知
                    if (durationMinutes != null && durationMinutes > 0) {
                        scheduleNotificationDismiss(durationMinutes, notificationId)
                    }

                    val qrInfo = if (qrResult != null) " [含二维码]" else ""
                    showToast("${extract.title}: ${extract.content}$qrInfo")
                }

                PinMeWidget.updateWidgetContent(this)
            } catch (e: Exception) {
                Log.e(TAG, "processScreenshot failed", e)
                showToast("模型处理失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "performCapture failed", e)
            showToast("截屏失败: ${e.message}")
        }
    }

    private suspend fun captureScreen(resultCode: Int, resultData: Intent): Bitmap? = withContext(Dispatchers.Default) {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val bounds = windowManager.currentWindowMetrics.bounds
        val width = bounds.width()
        val height = bounds.height()
        val densityDpi = resources.displayMetrics.densityDpi

        if (width <= 0 || height <= 0) return@withContext null

        var projection: MediaProjection? = null
        var imageReader: ImageReader? = null
        var virtualDisplay: VirtualDisplay? = null

        try {
            projection = withContext(Dispatchers.Main) {
                mediaProjectionManager.getMediaProjection(resultCode, resultData)
            } ?: return@withContext null

            // 注册 callback (Android 14+ 要求)
            projection.registerCallback(object : MediaProjection.Callback() {}, mainHandler)

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            virtualDisplay = projection.createVirtualDisplay(
                "pinme_screen_capture",
                width,
                height,
                densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                null
            )

            val image = withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine<android.media.Image?> { cont ->
                    val listener = ImageReader.OnImageAvailableListener { reader ->
                        try {
                            val img = reader.acquireLatestImage()
                            if (img != null && cont.isActive) {
                                try {
                                    reader.setOnImageAvailableListener(null, mainHandler)
                                } catch (_: Exception) {
                                }
                                cont.resume(img)
                            }
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resume(null)
                        }
                    }
                    imageReader.setOnImageAvailableListener(listener, mainHandler)
                    cont.invokeOnCancellation {
                        try {
                            imageReader.setOnImageAvailableListener(null, mainHandler)
                        } catch (_: Exception) {
                        }
                    }
                }
            } ?: return@withContext null

            try {
                val planes = image.planes
                if (planes.isEmpty()) return@withContext null
                val buffer: ByteBuffer = planes[0].buffer
                buffer.rewind()
                val pixelStride = planes[0].pixelStride
                val rowStride = planes[0].rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmapWidth = width + (rowPadding / pixelStride)
                val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(buffer)
                return@withContext Bitmap.createBitmap(bitmap, 0, 0, width, height)
            } finally {
                try { image.close() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "captureScreen failed", e)
            return@withContext null
        } finally {
            try { imageReader?.setOnImageAvailableListener(null, mainHandler) } catch (_: Exception) {}
            try { virtualDisplay?.release() } catch (_: Exception) {}
            try { imageReader?.close() } catch (_: Exception) {}
            try { projection?.stop() } catch (_: Exception) {}
        }
    }

    /**
     * 根据提取结果的 title 匹配市场类型
     * @return 匹配到的市场条目，未匹配返回 null
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

        // 模糊匹配（title 包含市场类型名称，或市场类型名称包含 title）
        return marketItems.find {
            title.contains(it.title) || it.title.contains(title)
        }
    }

    /**
     * 设置定时取消通知
     */
    private fun scheduleNotificationDismiss(durationMinutes: Int, notificationId: Int = com.brycewg.pinme.notification.UnifiedNotificationManager.EXTRACT_NOTIFICATION_ID) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationDismissReceiver::class.java).apply {
            putExtra("notification_id", notificationId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            notificationId, // 使用通知ID作为requestCode，确保每个通知有独立的PendingIntent
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val triggerTime = System.currentTimeMillis() + durationMinutes * 60 * 1000L

        try {
            // 尝试使用精确闹钟（需要 SCHEDULE_EXACT_ALARM 权限）
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            } else {
                // 回退到非精确闹钟
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.d(TAG, "Scheduled notification dismiss in $durationMinutes minutes")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule notification dismiss", e)
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    /**
     * 将 Bitmap 转换为 JPEG 格式的 Base64 字符串
     */
    private fun Bitmap.toJpegBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
