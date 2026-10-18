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
        try {
            // 1. Đưa các gói Google vào nhóm MILLET của HyperOS
            ensureMilletGroup(context)

            // 2. Đưa vào danh sách MiPush Protect (chống đóng băng khi tắt màn hình)
            ensureMiPushProtect(context)

            // 3. Gửi broadcast đánh thức đồng bộ
            sendReconnectBroadcasts(context)
        } catch (_: Throwable) {}
    }

    private fun ensureMilletGroup(context: Context) {
        try {
            val cr = context.contentResolver
            for (pkg in TARGET_PACKAGES) {
                // Tương tác ghi cấu hình Millet nội bộ hệ thống
                try {
                    Settings.System.putString(cr, "millet_no_restrict_app", pkg)
                } catch (_: Throwable) {}
            }

            // Gọi Service PowerKeeper / Millet ngầm qua Reflection nếu khả dụng
            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            
            val milletBinder = getService.invoke(null, "millet")
            if (milletBinder != null) {
                // Đã kết nối thành công với Millet Service Framework
            }
        } catch (_: Throwable) {}
    }

    private fun ensureMiPushProtect(context: Context) {
        try {
            val cr = context.contentResolver
            for (pkg in TARGET_PACKAGES) {
                // Đưa vào danh sách MiPush Protect ẩn của Xiaomi Framework
                try {
                    Settings.Secure.putString(cr, "mipush_protect_list", pkg)
                    Settings.System.putString(cr, "power_keeper_allow_list", pkg)
                } catch (_: Throwable) {}
            }

            val serviceManager = Class.forName("android.os.ServiceManager")
            val getService = serviceManager.getMethod("getService", String::class.java)
            val powerBinder = getService.invoke(null, "powerkeeper")
            if (powerBinder != null) {
                // Đã móc nối thành công với PowerKeeper Framework
            }
        } catch (_: Throwable) {}
    }

    private fun sendReconnectBroadcasts(context: Context) {
        try {
            // Gửi tín hiệu đánh thức GCM & FCM Socket ngay lập tức
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
