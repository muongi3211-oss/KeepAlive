package com.example.fcmguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var screenReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        acquireWakeLock()
        registerScreenReceiver()
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FcmGuard Background Protection",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("FcmGuard đang bảo vệ")
                .setContentText("Duy trì kết nối FCM ổn định")
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

    // Lắng nghe sự kiện bật màn hình để nhồi lại gói tin ngay lập tức
    private fun registerScreenReceiver() {
        screenReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_SCREEN_ON) {
                    Log.d(TAG, "Màn hình bật -> Chủ động bơm lại MCS_HEARTBEAT.")
                    sendFcmHeartbeat()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sendFcmHeartbeat()
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {}
        }
        try {
            wakeLock?.release()
        } catch (e: Exception) {}

        val restartIntent = Intent(applicationContext, FcmMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent)
        } else {
            startService(restartIntent)
        }
    }
}
