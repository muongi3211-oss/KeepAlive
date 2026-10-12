package com.example.fcmguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class FcmMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "FcmGuardChannel"
        private const val NOTIFICATION_ID = 1001
    }

    override fun onCreate() {
        super.onCreate()
        // Bắt buộc gọi startForeground ngay khi service khởi tạo để tránh bị Android crash app
        startForegroundServiceNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            LogStore.add(applicationContext, "⚠️ Cảnh báo: App đã bị hệ thống kill ngầm và được khởi động lại!")
        } else {
            LogStore.add(applicationContext, "✅ Dịch vụ đang chạy ổn định.")
        }
        
        pingGoogleServices()
        return START_STICKY
    }


    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FCM Guard Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì kết nối FCM không bị trễ"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("FCM Guard đang chạy")
                .setContentText("Đang bảo vệ kết nối thông báo nền...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun pingGoogleServices() {
        try {
            val gmsIntent = Intent("com.google.android.intent.action.MCS_HEARTBEAT")
            gmsIntent.setPackage("com.google.android.gms")
            sendBroadcast(gmsIntent)
            LogStore.add(applicationContext, "Đã gửi tín hiệu giữ kết nối đến Google Play Services.")
        } catch (e: Exception) {
            LogStore.add(applicationContext, "Lỗi ping GMS: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
