package com.brycewg.pinme.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock

internal object WidgetUpdateScheduler {
    const val ACTION_PERIODIC_UPDATE = "com.brycewg.pinme.action.WIDGET_PERIODIC_UPDATE"

    private const val REQUEST_CODE = 7101
    private const val INTERVAL_MS = AlarmManager.INTERVAL_FIFTEEN_MINUTES

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        if (!hasWidgets(appContext)) {
            cancel(appContext)
            return
        }
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + INTERVAL_MS,
            pendingIntent(appContext),
        )
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(AlarmManager::class.java) ?: return
        alarmManager.cancel(pendingIntent(appContext))
    }

    fun hasWidgets(context: Context): Boolean {
        val ids =
            AppWidgetManager.getInstance(context).getAppWidgetIds(
                ComponentName(context, PinMeWidgetReceiver::class.java),
            )
        return ids.isNotEmpty()
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent =
            Intent(context, WidgetAutoUpdateReceiver::class.java).apply {
                action = ACTION_PERIODIC_UPDATE
            }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
