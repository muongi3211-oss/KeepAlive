package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object MilletNoRestrict {
    private const val KEY = "millet_no_restrict_app"

    // Khôi phục hàm ensureGms để các lớp khác gọi không bị lỗi Unresolved reference
    fun ensureGms(context: Context): Boolean {
        return applyProtection(context)
    }

    fun applyProtection(context: Context): Boolean {
        try {
            val resolver = context.contentResolver
            val current = Settings.System.getString(resolver, KEY) ?: ""
            val list = current.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()

            val targetPackages = listOf(
                "com.google.android.gms",
                "com.android.vending",
                context.packageName
            )

            var modified = false
            // Sửa lỗi cú pháp: Thêm dấu ngoặc tròn () cho vòng lặp trong Kotlin
            for (pkg in targetPackages) {
                if (!list.contains(pkg)) {
                    list.add(pkg)
                    modified = true
                }
            }

            if (modified || current.isEmpty()) {
                val updated = list.joinToString(",")
                Settings.System.putString(resolver, KEY, updated)
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
