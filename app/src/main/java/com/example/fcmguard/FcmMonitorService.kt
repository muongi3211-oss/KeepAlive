package com.example.fcmguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class FcmMonitorService : Service() {

    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMyForeground()
        
        // Định kỳ 5 phút quét dấu hiệu gián tiếp một lần
        scheduler.scheduleAtFixedRate({
            checkIndirectSigns()
        }, 0, 5, TimeUnit.MINUTES)

        return START_STICKY
    }

    private fun checkIndirectSigns() {
        try {
            val context = applicationContext
            
            // 1. Kiểm tra trạng thái Doze Mode (Ngủ đông sâu của hệ thống)
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isIdle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                powerManager.isDeviceIdleMode
            } else {
                false
            }

            // 2. Kiểm tra khả năng kết nối mạng thực tế của tiến trình
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork
            val capabilities = cm.getNetworkCapabilities(network)
            val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

            // Ghi nhận log trạng thái
            LogStore.add(context, "Trạng thái: Doze Mode = $isIdle | Mạng khả dụng = $hasInternet")

            if (isIdle) {
                LogStore.add(context, "CẢNH BÁO: Máy vào chế độ ngủ sâu (Doze), kết nối FCM dễ bị bóp!")
            }
            if (!hasInternet) {
                LogStore.add(context, "CẢNH BÁO: Mạng nền bị cô lập, Socket FCM có nguy cơ mất kết nối!")
            }

        } catch (e: Exception) {
            Log.e("FcmGuard", "Error checking signs: ${e.message}")
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        LogStore.add(applicationContext, "CẢNH BÁO: App bị vuốt khỏi đa nhiệm (Recents)!")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        LogStore.add(applicationContext, "NGUY HIỂM: Process bị hệ thống Kill!")
        scheduler.shutdown()
        super.onDestroy()
    }

    private fun startMyForeground() {
        try {
            val channelId = "fcm_guard_channel"
            val notificationManager = getSystemService(NotificationManager::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    channelId,
                    "FCM Guard Monitor",
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationManager?.createNotificationChannel(channel)
            }

            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("FCM Guard đang chạy")
                .setContentText("Đang giám sát chống ngủ đông...")
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
            } else {
                startForeground(1, notification)
            }
        } catch (e: Exception) {
            Log.e("FcmGuard", "Error foreground: ${e.message}")
        }
    }
}
