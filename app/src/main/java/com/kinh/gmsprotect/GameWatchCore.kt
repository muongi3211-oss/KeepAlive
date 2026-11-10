package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.provider.Settings

object GameWatchCore {
    const val GMS = "com.google.android.gms"
    const val GMS_PACKAGE = "com.google.android.gms"

    // Định nghĩa enum Result dùng cho giao diện check trạng thái
    enum class Result {
        VERIFIED, ATTENTION, PENDING, OFF
    }

    fun canWrite(context: Context): Boolean {
        return try {
            Settings.System.canWrite(context)
        } catch (_: Throwable) {
            false
        }
    }

    fun isEnabled(context: Context): Boolean = true

    fun ensureTargets(context: Context, reason: String) {
        AdvancedAppProtector.maintain(context, reason)
    }

    fun ensureGms(context: Context, reason: String) {
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

    fun raw(context: Context): String? {
        return try {
            Settings.System.getString(context.contentResolver, "game_booster_allowed_packages")
        } catch (_: Throwable) {
            null
        }
    }

    fun contains(raw: String?, pkg: String): Boolean {
        if (raw == null) return false
        return raw.contains(pkg)
    }

    fun containsGms(raw: String?): Boolean {
        return contains(raw, GMS)
    }

    fun containsPlayStore(raw: String?): Boolean {
        return contains(raw, MilletNoRestrict.PLAY_STORE_PACKAGE)
    }

    fun containsSelf(context: Context, raw: String?): Boolean {
        return contains(raw, context.packageName)
    }
}
