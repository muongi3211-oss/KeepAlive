package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings
import android.util.Log

object MilletNoRestrict {
    const val KEY = "millet_no_restrict_app"
    const val GMS_PACKAGE = "com.google.android.gms"
    const val PLAY_STORE_PACKAGE = "com.android.vending"

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

    enum class Result {
        ADDED, ALREADY_PRESENT, FAILED, NO_PERMISSION
    }

    fun ensureGms(context: Context): Result {
        try {
            if (!canWrite(context)) {
                return Result.NO_PERMISSION
            }
            val current = Settings.System.getString(context.contentResolver, KEY) ?: ""
            val targets = listOf(GMS_PACKAGE, PLAY_STORE_PACKAGE, context.packageName)
            
            // Tách các gói hiện có thành một tập hợp (Set) để tránh trùng lặp
            val set = current.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            
            var modified = false
            for (pkg in targets) {
                if (!set.contains(pkg)) {
                    set.add(pkg)
                    modified = true
                }
            }

            if (modified || current.isEmpty()) {
                val newString = set.joinToString(",")
                val success = Settings.System.putString(context.contentResolver, KEY, newString)
                return if (success) Result.ADDED else Result.FAILED
            }
            return Result.ALREADY_PRESENT
        } catch (e: Throwable) {
            Log.e("MilletNoRestrict", "Ensure GMS failed", e)
            return Result.FAILED
        }
    }
}
