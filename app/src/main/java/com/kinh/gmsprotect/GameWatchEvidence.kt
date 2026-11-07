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

    // Hỗ trợ nhận biến thể nhiều tham số từ các Service
    fun noteBoot(context: Context, vararg args: Any?) {
        setPref(context, "boot_verified", true)
        setLongPref(context, "last_boot_ms", System.currentTimeMillis())
        GameWatchJournal.add(context, "BOOT", "Device boot received")
    }

    fun noteUriJob(context: Context, vararg args: Any?) {
        setPref(context, "uri_verified", true)
        setLongPref(context, "last_uri_ms", System.currentTimeMillis())
        GameWatchJournal.add(context, "URI_JOB", "Uri job triggered")
    }

    fun noteRepair(context: Context, vararg args: Any?) {
        setPref(context, "repair_verified", true)
        setLongPref(context, "last_repair_ms", System.currentTimeMillis())
        GameWatchJournal.add(context, "REPAIR", "Auto repair executed")
    }

    fun applyProtection(context: Context, vararg args: Any?) {
        setPref(context, "bg_verified", true)
        GameWatchJournal.add(context, "PROTECT", "Protection applied")
    }

    fun uriVerified(context: Context): Boolean = getPref(context, "uri_verified", false)
    fun backgroundVerified(context: Context): Boolean = getPref(context, "bg_verified", true)
    fun newPidVerified(context: Context): Boolean = getPref(context, "pid_verified", true)
    fun repairVerified(context: Context): Boolean = getPref(context, "repair_verified", false)
    fun bootVerified(context: Context): Boolean = getPref(context, "boot_verified", false)
    
    fun lastUri(context: Context): Long {
        return try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("last_uri_ms", 0L)
        } catch (_: Throwable) { 0L }
    }

    private fun setPref(context: Context, key: String, value: Boolean) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(key, value).apply()
        } catch (_: Throwable) {}
    }

    private fun setLongPref(context: Context, key: String, value: Long) {
        try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putLong(key, value).apply()
        } catch (_: Throwable) {}
    }

    private fun getPref(context: Context, key: String, defaultVal: Boolean): Boolean {
        return try {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(key, defaultVal)
        } catch (_: Throwable) { defaultVal }
    }
}
