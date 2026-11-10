package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object AdvancedAppProtector {

    fun maintain(context: Context, trigger: String) {
        try {
            // 1. Kiểm tra và duy trì GMS cùng các ứng dụng người dùng trong danh sách MILLET
            val currentMillet = Settings.System.getString(context.contentResolver, MilletNoRestrict.KEY) ?: ""
            val selectedApps = AdvancedAppStore.list(context)
            
            val targetPackages = mutableSetOf(GameWatchCore.GMS, MilletNoRestrict.PLAY_STORE_PACKAGE, context.packageName)
            for (entry in selectedApps) {
                if (entry.enabled) {
                    targetPackages.add(entry.pkg)
                }
            }

            // Xây dựng lại chuỗi MILLET chuẩn không bị mất mục cũ của Xiaomi
            val builder = StringBuilder(currentMillet)
            for (pkg in targetPackages) {
                if (!currentMillet.contains(pkg)) {
                    if (builder.isNotEmpty() && !builder.endsWith(",")) {
                        builder.append(",")
                    }
                    builder.append(pkg)
                }
            }

            // Ghi ngược lại vào hệ thống thông qua API chuẩn (nếu có quyền WRITE_SETTINGS)
            if (GameWatchCore.canWrite(context)) {
                Settings.System.putString(context.contentResolver, MilletNoRestrict.KEY, builder.toString())
            }

            // 2. Kích hoạt tín hiệu MiPush / Greezer Signal làm mới kết nối nền
            GameWatchCore.refreshMiPush(context)

            log(context, "Maintain executed by trigger: $trigger")
        } catch (e: Throwable) {
            android.util.Log.e("AdvancedAppProtector", "Maintain failed", e)
        }
    }

    fun log(context: Context, message: String) {
        try {
            val prefs = context.getSharedPreferences("gmsprotect_logs", Context.MODE_PRIVATE)
            val logs = prefs.getString("history", "") ?: ""
            val newLog = "[$message] " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()) + "\n$logs"
            prefs.edit().putString("history", newLog.take(2000)).apply()
        } catch (_: Throwable) {}
    }
}
