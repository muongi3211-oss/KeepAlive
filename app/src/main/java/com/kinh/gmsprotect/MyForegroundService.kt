package com.kinh.gmsprotect

import android.app.Service
import android.content.Intent
import android.os.IBinder

class MyForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            GameWatchCore.applyMilletProtection(applicationContext)
            GameWatchCore.ensureTargets(applicationContext, "ForegroundService")
        } catch (_: Throwable) {}
        return START_STICKY
    }
}
