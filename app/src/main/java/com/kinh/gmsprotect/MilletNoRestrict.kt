package com.kinh.gmsprotect

import android.content.Context
import android.provider.Settings

object MilletNoRestrict {
    const val KEY = "millet_no_restrict_app"
    const val PLAY_STORE_PACKAGE = "com.android.vending"
    const val GMS_PACKAGE = "com.google.android.gms"

    enum class Result {
        ADDED, ALREADY_PRESENT, FAILED
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
            raw.contains(pkg)
        } catch (_: Throwable) {
            false
        }
    }

    fun containsGms(context: Context): Boolean = contains(context, GMS_PACKAGE)

    fun containsPlayStore(context: Context): Boolean = contains(context, PLAY_STORE_PACKAGE)

    fun containsSelf(context: Context): Boolean = contains(context, context.packageName)

    fun ensureGms(context: Context): Result {
        return try {
            if (!canWrite(context)) return Result.FAILED
            val current = Settings.System.getString(context.contentResolver, KEY) ?: ""
            val targets = listOf(GMS_PACKAGE, PLAY_STORE_PACKAGE, context.packageName)
            val builder = StringBuilder(current)
            var modified = false

            for (pkg in targets) {
                if (!builder.contains(pkg)) {
                    if (builder.isNotEmpty() && !builder.endsWith(",")) {
                        builder.append(",")
                    }
                    builder.append(pkg)
                    modified = true
                }
            }

            if (modified) {
                Settings.System.putString(context.contentResolver, KEY, builder.toString())
                Result.ADDED
            } else {
                Result.ALREADY_PRESENT
            }
        } catch (_: Throwable) {
            Result.FAILED
        }
    }
}
