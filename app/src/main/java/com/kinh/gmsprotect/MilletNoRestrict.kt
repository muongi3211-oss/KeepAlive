package com.kinh.gmsprotect

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import java.util.LinkedHashSet

/**
 * Best-effort HyperOS no-restrict helper.
 *
 * This uses the user-grantable WRITE_SETTINGS special access. It never replaces
 * the existing list blindly: current packages are read, normalized, preserved,
 * and GMS, Google Play Store, and GMS Protect itself are added idempotently. Every write is
 * verified by reading the setting back. Detailed logs are kept intentionally
 * small so failures on HyperOS can be diagnosed without guessing.
 */
object MilletNoRestrict {
    private const val TAG = "MilletNoRestrict"

    const val KEY = "MILLET_NO_RESTRICT_APP"
    val GMS_PACKAGE: String = GmsProtector.GMS_PACKAGE
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
                // Settings.System.canWrite() was introduced in API 23.
                // Pre-M devices check the install-time WRITE_SETTINGS permission.
                // ensureGms() still requires a successful putString + read-back.
                app.checkCallingOrSelfPermission(android.Manifest.permission.WRITE_SETTINGS) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
            } else {
                Settings.System.canWrite(app)
            }
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

    fun containsGms(context: Context): Boolean {
        return containsPackage(context, GMS_PACKAGE, "containsGms")
    }

    fun containsPlayStore(context: Context): Boolean {
        return containsPackage(context, PLAY_STORE_PACKAGE, "containsPlayStore")
    }

    fun containsSelf(context: Context): Boolean {
        return containsPackage(context, context.applicationContext.packageName, "containsSelf")
    }

    fun containsPackage(context: Context, packageName: String): Boolean {
        return containsPackage(context, packageName, "containsPackage")
    }

    private fun containsPackage(context: Context, packageName: String, logName: String): Boolean {
        return try {
            val raw = Settings.System.getString(
                context.applicationContext.contentResolver, KEY
            )
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

        if (!writeAllowed) {
            return Result.NO_PERMISSION
        }

        return try {
            val before = Settings.System.getString(app.contentResolver, KEY)
            val packages = parse(before)
            val hasGmsBefore = packages.contains(GMS_PACKAGE)
            val hasPlayStoreBefore = packages.contains(PLAY_STORE_PACKAGE)
            val hasSelfBefore = packages.contains(app.packageName)
            Log.i(
                TAG, "before containsGms=$hasGmsBefore"
                        + " containsPlayStore=$hasPlayStoreBefore"
                        + " containsSelf=$hasSelfBefore"
                        + " count=" + packages.size
                        + " value=" + safeValue(before)
            )

            if (hasGmsBefore && hasPlayStoreBefore && hasSelfBefore) {
                rememberLastGood(app, packages)
                Log.i(TAG, "result=ALREADY_PRESENT")
                return Result.ALREADY_PRESENT
            }

            // HyperOS can occasionally expose a transient empty value while its
            // power policy database is being rebuilt. Do not turn that temporary
            // empty state into a new one-item list and accidentally discard the
            // user's other no-restrict packages. Restore the last successfully
            // observed list first, then add GMS.
            if (packages.isEmpty()) {
                val cached = parse(readLastGood(app))
                if (!cached.isEmpty()) {
                    packages.addAll(cached)
                    Log.i(TAG, "restored lastGood count=" + cached.size)
                } else {
                    Log.i(TAG, "lastGood unavailable; starting from empty list")
                }
            }

            val addedGms = packages.add(GMS_PACKAGE)
            val addedPlayStore = packages.add(PLAY_STORE_PACKAGE)
            val addedSelf = packages.add(app.packageName)
            val merged = TextUtils.join(",", packages)
            Log.i(
                TAG, "addingGms=$addedGms"
                        + " addingPlayStore=$addedPlayStore"
                        + " addingSelf=$addedSelf"
                        + " newCount=" + packages.size
            )

            val writeAccepted = Settings.System.putString(
                app.contentResolver, KEY, merged
            )
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
            Log.i(
                TAG, "after containsGms=$gmsVerified"
                        + " containsPlayStore=$playStoreVerified"
                        + " containsSelf=$selfVerified"
                        + " value=" + safeValue(after)
            )

            if (!gmsVerified || !playStoreVerified || !selfVerified) {
                Log.w(
                    TAG, "result=VERIFY_FAILED gms=$gmsVerified"
                            + " playStore=$playStoreVerified"
                            + " self=$selfVerified"
                )
                return Result.VERIFY_FAILED
            }

            rememberLastGood(app, verifiedPackages)

            // Settings.System.putString() already notifies SettingsProvider observers.
            // Do not send a second manual notifyChange(): the JobScheduler
            // watcher receives the provider notification from this successful write.
            Log.i(TAG, "result=ADDED")
            Result.ADDED
        } catch (t: Throwable) {
            Log.e(TAG, "ensureGms FAILED error=" + errorOf(t), t)
            Result.FAILED
        }
    }

    fun readRaw(context: Context): String {
        return try {
            val raw = Settings.System.getString(
                context.applicationContext.contentResolver, KEY
            )
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
            } catch (ignored: Throwable) {
                // Fall back to normal app storage below.
            }
        }
        return app
    }

    private fun readLastGood(context: Context): String? {
        return try {
            val prefs = prefsContext(context)
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.getString(PREF_LAST_GOOD, null)
        } catch (t: Throwable) {
            Log.w(TAG, "readLastGood FAILED error=" + errorOf(t))
            null
        }
    }

    private fun rememberLastGood(context: Context, packages: LinkedHashSet<String>?) {
        if (packages == null || packages.isEmpty()) return

        val normalized = TextUtils.join(",", packages)
        try {
            val prefs = prefsContext(context)
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val old = prefs.getString(PREF_LAST_GOOD, null)
            if (normalized != old) {
                prefs.edit().putString(PREF_LAST_GOOD, normalized).apply()
                Log.i(TAG, "lastGood saved count=" + packages.size)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "rememberLastGood FAILED error=" + errorOf(t))
        }
    }

    private fun parse(raw: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        if (raw.isNullOrEmpty()) return out

        // ROM dumps have been observed with commas plus optional spaces. Accept
        // additional separators defensively without disturbing package names.
        val normalized = raw.replace(';', ',')
            .replace('\n', ',')
            .replace('\t', ',')

        for (token in normalized.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()) {
            val value = token.trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }

    private fun safeValue(value: String?): String {
        if (value == null) return "<null>"
        // This list is normally short, but avoid flooding logcat on unusual ROMs.
        if (value.length <= 1200) return value
        return value.substring(0, 1200) + "...(" + value.length + " chars)"
    }

    private fun errorOf(t: Throwable?): String {
        if (t == null) return "unknown"
        return t.javaClass.simpleName + ":" + t.message
    }
}
