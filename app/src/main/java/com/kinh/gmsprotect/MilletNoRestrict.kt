package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object MilletNoRestrict {
    const val KEY = "millet_no_restrict_app"

    fun canWrite(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun ensureGms(context: Context): Boolean {
        if (!canWrite(context)) return false
        try {
            val resolver = context.contentResolver
            val current = Settings.System.getString(resolver, KEY) ?: ""
            val list = current.split(",").toMutableList().map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

            // Thêm GMS, CH Play và tự bảo vệ chính app chống nightZone
            list.add("com.google.android.gms")
            list.add("com.android.vending")
            list.add(context.packageName)

            val updated = list.joinToString(",")
            if (updated != current) {
                Settings.System.putString(resolver, KEY, updated)
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    fun containsPackage(context: Context, packageName: String): Boolean {
        try {
            val current = Settings.System.getString(context.contentResolver, KEY) ?: ""
            return current.split(",").map { it.trim() }.contains(packageName)
        } catch (_: Throwable) {
            return false
        }
    }

    fun containsSelf(context: Context): Boolean = containsPackage(context, context.packageName)
    fun containsGms(context: Context): Boolean = containsPackage(context, "com.google.android.gms")
    fun containsPlayStore(context: Context): Boolean = containsPackage(context, "com.android.vending")
}
