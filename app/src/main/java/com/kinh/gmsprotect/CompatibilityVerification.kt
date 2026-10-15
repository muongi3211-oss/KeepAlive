package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

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
        val editor = editorForCurrentBuild(prefs(context))
        editor.putBoolean(KEY_WATCHER_TRIGGERED, true)
            .putLong(KEY_WATCHER_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    fun markAutoRepairVerified(context: Context) {
        val editor = editorForCurrentBuild(prefs(context))
        val now = System.currentTimeMillis()
        editor.putBoolean(KEY_WATCHER_TRIGGERED, true)
            .putLong(KEY_WATCHER_LAST_MS, now)
            .putBoolean(KEY_AUTO_REPAIR_VERIFIED, true)
            .putLong(KEY_AUTO_REPAIR_LAST_MS, now)
            .apply()
    }

    fun markHyperOsProtectionRequestSent(context: Context) {
        val editor = editorForCurrentBuild(prefs(context))
        editor.putBoolean(KEY_HYPEROS_PROTECTION_SENT, true)
            .putLong(KEY_HYPEROS_PROTECTION_LAST_MS, System.currentTimeMillis())
            .apply()
    }

    fun hyperOsProtectionRequestSent(context: Context): Boolean {
        val p = prefs(context)
        return isCurrentBuild(p) && p.getBoolean(KEY_HYPEROS_PROTECTION_SENT, false)
    }

    private fun editorForCurrentBuild(prefs: SharedPreferences): SharedPreferences.Editor {
        val current = Build.FINGERPRINT ?: ""
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
        return (Build.FINGERPRINT ?: "") == prefs.getString(KEY_FINGERPRINT, "")
    }

    private fun prefs(context: Context): SharedPreferences {
        var app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                app = app.createDeviceProtectedStorageContext()
            } catch (_: Throwable) {}
        }
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }
}
