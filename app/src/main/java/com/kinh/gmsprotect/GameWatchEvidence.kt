package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Process

/**
 * Evidence gathered from actual GAME_WATCH callbacks, never by a synthetic UI test.
 * PASS denotes observed scheduling/repair behavior, not guaranteed freeze immunity.
 * All ROM-sensitive evidence is invalidated when the system fingerprint changes.
 */
object GameWatchEvidence {
    private const val PREFS = "game_watch_compat_evidence_v1"
    private const val ROM = "rom_fingerprint"
    private const val UI_VISIBLE = "ui_visible"
    private const val UI_PID = "last_ui_pid"
    private const val URI = "real_uri"
    private const val BACKGROUND = "background_uri"
    private const val NEW_PID = "new_pid_uri"
    private const val DUAL_REPAIR = "dual_repair"
    private const val BOOT = "boot_rearmed"
    private const val LAST_URI = "last_uri_ms"
    private const val LAST_REPAIR = "last_repair_ms"
    private const val LAST_WAKE = "last_background_ms"
    private const val LAST_BOOT = "last_boot_ms"
    private const val EVENT_COUNT = "uri_count"

    private fun prefs(c: Context): SharedPreferences {
        return c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    private fun fingerprint(): String {
        return Build.FINGERPRINT ?: ""
    }

    private fun current(p: SharedPreferences): Boolean {
        return fingerprint() == p.getString(ROM, "")
    }

    private fun freshEditor(p: SharedPreferences): SharedPreferences.Editor {
        val e = p.edit()
        if (!current(p)) {
            e.clear()
            e.putString(ROM, fingerprint())
        }
        return e
    }

    /** Foreground state is best-effort; PID is the previously seen UI process. */
    @Synchronized
    fun noteUi(c: Context, visible: Boolean) {
        val p = prefs(c)
        freshEditor(p).putBoolean(UI_VISIBLE, visible)
            .putInt(UI_PID, Process.myPid()).commit()
    }

    /** Invoked only by the system-delivered JobService with a real matching URI. */
    @Synchronized
    fun noteUriJob(c: Context, realUri: Boolean) {
        if (!realUri || !GameWatchCore.isEnabled(c)) return
        val p = prefs(c)
        val sameRom = current(p)
        val wasBackground = sameRom && !p.getBoolean(UI_VISIBLE, true)
                && p.getInt(UI_PID, 0) > 0
        val earlierPid = if (sameRom) p.getInt(UI_PID, 0) else 0
        val newPid = wasBackground && earlierPid != Process.myPid()
        val e = freshEditor(p)
            .putBoolean(URI, true)
            .putLong(LAST_URI, System.currentTimeMillis())
            .putLong(EVENT_COUNT, (if (sameRom) p.getLong(EVENT_COUNT, 0L) else 0L) + 1L)
        if (wasBackground) {
            e.putBoolean(BACKGROUND, true).putLong(LAST_WAKE, System.currentTimeMillis())
        }
        if (newPid) e.putBoolean(NEW_PID, true)
        e.commit()
        GameWatchJournal.add(
            c, "COMPAT", "real URI callback; uiBackground=" + wasBackground
                    + " previousUiPid=" + earlierPid + " jobPid=" + Process.myPid()
                    + " newPid=" + newPid
        )
    }

    /** Marks ONLY a genuine URI-triggered missing -> restored dual entry transition. */
    @Synchronized
    fun noteRepair(
        c: Context, realUri: Boolean,
        missingBefore: Boolean, written: Boolean,
        bothPresentAfter: Boolean
    ) {
        if (!GameWatchCore.isEnabled(c) || !realUri || !missingBefore
            || !written || !bothPresentAfter
        ) return
        val p = prefs(c)
        freshEditor(p).putBoolean(DUAL_REPAIR, true)
            .putLong(LAST_REPAIR, System.currentTimeMillis()).commit()
        GameWatchJournal.add(c, "COMPAT", "real URI dual-entry repair verified")
    }

    /** A reboot event (not package-update) re-registered the job and checked both entries. */
    @Synchronized
    fun noteBoot(c: Context, successful: Boolean) {
        if (!successful || !GameWatchCore.isEnabled(c)) return
        val p = prefs(c)
        freshEditor(p).putBoolean(BOOT, true)
            .putLong(LAST_BOOT, System.currentTimeMillis()).commit()
    }

    fun uriVerified(c: Context): Boolean {
        val p = prefs(c)
        return current(p) && p.getBoolean(URI, false)
    }

    fun backgroundVerified(c: Context): Boolean {
        val p = prefs(c)
        return current(p) && p.getBoolean(BACKGROUND, false)
    }

    fun newPidVerified(c: Context): Boolean {
        val p = prefs(c)
        return current(p) && p.getBoolean(NEW_PID, false)
    }

    fun repairVerified(c: Context): Boolean {
        val p = prefs(c)
        return current(p) && p.getBoolean(DUAL_REPAIR, false)
    }

    fun bootVerified(c: Context): Boolean {
        val p = prefs(c)
        return current(p) && p.getBoolean(BOOT, false)
    }

    fun lastUri(c: Context): Long {
        val p = prefs(c)
        return if (current(p)) p.getLong(LAST_URI, 0L) else 0L
    }

    fun lastRepair(c: Context): Long {
        val p = prefs(c)
        return if (current(p)) p.getLong(LAST_REPAIR, 0L) else 0L
    }

    fun realUriCount(c: Context): Long {
        val p = prefs(c)
        return if (current(p)) p.getLong(EVENT_COUNT, 0L) else 0L
    }

    fun needsReverification(c: Context): Boolean {
        val old = prefs(c).getString(ROM, "") ?: ""
        return old.isNotEmpty() && fingerprint() != old
    }
}
