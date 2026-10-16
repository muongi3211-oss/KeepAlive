package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_USER_UNLOCKED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            // Giữ cho tiến trình Broadcast sống sót an toàn trong lúc thực hiện tác vụ nặng sau khởi động
            val pending = goAsync()
            try {
                val appContext = context.applicationContext
                
                // 1. Khôi phục dịch vụ nền Aurogon nếu người dùng đã bật trước đó
                AurogonAllowlistService.startIfEnabled(appContext)
                
                // 2. Ép ghi ngay danh sách trắng Millet cho GMS & Play Store
                MilletNoRestrict.ensureGms(appContext)
                
                // 3. Tái lập lịch trình JobScheduler định kỳ
                MilletWatchJobService.schedule(appContext)
            } catch (_: Throwable) {
                // Bắt trọn mọi ngoại lệ hệ thống để không gây lỗi ANR
            } finally {
                // Hoàn tất tiến trình an toàn
                pending.finish()
            }
        }
    }
}
