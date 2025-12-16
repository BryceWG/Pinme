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
import android.graphics.BitmapFactory
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
import com.brycewg.pinme.qrcode.QrCodeDetector
import com.brycewg.pinme.widget.PinMeWidget
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RootCaptureService : Service() {

    companion object {
        private const val TAG = "RootCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val NOTIFICATION_ID = 1002

        @Volatile
        private var cachedSuAvailable: Boolean? = null

        fun isSuAvailable(): Boolean {
            cachedSuAvailable?.let { return it }

            val candidates = listOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/vendor/bin/su",
                "/su/bin/su",
                "/data/local/bin/su",
                "/data/local/xbin/su",
                "/data/local/su"
            )
            if (candidates.any { File(it).exists() }) {
                cachedSuAvailable = true
                return true
            }

            val available = try {
                val process = ProcessBuilder("sh", "-c", "command -v su").start()
                process.outputStream.close()
                val output = process.inputStream.bufferedReader().use { it.readText() }.trim()
                val ok = process.waitFor(1, TimeUnit.SECONDS) && process.exitValue() == 0
                ok && output.isNotBlank()
            } catch (_: Exception) {
                false
            }
            cachedSuAvailable = available
            return available
        }

        fun start(context: Context) {
            val intent = Intent(context, RootCaptureService::class.java)
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

        serviceScope.launch {
            delay(500)
            performCapture()
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
            .setSmallIcon(R.drawable.ic_stat_pin)
            .setOngoing(true)
            .build()
    }

    private suspend fun performCapture() {
        if (!isSuAvailable()) {
            showToast("未检测到 su，无法使用 Root 截图")
            return
        }

        showToast("正在截图...")
        val bitmap = captureScreenViaRoot()
        if (bitmap == null) {
            showToast("Root 截屏失败（请检查 root 授权）")
            return
        }

        showToast("截图成功，正在处理")

        try {
            val (qrResult, extract) = coroutineScope {
                val qrDeferred = async { QrCodeDetector.detect(bitmap) }
                val extractDeferred = async { ExtractWorkflow(this@RootCaptureService).processScreenshot(bitmap) }
                qrDeferred.await() to extractDeferred.await()
            }

            if (qrResult != null) {
                val qrBase64 = qrResult.croppedBitmap.toJpegBase64()
                if (!DatabaseProvider.isInitialized()) {
                    DatabaseProvider.init(this)
                }
                DatabaseProvider.dao().updateExtractQrCode(extract.id, qrBase64)
            }

            val timeText = android.text.format.DateFormat.format("HH:mm", extract.createdAtMillis).toString()

            val matchedItem = findMatchedMarketItem(extract.title)
            val capsuleColor = matchedItem?.capsuleColor
            val durationMinutes = matchedItem?.durationMinutes
            val emoji = extract.emoji ?: matchedItem?.emoji

            UnifiedNotificationManager(this)
                .showExtractNotification(
                    title = extract.title,
                    content = extract.content,
                    timeText = timeText,
                    capsuleColor = capsuleColor,
                    emoji = emoji,
                    qrBitmap = qrResult?.croppedBitmap,
                    extractId = extract.id
                )

            if (durationMinutes != null && durationMinutes > 0) {
                scheduleNotificationDismiss(durationMinutes, extract.id)
            }

            PinMeWidget.updateWidgetContent(this)

            val qrInfo = if (qrResult != null) " [含二维码]" else ""
            showToast("${extract.title}: ${extract.content}$qrInfo")
        } catch (e: Exception) {
            Log.e(TAG, "processScreenshot failed", e)
            showToast("模型处理失败")
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun captureScreenViaRoot(): Bitmap? = withContext(Dispatchers.IO) {
        val bytes = try {
            coroutineScope {
                val process = ProcessBuilder("su", "-c", "screencap -p").start()
                try {
                    val stdoutDeferred = async { process.inputStream.use { it.readAllToBytes() } }
                    val stderrDeferred = async { process.errorStream.use { it.readAllToBytes() } }

                    val exited = process.waitFor(30, TimeUnit.SECONDS)
                    val stdout = runCatching { stdoutDeferred.await() }.getOrDefault(ByteArray(0))
                    val stderr = runCatching { stderrDeferred.await() }.getOrDefault(ByteArray(0))

                    if (!exited) {
                        Log.e(TAG, "su screencap timed out")
                        return@coroutineScope null
                    }

                    val exitCode = process.exitValue()
                    if (exitCode != 0 || stdout.isEmpty()) {
                        val errText = stderr.decodeToString().trim()
                        Log.e(TAG, "su screencap failed: exitCode=$exitCode stderr=$errText")
                        return@coroutineScope null
                    }

                    fixScreencapPng(stdout)
                } finally {
                    try {
                        process.destroy()
                    } catch (_: Exception) {
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "capture via root failed", e)
            null
        } ?: return@withContext null

        return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun InputStream.readAllToBytes(): ByteArray {
        val buffer = ByteArray(16 * 1024)
        val output = ByteArrayOutputStream()
        while (true) {
            val read = read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun fixScreencapPng(input: ByteArray): ByteArray {
        var i = 0
        val output = ByteArrayOutputStream(input.size)
        while (i < input.size) {
            if (i + 2 < input.size &&
                input[i] == 0x0D.toByte() &&
                input[i + 1] == 0x0D.toByte() &&
                input[i + 2] == 0x0A.toByte()
            ) {
                output.write(0x0D)
                output.write(0x0A)
                i += 3
                continue
            }
            output.write(input[i].toInt())
            i++
        }
        return output.toByteArray()
    }

    private suspend fun findMatchedMarketItem(title: String): com.brycewg.pinme.db.MarketItemEntity? {
        if (!DatabaseProvider.isInitialized()) {
            DatabaseProvider.init(this)
        }
        val dao = DatabaseProvider.dao()
        val marketItems = dao.getEnabledMarketItems()

        val exactMatch = marketItems.find { it.title == title }
        if (exactMatch != null) {
            return exactMatch
        }

        return marketItems.find {
            title.contains(it.title) || it.title.contains(title)
        }
    }

    private fun scheduleNotificationDismiss(durationMinutes: Int, extractId: Long) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, NotificationDismissReceiver::class.java).apply {
            putExtra(NotificationDismissReceiver.EXTRA_EXTRACT_ID, extractId)
        }
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
