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
}
