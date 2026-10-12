package com.example.fcmguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

class FcmMonitorService : Service() {

    companion object {
        private const val TAG = "FcmMonitorService"
        private const val CHANNEL_ID = "FcmGuardProtectionChannel"
        private const val NOTIF_ID = 1001
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        acquireWakeLock()
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FcmGuard Background Protection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Duy trì bảo vệ kết nối FCM 24/7"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("FcmGuard đang bảo vệ")
                .setContentText("Đang duy trì kết nối FCM ngầm")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build()
            
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "FcmGuard::Wakelock"
            ).apply {
                acquire(12 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.e(TAG, "WakeLock error: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Kích hoạt đồng thời cơ chế đánh thức MiPush và FCM Heartbeat
        triggerSystemProtection()
        return START_STICKY
    }

    private fun triggerSystemProtection() {
        try {
            // 1. Kích hoạt luồng nhận tin MiPush ẩn của Xiaomi Framework
            val milletIntent = Intent("com.xiaomi.mipush.RECEIVE_ACTION").apply {
                setPackage("com.google.android.gms")
                putExtra("mipush_payload_type", 0)
            }
            sendBroadcast(milletIntent)

            // 2. Bơm tín hiệu đánh thức MCS Heartbeat trực tiếp vào Google Play Services
            val heartbeatIntent = Intent("com.google.android.intent.action.MCS_HEARTBEAT").apply {
                setPackage("com.google.android.gms")
            }
            sendBroadcast(heartbeatIntent)

            Log.d(TAG, "Đã gửi tín hiệu tương tác hệ thống thành công.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi tương tác hệ thống: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            wakeLock?.release()
        } catch (e: Exception) {}

        // Tự phục hồi dịch vụ ngay lập tức khi bị hệ thống quét dọn
        val restartIntent = Intent(applicationContext, FcmMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}
