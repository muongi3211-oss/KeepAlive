package com.kinh.gmsprotect

import android.content.Context
import android.os.Build
import android.os.PowerManager

object MilletNoRestrict {

    fun ensureGms(context: Context): Boolean {
        return applyProtection(context)
    }

    fun applyProtection(context: Context): Boolean {
        // Tuân thủ cơ chế API chuẩn
        return true
    }

    // Kiểm tra thực tế GMS qua API PowerManager
    fun containsGms(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations("com.google.android.gms")
            } else {
                true
            }
        } catch (e: Throwable) {
            false
        }
    }

    // Cập nhật: Kiểm tra CH Play (com.android.vending) theo trạng thái gói hệ thống khả dụng
    fun containsPlayStore(context: Context): Boolean {
        return try {
            // Kiểm tra CH Play được cài đặt và đang active trên hệ thống
            val pm = context.packageManager
            val info = pm.getPackageInfo("com.android.vending", 0)
            info.applicationInfo.enabled
        } catch (e: Throwable) {
            false
        }
    }

    // Kiểm tra chính ứng dụng GMS Protect theo API PowerManager
    fun containsSelf(context: Context): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isIgnoringBatteryOptimizations(context.packageName)
            } else {
                true
            }
        } catch (e: Throwable) {
            false
        }
    }
}
