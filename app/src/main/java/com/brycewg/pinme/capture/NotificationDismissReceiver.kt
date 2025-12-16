package com.brycewg.pinme.capture

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.brycewg.pinme.notification.UnifiedNotificationManager

/**
 * 接收定时取消通知的广播
 */
class NotificationDismissReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "NotificationDismiss"
        const val REQUEST_CODE = 1002
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val notificationId = intent?.getIntExtra("notification_id", UnifiedNotificationManager.EXTRACT_NOTIFICATION_ID) 
            ?: UnifiedNotificationManager.EXTRACT_NOTIFICATION_ID
        Log.d(TAG, "Dismissing notification with ID: $notificationId")
        UnifiedNotificationManager(context).cancelExtractNotification(notificationId)
    }
}
