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
import com.brycewg.pinme.db.DatabaseProvider
import kotlinx.coroutines.runBlocking

class UnifiedNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService<NotificationManager>()!!

    companion object {
        private const val LIVE_CHANNEL_ID = "live_notification_channel"
        private const val LIVE_CHANNEL_NAME = "实况通知"

        private const val NORMAL_CHANNEL_ID = "pinme"
        private const val NORMAL_CHANNEL_NAME = "PinMe"

        const val EXTRACT_NOTIFICATION_ID = 2001
        const val MULTI_EXTRACT_NOTIFICATION_ID_START = 3000 // 多信息模式通知ID起始值

        /** 默认胶囊颜色（橙色） */
        const val DEFAULT_CAPSULE_COLOR = "#FF9800"
        
        // 用于生成唯一通知ID的原子计数器
        private var notificationIdCounter = MULTI_EXTRACT_NOTIFICATION_ID_START
        
        /**
         * 生成唯一的通知ID
         */
        @Synchronized
        fun generateNotificationId(): Int {
            return notificationIdCounter++
        }
    }

    init {
        createNotificationChannels()
    }

    fun cancelExtractNotification() {
        notificationManager.cancel(EXTRACT_NOTIFICATION_ID)
    }
    
    /**
     * 取消指定ID的通知
     */
    fun cancelExtractNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }

    /**
     * @param capsuleColor 胶囊颜色，如 "#FFC107"。传 null 使用默认橙色
     * @param emoji 实况通知卡片右侧显示的 emoji，如 "📦"。传 null 使用默认星星
     * @param qrBitmap 二维码图片，如果检测到二维码则传入，替代 emoji 显示
     * @param notificationId 通知ID，默认使用固定的EXTRACT_NOTIFICATION_ID
     */
    fun showExtractNotification(
        title: String,
        content: String,
        timeText: String = "",
        capsuleColor: String? = null,
        emoji: String? = null,
        qrBitmap: Bitmap? = null,
        notificationId: Int = EXTRACT_NOTIFICATION_ID
    ) {
        if (isLiveCapsuleCustomizationAvailable()) {
            showMeizuLiveNotification(
                title = title,
                content = content,
                timeText = timeText,
                customCapsuleColor = capsuleColor,
                emoji = emoji,
                qrBitmap = qrBitmap,
                notificationId = notificationId
            )
        } else {
            showNormalNotification(
                title = title,
                content = content,
                timeText = timeText,
                qrBitmap = qrBitmap,
                notificationId = notificationId
            )
        }
    }

    private fun createNotificationChannels() {
        val liveChannel = NotificationChannel(
            LIVE_CHANNEL_ID,
            LIVE_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Flyme 实况通知频道"
            enableLights(true)
            enableVibration(true)
            setBypassDnd(true)
            setShowBadge(true)
        }

        val normalChannel = NotificationChannel(
            NORMAL_CHANNEL_ID,
            NORMAL_CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
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
        val call: Bundle? = context.contentResolver.call(
            "content://com.android.systemui.notification.provider".toUri(),
            "isNotificationLiveEnabled",
            null as String?,
            null as Bundle?
        )
        return call?.getBoolean("result", false) ?: false
    }

    fun isLiveCapsuleCustomizationAvailable(): Boolean {
        return Build.MANUFACTURER.equals("meizu", ignoreCase = true) &&
            getFlymeVersion() >= 11 &&
            isFlymeLiveNotificationEnabled(context)
    }

    private fun showMeizuLiveNotification(
        title: String,
        content: String,
        timeText: String,
        customCapsuleColor: String? = null,
        emoji: String? = null,
        qrBitmap: Bitmap? = null,
        notificationId: Int = EXTRACT_NOTIFICATION_ID
    ) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 优先使用传入的颜色，否则使用数据库配置，最后使用默认橙色
        val capsuleBgColor = customCapsuleColor ?: runBlocking {
            if (!DatabaseProvider.isInitialized()) {
                DatabaseProvider.init(context)
            }
            val dao = DatabaseProvider.dao()
            dao.getPreference(Constants.PREF_LIVE_CAPSULE_BG_COLOR)
        } ?: DEFAULT_CAPSULE_COLOR
        val background = Color(capsuleBgColor.toColorInt())
        val contentColor = if (background.luminance() > 0.7f) Color.Black else Color.White

        val capsuleBundle = Bundle().apply {
            putInt("notification.live.capsuleStatus", 1)
            putInt("notification.live.capsuleType", 3)
            putString("notification.live.capsuleContent", content)

            val drawable = ContextCompat.getDrawable(context, R.drawable.ic_notification)?.mutate()
            if (drawable != null) {
                drawable.setTint(contentColor.toArgb())
                putParcelable("notification.live.capsuleIcon", Icon.createWithBitmap(drawable.toBitmap()))
            }
            putInt("notification.live.capsuleBgColor", capsuleBgColor.toColorInt())
            putInt("notification.live.capsuleContentColor", contentColor.toArgb())
        }

        val liveBundle = Bundle().apply {
            putBoolean("is_live", true)
            putInt("notification.live.operation", 0)
            putInt("notification.live.type", 10)
            putBundle("notification.live.capsule", capsuleBundle)
            putInt("notification.live.contentColor", contentColor.toArgb())
        }

        // 根据是否有二维码选择不同的布局
        val remoteViews = if (qrBitmap != null) {
            RemoteViews(context.packageName, R.layout.live_notification_qrcode_card).apply {
                setTextViewText(R.id.live_title, title)
                setTextViewText(R.id.location, content)
                setTextViewText(R.id.live_time, timeText)
                setImageViewBitmap(R.id.qr_code_image, qrBitmap)
            }
        } else {
            RemoteViews(context.packageName, R.layout.live_notification_card).apply {
                setTextViewText(R.id.live_title, title)
                setTextViewText(R.id.location, content)
                setTextViewText(R.id.live_time, timeText)
                setTextViewText(R.id.live_icon, emoji ?: "❌")
            }
        }

        val notification = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .addExtras(liveBundle)
            .setCustomContentView(remoteViews)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    private fun showNormalNotification(
        title: String,
        content: String,
        timeText: String,
        qrBitmap: Bitmap? = null,
        notificationId: Int = EXTRACT_NOTIFICATION_ID
    ) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val header = if (timeText.isBlank()) title else "$title · $timeText"
        val builder = androidx.core.app.NotificationCompat.Builder(context, NORMAL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(header)
            .setContentText(content)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MAX)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC)

        // 有二维码时使用 BigPictureStyle，否则使用 BigTextStyle
        if (qrBitmap != null) {
            builder.setStyle(
                androidx.core.app.NotificationCompat.BigPictureStyle()
                    .bigPicture(qrBitmap)
                    .setBigContentTitle(header)
                    .setSummaryText(content)
            )
        } else {
            builder.setStyle(
                androidx.core.app.NotificationCompat.BigTextStyle().bigText(content)
            )
        }

        notificationManager.notify(notificationId, builder.build())
    }
}

