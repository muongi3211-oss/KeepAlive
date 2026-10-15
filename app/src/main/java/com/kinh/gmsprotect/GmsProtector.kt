package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object GmsProtector {
    private const val TAG = "GmsProtector"
    private const val ACTION_REFRESH_MIPUSH = "com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION"

    enum class Result {
        SENT, FAILED, UNSUPPORTED
    }

    fun protect(context: Context): Result {
        val app = context.applicationContext
        
        // 1. Kiểm tra GMS có tồn tại không
        try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getApplicationInfo("com.google.android.gms", PackageManager.ApplicationInfoFlags.of(0))
            } else {
                app.packageManager.getApplicationInfo("com.google.android.gms", 0)
            }
            if (info.uid <= 0) return Result.UNSUPPORTED
        } catch (_: Throwable) {
            return Result.UNSUPPORTED
        }

        // 2. Kích hoạt Millet Whitelist
        try {
            MilletNoRestrict.ensureGms(app)
        } catch (e: Throwable) {
            Log.e(TAG, "Millet Whitelist failed to apply", e)
            return Result.FAILED
        }

        // 3. Gửi MiPush Whitelist Broadcast tới HyperOS PowerKeeper
        return try {
            val intent = Intent(ACTION_REFRESH_MIPUSH).apply {
                putExtra("pkgName", "com.google.android.gms")
                putExtra("option", 1)
            }
            app.sendBroadcast(intent)
            Log.i(TAG, "MiPush broadcast sent successfully")
            Result.SENT
        } catch (e: Throwable) {
            Log.e(TAG, "MiPush broadcast failed", e)
            Result.FAILED
        }
    }
}
