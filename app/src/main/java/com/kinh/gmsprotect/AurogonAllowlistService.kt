package com.kinh.gmsprotect

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class AurogonAllowlistService : Service() {
    companion object {
        private const val PREFS = "setup_v1"
        private const val KEY_ENABLED = "aurogon_enabled_v1"
        private const val CHANNEL_ID = "aurogon_channel"
        private const val NOTIF_ID = 1001

        fun isEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
        }

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ENABLED, enabled).apply()
            if (enabled) {
                startIfEnabled(context)
            } else {
                stop(context)
            }
        }

        fun startIfEnabled(context: Context) {
            if (!isEnabled(context)) return
            try {
                val intent = Intent(context, AurogonAllowlistService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Throwable) {
                // Bắt ngoại lệ nếu hệ thống Android 14+ chặn startForegroundService
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, AurogonAllowlistService::class.java)
                context.stopService(intent)
            } catch (_: Throwable) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Gọi startForeground ngay trong onCreate() để tránh bị Android 14+ kill vì trễ thời gian
        try {
            createNotificationChannel()
            val notification = createNotification()
            startForeground(NOTIF_ID, notification)
        } catch (_: Throwable) {
            // Nếu bị chặn quyền Notification hoặc ForegroundService, tự ngắt service an toàn
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // Tự động duy trì và làm mới danh sách Millet mỗi khi Service được kích hoạt
            MilletNoRestrict.ensureGms(this)
        } catch (_: Throwable) {}
        return START_STICKY
    }

### Các điểm mấu chốt được đảm bảo trong đoạn code này:
* **Kiểm tra trạng thái bật/tắt (`isEnabled`)**: Mặc định là `false` khi mới mở app lần đầu, giúp ngăn chặn việc gọi Foreground Service đột ngột gây văng ứng dụng.
* **Đăng ký `startForeground()` ngay tại `onCreate()`**: Đáp ứng thời gian phản hồi nghiêm ngặt của Android 14+, tránh lỗi Timeout.
* **Mức độ ưu tiên thông báo `IMPORTANCE_MIN`**: Giúp thông báo chạy chìm hoàn toàn ở thanh trạng thái mà không phát ra âm thanh hay làm phiền người dùng.
