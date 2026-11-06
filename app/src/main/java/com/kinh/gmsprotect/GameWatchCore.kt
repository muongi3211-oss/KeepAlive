package com.hanh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import java.util.ArrayList

object GameWatchCore {
    const val KEY = "com.miui.game.allowlist"
    const val GMS = "com.google.android.gms"
    private const val PREFS = "game_watch_full_state_v1"
    private const val RUNS = "job_runs"
    private const val URI_EVENTS = "uri_events"
    private const val WRITES = "repair_writes"
    private const val VALUE_CHANGES = "value_changes"
    private const val LAST_VALUE = "last_value"
    private const val VALUE_KNOWN = "value_known"
    private const val LAST_JOB = "last_job"
    private const val LAST_REPAIR = "last_repair"
    private const val LAST_ERROR = "last_error"

    private fun prefs(c: Context): SharedPreferences {
        return c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun isEnabled(c: Context): Boolean = Build.VERSION.SDK_INT >= 24
    fun count(c: Context, key: String): Long = prefs(c).getLong(key, 0L)
    fun jobCount(c: Context): Long = count(c, RUNS)
    fun uriCount(c: Context): Long = count(c, URI_EVENTS)
    fun repairCount(c: Context): Long = count(c, WRITES)
    fun changesCount(c: Context): Long = count(c, VALUE_CHANGES)
    fun lastJob(c: Context): Long = count(c, LAST_JOB)
    fun lastRepair(c: Context): Long = count(c, LAST_REPAIR)
    fun lastError(c: Context): String = prefs(c).getString(LAST_ERROR, "") ?: ""

    fun canWrite(c: Context): Boolean {
        return try {
            Build.VERSION.SDK_INT < 23 || Settings.System.canWrite(c)
        } catch (ex: RuntimeException) {
            false
        }
    }

    fun raw(c: Context): String? {
        return Settings.System.getString(c.contentResolver, KEY)
    }

    fun contains(raw: String?, pkg: String): Boolean {
        if (TextUtils.isEmpty(raw)) return false
        for (part in raw!!.split("_".toRegex()).dropLastWhile { it.isEmpty() }) {
            if (pkg == part.trim()) return true
        }
        return false
    }

    fun containsGms(c: Context): Boolean = contains(raw(c), GMS)
    fun containsSelf(c: Context): Boolean = contains(raw(c), c.packageName)

    private fun append(raw: String?, pkg: String): String {
        if (contains(raw, pkg)) return raw ?: pkg
        if (TextUtils.isEmpty(raw)) return pkg
        return raw + (if (raw.endsWith("_")) "" else "_") + pkg
    }

    @Synchronized
    fun observe(c: Context, from: String) {
        try {
            val value = raw(c)
            val p = prefs(c)
            val known = p.getBoolean(VALUE_KNOWN, false)
            val previous = p.getString(LAST_VALUE, null)
            val changed = known && !(previous == null && value == null || previous == value)
            val e = p.edit().putBoolean(VALUE_KNOWN, true).putString(LAST_VALUE, value)
            if (changed) e.putLong(VALUE_CHANGES, p.getLong(VALUE_CHANGES, 0L) + 1L)
            e.commit()
            if (changed) {
                GameWatchJournal.add(c, "CHANGE", "from=$from old=" + brief(previous) + " new=" + brief(value))
            } else if (!known) {
                GameWatchJournal.add(c, "BASELINE", "from=$from value=" + brief(value))
            }
        } catch (ex: Throwable) {
            recordError(c, "OBSERVE: $ex")
        }
    }

    private fun brief(raw: String?): String {
        if (raw == null) return "<null>"
        return if (raw.length > 200) raw.substring(0, 200) + "..." else raw
    }

    @Synchronized
    private fun recordError(c: Context, message: String) {
        prefs(c).edit().putString(LAST_ERROR, message).commit()
        GameWatchJournal.add(c, "ERROR", message)
    }

    @Synchronized
    fun onJobStart(c: Context, uriTriggered: Boolean) {
        val p = prefs(c)
        val e = p.edit().putLong(RUNS, p.getLong(RUNS, 0L) + 1L).putLong(LAST_JOB, System.currentTimeMillis())
        if (uriTriggered) e.putLong(URI_EVENTS, p.getLong(URI_EVENTS, 0L) + 1L)
        e.commit()
    }

    fun ensureTargets(c: Context, reason: String): Boolean {
        val managed = ArrayList<String>()
        for (e in AdvancedAppStore.list(c)) {
            if (e.enabled && e.pkg.indexOf('_') < 0 && AdvancedAppProtector.isInstalled(c, e.pkg)) {
                managed.add(e.pkg)
            }
        }
        return ensurePackages(c, managed, reason)
    }

    @Synchronized
    fun ensurePackages(c: Context, extra: List<String>?, reason: String): Boolean {
        if (!isEnabled(c)) return false
        if (!canWrite(c)) {
            recordError(c, "WRITE_SETTINGS missing; cause=$reason")
            return false
        }
        return try {
            val wanted = ArrayList<String>()
            wanted.add(GMS)
            wanted.add(c.packageName)
            if (extra != null) {
                for (pkg in extra) {
                    if (AdvancedAppStore.valid(c, pkg) && pkg.indexOf('_') < 0 && !wanted.contains(pkg)) {
                        wanted.add(pkg)
                    }
                }
            }
            val previous = raw(c)
            val hadGms = contains(previous, GMS)
            val hadSelf = contains(previous, c.packageName)
            var candidate = previous
            var missing = 0
            for (pkg in wanted) {
                if (!contains(candidate, pkg)) {
                    candidate = append(candidate, pkg)
                    missing++
                }
            }
            if (missing == 0) {
                if (lastError(c).isNotEmpty()) prefs(c).edit().putString(LAST_ERROR, "").commit()
                GameWatchJournal.add(c, "CHECK", "$reason already_present targets=${wanted.size}")
                return true
            }
            val justBefore = raw(c)
            if (!(previous == null && justBefore == null || previous == justBefore)) {
                GameWatchJournal.add(c, "REPAIR", "$reason concurrent_settings_update, defer")
                return false
            }
            val put = Settings.System.putString(c.contentResolver, KEY, candidate)
            val actual = raw(c)
            var verified = put
            for (pkg in wanted) verified = verified and contains(actual, pkg)
            GameWatchJournal.add(
                c, "REPAIR", "$reason missingGMS=" + !hadGms +
                        " missingAPP=" + !hadSelf + " missingTotal=$missing targets=${wanted.size} put=$put verified=$verified"
            )
            observe(c, "SELF_WRITE")
            if (verified) {
                val p = prefs(c)
                p.edit().putLong(WRITES, p.getLong(WRITES, 0L) + 1L)
                    .putLong(LAST_REPAIR, System.currentTimeMillis())
                    .putString(LAST_ERROR, "")
                    .commit()
            } else {
                recordError(c, "Repair unverified; reason=$reason")
            }
            verified
        } catch (ex: Throwable) {
            recordError(c, "REPAIR $reason: $ex")
            false
        }
    }
}
