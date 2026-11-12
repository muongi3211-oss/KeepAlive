package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.util.ArrayList
import java.util.regex.Pattern

/** User-managed packages: independent from mandatory GMS/Play Store/MILLET core.
 * v1.0.5-beta4 migration: legacy mipush/game flags are ignored intentionally.
 * Saved enabled=false remains disabled; enabled=true always manages both layers.
 */
object AdvancedAppStore {
    private const val PREFS = "advanced_apps_v1"
    private const val LIST = "entries_json"
    const val MAX_APPS = 50
    private val PACKAGE = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*(\\.[A-Za-z_][A-Za-z_0-9]*)+")

    class Entry(
        val pkg: String,
        val enabled: Boolean
    )

    fun prefs(c: Context): SharedPreferences {
        return c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun valid(c: Context, pkg: String?): Boolean {
        return !TextUtils.isEmpty(pkg) && pkg!!.length <= 255 && PACKAGE.matcher(pkg).matches()
                && pkg != c.packageName && pkg != GameWatchCore.GMS
                && pkg != MilletNoRestrict.PLAY_STORE_PACKAGE
    }

    @Synchronized
    fun list(c: Context): List<Entry> {
        val result = ArrayList<Entry>()
        val text = prefs(c).getString(LIST, "[]")
        try {
            val a = JSONArray(text)
            for (i in 0 until a.length() && result.size < MAX_APPS) {
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
                if (!duplicate) {
                    result.add(Entry(pkg, obj.optBoolean("enabled", true)))
                }
            }
        } catch (error: Throwable) {
            // Do not treat a corrupt file as an instruction to overwrite system lists.
            Log.e("AdvancedAppStore", "Unable to parse stored package list", error)
        }
        return result
    }

    @Synchronized
    fun save(c: Context, changed: Entry?): Boolean {
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
            GameWatchJournal.event(c, action, changed.pkg)
        }
        return saved
    }

    @Synchronized
    fun remove(c: Context, pkg: String): Boolean {
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
            GameWatchJournal.event(c, "app_removed", pkg)
        }
        return saved
    }

    private fun write(c: Context, rows: List<Entry>): Boolean {
        try {
            val a = JSONArray()
            for (e in rows) {
                val o = JSONObject().apply {
                    put("pkg", e.pkg)
                    put("enabled", e.enabled)
                }
                a.put(o)
            }
            // commit (not apply) so a reboot/job cannot see half-finished changes.
            return prefs(c).edit().putString(LIST, a.toString()).commit()
        } catch (error: Throwable) {
            Log.e("AdvancedAppStore", "Could not save package list", error)
            return false
        }
    }

    fun hasEnabledEntries(c: Context): Boolean {
        for (e in list(c)) {
            if (e.enabled) return true
        }
        return false
    }

    fun activeCount(c: Context): Int {
        var count = 0
        for (e in list(c)) {
            if (e.enabled) count++
        }
        return count
    }

    internal fun noteMiPush(c: Context, pkg: String, sent: Boolean, error: String?) {
        val edit = prefs(c).edit()
        edit.putLong("mipush_attempt:$pkg", System.currentTimeMillis())
        edit.putBoolean("mipush_sent:$pkg", sent)
        edit.putString("mipush_error:$pkg", error ?: "")
        edit.apply()
    }

    fun lastMiPushAttempt(c: Context, pkg: String): Long {
        return prefs(c).getLong("mipush_attempt:$pkg", 0L)
    }

    fun lastMiPushSent(c: Context, pkg: String): Boolean {
        return prefs(c).getBoolean("mipush_sent:$pkg", false)
    }
}
