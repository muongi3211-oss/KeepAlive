package com.kinh.gmsprotect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class MyForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "GMSProtectChannel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Khởi tạo thông báo Foreground Service bắt buộc cho Android
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Đảm bảo cờ Millet được kích hoạt lại khi service chạy
        MainActivity.applyMilletProtection(applicationContext)

        // START_STICKY giúp service tự động khởi động lại nếu bị hệ thống kill
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "GMS-Protect Running Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì tiến trình bảo vệ GMS ngầm"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("GMS-Protect đang hoạt động")
            .setContentText("Đang ngăn chặn hệ thống bóp tiến trình ngầm.")
            .setSmallIcon(android.R.drawable.ic_secure) // Bạn có thể thay bằng icon riêng của app: R.drawable.ten_icon
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
