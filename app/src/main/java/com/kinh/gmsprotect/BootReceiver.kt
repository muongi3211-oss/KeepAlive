package com.kinh.gmsprotect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            
            // 1. Áp dụng bảo vệ Millet ngay khi boot 
            // (Đổi thành MainActivity.applyMilletProtection nếu bạn để hàm này trong MainActivity)
            MilletManager.applyMilletProtection(context)

            // 2. Kích hoạt JobScheduler chạy định kỳ ngầm
            MilletWatchJobService.scheduleJob(context)

            // 3. Khởi chạy ForegroundService duy trì tiến trình
            val serviceIntent = Intent(context, MyForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}
