package com.kinh.gmsprotect

import android.app.Service
import android.content.Intent
import android.os.IBinder

class AurogonAllowlistService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            GameWatchCore.ensureTargets(applicationContext, "AurogonAllowlistService")
        } catch (_: Throwable) {}
        return START_STICKY
    }
}
