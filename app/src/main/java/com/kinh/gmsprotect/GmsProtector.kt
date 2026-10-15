package com.kinh.gmsprotect

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

object GmsProtector {
    private const val TAG = "GmsProtector"
    private const val ACTION_REFRESH_MIPUSH = "com.xiaomi.mipush.REFRESH_WHITE_LIST_ACTION"

    fun protect(context: Context): Boolean {
        val app = context.applicationContext
        
        // 1. Kiểm tra GMS có tồn tại không
        try {
            val info = if (android.os.Build.VERSION.SDK_INT >= 33) {
                app.packageManager.getApplicationInfo(MilletNoRestrict.GMS_PACKAGE, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                app.packageManager.getApplicationInfo(MilletNoRestrict.GMS_PACKAGE, 0)
            }
            if (info.uid <= 0) return false
        } catch (e: Throwable) {
            return false
        }

        // 2. Kích hoạt Millet Whitelist
        val milletOk = MilletNoRestrict.ensureGms(app)
        if (!milletOk) {
            Log.e(TAG, "Millet Whitelist failed to apply")
            return false
        }

        // 3. Gửi MiPush Whitelist Broadcast tới HyperOS PowerKeeper
        return try {
            val intent = Intent(ACTION_REFRESH_MIPUSH).apply {
                putExtra("pkgName", MilletNoRestrict.GMS_PACKAGE)
                putExtra("option", 1)
            }
            app.sendBroadcast(intent)
            Log.i(TAG, "MiPush broadcast sent successfully")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "MiPush broadcast failed", e)
            false
        }
    }
}
