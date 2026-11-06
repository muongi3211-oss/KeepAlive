package com.kinh.gmsprotect

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

object BatterySetupState {
    private const val TAG = "BatterySetupState"

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm != null && pm.isIgnoringBatteryOptimizations(context.packageName)
        } catch (t: Throwable) {
            Log.w(TAG, "isIgnoringBatteryOptimizations unavailable", t)
            false
        }
    }

    fun isBackgroundRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            am != null && am.isBackgroundRestricted
        } catch (t: Throwable) {
            Log.w(TAG, "isBackgroundRestricted unavailable", t)
            false
        }
    }
}
