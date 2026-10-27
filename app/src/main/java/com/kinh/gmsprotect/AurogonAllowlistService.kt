package com.kinh.gmsprotect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper

class AurogonAllowlistService : Service() {

    companion object {
        fun startProtectionService(context: Context) {
            try {
                val intent = Intent(context, AurogonAllowlistService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {}
        }

        fun startIfEnabled(context: Context) {
            try {
                val prefs = context.getSharedPreferences("GMSProtectPrefs", Context.MODE_PRIVATE)
                val isAurogonEnabled = prefs.getBoolean("pref_aurogon_allowlist", false)
                
                MilletNoRestrict.applyProtection(context)
                MilletWatchScheduler.schedule(context)

                if (isAurogonEnabled) {
                    startProtectionService(context)
                }
            } catch (_: Throwable) {}
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var isScreenOff = false
    private var offStep = 0

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    offStep = 0
                    scheduleNextCheck()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                    handler.removeCallbacksAndMessages(null)
                    // Chỉ củng cố 1 lần nhẹ nhàng khi bật sáng màn hình, KHÔNG ép reconnect mạng
                    performQuietProtection()
                }
            }
        }
    }

    private val checkRunnable = Runnable {
        if (isScreenOff) {
            performQuietProtection()
            offStep++
            scheduleNextCheck()
        }
    }

    private fun scheduleNextCheck() {
        handler.removeCallbacks(checkRunnable)
        if (!isScreenOff) return

        // Giãn nhịp thưa hơn để tránh làm phiền hệ thống (4 phút -> 15 phút)
        val delayMillis: Long = when (offStep) {
            0 -> 240_000L     // 4 phút sau khi tắt màn
            else -> 900_000L  // Mỗi 15 phút tiếp theo
        }
        handler.postDelayed(checkRunnable, delayMillis)
    }

    private fun performQuietProtection() {
        try {
            // Chỉ gọi ghi bảo vệ Millet nếu thực sự cần thiết (bên trong ensureGms đã có kiểm tra thông minh)
            // TUYỆT ĐỐI KHÔNG gửi broadcast GCM_RECONNECT để tránh làm đứt ngắt socket FCM
            MilletNoRestrict.ensureGms(this)
        } catch (_: Throwable) {}
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        performQuietProtection()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(screenReceiver) } catch (_: Throwable) {}
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "aurogon_protection_channel"
            val channel = NotificationChannel(
                channelId,
                "Aurogon Protection Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, channelId)
                .setContentTitle("GMS Protect đang chạy ngầm")
                .setContentText("Duy trì bảo vệ Google Play services")
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .build()

            startForeground(1001, notification)
        }
    }
}
