package com.example.fcmguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_USER_UNLOCKED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                startMonitorService(context)
                FcmKeepAliveJobService.scheduleJob(context)
            }
        }
    }

    private fun startMonitorService(context: Context) {
        // Tích hợp trực tiếp với FcmMonitorService sẵn có của bạn
        val serviceIntent = Intent(context, FcmMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
