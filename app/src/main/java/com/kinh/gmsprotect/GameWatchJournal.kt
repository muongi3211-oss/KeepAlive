package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.List
import java.util.Locale
import java.util.regex.Pattern

/**
 * Event-only, bounded, on-device journal. No worker, timer, receiver, network,
 * database or system-setting access. Logging failures MUST NOT affect protection.
 *
 * v1 had a 16K truncated text history. v2 migrates meaningful entries once,
 * retaining the old preference unchanged in case a diagnostic needs it later.
 */
object GameWatchJournal {
    private const val TAG = "GAME_WATCH"
    private const val PREFS = "protect_event_journal_v2"
    private const val HISTORY = "events"
    private const val INIT = "initialized"
    private const val LEGACY_PREFS = "game_watch_full_journal_v1"
    private const val LEGACY_HISTORY = "history"
    private const val LIMIT = 200
    private const val MAX_DETAIL = 360
    private const val MERGE_WINDOW_MS = 5L * 60_000L
    private val OLD_LINE = Pattern.compile(
        "^(\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s+\\[([^]]+)\\]\\s*(.*)$"
    )

    class Event(
        val timeMs: Long,
        val lastMs: Long,
        val count: Int,
        val kind: String, // success / info / change / warning / error
        val code: String,
        val key: String,
        val detail: String // technical payload, never a claimed verified state
    ) {
        @Throws(JSONException::class)
        fun json(): JSONObject {
            return JSONObject().apply {
                put("t", timeMs)
                put("last", lastMs)
                put("n", count)
                put("kind", kind)
                put("code", code)
                put("key", key)
                put("detail", detail)
            }
        }

        companion object {
            fun parse(o: JSONObject): Event {
                val time = o.optLong("t", 0L)
                return Event(
                    time, o.optLong("last", time),
                    Math.max(1, o.optInt("n", 1)), o.optString("kind", "info"),
                    o.optString("code", "other"), o.optString("key", ""),
                    o.optString("detail", "")
                )
            }
        }
    }

    private fun prefs(c: Context): SharedPreferences {
        return c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    /** Existing call sites remain API compatible; routine checks are never saved. */
    fun add(c: Context, category: String?, message: String?) {
        // Preserve the original Logcat diagnostics independently from user-facing history.
        Log.i(TAG, "[$category] $message")
        val raw = message ?: ""
        val cat = category ?: ""
        if ("CHECK" == cat || "BASELINE" == cat || "JOB" == cat
            && !raw.startsWith("END rearm=false")
        ) return
        if ("COMPAT" == cat) {
            if (raw.startsWith("real URI dual-entry repair verified"))
                event(c, "watch_verified", "GameWatchEvidence: $raw")
            else if (raw.contains("failed")) event(c, "game_error", "COMPAT $raw")
            return // URI callback alone is not proof of repair.
        }
        if ("CHANGE" == cat) {
            if (!raw.startsWith("from=SELF_WRITE")) event(c, "game_changed", raw)
            return
        }
        if ("REPAIR" == cat) {
            if (raw.contains("concurrent_settings_update")) event(c, "game_deferred", raw)
            else if (raw.contains("verified=true")) event(c, "game_repaired", raw)
            // Failed write paths already invoke recordError(), so avoid a double entry.
            return
        }
        if ("WATCH" == cat) {
            if (raw.startsWith("schedule=true") && !raw.contains("reason=JOB_REARM"))
                event(c, "watch_registered", raw)
            else if (raw.contains("error") || raw.startsWith("schedule=false"))
                event(c, "watch_error", raw)
            return
        }
        if ("ERROR" == cat) {
            event(c, "game_error", raw)
            return
        }
        if ("ADV" == cat) {
            event(c, "app_error", raw)
            return
        }
        if ("JOB" == cat) {
            event(c, "watch_error", raw)
        }
    }

    /** Called ONLY at existing state transitions, not by an automatic polling loop. */
    @Synchronized
    fun event(c: Context, code: String?, technicalDetail: String?) {
        if (c == null || code.isNullOrEmpty()) return
        try {
            val detail = sanitize(technicalDetail)
            val kind = kindFor(code)
            val key = fingerprint(code, detail)
            val now = System.currentTimeMillis()
            val p = prefs(c)
            val all = read(p)
            if (!p.getBoolean(INIT, false)) migrateLegacy(c, all)
            var merged = false
            for (i in all.indices) {
                val e = all[i]
                if (key == e.key && code == e.code
                    && now >= e.lastMs && now - e.lastMs <= MERGE_WINDOW_MS
                ) {
                    all.removeAt(i)
                    all.add(
                        0, Event(
                            e.timeMs, now, e.count + 1,
                            kind, code, key, detail
                        )
                    )
                    merged = true
                    break
                }
            }
            if (!merged) all.add(0, Event(now, now, 1, kind, code, key, detail))
            persist(p, all)
        } catch (failure: RuntimeException) {
            // Journaling must never break a Xiaomi settings write or a JobService.
            Log.w(TAG, "Optional event storage unavailable", failure)
        }
    }

    /** Returns an immutable-snapshot-like copy; the caller cannot edit stored data. */
    @Synchronized
    fun entries(c: Context): java.util.List<Event> {
        var result = ArrayList<Event>()
        try {
            val p = prefs(c)
            result = read(p)
            if (!p.getBoolean(INIT, false)) {
                migrateLegacy(c, result)
                persist(p, result)
            }
        } catch (failure: RuntimeException) {
            Log.w(TAG, "Unable to read optional journal", failure)
        }
        return result
    }

    /** No locale-specific strings are persisted. Raw export is suitable for bug reports. */
    fun technicalHistory(c: Context): String {
        val out = StringBuilder()
        val iso = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        for (e in entries(c)) {
            out.append(iso.format(Date(e.lastMs)))
                .append(" [").append(e.kind).append("] ")
                .append(e.code)
            if (e.count > 1) out.append(" count=").append(e.count)
                .append(" first=").append(iso.format(Date(e.timeMs)))
            out.append("\n")
            if (e.detail.isNotEmpty()) out.append("  ").append(e.detail).append("\n")
        }
        return out.toString()
    }

    /** Backward-compatible helper for any older UI/diagnostic call sites. */
    fun history(c: Context): String = technicalHistory(c)

    private fun kindFor(code: String): String {
        if (code.endsWith("_error")) return "error"
        if ("game_deferred" == code) return "warning"
        if (code.endsWith("_repaired") || "watch_verified" == code) return "success"
        if (code.startsWith("app_") || "game_changed" == code) return "change"
        return "info"
    }

    private fun sanitize(raw: String?): String {
        if (raw == null) return ""
        val s = raw.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ').trim()
        return if (s.length <= MAX_DETAIL) s else s.substring(0, MAX_DETAIL) + "…"
    }

    private fun fingerprint(code: String, detail: String): String {
        // Most protection events are keyed by effect, not volatile pid/reason.
        // User-app events must remain distinct per package.
        if (code.startsWith("app_added") || code.startsWith("app_enabled")
            || code.startsWith("app_disabled") || code.startsWith("app_removed")
            || "mipush_requested_app" == code
        ) return "$code:$detail"
        if (code.endsWith("_error")) {
            // Do not merge unrelated failures merely because both say ERROR.
            val signature = detail.replace("(?i)(reason|cause|pid)=[^ ]+".toRegex(), "$1=*")
                .replace("\\b[0-9]{4,}\\b".toRegex(), "#")
            return "$code:" + if (signature.length > 100) signature.substring(0, 100) else signature
        }
        return code
    }

    private fun read(p: SharedPreferences): ArrayList<Event> {
        val list = ArrayList<Event>()
        val json = p.getString(HISTORY, "[]")
        try {
            val a = JSONArray(json)
            for (i in 0 until a.length() && list.size < LIMIT) {
                val o = a.optJSONObject(i)
                if (o != null && o.optLong("t", 0L) > 0) list.add(Event.parse(o))
            }
        } catch (invalid: JSONException) {
            // Preserve the unreadable original for diagnostic recovery.
            if (!p.contains("corrupt_backup"))
                p.edit().putString("corrupt_backup", json).apply()
            Log.w(TAG, "Unreadable journal JSON; original backed up", invalid)
        }
        return list
    }

    private fun persist(p: SharedPreferences, all: java.util.List<Event>) {
        val a = JSONArray()
        for (i in 0 until all.size && i < LIMIT) {
            try {
                a.put(all[i].json())
            } catch (invalid: JSONException) {
                Log.w(TAG, "Skipping invalid event", invalid)
            }
        }
        p.edit().putString(HISTORY, a.toString()).putBoolean(INIT, true).apply()
    }

    private fun migrateLegacy(c: Context, into: ArrayList<Event>) {
        val old = c.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getString(LEGACY_HISTORY, "")
        if (old.isNullOrEmpty()) return
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val now = System.currentTimeMillis()
        val lines = old.split("\r?\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (line in lines) {
            // Legacy order: newest -> oldest; preserve that order.
            if (into.size >= LIMIT) break
            val match = OLD_LINE.matcher(line)
            if (!match.matches()) continue
            val cat = match.group(2)
            val msg = match.group(3)
            val code = legacyCode(cat, msg) ?: continue
            var whenTime = now
            try {
                whenTime = format.parse("$year-${match.group(1)}").time
                if (whenTime > now + 12L * 3600_000L) {
                    whenTime = format.parse("${year - 1}-${match.group(1)}").time
                }
            } catch (ignored: ParseException) {
            }
            val detail = sanitize(msg)
            into.add(
                Event(
                    whenTime, whenTime, 1, kindFor(code), code,
                    fingerprint(code, detail), detail
                )
            )
        }
    }

    private fun legacyCode(cat: String?, msg: String?): String? {
        if ("ERROR" == cat) return "game_error"
        if ("ADV" == cat) return "app_error"
        if ("CHANGE" == cat && !msg.isNullOrEmpty() && !msg.startsWith("from=SELF_WRITE")) return "game_changed"
        if ("REPAIR" == cat) {
            return if (!msg.isNullOrEmpty() && msg.contains("concurrent_settings_update"))
                "game_deferred"
            else if (!msg.isNullOrEmpty() && msg.contains("verified=true")) "game_repaired" else "game_error"
        }
        if ("COMPAT" == cat && !msg.isNullOrEmpty() && msg.contains("dual-entry repair verified")) return "watch_verified"
        if ("WATCH" == cat) {
            if (!msg.isNullOrEmpty() && msg.startsWith("schedule=true") && !msg.contains("reason=JOB_REARM"))
                return "watch_registered"
            if (!msg.isNullOrEmpty() && (msg.contains("error") || msg.startsWith("schedule=false"))) return "watch_error"
        }
        return null
    }
}
