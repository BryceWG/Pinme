package com.brycewg.pinme.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.core.graphics.drawable.toBitmap
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import com.brycewg.pinme.Constants
import com.brycewg.pinme.MainActivity
import com.brycewg.pinme.R
import com.brycewg.pinme.capture.NotificationDismissReceiver
import com.brycewg.pinme.db.DatabaseProvider
import kotlinx.coroutines.runBlocking

class UnifiedNotificationManager(
    private val context: Context,
) {
    private val notificationManager = context.getSystemService<NotificationManager>()!!

    companion object {
        private const val LIVE_CHANNEL_ID = "live_notification_channel"
        private const val LIVE_CHANNEL_NAME = "实况通知"

        private const val NORMAL_CHANNEL_ID = "pinme"
        private const val NORMAL_CHANNEL_NAME = "PinMe"

        /** 通知 ID 基础值，实际 ID = BASE + extractId */
        private const val NOTIFICATION_ID_BASE = 2000

        /** 默认胶囊颜色（橙色） */
        const val DEFAULT_CAPSULE_COLOR = "#FF9800"

        /**
         * 将颜色与白色混合：40% 原色 + 60% 白色
         */
        fun blendWithWhite(color: Int): Int {
            val r = ((android.graphics.Color.red(color) * 0.4 + 255 * 0.6)).toInt()
            val g = ((android.graphics.Color.green(color) * 0.4 + 255 * 0.6)).toInt()
            val b = ((android.graphics.Color.blue(color) * 0.4 + 255 * 0.6)).toInt()
            return android.graphics.Color.rgb(r, g, b)
        }

        /** 活动通知的 extractId 集合，用于跟踪当前显示的通知 */
        private val activeNotifications =
            java.util.concurrent.ConcurrentHashMap
                .newKeySet<Long>()

        /**
         * 添加活动通知
         */
        fun addActiveNotification(extractId: Long) {
            activeNotifications.add(extractId)
        }

        /**
         * 移除活动通知
         */
        fun removeActiveNotification(extractId: Long) {
            activeNotifications.remove(extractId)
        }

        /**
         * 检查通知是否活动
         */
        fun isNotificationActive(extractId: Long): Boolean = activeNotifications.contains(extractId)

        /**
         * 获取所有活动通知的 extractId
         */
        fun getActiveNotificationIds(): Set<Long> = activeNotifications.toSet()

        /**
         * 根据 extractId 计算通知 ID
         */
        fun getNotificationId(extractId: Long): Int = NOTIFICATION_ID_BASE + (extractId % Int.MAX_VALUE).toInt()
    }

    init {
        createNotificationChannels()
    }

    /**
     * 取消指定 extractId 的通知
     */
    fun cancelExtractNotification(extractId: Long) {
        val notificationId = getNotificationId(extractId)
        notificationManager.cancel(notificationId)
        removeActiveNotification(extractId)
    }

    /**
     * 取消所有活动的提取通知
     */
    fun cancelAllExtractNotifications() {
        getActiveNotificationIds().forEach { extractId ->
            cancelExtractNotification(extractId)
        }
    }

    /**
     * 将正方形图片填充为 2:1 的宽幅图片，防止 BigPictureStyle 裁切
     */
    private fun padBitmapToAspectRatio(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        // 目标宽度：高度 * 2
        val targetWidth = (height * 2).coerceAtLeast(width)

        if (targetWidth <= width) return bitmap

        val output = Bitmap.createBitmap(targetWidth, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        // 填充白色背景
        canvas.drawColor(android.graphics.Color.WHITE)
        // 居中绘制原图
        val left = (targetWidth - width) / 2f
        canvas.drawBitmap(bitmap, left, 0f, null)
        return output
    }

    /**
     * 仅当传入的 ID 对应的通知存在时才取消
     * @return true 如果通知被取消，false 如果通知不存在
     */
    fun cancelExtractNotificationIfExists(extractId: Long): Boolean {
        if (isNotificationActive(extractId)) {
            cancelExtractNotification(extractId)
            return true
        }
        return false
    }

    /**
     * @param capsuleColor 胶囊颜色，如 "#FFC107"。传 null 使用默认橙色
     * @param emoji 实况通知卡片右侧显示的 emoji，如 "📦"。传 null 使用默认星星
     * @param qrBitmap 二维码图片，如果检测到二维码则传入，替代 emoji 显示
     * @param extractId 对应的数据库记录 ID，用于标识和管理通知
     */
    fun showExtractNotification(
        title: String,
        content: String,
        timeText: String = "",
        capsuleColor: String? = null,
        emoji: String? = null,
        qrBitmap: Bitmap? = null,
        sourcePackage: String? = null,
        extractId: Long,
    ) {
        addActiveNotification(extractId)
        val notificationId = getNotificationId(extractId)
        val isParseError = title == Constants.PARSE_ERROR_TITLE
        val isModelError = title == Constants.MODEL_ERROR_TITLE
        val isFailureNotification = isParseError || isModelError
        val resolvedCapsuleColor =
            when {
                isParseError -> Constants.PARSE_ERROR_CAPSULE_COLOR
                isModelError -> Constants.MODEL_ERROR_CAPSULE_COLOR
                else -> capsuleColor
            }
        val resolvedEmoji =
            when {
                isParseError -> Constants.PARSE_ERROR_EMOJI
                isModelError -> Constants.MODEL_ERROR_EMOJI
                else -> emoji
            }
        val resolvedQrBitmap = if (isFailureNotification) null else qrBitmap
        if (isLiveCapsuleCustomizationAvailable()) {
            showMeizuLiveNotification(
                title = title,
                content = content,
                timeText = timeText,
                customCapsuleColor = resolvedCapsuleColor,
                emoji = resolvedEmoji,
                qrBitmap = resolvedQrBitmap,
                sourcePackage = sourcePackage,
                notificationId = notificationId,
                extractId = extractId,
            )
        } else if (isGoogleLiveNotificationAvailable()) {
            showGoogleLiveNotification(
                title = title,
                content = content,
                timeText = timeText,
                qrBitmap = resolvedQrBitmap,
                notificationId = notificationId,
                sourcePackage = sourcePackage,
                extractId = extractId,
            )
        } else {
            showNormalNotification(
                title = title,
                content = content,
                timeText = timeText,
                qrBitmap = resolvedQrBitmap,
                notificationId = notificationId,
                sourcePackage = sourcePackage,
                extractId = extractId,
            )
        }
    }

    private fun createNotificationChannels() {
        val liveChannel =
            NotificationChannel(
                LIVE_CHANNEL_ID,
                LIVE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Flyme 实况通知频道"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                setShowBadge(true)
            }

        val normalChannel =
            NotificationChannel(
                NORMAL_CHANNEL_ID,
                NORMAL_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "PinMe 通知"
                enableLights(true)
                enableVibration(true)
                setBypassDnd(true)
                setShowBadge(true)
            }

        notificationManager.createNotificationChannel(liveChannel)
        notificationManager.createNotificationChannel(normalChannel)
    }

    private fun getFlymeVersion(): Int {
        val display = Build.DISPLAY ?: return -1
        val match = Regex("Flyme\\s*([0-9]+)").find(display)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull() ?: -1
    }

    private fun isFlymeLiveNotificationEnabled(context: Context): Boolean {
        if (context.checkSelfPermission("flyme.permission.READ_NOTIFICATION_LIVE_STATE") != PackageManager.PERMISSION_GRANTED) {
            Log.e("LiveUtil", "Missing permission: flyme.permission.READ_NOTIFICATION_LIVE_STATE")
            return false
        }
        val call: Bundle? =
            context.contentResolver.call(
                "content://com.android.systemui.notification.provider".toUri(),
                "isNotificationLiveEnabled",
                null as String?,
                null as Bundle?,
            )
        return call?.getBoolean("result", false) ?: false
    }

    fun isLiveCapsuleCustomizationAvailable(): Boolean =
        Build.VERSION.SDK_INT >= 26 && Build.MANUFACTURER.equals("meizu", ignoreCase = true) &&
            getFlymeVersion() >= 11 &&
            isFlymeLiveNotificationEnabled(context)

    fun isGoogleLiveNotificationAvailable(): Boolean {
        // Android 16 (API 36) introduces promoted notifications
        return Build.VERSION.SDK_INT >= 36 && notificationManager.canPostPromotedNotifications()
    }

    /**
     * 根据文本长度计算合适的字体大小和行数
     * @return Pair<textSizeSp, maxLines>
     */
    private fun calculateTextStyle(text: String): Pair<Float, Int> {
        val length = text.length
        return when {
            length <= 7 -> 30f to 1
            length <= 10 -> 24f to 1
            length <= 18 -> 20f to 2
            length <= 30 -> 18f to 2
            else -> 16f to 2
        }
    }

    private fun showMeizuLiveNotification(
        title: String,
        content: String,
        timeText: String,
        customCapsuleColor: String? = null,
        emoji: String? = null,
        qrBitmap: Bitmap? = null,
        sourcePackage: String? = null,
        notificationId: Int,
        extractId: Long,
    ) {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // 优先使用传入的颜色，否则使用数据库配置，最后使用默认橙色
        val capsuleBgColor =
            customCapsuleColor ?: runBlocking {
                if (!DatabaseProvider.isInitialized()) {
                    DatabaseProvider.init(context)
                }
                val dao = DatabaseProvider.dao()
                dao.getPreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR)
            } ?: DEFAULT_CAPSULE_COLOR
        val background = Color(capsuleBgColor.toColorInt())
        val contentColor = if (background.luminance() > 0.7f) Color.Black else Color.White

        val capsuleBundle =
            Bundle().apply {
                putInt("notification.live.capsuleStatus", 1)
                putInt("notification.live.capsuleType", 1)
                putString("notification.live.capsuleContent", content)
                putString("notification.live.capsuleTitle", content)
                // 使用圆环图标
                val drawable = ContextCompat.getDrawable(context, R.drawable.ic_capsule_ring)?.mutate()
                if (drawable != null) {
                    drawable.setTint(contentColor.toArgb())
                    putParcelable("notification.live.capsuleIcon", Icon.createWithBitmap(drawable.toBitmap()))
                }
                putInt("notification.live.capsuleBgColor", capsuleBgColor.toColorInt())
                putInt("notification.live.capsuleContentColor", contentColor.toArgb())
            }

        val liveBundle =
            Bundle().apply {
                putBoolean("is_live", true)
                putInt("notification.live.operation", 0)
                putInt("notification.live.type", 10)
                putBundle("notification.live.capsule", capsuleBundle)
                putInt("notification.live.contentColor", contentColor.toArgb())
            }

        // 关闭按钮的 PendingIntent（传递 extractId 以便取消特定通知）
        val dismissIntent =
            Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra(NotificationDismissReceiver.EXTRA_EXTRACT_ID, extractId)
            }
        val dismissPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId, // 使用 notificationId 作为 requestCode 确保每个通知有唯一的 PendingIntent
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // 计算撕开区域的混合颜色（40% 胶囊色 + 60% 白色）
        val tearAreaColor = blendWithWhite(capsuleBgColor.toColorInt())

        // 计算文本样式（字体大小和行数）
        val (textSize, maxLines) = calculateTextStyle(content)
        val useInlineTime = maxLines > 1

        // 根据是否有二维码选择不同的布局
        val jumpEnabled = isSourceAppJumpEnabled()
        val openSourceAppPendingIntent =
            buildOpenSourceAppPendingIntent(
                sourcePackage,
                notificationId,
                jumpEnabled,
            )
        val remoteViews =
            if (qrBitmap != null) {
                RemoteViews(context.packageName, R.layout.live_notification_qrcode_card).apply {
                    setTextViewText(R.id.live_title, title)
                    setTextViewText(R.id.location, content)
                    setTextViewText(R.id.live_time, timeText)
                    setTextViewText(R.id.live_time_inline, timeText)
                    setViewVisibility(R.id.live_time, if (useInlineTime) View.GONE else View.VISIBLE)
                    setViewVisibility(R.id.live_time_inline, if (useInlineTime) View.VISIBLE else View.GONE)
                    setImageViewBitmap(R.id.qr_code_image, qrBitmap)
                    setOnClickPendingIntent(R.id.btn_close, dismissPendingIntent)
                    if (openSourceAppPendingIntent != null) {
                        setOnClickPendingIntent(R.id.live_title, openSourceAppPendingIntent)
                        setOnClickPendingIntent(R.id.btn_view_source, openSourceAppPendingIntent)
                        setViewVisibility(R.id.btn_view_source, View.VISIBLE)
                    } else {
                        setViewVisibility(R.id.btn_view_source, View.GONE)
                    }
                    // 设置撕开区域和锯齿的颜色
                    setInt(R.id.btn_close, "setBackgroundColor", tearAreaColor)
                    setInt(R.id.ticket_perforation, "setColorFilter", tearAreaColor)
                    // 动态设置字体大小和行数
                    setTextViewTextSize(R.id.location, TypedValue.COMPLEX_UNIT_SP, textSize)
                    setInt(R.id.location, "setMaxLines", maxLines)
                }
            } else {
                RemoteViews(context.packageName, R.layout.live_notification_card).apply {
                    setTextViewText(R.id.live_title, title)
                    setTextViewText(R.id.location, content)
                    setTextViewText(R.id.live_time, timeText)
                    setTextViewText(R.id.live_icon, emoji ?: "❌")
                    setOnClickPendingIntent(R.id.btn_close, dismissPendingIntent)
                    if (openSourceAppPendingIntent != null) {
                        setOnClickPendingIntent(R.id.live_title, openSourceAppPendingIntent)
                        setOnClickPendingIntent(R.id.btn_view_source, openSourceAppPendingIntent)
                        setViewVisibility(R.id.btn_view_source, View.VISIBLE)
                    } else {
                        setViewVisibility(R.id.btn_view_source, View.GONE)
                    }
                    // 设置撕开区域和锯齿的颜色
                    setInt(R.id.btn_close, "setBackgroundColor", tearAreaColor)
                    setInt(R.id.ticket_perforation, "setColorFilter", tearAreaColor)
                    // 动态设置字体大小和行数
                    setTextViewTextSize(R.id.location, TypedValue.COMPLEX_UNIT_SP, textSize)
                    setInt(R.id.location, "setMaxLines", maxLines)
                }
            }

        val notification =
            Notification
                .Builder(context, LIVE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle(title)
                .setContentText(content)
                .addExtras(liveBundle)
                .setCustomContentView(remoteViews)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showGoogleLiveNotification(
        title: String,
        content: String,
        timeText: String,
        qrBitmap: Bitmap? = null,
        notificationId: Int,
        sourcePackage: String? = null,
        extractId: Long,
    ) {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // 删除按钮的 PendingIntent
        val dismissIntent =
            Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra(NotificationDismissReceiver.EXTRA_EXTRACT_ID, extractId)
            }
        val dismissPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val header = if (content.isBlank()) title else "$title · $content"
        val builder =
            androidx.core.app.NotificationCompat
                .Builder(context, NORMAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle(header)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setRequestPromotedOngoing(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .setShortCriticalText(content.take(6))
                .addAction(0, "关闭", dismissPendingIntent)

        // 有二维码时使用 BigPictureStyle，否则使用 BigTextStyle
        if (qrBitmap != null) {
            builder.setStyle(
                androidx.core.app.NotificationCompat
                    .BigPictureStyle()
                    .bigPicture(padBitmapToAspectRatio(qrBitmap))
                    .setBigContentTitle(header)
                    .setSummaryText(content),
            )
        } else {
            builder.setStyle(
                androidx.core.app.NotificationCompat
                    .BigTextStyle()
                    .bigText(content),
            )
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun showNormalNotification(
        title: String,
        content: String,
        timeText: String,
        qrBitmap: Bitmap? = null,
        notificationId: Int,
        sourcePackage: String? = null,
        extractId: Long,
    ) {
        val launchIntent =
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        // 删除按钮的 PendingIntent
        val dismissIntent =
            Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra(NotificationDismissReceiver.EXTRA_EXTRACT_ID, extractId)
            }
        val dismissPendingIntent =
            PendingIntent.getBroadcast(
                context,
                notificationId,
                dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val header = if (timeText.isBlank()) title else "$title · $timeText"
        val builder =
            androidx.core.app.NotificationCompat
                .Builder(context, NORMAL_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pin)
                .setContentTitle(header)
                .setContentText(content)
                .setContentIntent(pendingIntent)
                .setAutoCancel(false)
                .setOngoing(true)
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
                .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(0, "关闭", dismissPendingIntent)

        // 有二维码时使用 BigPictureStyle，否则使用 BigTextStyle
        if (qrBitmap != null) {
            builder.setStyle(
                androidx.core.app.NotificationCompat
                    .BigPictureStyle()
                    .bigPicture(padBitmapToAspectRatio(qrBitmap))
                    .setBigContentTitle(header)
                    .setSummaryText(content),
            )
        } else {
            builder.setStyle(
                androidx.core.app.NotificationCompat
                    .BigTextStyle()
                    .bigText(content),
            )
        }

        notificationManager.notify(notificationId, builder.build())
    }

    private fun buildOpenSourceAppPendingIntent(
        sourcePackage: String?,
        notificationId: Int,
        jumpEnabled: Boolean,
    ): PendingIntent? {
        if (!jumpEnabled) return null
        val packageName = sourcePackage?.trim().orEmpty()
        val intent =
            Intent(context, OpenSourceAppReceiver::class.java).apply {
                putExtra(OpenSourceAppReceiver.EXTRA_PACKAGE_NAME, packageName)
            }
        return PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun isSourceAppJumpEnabled(): Boolean =
        runBlocking {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
            }
            DatabaseProvider.dao().getPreference(Constants.PREF_SOURCE_APP_JUMP_ENABLED) == "true"
        }
}
