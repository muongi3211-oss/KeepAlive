package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log

object MilletNoRestrict {
    private const val TAG = "MilletNoRestrict"
    const val KEY = "MILLET_NO_RESTRICT_APP"
    const val GMS_PACKAGE = "com.google.android.gms"
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    fun canWrite(context: Context): Boolean = try {
        Settings.System.canWrite(context.applicationContext)
    } catch (e: Throwable) {
        false
    }

    fun containsPackage(context: Context, pkg: String): Boolean = try {
        val raw = Settings.System.getString(context.applicationContext.contentResolver, KEY) ?: ""
        parsePackages(raw).contains(pkg)
    } catch (e: Throwable) {
        false
    }

    fun containsGms(context: Context): Boolean = containsPackage(context, GMS_PACKAGE)
    fun containsPlayStore(context: Context): Boolean = containsPackage(context, PLAY_STORE_PACKAGE)

    fun ensureGms(context: Context): Boolean {
        val app = context.applicationContext
        if (!canWrite(app)) return false

        try {
            val resolver = app.contentResolver
            val raw = Settings.System.getString(resolver, KEY) ?: ""
            val packages = parsePackages(raw).toMutableSet()

            val addedGms = packages.add(GMS_PACKAGE)
            val addedPlayStore = packages.add(PLAY_STORE_PACKAGE)
            val addedSelf = packages.add(app.packageName)

            // Luôn ghi đè và ép cập nhật để xóa cache cũ của hệ thống MIUI/HyperOS
            val merged = TextUtils.join(",", packages)
            val success = Settings.System.putString(resolver, KEY, merged)

            // Gửi một broadcast nội bộ hoặc kích hoạt lại service để ép hệ thống reload cache
            AurogonAllowlistService.startIfEnabled(app)

            val verifiedRaw = Settings.System.getString(resolver, KEY) ?: ""
            val verifiedPackages = parsePackages(verifiedRaw)

            return success && verifiedPackages.contains(GMS_PACKAGE) && verifiedPackages.contains(app.packageName)
        } catch (e: Throwable) {
            Log.e(TAG, "ensureGms failed", e)
            return false
        }
    }

    private fun parsePackages(raw: String): Set<String> {
        if (raw.isBlank()) return emptySet()
        return raw.replace(';', ',').replace('\n', ',').replace('\t', ',')
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
