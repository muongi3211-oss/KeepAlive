package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

object MilletNoRestrict {
    const val KEY = "millet_no_restrict_app"
    const val GMS_PACKAGE = "com.google.android.gms"
    const val PLAY_STORE_PACKAGE = "com.android.vending"

    enum class Result {
        ADDED, ALREADY_PRESENT, FAILED, NO_PERMISSION
    }

    fun canWrite(context: Context): Boolean {
        return try {
            Settings.System.canWrite(context)
        } catch (_: Throwable) {
            false
        }
    }

    fun contains(context: Context, pkg: String): Boolean {
        return try {
            val raw = Settings.System.getString(context.contentResolver, KEY) ?: ""
            raw.split(",").any { it.trim() == pkg }
        } catch (_: Throwable) {
            false
        }
    }

    fun containsGms(context: Context): Boolean = contains(context, GMS_PACKAGE)

    fun containsPlayStore(context: Context): Boolean = contains(context, PLAY_STORE_PACKAGE)

    fun containsSelf(context: Context): Boolean = contains(context, context.packageName)

    fun ensureGms(context: Context): Result {
        try {
            if (!canWrite(context)) {
                return Result.NO_PERMISSION
            }

            val resolver = context.contentResolver
            val current = Settings.System.getString(resolver, KEY) ?: ""
            
            // Các định danh mục tiêu bắt buộc phải có trong whitelist cấp thấp của Xiaomi
            val targets = listOf(
                GMS_PACKAGE,
                PLAY_STORE_PACKAGE,
                context.packageName
            )

            val set = current.split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableSet()

            var modified = false
            for (pkg in targets) {
                if (!set.contains(pkg)) {
                    set.add(pkg)
                    modified = true
                }
            }

            val newString = set.joinToString(",")
            val success = Settings.System.putString(resolver, KEY, newString)

            if (success) {
                // Kích hoạt đồng bộ hóa tín hiệu MiPush nền (Cơ chế gốc kích hoạt broadcast khẩn cấp)
                triggerMiPushSync(context)
                return if (modified) Result.ADDED else Result.ALREADY_PRESENT
            }
            
            return Result.FAILED
        } catch (e: Throwable) {
            Log.e("MilletNoRestrict", "Failed to ensure GMS in whitelist", e)
            return Result.FAILED
        }
    }

    private fun triggerMiPushSync(context: Context) {
        try {
            // Gửi broadcast chuẩn mô phỏng tín hiệu MiPush để hệ thống nhận diện và nạp lại policy ngay lập tức
            val intent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
                setPackage(GMS_PACKAGE)
                putExtra("mipush_payload_type", 0)
            }
            context.sendBroadcast(intent)
        } catch (_: Throwable) {}
    }
}
