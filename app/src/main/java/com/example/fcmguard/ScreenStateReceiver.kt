package com.example.fcmguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class ScreenStateReceiver : BroadcastReceiver() {

    companion object {
        private val handler = Handler(Looper.getMainLooper())
        private var appContextRef: Context? = null

        private val periodicRunnable = object : Runnable {
            override fun run() {
                appContextRef?.let { pingGoogleServices(it) }
                handler.postDelayed(this, 10 * 60 * 1000L)
            }
        }

        private fun pingGoogleServices(context: Context) {
            try {
                val gmsIntent = Intent("com.google.android.intent.action.MCS_HEARTBEAT").apply {
                    setPackage("com.google.android.gms")
                }
                context.sendBroadcast(gmsIntent)
                LogStore.add(context, "Aurogon Allowlist: Đã duy trì kết nối GMS thông minh.")
            } catch (e: Exception) {
                LogStore.add(context, "Lỗi Aurogon Pulse: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        appContextRef = appContext

        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                handler.removeCallbacksAndMessages(null)

                // 1. Nhịp 1: 40 giây sau khi tắt màn hình
                handler.postDelayed({
                    pingGoogleServices(appContext)
                }, 40 * 1000L)

                // 2. Nhịp 2: Phút thứ 2
                handler.postDelayed({
                    pingGoogleServices(appContext)
                }, 2 * 60 * 1000L)

                // 3. Nhịp 3: Bắt đầu chu kỳ lặp lại mỗi 10 phút
                handler.postDelayed(periodicRunnable, 12 * 60 * 1000L)
            }

            Intent.ACTION_SCREEN_ON -> {
                // Khôi phục 1 lần khi sáng màn hình và dừng lịch ngầm
                handler.removeCallbacksAndMessages(null)
                pingGoogleServices(appContext)
            }
        }
    }
}
