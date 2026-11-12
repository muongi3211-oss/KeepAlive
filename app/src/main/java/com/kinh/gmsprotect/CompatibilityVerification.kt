package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * Stores only evidence that has actually happened on this device/ROM build.
 *
 * A successful schedule is not treated as proof that HyperOS can wake the app.
 * The stronger flags below are written only from MilletWatchJobService after
 * system_server has delivered the MILLET content trigger, and after a real
 * repair when GMS was missing.
 */
object CompatibilityVerification {
    private const val PREFS = "compatibility_verification"
    private const val KEY_FINGERPRINT = "fingerprint"
    private const val KEY_WATCHER_TRIGGERED = "watcher_triggered"
    private const val KEY_WATCHER_LAST_MS = "watcher_last_ms"
    private const val KEY_AUTO_REPAIR_VERIFIED = "auto_repair_verified"
    private const val KEY_AUTO_REPAIR_LAST_MS = "auto_repair_last_ms"
    private const val KEY_HYPEROS_PROTECTION_SENT = "hyperos_protection_sent"
    private const val KEY_HYPEROS_PROTECTION_LAST_MS = "hyperos_protection_last_ms"

    fun markWatcherTriggered(context: Context) {
        val prefs = prefs(context)
        val editor = editorForCurrentBuild(prefs)
        editor.putBoolean(KEY_WATCHER_TRIGGERED, true)
            .putLong(KEY_WATCHER_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    fun markAutoRepairVerified(context: Context) {
        val prefs = prefs(context)
        val editor = editorForCurrentBuild(prefs)
        val now = System.currentTimeMillis()
        editor.putBoolean(KEY_WATCHER_TRIGGERED, true)
            .putLong(KEY_WATCHER_LAST_MS, now)
            .putBoolean(KEY_AUTO_REPAIR_VERIFIED, true)
            .putLong(KEY_AUTO_REPAIR_LAST_MS, now)
            .apply()
    }

    fun markHyperOsProtectionRequestSent(context: Context) {
        val prefs = prefs(context)
        val editor = editorForCurrentBuild(prefs)
        editor.putBoolean(KEY_HYPEROS_PROTECTION_SENT, true)
            .putLong(KEY_HYPEROS_PROTECTION_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    fun hyperOsProtectionRequestSent(context: Context): Boolean {
        val prefs = prefs(context)
        return isCurrentBuild(prefs) && prefs.getBoolean(KEY_HYPEROS_PROTECTION_SENT, false)
    }

    fun hyperOsProtectionRequestLastTime(context: Context): Long {
        val prefs = prefs(context)
        if (!isCurrentBuild(prefs)) return 0L
        return prefs.getLong(KEY_HYPEROS_PROTECTION_LAST_MS, 0L)
    }

    fun watcherVerified(context: Context): Boolean {
        val prefs = prefs(context)
        return isCurrentBuild(prefs) && prefs.getBoolean(KEY_WATCHER_TRIGGERED, false)
    }

    fun autoRepairVerified(context: Context): Boolean {
        val prefs = prefs(context)
        return isCurrentBuild(prefs) && prefs.getBoolean(KEY_AUTO_REPAIR_VERIFIED, false)
    }

    fun lastVerifiedTime(context: Context): Long {
        val prefs = prefs(context)
        if (!isCurrentBuild(prefs)) return 0L
        return Math.max(
            prefs.getLong(KEY_WATCHER_LAST_MS, 0L),
            prefs.getLong(KEY_AUTO_REPAIR_LAST_MS, 0L)
        )
    }

    fun needsReverificationAfterSystemUpdate(context: Context): Boolean {
        val prefs = prefs(context)
        val stored = prefs.getString(KEY_FINGERPRINT, "") ?: ""
        return stored.isNotEmpty() && stored != currentFingerprint()
    }

    private fun editorForCurrentBuild(prefs: SharedPreferences): SharedPreferences.Editor {
        val current = currentFingerprint()
        val stored = prefs.getString(KEY_FINGERPRINT, "") ?: ""
        val editor = prefs.edit()
        if (current != stored) {
            editor.remove(KEY_HYPEROS_PROTECTION_SENT)
                .remove(KEY_HYPEROS_PROTECTION_LAST_MS)
                .remove(KEY_WATCHER_TRIGGERED)
                .remove(KEY_WATCHER_LAST_MS)
                .remove(KEY_AUTO_REPAIR_VERIFIED)
                .remove(KEY_AUTO_REPAIR_LAST_MS)
                .putString(KEY_FINGERPRINT, current)
        }
        return editor
    }

    private fun isCurrentBuild(prefs: SharedPreferences): Boolean {
        return currentFingerprint() == prefs.getString(KEY_FINGERPRINT, "")
    }

    private fun currentFingerprint(): String {
        val fingerprint = Build.FINGERPRINT
        return fingerprint ?: ""
    }

    private fun prefs(context: Context): SharedPreferences {
        var app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                app = app.createDeviceProtectedStorageContext()
            } catch (ignored: Throwable) {
                // Fall back to credential-protected storage below.
            }
        }
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
