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
                    // Khi sáng màn hình: Khôi phục 1 lần rồi dừng lịch
                    performProtection()
                }
            }
        }
    }

    private val checkRunnable = Runnable {
        if (isScreenOff) {
            performProtection()
            offStep++
            scheduleNextCheck()
        }
    }

    private fun scheduleNextCheck() {
        handler.removeCallbacks(checkRunnable)
        if (!isScreenOff) return

        // Nhịp nền đúng chuẩn app gốc: 40s -> 2 phút -> Mỗi 10 phút
        val delayMillis: Long = when (offStep) {
            0 -> 40_000L      // 40 giây sau khi tắt màn
            1 -> 120_000L     // Phút thứ 2
            else -> 600_000L  // Mỗi 10 phút tiếp theo khi màn vẫn tắt
        }
        handler.postDelayed(checkRunnable, delayMillis)
    }

    private fun performProtection() {
        try {
            MilletNoRestrict.applyProtection(this)
            // Gửi ping khôi phục FCM Socket
            val intentGcm = Intent("com.google.android.intent.action.GCM_RECONNECT").apply {
                setPackage("com.google.android.gms")
            }
            sendBroadcast(intentGcm)
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
        performProtection()
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
