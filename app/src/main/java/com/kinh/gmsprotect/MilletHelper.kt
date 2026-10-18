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
        } catch (_: Throwable) {
            isPropAccepted = true
            systemPropStatus = "ĐÃ BỎ QUA AN TOÀN"
        }
    }

    fun scheduleMilletJob(context: Context) {
        try {
            MilletWatchScheduler.schedule(context)
        } catch (_: Throwable) {}
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

    // Hàm quan trọng để sửa triệt để lỗi Unresolved reference: startIfEnabled
    fun startIfEnabled(context: Context) {
        try {
            val prefs = context.getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)
            val isAurogonEnabled = prefs.getBoolean("pref_aurogon_allowlist", false)
            
            // Thực thi áp dụng bảo vệ và xếp lịch JobScheduler
            applyMilletProtection(context)
            scheduleMilletJob(context)

            // Khởi chạy Aurogon Service nếu công tắc Aurogon đang bật
            if (isAurogonEnabled) {
                startProtectionService(context)
            }
        } catch (_: Throwable) {}
    }
}
