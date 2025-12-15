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
        Log.d(TAG, "Dismissing notification due to timeout")
        UnifiedNotificationManager(context).cancelExtractNotification()
    }
}
