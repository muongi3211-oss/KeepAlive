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
        Log.d(TAG, "FcmMonitorService đã khởi tạo.")
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
                .setContentText("Đang duy trì danh sách trắng kết nối FCM")
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
        Log.d(TAG, "Thực thi bơm danh sách trắng cho Google Play Services...")
        
        // 1. Gửi tín hiệu ép đánh thức FCM
        sendFcmHeartbeat()

        // 2. Kích hoạt lệnh ngầm đẩy GMS vào danh sách ưu tiên của HyperOS / MIUI Security Center
        injectGmsIntoMiuiWhitelist()

        return START_STICKY
    }

    private fun sendFcmHeartbeat() {
        try {
            val heartbeatIntent = Intent("com.google.android.intent.action.MCS_HEARTBEAT").apply {
                setPackage("com.google.android.gms")
            }
            sendBroadcast(heartbeatIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat error: ${e.message}")
        }
    }

    // Cơ chế gọi ngầm các thành phần bảo vệ hệ thống của Xiaomi
    private fun injectGmsIntoMiuiWhitelist() {
        try {
            // Gửi lệnh cấu hình trực tiếp tới PowerKeeper / Security Center để bảo vệ GMS
            val intentsToTry = listOf(
                Intent("miui.intent.action.POWER_SAVE_MODE_CHANGE"),
                Intent("com.miui.powerkeeper.action.TOGGLE_POWER_MODE"),
                Intent("miui.intent.action.APP_ADJUST_MODE")
            )
            
            for (i in intentsToTry) {
                i.putExtra("package_name", "com.google.android.gms")
                i.putExtra("is_white_list", true)
                sendBroadcast(i)
            }
            
            // Đánh thức dịch vụ quản lý nền của MiPush / MILLET thông qua Broadcast ẩn
            val milletIntent = Intent("com.xiaomi.mipush.RECEIVE_ACTION").apply {
                setPackage("com.google.android.gms")
                putExtra("mipush_payload_type", 0)
            }
            sendBroadcast(milletIntent)

            Log.d(TAG, "Đã gửi lệnh chèn GMS vào danh sách trắng hệ thống thành công.")
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bơm danh sách trắng hệ thống: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            wakeLock?.release()
        } catch (e: Exception) {}

        // Tự phục hồi dịch vụ ngay lập tức khi bị hệ thống quét
        val restartIntent = Intent(applicationContext, FcmMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}
