package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.provider.Settings

object GameWatchCore {
    const val GMS = "com.google.android.gms"
    const val GMS_PACKAGE = "com.google.android.gms"

    enum class Result {
        VERIFIED, ATTENTION, PENDING, OFF
    }

    fun canWrite(context: Context): Boolean = GameWatchCore.canW(context)
    fun isEnabled(context: Context): Boolean = true
    fun ensureTargets(context: Context, reason: String) = AdvancedAppProtector.maintain(context, reason)
    fun ensureGms(context: Context, reason: String) = AdvancedAppProtector.maintain(context, reason)
    fun protect(context: Context, reason: String) = AdvancedAppProtector.maintain(context, reason)
    fun schedule(context: Context, reason: String) = GameWatchScheduler.ensure(context, reason)
    fun rearm(context: Context) = ensureTargets(context, "REARM")
    fun startIfEnabled(context: Context) {
        try { if (isEnabled(context)) ensureTargets(context, "START_IF_ENABLED") } catch (_: Throwable) {}
    }
    fun applyMilletProtection(context: Context) = AdvancedAppProtector.maintain(context, "APPLY_MILLET")
    
    fun refreshMiPush(context: Context) {
        try {
            val intent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply { setPackage(GMS) }
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

    fun contains(raw: String?, pkg: String): Boolean = raw?.contains(pkg) == true
    fun containsGms(raw: String?): Boolean = contains(raw, GMS)
    fun containsPlayStore(raw: String?): Boolean = contains(raw, MilletNoRestrict.PLAY_STORE_PACKAGE)
    fun containsSelf(context: Context, raw: String?): Boolean = contains(raw, context.packageName)

    fun canW(context: Context): Boolean {
        return try {
            Settings.System.canWrite(context)
        } catch (_: Throwable) {
            false
        }
    }
}

// Top-level compatibility wrappers để các Activity gọi trực tiếp không cần qua object nếu cần
fun canWrite(context: Context): Boolean = GameWatchCore.canWrite(context)
fun ensureGms(context: Context, reason: String) = GameWatchCore.ensureGms(context, reason)
fun protect(context: Context, reason: String) = GameWatchCore.protect(context, reason)
fun containsGms(raw: String?): Boolean = GameWatchCore.containsGms(raw)
fun containsPlayStore(raw: String?): Boolean = GameWatchCore.containsPlayStore(raw)
fun containsSelf(context: Context, raw: String?): Boolean = GameWatchCore.containsSelf(context, raw)
