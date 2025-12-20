package com.brycewg.pinme.share

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.widget.Toast
import com.brycewg.pinme.R
import com.brycewg.pinme.db.DatabaseProvider
import com.brycewg.pinme.db.ExtractEntity
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

class ShareProcessorService : Service() {

    companion object {
        private const val TAG = "ShareProcessorService"
        private const val CHANNEL_ID = "share_processor_channel"
        private const val NOTIFICATION_ID = 1003

        private const val EXTRA_TYPE = "type"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_SOURCE_PACKAGE = "source_package"

        private const val TYPE_IMAGE = "image"
        private const val TYPE_TEXT = "text"

        fun startWithImage(context: Context, uri: Uri, sourcePackage: String?) {
            val intent = Intent(context, ShareProcessorService::class.java).apply {
                putExtra(EXTRA_TYPE, TYPE_IMAGE)
                putExtra(EXTRA_URI, uri.toString())
                putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage)
            }
            context.startForegroundService(intent)
        }

        fun startWithText(context: Context, text: String, sourcePackage: String?) {
            val intent = Intent(context, ShareProcessorService::class.java).apply {
                putExtra(EXTRA_TYPE, TYPE_TEXT)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_SOURCE_PACKAGE, sourcePackage)
            }
            context.startForegroundService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        val type = intent?.getStringExtra(EXTRA_TYPE)
        val sourcePackage = intent?.getStringExtra(EXTRA_SOURCE_PACKAGE)

        serviceScope.launch {
            when (type) {
                TYPE_IMAGE -> {
                    val uriString = intent.getStringExtra(EXTRA_URI)
                    if (uriString != null) {
                        processImage(Uri.parse(uriString), sourcePackage)
                    }
                }
                TYPE_TEXT -> {
                    val text = intent.getStringExtra(EXTRA_TEXT)
                    if (text != null) {
                        processText(text, sourcePackage)
                    }
                }
            }
            stopSelf()
        }

        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "分享处理",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "用于处理分享内容的前台服务"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("PinMe")
            .setContentText("正在处理分享内容…")
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setOngoing(true)
            .build()
    }

    private suspend fun processImage(uri: Uri, sourcePackage: String?) {
        try {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(this)
            }

            val bitmap = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    BitmapFactory.decodeStream(stream)
                } ?: throw IllegalStateException("无法读取图片")
            }

            val (qrResult, extract) = coroutineScope {
                val qrDeferred = async { QrCodeDetector.detect(bitmap) }
                val extractDeferred = async {
                    ExtractWorkflow(this@ShareProcessorService).processScreenshot(bitmap, sourcePackage)
                }
                qrDeferred.await() to extractDeferred.await()
            }

            if (qrResult != null) {
                val qrBase64 = withContext(Dispatchers.IO) {
                    qrResult.croppedBitmap.toJpegBase64()
                }
                DatabaseProvider.dao().updateExtractQrCode(extract.id, qrBase64)
            }

            val marketItem = withContext(Dispatchers.IO) {
                DatabaseProvider.dao().getEnabledMarketItems()
                    .find { it.title == extract.title }
            }

            val timeText = android.text.format.DateFormat.format("HH:mm", extract.createdAtMillis).toString()

            val notificationManager = UnifiedNotificationManager(this)
            notificationManager.showExtractNotification(
                title = extract.title,
                content = extract.content,
                timeText = timeText,
                capsuleColor = marketItem?.capsuleColor,
                emoji = extract.emoji ?: marketItem?.emoji,
                qrBitmap = qrResult?.croppedBitmap,
                extractId = extract.id,
                sourcePackage = extract.sourcePackage
            )

            PinMeWidget.updateWidgetContent(this)

            val qrInfo = if (qrResult != null) " [含二维码]" else ""
            showToast("${extract.title}: ${extract.content}$qrInfo")

            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "processImage failed", e)
            showToast("识别失败：${e.message}")
        }
    }

    private suspend fun processText(text: String, sourcePackage: String?) {
        try {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(this)
            }

            val parsed = withContext(Dispatchers.IO) {
                ExtractWorkflow(this@ShareProcessorService).extractFromText(text)
            }
            val createdAt = System.currentTimeMillis()
            val entity = ExtractEntity(
                title = parsed.title,
                content = parsed.content,
                emoji = parsed.emoji,
                source = "share_text",
                sourcePackage = sourcePackage,
                rawModelOutput = "",
                createdAtMillis = createdAt
            )
            val id = withContext(Dispatchers.IO) {
                DatabaseProvider.dao().insertExtract(entity)
            }

            val marketItem = withContext(Dispatchers.IO) {
                DatabaseProvider.dao().getEnabledMarketItems()
                    .find { it.title == entity.title }
            }

            val notificationManager = UnifiedNotificationManager(this)
            val timeText = android.text.format.DateFormat.format("HH:mm", createdAt).toString()
            notificationManager.showExtractNotification(
                title = entity.title,
                content = entity.content,
                timeText = timeText,
                capsuleColor = marketItem?.capsuleColor,
                emoji = entity.emoji ?: marketItem?.emoji,
                extractId = id,
                sourcePackage = entity.sourcePackage
            )

            PinMeWidget.updateWidgetContent(this)

            showToast("${entity.title}: ${entity.content}")
        } catch (e: Exception) {
            Log.e(TAG, "processText failed", e)
            showToast("识别失败：${e.message}")
        }
    }

    private fun showToast(message: String) {
        mainHandler.post {
            Toast.makeText(this@ShareProcessorService, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun Bitmap.toJpegBase64(): String {
        val stream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 85, stream)
        val bytes = stream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }
}
