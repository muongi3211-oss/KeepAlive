package com.kinh.gmsprotect

import android.content.Context
import android.os.Build
import android.os.PowerManager

object BatterySetupState {
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return try {
            pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (_: Throwable) {
            false
        }
    }

    fun isBackgroundRestricted(context: Context): Boolean {
        return !isIgnoringBatteryOptimizations(context)
    }
}
