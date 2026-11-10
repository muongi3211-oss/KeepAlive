package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent

object GameWatchCore {
    const val GMS = "com.google.android.gms"
    const val GMS_PACKAGE = "com.google.android.gms"

    // Các kiểu kết quả check trạng thái tương thích
    enum class Result {
        VERIFIED, ATTENTION, PENDING, OFF
    }

    fun ensureTargets(context: Context, reason: String) {
        AdvancedAppProtector.maintain(context, reason)
    }

    fun protect(context: Context, reason: String) {
        AdvancedAppProtector.maintain(context, reason)
    }

    fun schedule(context: Context, reason: String) {
        GameWatchScheduler.ensure(context, reason)
    }

    fun rearm(context: Context) {
        ensureTargets(context, "REARM")
    }

    fun startIfEnabled(context: Context) {
        try {
            if (isEnabled(context)) {
                ensureTargets(context, "START_IF_ENABLED")
            }
        } catch (_: Throwable) {}
    }

    fun applyMilletProtection(context: Context) {
        AdvancedAppProtector.maintain(context, "APPLY_MILLET")
    }

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

    fun isEnabled(context: Context): Boolean = true

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
