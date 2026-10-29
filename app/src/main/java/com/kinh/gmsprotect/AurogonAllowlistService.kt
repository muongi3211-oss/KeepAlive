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

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOff = true
                    scheduleNextCheck()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOff = false
                    handler.removeCallbacksAndMessages(null)
                    // Khi vừa sáng màn hình: Củng cố bảo vệ nhẹ nhàng
                    performProtection(false)
                }
            }
        }
    }

    private val checkRunnable = Runnable {
        if (isScreenOff) {
            // Khi màn hình tắt: Thực hiện giữ nhịp định kỳ để chống timeout socket
            performProtection(true)
            scheduleNextCheck()
        }
    }

    private fun scheduleNextCheck() {
        handler.removeCallbacks(checkRunnable)
        if (!isScreenOff) return

        // Nhịp duy trì tối ưu khi màn hình tắt: 20 phút / lần
        // Đủ thưa để không làm reset socket liên tục, nhưng đủ gần để giữ kết nối FCM luôn sống
        val delayMillis: Long = 1_200_000L // 20 phút
        handler.postDelayed(checkRunnable, delayMillis)
    }

    private fun performProtection(isKeepAlive: Boolean) {
        try {
            MilletNoRestrict.ensureGms(this)
            if (isKeepAlive) {
                // Gửi ping giữ kết nối định kỳ để tránh hệ thống đóng băng socket TCP
                val intentGcm = Intent("com.google.android.intent.action.GCM_RECONNECT").apply {
                    setPackage("com.google.android.gms")
                }
                sendBroadcast(intentGcm)
            }
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
        performProtection(false)
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
