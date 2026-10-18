package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.os.PowerManager

object FcmKeepAliveHelper {
    fun pingFcmConnection(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = pm?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "GMSProtect:FcmKeepAlive"
        )

        try {
            wakeLock?.acquire(3000)

            val intentGcm = Intent("com.google.android.intent.action.GCM_RECONNECT").apply {
                setPackage("com.google.android.gms")
            }
            context.sendBroadcast(intentGcm)

            val intentMcs = Intent("com.google.android.intent.action.MCS_HEARTBEAT").apply {
                setPackage("com.google.android.gms")
            }
            context.sendBroadcast(intentMcs)
        } catch (_: Throwable) {
        } finally {
            if (wakeLock != null && wakeLock.isHeld) {
                try { wakeLock.release() } catch (_: Throwable) {}
            }
        }
    }
}
