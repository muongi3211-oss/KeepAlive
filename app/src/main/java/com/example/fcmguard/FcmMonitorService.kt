package com.example.fcmguard

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.content.ComponentName

class FcmMonitorService : Service() {

    companion object {
        private const val TAG = "FcmMonitorService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "FcmMonitorService đã được tạo.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "FcmMonitorService đang thực thi onStartCommand...")

        // Kích hoạt các cơ chế Intent ẩn của HyperOS / MIUI Security Center
        triggerMiuiDeepProtections()

        // Giữ dịch vụ chạy bền bỉ, tự động hồi sinh nếu bị hệ thống kill
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    // Cơ chế gọi các cổng Intent ẩn của Xiaomi Security Center & PowerKeeper
    private fun triggerMiuiDeepProtections() {
        try {
            // 1. Kích hoạt trang Quản lý tự khởi chạy (Autostart Management)
            val autoStartIntent = Intent().apply {
                component = ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(autoStartIntent)
            Log.d(TAG, "Đã gọi thành công Intent Autostart của Xiaomi.")
        } catch (e: Exception) {
            Log.e(TAG, "Không thể gọi Intent Autostart: ${e.message}")
        }

        try {
            // 2. Kích hoạt thành phần quản lý pin sâu (PowerKeeper / Hidden Apps Config)
            val powerIntent = Intent().apply {
                component = ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.PowerHideModelActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(powerIntent)
            Log.d(TAG, "Đã gọi thành công Intent PowerKeeper của Xiaomi.")
        } catch (e: Exception) {
            Log.e(TAG, "Không thể gọi Intent PowerKeeper: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "FcmMonitorService bị hủy, đang gửi tín hiệu tự khởi động lại...")
        
        // Gửi Broadcast để đánh thức lại dịch vụ nếu bị hệ thống quét dọn
        val restartIntent = Intent(applicationContext, FcmMonitorService::class.java)
        startService(restartIntent)
    }
}
