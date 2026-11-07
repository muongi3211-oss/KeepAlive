package com.kinh.gmsprotect

import android.content.Context
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
            false
        }
    }

    fun canRead(context: Context): Boolean {
        return try {
            Settings.System.getString(context.applicationContext.contentResolver, KEY)
            true
        } catch (t: Throwable) {
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
            false
        }
    }

    fun ensureGms(context: Context): Result {
        val app = context.applicationContext
        
        // CHỐT AN TOÀN: Nếu chưa có quyền ghi hệ thống, trả về NO_PERMISSION ngay lập tức, không cố ghi tránh crash
        if (!canWrite(app)) return Result.NO_PERMISSION

        return try {
            val before = Settings.System.getString(app.contentResolver, KEY)
            val packages = parse(before)
            val hasGmsBefore = packages.contains(GMS_PACKAGE)
            val hasPlayStoreBefore = packages.contains(PLAY_STORE_PACKAGE)
            val hasSelfBefore = packages.contains(app.packageName)

            if (hasGmsBefore && hasPlayStoreBefore && hasSelfBefore) {
                rememberLastGood(app, packages)
                return Result.ALREADY_PRESENT
            }

            if (packages.isEmpty()) {
                val cached = parse(readLastGood(app))
                if (!cached.isEmpty()) {
                    packages.addAll(cached)
                }
            }

            packages.add(GMS_PACKAGE)
            packages.add(PLAY_STORE_PACKAGE)
            packages.add(app.packageName)
            val merged = TextUtils.join(",", packages)

            val writeAccepted = Settings.System.putString(app.contentResolver, KEY, merged)
            if (!writeAccepted) {
                return Result.FAILED
            }

            val after = Settings.System.getString(app.contentResolver, KEY)
            val verifiedPackages = parse(after)
            if (!verifiedPackages.contains(GMS_PACKAGE) || !verifiedPackages.contains(PLAY_STORE_PACKAGE) || !verifiedPackages.contains(app.packageName)) {
                return Result.VERIFY_FAILED
            }

            rememberLastGood(app, verifiedPackages)
            Result.ADDED
        } catch (t: Throwable) {
            Result.FAILED
        }
    }

    private fun readLastGood(context: Context): String? {
        return try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(PREF_LAST_GOOD, null)
        } catch (t: Throwable) {
            null
        }
    }

    private fun rememberLastGood(context: Context, packages: LinkedHashSet<String>) {
        if (packages.isEmpty()) return
        val normalized = TextUtils.join(",", packages)
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putString(PREF_LAST_GOOD, normalized).apply()
        } catch (t: Throwable) {}
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
}
