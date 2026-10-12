package com.example.fcmguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private val handler = Handler(Looper.getMainLooper())
        private val periodicRunnable = object : Runnable {
            override fun run() {
                // Định kỳ mỗi 10 phút khi màn hình vẫn đang tắt[span_2](start_span)[span_2](end_span)
                appContextRef?.let { pingGoogleServices(it) }
                handler.postDelayed(this, 10 * 60 * 1000L)
            }
        }
        private var appContextRef: Context? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        appContextRef = appContext

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                // Xóa bỏ lịch trình cũ nếu có để tránh chồng chéo
                handler.removeCallbacksAndMessages(null)

                // 1. Nhịp 1: 40 giây sau khi tắt màn hình[span_3](start_span)[span_3](end_span)
                handler.postDelayed({
                    pingGoogleServices(appContext)
                }, 40 * 1000L)

                // 2. Nhịp 2: Phút thứ 2[span_4](start_span)[span_4](end_span)
                handler.postDelayed({
                    pingGoogleServices(appContext)
                }, 2 * 60 * 1000L)

                // 3. Nhịp 3: Bắt đầu chu kỳ lặp lại mỗi 10 phút tiếp theo[span_5](start_span)[span_5](end_span)
                handler.postDelayed(periodicRunnable, 12 * 60 * 1000L)
            }

            Intent.ACTION_SCREEN_ON -> {
                // Khi bật màn hình sẽ khôi phục thêm 1 lần rồi dừng toàn bộ lịch ngầm[span_6](start_span)[span_6](end_span)
                handler.removeCallbacksAndMessages(null)
                pingGoogleServices(appContext)
            }
        }
    }

    private static fun pingGoogleServices(context: Context) {
        try {
            val gmsIntent = Intent("com.google.android.intent.action.MCS_HEARTBEAT")
            gmsIntent.setPackage("com.google.android.gms")
            context.sendBroadcast(gmsIntent)
            LogStore.add(context, "Aurogon Allowlist: Đã duy trì kết nối GMS thông minh.")
        } catch (e: Exception) {
            LogStore.add(context, "Lỗi Aurogon Pulse: ${e.message}")
        }
    }
}
