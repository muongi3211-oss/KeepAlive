package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList
import java.util.regex.Pattern

object AdvancedAppStore {
    private const val PREFS = "advanced_apps_v1"
    private const val LIST = "entries_json"
    const val MAX_APPS = 50
    private val PACKAGE = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")

    class Entry(val pkg: String, val enabled: Boolean)

    private fun prefs(c: Context): SharedPreferences {
        return c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun valid(c: Context, pkg: String?): Boolean {
        return try {
            !TextUtils.isEmpty(pkg) && pkg!!.length <= 255 && PACKAGE.matcher(pkg).matches() &&
                    pkg != c.packageName && pkg != GameWatchCore.GMS && pkg != MilletNoRestrict.PLAY_STORE_PACKAGE
        } catch (_: Throwable) {
            false
        }
    }

    @Synchronized
    fun list(c: Context): List<Entry> {
        val result = ArrayList<Entry>()
        try {
            val text = prefs(c).getString(LIST, "[]") ?: "[]"
            val a = JSONArray(text)
            for (i in 0 until a.length()) {
                if (result.size >= MAX_APPS) break
                val obj = a.optJSONObject(i) ?: continue
                val pkg = obj.optString("pkg", "")
                if (!valid(c, pkg)) continue
                var duplicate = false
                for (e in result) {
                    if (e.pkg == pkg) {
                        duplicate = true
                        break
                    }
                }
                if (!duplicate) result.add(Entry(pkg, obj.optBoolean("enabled", true)))
            }
        } catch (error: Throwable) {
            Log.e("AdvancedAppStore", "Unable to parse stored package list", error)
        }
        return result
    }

    @Synchronized
    fun save(c: Context, changed: Entry?): Boolean {
        try {
            if (changed == null || !valid(c, changed.pkg)) return false
            val rows = list(c).toMutableList()
            var replaced = false
            var stateChanged = false
            for (i in rows.indices) {
                if (rows[i].pkg == changed.pkg) {
                    stateChanged = rows[i].enabled != changed.enabled
                    rows[i] = changed
                    replaced = true
                    break
                }
            }
            if (!replaced) {
                if (rows.size >= MAX_APPS) return false
                rows.add(changed)
            }
            val saved = write(c, rows)
            if (saved && (!replaced || stateChanged)) {
                val action = if (!replaced) "app_added" else if (changed.enabled) "app_enabled" else "app_disabled"
                try { GameWatchJournal.event(c, action, changed.pkg) } catch (_: Throwable) {}
            }
            return saved
        } catch (_: Throwable) {
            return false
        }
    }

    @Synchronized
    fun remove(c: Context, pkg: String): Boolean {
        try {
            val rows = list(c).toMutableList()
            var removed = false
            for (i in rows.indices.reversed()) {
                if (rows[i].pkg == pkg) {
                    rows.removeAt(i)
                    removed = true
                }
            }
            val saved = removed && write(c, rows)
            if (saved) {
                try { GameWatchJournal.event(c, "app_removed", pkg) } catch (_: Throwable) {}
            }
            return saved
        } catch (_: Throwable) {
            return false
        }
    }

    private fun write(c: Context, rows: List<Entry>): Boolean {
        return try {
            val a = JSONArray()
            for (e in rows) {
                val o = JSONObject().apply {
                    put("pkg", e.pkg)
                    put("enabled", e.enabled)
                }
                a.put(o)
            }
            prefs(c).edit().putString(LIST, a.toString()).commit()
        } catch (error: Throwable) {
            Log.e("AdvancedAppStore", "Could not save package list", error)
            false
        }
    }

    fun hasEnabledEntries(c: Context): Boolean {
        return try {
            for (e in list(c)) if (e.enabled) return true
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun activeCount(c: Context): Int {
        return try {
            var count = 0
            for (e in list(c)) if (e.enabled) count++
            count
        } catch (_: Throwable) {
            0
        }
    }

    fun noteMiPush(c: Context, pkg: String, sent: Boolean, error: String?) {
        try {
            val edit = prefs(c).edit()
            edit.putLong("mipush_attempt:$pkg", System.currentTimeMillis())
            edit.putBoolean("mipush_sent:$pkg", sent)
            edit.putString("mipush_error:$pkg", error ?: "")
            edit.apply()
        } catch (_: Throwable) {}
    }

    fun lastMiPushAttempt(c: Context, pkg: String): Long {
        return try {
            prefs(c).getLong("mipush_attempt:$pkg", 0L)
        } catch (_: Throwable) {
            0L
        }
    }

    fun lastMiPushSent(c: Context, pkg: String): Boolean {
        return try {
            prefs(c).getBoolean("mipush_sent:$pkg", false)
        } catch (_: Throwable) {
            false
        }
    }
}
