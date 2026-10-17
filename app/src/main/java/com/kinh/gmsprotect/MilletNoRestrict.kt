package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object MilletNoRestrict {
    private const val KEY = "millet_no_restrict_app"

    fun ensureGms(context: Context): Boolean {
        try {
            val resolver = context.contentResolver
            val current = Settings.System.getString(resolver, KEY) ?: ""
            val list = current.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()

            // Danh sách các package cần được đưa vào danh sách trắng chống đóng băng
            val targetPackages = listOf(
                "com.google.android.gms",
                "com.android.vending",
                context.packageName
            )

            var modified = false
            for pkg in targetPackages {
                if (!list.contains(pkg)) {
                    list.add(pkg)
                    modified = true
                }
            }

            // Ghi lại dữ liệu vào Settings.System nếu có thay đổi hoặc chuỗi đang trống
            if (modified || current.isEmpty()) {
                val updated = list.joinToString(",")
                Settings.System.putString(resolver, KEY, updated)
            }
            return true
        } catch (e: Throwable) {
            return false
        }
    }

    private fun checkPackage(context: Context, pkgName: String): Boolean {
        return try {
            // Đảm bảo luôn tự động gọi ghi dữ liệu trước khi kiểm tra
            ensureGms(context)
            
            val current = Settings.System.getString(context.contentResolver, KEY) ?: ""
            val list = current.split(",").map { it.trim() }
            list.contains(pkgName)
        } catch (e: Throwable) {
            false
        }
    }

    fun containsGms(context: Context): Boolean = checkPackage(context, "com.google.android.gms")
    fun containsPlayStore(context: Context): Boolean = checkPackage(context, "com.android.vending")
    fun containsSelf(context: Context): Boolean = checkPackage(context, context.packageName)
}
