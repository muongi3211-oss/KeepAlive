package com.kinh.gmsprotect

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import java.util.LinkedHashSet

object MilletNoRestrict {
    private const val TAG = "MilletNoRestrict"

    const val KEY = "MILLET_NO_RESTRICT_APP"
    const val GMS_PACKAGE = "com.google.android.gms"
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    private const val PREFS = "millet_no_restrict_state"
    private const val PREF_LAST_GOOD = "last_good_$KEY"

    enum class Result {
        ALREADY_PRESENT,
        ADDED,
        NO_PERMISSION,
        VERIFY_FAILED,
        FAILED
    }

    fun canWrite(context: Context): Boolean {
        return try {
            Settings.System.canWrite(context.applicationContext)
        } catch (e: Throwable) {
            false
        }
    }

    fun containsGms(context: Context): Boolean = containsPackage(context, GMS_PACKAGE)
    fun containsPlayStore(context: Context): Boolean = containsPackage(context, PLAY_STORE_PACKAGE)
    fun containsSelf(context: Context): Boolean = containsPackage(context, context.applicationContext.packageName)

    fun containsPackage(context: Context, packageName: String): Boolean {
        try {
            val raw = Settings.System.getString(context.applicationContext.contentResolver, KEY)
            return parse(raw).contains(packageName)
        } catch (e: Throwable) {
            return false
        }
    }

    fun ensureGms(context: Context): Result {
        val app = context.applicationContext
        if (!canWrite(app)) return Result.NO_PERMISSION

        try {
            val resolver = app.contentResolver
            val before = Settings.System.getString(resolver, KEY)
            val packages = parse(before)

            val hasGms = packages.contains(GMS_PACKAGE)
            val hasPlayStore = packages.contains(PLAY_STORE_PACKAGE)
            val hasSelf = packages.contains(app.packageName)

            if (hasGms && hasPlayStore && hasSelf) {
                rememberLastGood(app, packages)
                return Result.ALREADY_PRESENT
            }

            if (packages.isEmpty()) {
                val cached = parse(readLastGood(app))
                if (cached.isNotEmpty()) {
                    packages.addAll(cached)
                }
            }

            packages.add(GMS_PACKAGE)
            packages.add(PLAY_STORE_PACKAGE)
            packages.add(app.packageName)
            val merged = TextUtils.join(",", packages)

            val writeAccepted = Settings.System.putString(resolver, KEY, merged)
            if (!writeAccepted) return Result.FAILED

            val after = Settings.System.getString(resolver, KEY)
            val verified = parse(after)
            if (!verified.contains(GMS_PACKAGE) || !verified.contains(PLAY_STORE_PACKAGE) || !verified.contains(app.packageName)) {
                return Result.VERIFY_FAILED
            }

            rememberLastGood(app, verified)
            return Result.ADDED
        } catch (e: Throwable) {
            return Result.FAILED
        }
    }

    fun readRaw(context: Context): String {
        return try {
            Settings.System.getString(context.applicationContext.contentResolver, KEY) ?: ""
        } catch (e: Throwable) {
            ""
        }
    }

    private fun prefsContext(context: Context): Context {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return app.createDeviceProtectedStorageContext()
            } catch (_: Throwable) {}
        }
        return app
    }

    private fun readLastGood(context: Context): String? {
        return try {
            val prefs = prefsContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(PREF_LAST_GOOD, null)
        } catch (_: Throwable) {
            null
        }
    }

    private fun rememberLastGood(context: Context, packages: LinkedHashSet<String>) {
        if (packages.isEmpty()) return
        val normalized = TextUtils.join(",", packages)
        try {
            val prefs = prefsContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = prefs.getString(PREF_LAST_GOOD, null)
            if (normalized != old) {
                prefs.edit().putString(PREF_LAST_GOOD, normalized).apply()
            }
        } catch (_: Throwable) {}
    }

    private fun parse(raw: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        if (raw.isNullOrBlank()) return out
        val normalized = raw.replace(';', ',').replace('\n', ',').replace('\t', ',')
        for (token in normalized.split(",")) {
            val value = token.trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }
}
