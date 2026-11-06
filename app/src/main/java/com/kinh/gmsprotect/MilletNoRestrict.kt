package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import java.util.LinkedHashSet

object MilletNoRestrict {
    private const val TAG = "MilletNoRestrict"

    const val KEY = "MILLET_NO_RESTRICT_APP"
    const val GMS_PACKAGE = GmsProtector.GMS_PACKAGE
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
            val app = context.applicationContext
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                return app.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SETTINGS) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            Settings.System.canWrite(app)
        } catch (t: Throwable) {
            Log.w(TAG, "canWrite FAILED error=" + errorOf(t))
            false
        }
    }

    fun canRead(context: Context): Boolean {
        return try {
            Settings.System.getString(context.applicationContext.contentResolver, KEY)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "canRead FAILED error=" + errorOf(t))
            false
        }
    }

    fun containsGms(context: Context): Boolean = containsPackage(context, GMS_PACKAGE, "containsGms")
    fun containsPlayStore(context: Context): Boolean = containsPackage(context, PLAY_STORE_PACKAGE, "containsPlayStore")
    fun containsSelf(context: Context): Boolean = containsPackage(context, context.applicationContext.packageName, "containsSelf")
    fun containsPackage(context: Context, packageName: String): Boolean = containsPackage(context, packageName, "containsPackage")

    private fun containsPackage(context: Context, packageName: String, logName: String): Boolean {
        return try {
            val raw = Settings.System.getString(context.applicationContext.contentResolver, KEY)
            parse(raw).contains(packageName)
        } catch (t: Throwable) {
            Log.w(TAG, "$logName FAILED error=" + errorOf(t))
            false
        }
    }

    fun ensureGms(context: Context): Result {
        val app = context.applicationContext
        val writeAllowed = canWrite(app)
        Log.i(TAG, "ensureGms canWrite=$writeAllowed")

        if (!writeAllowed) return Result.NO_PERMISSION

        return try {
            val before = Settings.System.getString(app.contentResolver, KEY)
            val packages = parse(before)
            val hasGmsBefore = packages.contains(GMS_PACKAGE)
            val hasPlayStoreBefore = packages.contains(PLAY_STORE_PACKAGE)
            val hasSelfBefore = packages.contains(app.packageName)
            Log.i(TAG, "before containsGms=$hasGmsBefore containsPlayStore=$hasPlayStoreBefore containsSelf=$hasSelfBefore count=${packages.size} value=" + safeValue(before))

            if (hasGmsBefore && hasPlayStoreBefore && hasSelfBefore) {
                rememberLastGood(app, packages)
                Log.i(TAG, "result=ALREADY_PRESENT")
                return Result.ALREADY_PRESENT
            }

            if (packages.isEmpty()) {
                val cached = parse(readLastGood(app))
                if (!cached.isEmpty()) {
                    packages.addAll(cached)
                    Log.i(TAG, "restored lastGood count=${cached.size}")
                }
            }

            val addedGms = packages.add(GMS_PACKAGE)
            val addedPlayStore = packages.add(PLAY_STORE_PACKAGE)
            val addedSelf = packages.add(app.packageName)
            val merged = TextUtils.join(",", packages)
            Log.i(TAG, "addingGms=$addedGms addingPlayStore=$addedPlayStore addingSelf=$addedSelf newCount=${packages.size}")

            val writeAccepted = Settings.System.putString(app.contentResolver, KEY, merged)
            Log.i(TAG, "putString accepted=$writeAccepted")

            if (!writeAccepted) {
                Log.w(TAG, "result=FAILED reason=putString_false")
                return Result.FAILED
            }

            val after = Settings.System.getString(app.contentResolver, KEY)
            val verifiedPackages = parse(after)
            val gmsVerified = verifiedPackages.contains(GMS_PACKAGE)
            val playStoreVerified = verifiedPackages.contains(PLAY_STORE_PACKAGE)
            val selfVerified = verifiedPackages.contains(app.packageName)

            if (!gmsVerified || !playStoreVerified || !selfVerified) {
                Log.w(TAG, "result=VERIFY_FAILED gms=$gmsVerified playStore=$playStoreVerified self=$selfVerified")
                return Result.VERIFY_FAILED
            }

            rememberLastGood(app, verifiedPackages)
            Log.i(TAG, "result=ADDED")
            Result.ADDED
        } catch (t: Throwable) {
            Log.e(TAG, "ensureGms FAILED error=" + errorOf(t), t)
            Result.FAILED
        }
    }

    fun readRaw(context: Context): String {
        return try {
            val raw = Settings.System.getString(context.applicationContext.contentResolver, KEY)
            raw ?: ""
        } catch (t: Throwable) {
            Log.w(TAG, "readRaw FAILED error=" + errorOf(t))
            ""
        }
    }

    private fun prefsContext(context: Context): Context {
        val app = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                return app.createDeviceProtectedStorageContext()
            } catch (ignored: Throwable) {}
        }
        return app
    }

    private fun readLastGood(context: Context): String? {
        return try {
            val prefs = prefsContext(context).getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(PREF_LAST_GOOD, null)
        } catch (t: Throwable) {
            Log.w(TAG, "readLastGood FAILED error=" + errorOf(t))
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
                Log.i(TAG, "lastGood saved count=${packages.size}")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "rememberLastGood FAILED error=" + errorOf(t))
        }
    }

    private fun parse(raw: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        if (raw.isNullOrBlank()) return out
        val normalized = raw.replace(';', ',').replace('\n', ',').replace('\t', ',')
        for (token in normalized.split(",".toRegex()).dropLastWhile { it.isEmpty() }) {
            val value = token.trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }

    private fun safeValue(value: String?): String {
        if (value == null) return "<null>"
        if (value.length <= 1200) return value
        return value.substring(0, 1200) + "...(len=" + value.length + ")"
    }

    private fun errorOf(t: Throwable?): String {
        if (t == null) return "unknown"
        return t.javaClass.simpleName + ":" + t.message
    }
}
