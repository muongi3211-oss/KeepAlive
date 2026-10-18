package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val action = intent?.action
            Log.i("BootReceiver", "Received action: $action")

            // Gọi đồng thời schedule từ MilletHelper hoặc MilletWatchScheduler an toàn
            try {
                MilletHelper.schedule(context)
            } catch (_: Throwable) {
                try {
                    MilletWatchScheduler.schedule(context)
                } catch (_: Throwable) {}
            }

            // Khi khởi động thiết bị hoặc mở khóa, tự động áp dụng bảo vệ Millet
            if (action == Intent.ACTION_BOOT_COMPLETED || 
                action == "android.intent.action.LOCKED_BOOT_COMPLETED" || 
                action == Intent.ACTION_USER_UNLOCKED ||
                action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                try {
                    MilletNoRestrict.applyProtection(context)
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.e("BootReceiver", "Error in onReceive: ${t.message}", t)
        }
    }
}
