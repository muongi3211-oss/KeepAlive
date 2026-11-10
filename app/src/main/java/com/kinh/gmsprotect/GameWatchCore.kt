package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent

object GameWatchCore {
    const val GMS = "com.google.android.gms"

    fun refreshMiPush(context: Context) {
        try {
            val intent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
                setPackage(GMS)
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}
    }

    fun canWrite(context: Context): Boolean {
        return try {
            android.provider.Settings.System.canWrite(context)
        } catch (_: Throwable) {
            false
        }
    }

    fun isEnabled(context: Context): Boolean {
        return true
    }

    fun raw(context: Context): String? {
        return try {
            android.provider.Settings.System.getString(context.contentResolver, "game_booster_allowed_packages")
        } catch (_: Throwable) {
            null
        }
    }

    fun contains(raw: String?, pkg: String): Boolean {
        if (raw == null) return false
        return raw.contains(pkg)
    }
}
