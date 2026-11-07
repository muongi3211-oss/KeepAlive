package com.kinh.gmsprotect

import android.content.Context

object GameWatchEvidence {
    private const val PREFS = "gamewatch_evidence_v1"
    private const val KEY_UI_ACTIVE = "ui_active"
    private const val KEY_LAST_SEEN = "last_seen_ms"

    fun noteUi(context: Context, active: Boolean) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_UI_ACTIVE, active)
                .putLong(KEY_LAST_SEEN, System.currentTimeMillis())
                .apply()
            GameWatchJournal.add(context, "EVIDENCE", "UI active state: $active")
        } catch (_: Throwable) {}
    }

    fun isUiActive(context: Context): Boolean {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getBoolean(KEY_UI_ACTIVE, false)
        } catch (_: Throwable) {
            false
        }
    }

    // Các hàm phụ trợ trạng thái được gọi từ CompatibilityActivity
    fun uriVerified(context: Context): Boolean = getPref(context, "uri_verified", false)
    fun backgroundVerified(context: Context): Boolean = getPref(context, "bg_verified", false)
    fun newPidVerified(context: Context): Boolean = getPref(context, "pid_verified", false)
    fun repairVerified(context: Context): Boolean = getPref(context, "repair_verified", false)
    fun bootVerified(context: Context): Boolean = getPref(context, "boot_verified", false)
    fun lastUri(context: Context): Long {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getLong("last_uri_ms", 0L)
        } catch (_: Throwable) {
            0L
        }
    }

    private fun getPref(context: Context, key: String, defaultVal: Boolean): Boolean {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getBoolean(key, defaultVal)
        } catch (_: Throwable) {
            defaultVal
        }
    }
}
