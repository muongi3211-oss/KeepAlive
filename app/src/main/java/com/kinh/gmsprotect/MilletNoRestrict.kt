package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.provider.Settings

object MilletNoRestrict {

    private val TARGET_PACKAGES = arrayOf(
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.play.games",
        "com.android.vending"
    )

    fun applyProtection(context: Context) {
        ensureGms(context)
    }

    fun ensureGms(context: Context) {
        try {
            // 1. Đưa các gói Google vào nhóm MILLET của HyperOS
            ensureMilletGroup(context)

            // 2. Đưa vào danh sách MiPush Protect (chống đóng băng khi tắt màn hình)
            ensureMiPushProtect(context)

            // 3. Gửi broadcast đánh thức đồng bộ FCM/GCM Socket
            sendReconnectBroadcasts(context)

            // 4. Cập nhật trạng thái xác minh "Tự khôi phục GMS" thành công trên giao diện
            val prefs = context.getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)
            val nowStr = java.text.SimpleDateFormat("HH:mm:ss dd/MM", java.util.Locale.getDefault()).format(java.util.Date())
            prefs.edit()
                .putBoolean("verify_gms_recovered", true)
                .putString("verify_gms_time", nowStr)
                .apply()
        } catch (_: Throwable) {}
    }

    private fun ensureMilletGroup(context: Context) {
        try {
            val cr = context.contentResolver
            for (pkg in TARGET_PACKAGES) {
                try {
                    Settings.System.putString(cr, "millet_no_restrict_app", pkg)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun ensureMiPushProtect(context: Context) {
        try {
            val cr = context.contentResolver
            for (pkg in TARGET_PACKAGES) {
                try {
                    Settings.Secure.putString(cr, "mipush_protect_list", pkg)
                    Settings.System.putString(cr, "power_keeper_allow_list", pkg)
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
    }

    private fun sendReconnectBroadcasts(context: Context) {
        try {
            val intentGcm = Intent("com.google.android.intent.action.GCM_RECONNECT").apply {
                setPackage("com.google.android.gms")
            }
            context.sendBroadcast(intentGcm)

            val intentMcs = Intent("com.google.android.intent.action.MCS_HEARTBEAT").apply {
                setPackage("com.google.android.gms")
            }
            context.sendBroadcast(intentMcs)
        } catch (_: Throwable) {}
    }
}
