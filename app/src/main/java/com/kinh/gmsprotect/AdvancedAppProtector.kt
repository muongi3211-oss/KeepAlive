package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object AdvancedAppProtector {
    fun maintain(context: Context, trigger: String) {
        try {
            val currentMillet = Settings.System.getString(context.contentResolver, MilletNoRestrict.KEY) ?: ""
            val selectedApps = AdvancedAppStore.list(context)
            
            val targetPackages = mutableSetOf(GameWatchCore.GMS, MilletNoRestrict.PLAY_STORE_PACKAGE, context.packageName)
            for (entry in selectedApps) {
                if (entry.enabled) {
                    targetPackages.add(entry.pkg)
                }
            }

            val builder = StringBuilder(currentMillet)
            for (pkg in targetPackages) {
                if (!currentMillet.contains(pkg)) {
                    if (builder.isNotEmpty() && !builder.endsWith(",")) {
                        builder.append(",")
                    }
                    builder.append(pkg)
                }
            }

            if (GameWatchCore.canWrite(context)) {
                Settings.System.putString(context.contentResolver, MilletNoRestrict.KEY, builder.toString())
            }

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
