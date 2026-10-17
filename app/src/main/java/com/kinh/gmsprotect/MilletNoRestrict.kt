package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object MilletNoRestrict {
    private const val KEY = "millet_no_restrict_app"

    fun applyProtection(context: Context): Boolean {
        try {
            val targetPackages = listOf(
                "com.google.android.gms",
                "com.android.vending",
                context.packageName
            ).joinToString(",")

            // Thử ghi bằng Settings.System chuẩn
            if (Settings.System.canWrite(context)) {
                Settings.System.putString(context.contentResolver, KEY, targetPackages)
            }

            // Fallback ép ghi bằng lệnh Shell (Hữu ích trên Android 15/16 khi Settings.System bị hạn chế)
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "settings put system $KEY \"$targetPackages\""))
                process.waitFor()
            } catch (_: Throwable) {
                // Nếu không có root, thử qua tiến trình sh thông thường
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "settings put system $KEY \"$targetPackages\""))
                    process.waitFor()
                } catch (_: Throwable) {}
            }

            return true
        } catch (e: Throwable) {
            return false
        }
    }

    private fun getActualSystemList(context: Context): List<String> {
        return try {
            val resolver = context.contentResolver
            val current = Settings.System.getString(resolver, KEY) ?: ""
            current.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } catch (e: Throwable) {
            emptyList()
        }
    }

    fun containsGms(context: Context): Boolean {
        return getActualSystemList(context).contains("com.google.android.gms")
    }

    fun containsPlayStore(context: Context): Boolean {
        return getActualSystemList(context).contains("com.android.vending")
    }

    fun containsSelf(context: Context): Boolean {
        return getActualSystemList(context).contains(context.packageName)
    }
}
