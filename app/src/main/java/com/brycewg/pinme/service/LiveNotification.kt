package com.brycewg.pinme.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.brycewg.pinme.BuildConfig

private const val TAG = "LiveNotification"

class LiveNotification : Service() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Service created")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Service started")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Service bound")
        }
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Service destroyed")
        }
    }

}
