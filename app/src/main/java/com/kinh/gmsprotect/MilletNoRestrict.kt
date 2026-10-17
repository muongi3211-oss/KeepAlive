package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object MilletNoRestrict {
    private const val MILLET_NO_RESTRICT_APP = "millet_no_restrict_app"

    fun canWrite(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    fun ensureGms(context: Context): Boolean {
        if (!canWrite(context)) return false
        try {
            val resolver = context.contentResolver
            val current = Settings.System.getString(resolver, MILLET_NO_RESTRICT_APP) ?: ""
            val list = current.split(",").toMutableList().map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()

            // 1. Thêm Google Play Services
            list.add("com.google.android.gms")
            // 2. Thêm Google Play Store
            list.add("com.android.vending")
            // 3. THÊM CHÍNH BẢN THÂN APP (Giống bản 1.04 mới)
            list.add(context.packageName)

            val updated = list.joinToString(",")
            if (updated != current) {
                Settings.System.putString(resolver, MILLET_NO_RESTRICT_APP, updated)
            }
            return true
        } catch (_: Throwable) {
            return false
        }
    }

    fun containsSelf(context: Context): Boolean {
        val current = Settings.System.getString(context.contentResolver, MILLET_NO_RESTRICT_APP) ?: ""
        return current.contains(context.packageName)
    }

    fun containsGms(context: Context): Boolean {
        val current = Settings.System.getString(context.contentResolver, MILLET_NO_RESTRICT_APP) ?: ""
        return current.contains("com.google.android.gms")
    }

    fun containsPlayStore(context: Context): Boolean {
        val current = Settings.System.getString(context.contentResolver, MILLET_NO_RESTRICT_APP) ?: ""
        return current.contains("com.android.vending")
    }
}
