package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.os.Build

object MilletHelper {
    var systemPropStatus: String = "ĐÃ ÁP DỤNG"
    var isPropAccepted: Boolean = true

    fun applyMilletProtection(context: Context) {
        try {
            MilletNoRestrict.applyProtection(context)
            isPropAccepted = true
            systemPropStatus = "ĐÃ ÁP DỤNG"
        } catch (e: Throwable) {
            isPropAccepted = true
            systemPropStatus = "ĐÃ BỎ QUA AN TOÀN"
        }
    }

    fun schedule(context: Context): Boolean {
        return scheduleMilletJob(context)
    }

    fun scheduleMilletJob(context: Context): Boolean {
        return try {
            MilletWatchScheduler.schedule(context)
        } catch (_: Throwable) {
            false
        }
    }

    fun startProtectionService(context: Context) {
        try {
            val intent = Intent(context, AurogonAllowlistService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (_: Throwable) {}
    }
}
