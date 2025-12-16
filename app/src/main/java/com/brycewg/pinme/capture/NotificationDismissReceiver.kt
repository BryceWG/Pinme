package com.brycewg.pinme.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.brycewg.pinme.notification.UnifiedNotificationManager

/**
 * 接收取消通知的广播（用于定时取消或用户点击关闭按钮）
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationDismiss"
        const val EXTRA_EXTRACT_ID = "extra_extract_id"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val extractId = intent?.getLongExtra(EXTRA_EXTRACT_ID, -1L) ?: -1L
        if (extractId != -1L) {
            Log.d(TAG, "Dismissing notification for extractId: $extractId")
            UnifiedNotificationManager(context).cancelExtractNotification(extractId)
        } else {
            Log.w(TAG, "Received dismiss intent without valid extractId")
        }
    }
}
